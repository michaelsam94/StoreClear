package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.presentation.screen.*
import com.example.presentation.viewmodel.StoreClearViewModel
import com.example.presentation.viewmodel.StoreClearViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as StoreClearApp
        val container = app.container

        val viewModelFactory = StoreClearViewModelFactory(
            context = applicationContext,
            getStorageSummaryUseCase = container.getStorageSummaryUseCase,
            scanStorageUseCase = container.scanStorageUseCase,
            findDuplicatesUseCase = container.findDuplicatesUseCase,
            buildHeatmapUseCase = container.buildHeatmapUseCase,
            shredFilesUseCase = container.shredFilesUseCase,
            cleanEmptyDirsUseCase = container.cleanEmptyDirsUseCase,
            cleanBrokenCacheUseCase = container.cleanBrokenCacheUseCase,
            hashRepository = container.hashRepository,
            shredRepository = container.shredRepository
        )

        setContent {
            val storeClearViewModel: StoreClearViewModel = viewModel(factory = viewModelFactory)
            val settingsState by storeClearViewModel.settingsState.collectAsState()

            MyApplicationTheme(darkTheme = settingsState.screenDarkTheme) {
                MainAppContent(viewModel = storeClearViewModel)
            }
        }
    }
}

@Composable
fun MainAppContent(viewModel: StoreClearViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomDestinations = listOf("home", "analyze", "shred", "settings")
    val showBottomBar = currentRoute in bottomDestinations

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == "home",
                        onClick = {
                            navController.navigate("home") {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == "analyze",
                        onClick = {
                            navController.navigate("analyze") {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Default.Layers, contentDescription = "Analyze") },
                        label = { Text("Analyze") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == "shred",
                        onClick = {
                            navController.navigate("shred") {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Default.Whatshot, contentDescription = "Shred") },
                        label = { Text("Shred") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == "settings",
                        onClick = {
                            navController.navigate("settings") {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") }
                    )
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home") {
                DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToSection = { section -> navController.navigate(section) }
                )
            }
            composable("analyze") {
                HeatmapAnalyzerScreen(viewModel = viewModel)
            }
            composable("shred") {
                SecureShredderScreen(viewModel = viewModel)
            }
            composable("settings") {
                SettingsScreen(viewModel = viewModel)
            }
            composable("duplicates") {
                DuplicateFinderScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable("empty_dirs") {
                EmptyDirsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable("cache") {
                CacheCleanerScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}
