package com.cloudflare.manager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.cloudflare.manager.ui.theme.CloudflareManagerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: MainActivityViewModel = hiltViewModel()
            val settings by viewModel.settings.collectAsState()
            val darkTheme = when {
                settings.followSystem -> isSystemInDarkTheme()
                settings.isDarkMode != null -> settings.isDarkMode == true
                else -> isSystemInDarkTheme()
            }

            CloudflareManagerTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CloudflareManagerAppContent()
                }
            }
        }
    }
}
