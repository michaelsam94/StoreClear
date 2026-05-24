package com.michael.storeclear.playstore

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.michael.storeclear.presentation.screen.DashboardScreen
import com.michael.storeclear.presentation.screen.DuplicateFinderScreen
import com.michael.storeclear.presentation.screen.HeatmapAnalyzerScreen
import com.michael.storeclear.presentation.screen.SecureShredderScreen
import com.michael.storeclear.presentation.viewmodel.StoreClearViewModel
import com.michael.storeclear.ui.theme.MyApplicationTheme

enum class PlayStoreScene {
    Dashboard,
    Duplicates,
    Heatmap,
    Shredder,
}

@Composable
fun PlayStoreScreenshotFrame(
    scene: PlayStoreScene,
    viewModel: StoreClearViewModel,
) {
    val settings by viewModel.settingsState.collectAsState()

    MyApplicationTheme(darkTheme = settings.screenDarkTheme) {
        when (scene) {
            PlayStoreScene.Dashboard -> {
                Scaffold(
                    bottomBar = { PlayStoreBottomBar(selected = "home") },
                    modifier = Modifier.fillMaxSize(),
                ) { padding ->
                    DashboardScreen(
                        viewModel = viewModel,
                        onNavigateToSection = {},
                        modifier = Modifier.padding(padding),
                    )
                }
            }
            PlayStoreScene.Duplicates -> {
                DuplicateFinderScreen(
                    viewModel = viewModel,
                    onNavigateBack = {},
                    autoLoad = false,
                )
            }
            PlayStoreScene.Heatmap -> {
                Scaffold(
                    bottomBar = { PlayStoreBottomBar(selected = "analyze") },
                    modifier = Modifier.fillMaxSize(),
                ) { padding ->
                    HeatmapAnalyzerScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(padding),
                        autoLoad = false,
                    )
                }
            }
            PlayStoreScene.Shredder -> {
                Scaffold(
                    bottomBar = { PlayStoreBottomBar(selected = "shred") },
                    modifier = Modifier.fillMaxSize(),
                ) { padding ->
                    SecureShredderScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayStoreBottomBar(selected: String) {
    NavigationBar {
        NavigationBarItem(
            selected = selected == "home",
            onClick = {},
            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
            label = { Text("Home") },
        )
        NavigationBarItem(
            selected = selected == "analyze",
            onClick = {},
            icon = { Icon(Icons.Default.Layers, contentDescription = "Analyze") },
            label = { Text("Analyze") },
        )
        NavigationBarItem(
            selected = selected == "shred",
            onClick = {},
            icon = { Icon(Icons.Default.Whatshot, contentDescription = "Shred") },
            label = { Text("Shred") },
        )
        NavigationBarItem(
            selected = selected == "settings",
            onClick = {},
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            label = { Text("Settings") },
        )
    }
}
