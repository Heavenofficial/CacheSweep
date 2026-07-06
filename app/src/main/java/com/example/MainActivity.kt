package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.root.RootState
import com.example.ui.screens.CleanScreen
import com.example.ui.screens.RootRequiredScreen
import com.example.ui.screens.WhitelistScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.CacheSweepViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: CacheSweepViewModel = viewModel()
                val rootState by viewModel.rootState.collectAsState()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (rootState) {
                        RootState.UNKNOWN, RootState.CHECKING -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        RootState.DENIED -> {
                            RootRequiredScreen(
                                onRetryClick = { viewModel.checkRootAndLoad() }
                            )
                        }
                        RootState.GRANTED -> {
                            val navController = rememberNavController()
                            NavHost(
                                navController = navController,
                                startDestination = "clean"
                            ) {
                                composable("clean") {
                                    CleanScreen(
                                        viewModel = viewModel,
                                        onNavigateToWhitelist = {
                                            navController.navigate("whitelist")
                                        }
                                    )
                                }
                                composable("whitelist") {
                                    WhitelistScreen(
                                        viewModel = viewModel,
                                        onNavigateBack = {
                                            navController.popBackStack()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
