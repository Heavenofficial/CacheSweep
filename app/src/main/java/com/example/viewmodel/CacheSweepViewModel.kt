package com.example.viewmodel

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.AppInfo
import com.example.repository.WhitelistRepository
import com.example.root.RootShell
import com.example.root.RootState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface CleanState {
    object Idle : CleanState
    data class Cleaning(
        val currentAppName: String,
        val currentIndex: Int,
        val totalCount: Int,
        val progress: Float
    ) : CleanState
    data class Finished(
        val appsCleanedCount: Int,
        val totalBytesFreed: Long
    ) : CleanState
}

class CacheSweepViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val repository = WhitelistRepository(context)
    private val rootShell = RootShell()

    private val _rootState = MutableStateFlow(RootState.UNKNOWN)
    val rootState: StateFlow<RootState> = _rootState.asStateFlow()

    private val _allApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val allApps: StateFlow<List<AppInfo>> = _allApps.asStateFlow()

    private val _whitelistedPackages = MutableStateFlow<Set<String>>(emptySet())
    val whitelistedPackages: StateFlow<Set<String>> = _whitelistedPackages.asStateFlow()

    private val _combinedCacheSize = MutableStateFlow<Long>(0L)
    val combinedCacheSize: StateFlow<Long> = _combinedCacheSize.asStateFlow()

    private val _isCalculatingCache = MutableStateFlow(false)
    val isCalculatingCache: StateFlow<Boolean> = _isCalculatingCache.asStateFlow()

    private val _cleanState = MutableStateFlow<CleanState>(CleanState.Idle)
    val cleanState: StateFlow<CleanState> = _cleanState.asStateFlow()

    init {
        checkRootAndLoad()
    }

    fun checkRootAndLoad() {
        viewModelScope.launch {
            _rootState.value = RootState.CHECKING
            val state = rootShell.checkAndInitRoot()
            _rootState.value = state
            if (state == RootState.GRANTED) {
                // Load whitelist first, then list apps and measure cache
                _whitelistedPackages.value = repository.whitelistedPackages.first()
                loadAppsAndCacheSizes()
                
                // Observe whitelist changes to update local flow
                viewModelScope.launch {
                    repository.whitelistedPackages.collect { list ->
                        _whitelistedPackages.value = list
                        recalculateCombinedCacheSize()
                    }
                }
            }
        }
    }

    private fun loadAppsAndCacheSizes() {
        viewModelScope.launch(Dispatchers.Default) {
            _isCalculatingCache.value = true
            try {
                val packageManager = context.packageManager
                val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
                } else {
                    packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                }

                // Filter out CacheSweep itself from the list
                val ownPackageName = context.packageName
                val filteredApps = packages.filter { it.packageName != ownPackageName }

                // Map packageNames
                val packageNames = filteredApps.map { it.packageName }
                
                // Fetch cache sizes via batched du -sk
                val sizeMap = queryCacheSizes(packageNames)

                val appList = filteredApps.map { app ->
                    val appName = app.loadLabel(packageManager).toString()
                    val pkg = app.packageName
                    val size = sizeMap[pkg] ?: 0L
                    val whitelisted = _whitelistedPackages.value.contains(pkg)
                    AppInfo(
                        packageName = pkg,
                        appName = appName,
                        cacheSize = size,
                        isWhitelisted = whitelisted
                    )
                }

                _allApps.value = appList
                recalculateCombinedCacheSize()
            } catch (e: Exception) {
                Log.e("CacheSweepVM", "Error loading apps and cache sizes", e)
            } finally {
                _isCalculatingCache.value = false
            }
        }
    }

    private suspend fun queryCacheSizes(packageNames: List<String>): Map<String, Long> = withContext(Dispatchers.IO) {
        val sizeMap = mutableMapOf<String, Long>()
        
        // Build path chunks to du -sk
        val paths = packageNames.flatMap { pkg ->
            listOf(
                "/data/data/$pkg/cache",
                "/data/data/$pkg/code_cache",
                "/sdcard/Android/data/$pkg/cache",
                "/storage/emulated/0/Android/data/$pkg/cache"
            )
        }

        // Chunk by 120 paths (about 30 apps at a time) to ensure command doesn't exceed buffer limits
        paths.chunked(120).forEach { chunk ->
            val cmd = "du -sk ${chunk.joinToString(" ")} 2>/dev/null"
            val result = rootShell.execute(cmd)
            
            result.stdout.lines().forEach { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size >= 2) {
                    val kb = parts[0].toLongOrNull() ?: 0L
                    val path = parts[1]
                    val pkg = extractPackageName(path)
                    if (pkg != null) {
                        sizeMap[pkg] = (sizeMap[pkg] ?: 0L) + (kb * 1024L) // Convert KB to bytes
                    }
                }
            }
        }
        sizeMap
    }

    private fun extractPackageName(path: String): String? {
        if (path.contains("Android/data/")) {
            val parts = path.split("Android/data/")
            if (parts.size > 1) {
                return parts[1].split("/").firstOrNull()
            }
        }
        if (path.contains("/data/data/")) {
            val parts = path.split("/data/data/")
            if (parts.size > 1) {
                return parts[1].split("/").firstOrNull()
            }
        }
        if (path.contains("/data/user/")) {
            val parts = path.split("/data/user/")
            if (parts.size > 1) {
                val userParts = parts[1].split("/")
                if (userParts.size > 1) {
                    return userParts[1] // userParts[0] is user id e.g. "0", userParts[1] is package name
                }
            }
        }
        return null
    }

    private fun recalculateCombinedCacheSize() {
        val currentWhitelist = _whitelistedPackages.value
        val sum = _allApps.value
            .filter { !currentWhitelist.contains(it.packageName) }
            .sumOf { it.cacheSize }
        _combinedCacheSize.value = sum
    }

    fun toggleWhitelist(packageName: String) {
        viewModelScope.launch {
            val current = _whitelistedPackages.value
            val newSet = if (current.contains(packageName)) {
                repository.removeFromWhitelist(packageName)
                current - packageName
            } else {
                repository.addToWhitelist(packageName)
                current + packageName
            }
            _whitelistedPackages.value = newSet
            
            // Update _allApps status in memory
            _allApps.value = _allApps.value.map {
                if (it.packageName == packageName) {
                    it.copy(isWhitelisted = newSet.contains(packageName))
                } else {
                    it
                }
            }
            recalculateCombinedCacheSize()
        }
    }

    fun selectAllWhitelist(select: Boolean) {
        viewModelScope.launch {
            val newSet = if (select) {
                _allApps.value.map { it.packageName }.toSet()
            } else {
                emptySet()
            }
            repository.saveWhitelist(newSet)
            _whitelistedPackages.value = newSet
            
            _allApps.value = _allApps.value.map {
                it.copy(isWhitelisted = select)
            }
            recalculateCombinedCacheSize()
        }
    }

    fun clearAllCache() {
        if (_rootState.value != RootState.GRANTED) return
        
        viewModelScope.launch(Dispatchers.Default) {
            val currentWhitelist = _whitelistedPackages.value
            // Filter apps that are not whitelisted
            val appsToClean = _allApps.value.filter { !currentWhitelist.contains(it.packageName) }
            val totalCount = appsToClean.size
            
            if (totalCount == 0) {
                _cleanState.value = CleanState.Finished(0, 0L)
                return@launch
            }

            var totalBytesFreed = 0L
            var appsCleanedCount = 0

            appsToClean.forEachIndexed { index, app ->
                _cleanState.value = CleanState.Cleaning(
                    currentAppName = app.appName,
                    currentIndex = index + 1,
                    totalCount = totalCount,
                    progress = (index + 1).toFloat() / totalCount
                )

                // Cache size of this app before cleaning
                val freedBytes = app.cacheSize
                
                // Perform the actual deletion in root shell
                val pkg = app.packageName
                val cleanCmd = "rm -rf /data/data/$pkg/cache/* /data/data/$pkg/code_cache/* /sdcard/Android/data/$pkg/cache/* /storage/emulated/0/Android/data/$pkg/cache/*"
                
                // Execute command blockingly in persistent shell
                withContext(Dispatchers.IO) {
                    rootShell.execute(cleanCmd)
                }

                totalBytesFreed += freedBytes
                if (freedBytes > 0) {
                    appsCleanedCount++
                } else {
                    // Even if pre-measured size is 0, count as cleaned if directory clear was triggered
                    appsCleanedCount++
                }
            }

            _cleanState.value = CleanState.Finished(appsCleanedCount, totalBytesFreed)
            
            // Re-scan sizes so the UI shows updated sizes
            loadAppsAndCacheSizes()
        }
    }

    fun resetCleanState() {
        _cleanState.value = CleanState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            rootShell.closeSession()
        }
    }
}
