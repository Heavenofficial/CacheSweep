package com.example.root

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

enum class RootState {
    UNKNOWN,
    CHECKING,
    GRANTED,
    DENIED
}

data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
)

class RootShell {
    private var process: Process? = null
    private var os: DataOutputStream? = null
    private var reader: BufferedReader? = null
    private val mutex = Mutex()

    suspend fun checkAndInitRoot(): RootState = mutex.withLock {
        withContext(Dispatchers.IO) {
            Log.d("RootShell", "Initializing root shell session...")
            closeSessionInternal()
            
            try {
                val p = ProcessBuilder("su").start()
                val dos = DataOutputStream(p.outputStream)
                
                // Check identity to confirm root
                dos.writeBytes("id\n")
                dos.writeBytes("echo 'ROOT_SUCCESS'\n")
                dos.flush()
                
                val r = BufferedReader(InputStreamReader(p.inputStream))
                var success = false
                
                // Read lines with simple timeout
                val startTime = System.currentTimeMillis()
                while (System.currentTimeMillis() - startTime < 3000) {
                    if (r.ready()) {
                        val line = r.readLine() ?: break
                        Log.d("RootShell", "init output: $line")
                        if (line.contains("ROOT_SUCCESS") || line.contains("uid=0")) {
                            success = true
                            break
                        }
                    } else {
                        Thread.sleep(50)
                    }
                }
                
                if (success) {
                    process = p
                    os = dos
                    reader = r
                    Log.d("RootShell", "Root session granted successfully!")
                    RootState.GRANTED
                } else {
                    Log.d("RootShell", "Root session initiation failed or timed out.")
                    p.destroy()
                    RootState.DENIED
                }
            } catch (e: Exception) {
                Log.e("RootShell", "Error starting su process", e)
                RootState.DENIED
            }
        }
    }

    suspend fun execute(command: String): CommandResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            val p = process ?: return@withContext CommandResult(-1, "", "No root session")
            val dos = os ?: return@withContext CommandResult(-1, "", "No root output stream")
            val r = reader ?: return@withContext CommandResult(-1, "", "No root input stream")
            
            try {
                val marker = "CMD_END_MARKER_" + System.currentTimeMillis()
                // Redirect stderr to stdout so we read both through a single stream safely
                dos.writeBytes("$command 2>&1; echo \"$marker \$?\"\n")
                dos.flush()
                
                val stdoutBuilder = StringBuilder()
                var exitCode = -1
                
                while (true) {
                    val line = r.readLine() ?: break
                    if (line.startsWith(marker)) {
                        val parts = line.split(" ")
                        if (parts.size >= 2) {
                            exitCode = parts[1].toIntOrNull() ?: -1
                        }
                        break
                    }
                    stdoutBuilder.append(line).append("\n")
                }
                
                CommandResult(exitCode, stdoutBuilder.toString().trim(), "")
            } catch (e: Exception) {
                Log.e("RootShell", "Error executing command: $command", e)
                closeSessionInternal()
                CommandResult(-1, "", e.message ?: "Execution error")
            }
        }
    }

    suspend fun closeSession() = mutex.withLock {
        closeSessionInternal()
    }

    private fun closeSessionInternal() {
        try {
            os?.writeBytes("exit\n")
            os?.flush()
            process?.destroy()
        } catch (e: Exception) {
            // Ignore
        } finally {
            process = null
            os = null
            reader = null
        }
    }
}
