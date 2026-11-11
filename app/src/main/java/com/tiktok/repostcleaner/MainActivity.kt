package com.tiktok.repostcleaner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.tiktok.repostcleaner.presentation.screens.MainScreen
import com.tiktok.repostcleaner.presentation.theme.TikTokBlack
import com.tiktok.repostcleaner.presentation.theme.TikTokRepostCleanerTheme
import com.tiktok.repostcleaner.presentation.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            TikTokRepostCleanerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = TikTokBlack
                ) {
                    val uiState by viewModel.uiState.collectAsState()

                    MainScreen(
                        uiState = uiState,
                        onCheckStatus = { viewModel.refreshTikTokStatus() },
                        onOpenAccessibility = { viewModel.openAccessibilitySettings() },
                        onOpenTikTok = { viewModel.openTikTok() },
                        onStartScan = { viewModel.startScan() },
                        onStartDelete = { viewModel.startDeletion() },
                        onPause = { viewModel.pauseDeletion() },
                        onResume = { viewModel.resumeDeletion() },
                        onStop = { viewModel.stopDeletion() },
                        onRescan = { viewModel.startRescan() },
                        onClearError = { viewModel.clearError() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check accessibility service status when returning from settings
        viewModel.refreshTikTokStatus()
    }
}
