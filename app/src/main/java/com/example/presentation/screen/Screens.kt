package com.example.presentation.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.domain.model.*
import com.example.domain.repository.ShredHistoryLog
import com.example.presentation.component.*
import com.example.presentation.viewmodel.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: StoreClearViewModel,
    onNavigateToSection: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.dashboardState.collectAsState()
    val context = LocalContext.current

    val treeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.setRootUri(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = "StoreClear",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground,
                                letterSpacing = (-0.5).sp
                            )
                        )
                        Text(
                            text = "SECURE STORAGE MANAGER",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        if (state.rootUriString == null) {
            // Storage access onboarding card
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "Folder",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(54.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Grant Storage Permission",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "StoreClear works 100% on-device. Grant access to the root directory to find duplicates, analyze folder trees, clean caches, and shred files completely offline.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = { treeLauncher.launch(null) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    Icon(imageVector = Icons.Default.LockOpen, contentDescription = "Grant")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select Storage Root", fontWeight = FontWeight.Bold)
                }
            }
        } else {
            // Normal dashboard with active charts
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    state.storageSummary?.let { summary ->
                        StorageDonutCard(summary = summary)
                    } ?: Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                // Grid of Actions
                item {
                    Text(
                        text = "QUICK CLEAN TOOLS",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        QuickActionCard(
                            title = "Shredder",
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Whatshot,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    contentDescription = "Shredder"
                                )
                            },
                            onClick = { onNavigateToSection("shred") },
                            modifier = Modifier.weight(1f)
                        )
                        QuickActionCard(
                            title = "Duplicates",
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.CopyAll,
                                    tint = MaterialTheme.colorScheme.primary,
                                    contentDescription = "Duplicates"
                                )
                            },
                            onClick = { onNavigateToSection("duplicates") },
                            modifier = Modifier.weight(1f)
                        )
                        QuickActionCard(
                            title = "Empty Dirs",
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.FolderOff,
                                    tint = Color(0xFF06B6D4),
                                    contentDescription = "Empty Dirs"
                                )
                            },
                            onClick = { onNavigateToSection("empty_dirs") },
                            modifier = Modifier.weight(1f)
                        )
                        QuickActionCard(
                            title = "Cache",
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.CleaningServices,
                                    tint = Color(0xFFEAB308),
                                    contentDescription = "Cache"
                                )
                            },
                            onClick = { onNavigateToSection("cache") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Text(
                        text = "STORAGE OVERVIEW",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToSection("analyze") }
                            .padding(bottom = 24.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = "Heatmap",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Visual Heatmap Analyzer",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "See which subfolders contain the largest files in a responsive grid layout.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Go",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- DUPLICATE FINDER SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicateFinderScreen(
    viewModel: StoreClearViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.duplicateState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.runDuplicateFinder()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Duplicate Finder", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            if (state.scanResult is ScanResult.Success) {
                val groups = (state.scanResult as ScanResult.Success).duplicateGroups
                val totalSelectedBytes = groups.flatMap { it.files }
                    .filter { state.checkedFiles.contains(it.uriString) }
                    .sumOf { it.sizeBytes }

                if (state.checkedFiles.isNotEmpty()) {
                    Surface(
                        tonalElevation = 8.dp,
                        modifier = Modifier.navigationBarsPadding()
                    ) {
                        Button(
                            onClick = { viewModel.deleteSelectedDuplicates() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Remove ${state.checkedFiles.size} duplicate files · Free ${formatSize(totalSelectedBytes)}",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val res = state.scanResult) {
                is ScanResult.Idle -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is ScanResult.Scanning -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Phase: ${res.progress.statusText}",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = res.progress.percentage / 100f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "${res.progress.percentage}%",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                is ScanResult.Success -> {
                    if (res.duplicateGroups.isEmpty()) {
                        DuplicatesEmptyState(modifier = Modifier.fillMaxSize())
                    } else {
                        // Header cards
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val totalRecoverableStr = formatSize(res.duplicateGroups.sumOf { it.totalRecoverableSpace })
                                    Card(
                                        modifier = Modifier.weight(1f),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text("Recoverable", style = MaterialTheme.typography.labelSmall)
                                            Text(
                                                totalRecoverableStr,
                                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                                            )
                                        }
                                    }
                                    Card(
                                        modifier = Modifier.weight(1f),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text("Groups", style = MaterialTheme.typography.labelSmall)
                                            Text(
                                                "${res.duplicateGroups.size}",
                                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                                            )
                                        }
                                    }
                                }
                            }

                            items(res.duplicateGroups.size) { index ->
                                val group = res.duplicateGroups[index]
                                val firstFile = group.files.firstOrNull() ?: return@items
                                val isExpanded = state.expandedGroupIndex == index

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { viewModel.toggleGroupExpanded(index) },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(MaterialTheme.colorScheme.background),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.InsertDriveFile,
                                                    contentDescription = "File",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = firstFile.name,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${group.files.size} copies · ${formatSize(group.sizeBytes)} each",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Icon(
                                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                contentDescription = "Expand"
                                            )
                                        }

                                        if (isExpanded) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            HorizontalDivider()
                                            Spacer(modifier = Modifier.height(8.dp))
                                            
                                            // Sort files by lastModified
                                            val sortedFiles = group.files.sortedBy { it.lastModified }
                                            sortedFiles.forEachIndexed { fIdx, file ->
                                                val isOriginal = fIdx == 0
                                                val isChecked = state.checkedFiles.contains(file.uriString)

                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Checkbox(
                                                        checked = isChecked,
                                                        onCheckedChange = {
                                                            if (!isOriginal) {
                                                                viewModel.toggleDuplicateChecked(file.uriString)
                                                            }
                                                        },
                                                        enabled = !isOriginal
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = if (isOriginal) "Original" else "Duplicate Copy",
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isOriginal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                        Text(
                                                            text = file.path.substringAfter("Primary:"),
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis,
                                                            fontSize = 11.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                }
                is ScanResult.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "An error occurred: ${res.message}", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (state.isDeleting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Deleting selected copies...", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// --- VISUAL HEATMAP SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeatmapAnalyzerScreen(
    viewModel: StoreClearViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.heatmapState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadHeatmap()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Storage Heatmap", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (state.isLoading) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                state.currentNode?.let { current ->
                    // Breadcrumbs Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (state.nodeHistory.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.navigateUpHeatNode() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Up",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        
                        Text(
                            text = current.path.substringAfterLast("/", "/"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Heatmap representation
                    HeatmapCanvas(
                        rootNode = current,
                        onCellClick = { viewModel.navigateIntoHeatNode(it) },
                        modifier = Modifier.padding(16.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Children directory breakdown list
                    Text(
                        text = "SUBFOLDERS IN COCHLEAR",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        val validChildren = current.children.filter { it.sizeBytes > 0 }
                        if (validChildren.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "No children subdirectories with files detected.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            items(validChildren) { child ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (child.children.isNotEmpty()) {
                                                viewModel.navigateIntoHeatNode(child)
                                            }
                                        }
                                        .padding(vertical = 12.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = "Folder",
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = child.name,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "${child.children.size} folders",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = formatSize(child.sizeBytes),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- SECURE FILE SHREDDER SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecureShredderScreen(
    viewModel: StoreClearViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.shredState.collectAsState()
    val settings by viewModel.settingsState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.runShredFile(uri)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Secure Shredder", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Options picker banner
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Shield",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Intensity: ${settings.intensity.name}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "DoD 5220.22-M with ${settings.intensity.passCount} overwrite passes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = { fileLauncher.launch(arrayOf("*/*")) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Add File")
                    }
                }
            }

            // Shred Queue List
            Text(
                text = "ACTIVE QUEUED SHRED JOBS",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            if (state.activeJobs.isEmpty()) {
                ShredderEmptyState(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.activeJobs) { job ->
                        val isDone = job.status == ShredStatus.DONE
                        val isProgress = job.status == ShredStatus.SHREDDING

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isDone) Icons.Default.CheckCircle else Icons.Default.Whatshot,
                                        contentDescription = "Status",
                                        tint = if (isDone) Color.Green else MaterialTheme.colorScheme.tertiary
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = job.fileName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            text = if (isDone) "Destroyed securely" else "Pass ${job.currentPass}/${job.totalPasses} · Overwriting...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = formatSize(job.fileSize),
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (isProgress) {
                                    val progressFraction = if (job.fileSize > 0) job.bytesWritten.toFloat() / job.fileSize else 0f
                                    Spacer(modifier = Modifier.height(12.dp))
                                    LinearProgressIndicator(
                                        progress = progressFraction,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(4.dp))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Certificate Bottom Sheet Popup
        state.currentCertificate?.let { cert ->
            AlertDialog(
                onDismissRequest = { viewModel.clearCertificate() },
                confirmButton = {
                    Button(onClick = { viewModel.clearCertificate() }) {
                        Text("Acknowledge")
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.VerifiedUser, tint = Color.Green, contentDescription = "Verified")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Certificate of Destruction", fontWeight = FontWeight.Black, fontSize = 18.sp)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "FILE NAME: ${cert.fileName}", fontWeight = FontWeight.Bold)
                        Text(text = "SIZE DESTROYED: ${formatSize(cert.fileSize)}")
                        Text(text = "SHRED ALGORITHM: DoD 5220.22-M (${cert.totalPasses} Passes)")
                        val formattedTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                        Text(text = "OPERATION DATE: $formattedTime UTC")
                        Text(text = "METHODOLOGY: Encrypted SecureRandom / Multiphase Buffer Overwritings")
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                "SIGNATURE: SEC_STORE_CLEAR_KERNEL_PASS_VERIFIED",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            )
        }
    }
}

// --- EMPTY DIRS CLEANUP SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmptyDirsScreen(
    viewModel: StoreClearViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.emptyDirsState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.scanEmptyFolders()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Empty Folder Cleaner", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            if (state.emptyDirs.isNotEmpty() && state.checkedDirs.isNotEmpty()) {
                Surface(
                    tonalElevation = 8.dp,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    Button(
                        onClick = { viewModel.deleteSelectedEmptyDirs() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF06B6D4)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Text("Delete Checked Folders (${state.checkedDirs.size})", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.emptyDirs.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No Empty Directories Found", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    items(state.emptyDirs) { dir ->
                        val isChecked = state.checkedDirs.contains(dir.uriString)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggleEmptyDirChecked(dir.uriString) }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { viewModel.toggleEmptyDirChecked(dir.uriString) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = dir.name, fontWeight = FontWeight.Bold)
                                Text(
                                    text = dir.path.substringAfterLast("Primary:"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }

            if (state.isCleaning) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card {
                        Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Deleting directories...")
                        }
                    }
                }
            }
        }
    }
}

// --- CACHE CLEANER SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CacheCleanerScreen(
    viewModel: StoreClearViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.cacheCleanerState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.scanCacheApps()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cache Cleaner", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            if (state.cacheItems.isNotEmpty() && state.checkedItems.isNotEmpty()) {
                Surface(
                    tonalElevation = 8.dp,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    val totalBytes = state.cacheItems.filter { state.checkedItems.contains(it.packageName) }.sumOf { it.cacheSize }
                    Button(
                        onClick = { viewModel.deleteSelectedCaches() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEAB308)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Text("Wipe Checked Cache (${formatSize(totalBytes)})", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.cacheItems.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.CleaningServices,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No App Cache Folders Found", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    items(state.cacheItems) { item ->
                        val isChecked = state.checkedItems.contains(item.packageName)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggleCacheChecked(item.packageName) }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { viewModel.toggleCacheChecked(item.packageName) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            // App Logo Loader via Coil (handles local icons seamlessly)
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.background),
                                contentAlignment = Alignment.Center
                            ) {
                                // Fallback loading application icon through package details or custom vector
                                val fallbackIcon = if (item.isTombstoned) Icons.Default.DeleteOutline else Icons.Default.Android
                                Icon(imageVector = fallbackIcon, contentDescription = "App", tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = item.appName, fontWeight = FontWeight.Bold)
                                if (item.isTombstoned) {
                                    Text(
                                        text = "Uninstalled remnant / Tombstoned",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                } else {
                                    Text(
                                        text = item.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(text = formatSize(item.cacheSize), fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }

            if (state.isCleaning) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card {
                        Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Wiping application caches...")
                        }
                    }
                }
            }
        }
    }
}

// --- SYSTEM SETTINGS SCREEN ---
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: StoreClearViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.settingsState.collectAsState()
    val shredStateVal by viewModel.shredState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showHistoryDialog by remember { mutableStateOf(false) }
    var historyList by remember { mutableStateOf<List<ShredHistoryLog>>(emptyList()) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("StoreClear Settings", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Algorithm Selection
            item {
                Text(
                    text = "HASH MATCHING CONFIG",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setHashAlgorithm(HashAlgorithm.SHA256) }
                        ) {
                            RadioButton(
                                selected = state.algorithm == HashAlgorithm.SHA256,
                                onClick = { viewModel.setHashAlgorithm(HashAlgorithm.SHA256) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Cryptographic SHA-256 (Default)", fontWeight = FontWeight.Bold)
                                Text("Slower, cryptographically resilient, avoids any false matches.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setHashAlgorithm(HashAlgorithm.MD5) }
                        ) {
                            RadioButton(
                                selected = state.algorithm == HashAlgorithm.MD5,
                                onClick = { viewModel.setHashAlgorithm(HashAlgorithm.MD5) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Fast MD5", fontWeight = FontWeight.Bold)
                                Text("Ultra fast duplicate identification. Sufficient for standard scan.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // Shred Intensity Options
            item {
                Text(
                    text = "SECURE OVERWRITE INTENSITY",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setShredIntensity(ShredIntensity.QUICK) }
                        ) {
                            RadioButton(
                                selected = state.intensity == ShredIntensity.QUICK,
                                onClick = { viewModel.setShredIntensity(ShredIntensity.QUICK) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Quick Overwrite", fontWeight = FontWeight.Bold)
                                Text("1 pass of cryptographically random writes.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setShredIntensity(ShredIntensity.STANDARD) }
                        ) {
                            RadioButton(
                                selected = state.intensity == ShredIntensity.STANDARD,
                                onClick = { viewModel.setShredIntensity(ShredIntensity.STANDARD) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Standard Secure", fontWeight = FontWeight.Bold)
                                Text("3 passes (DoD 5220 - 3 standard iterations).", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setShredIntensity(ShredIntensity.SECURE) }
                        ) {
                            RadioButton(
                                selected = state.intensity == ShredIntensity.SECURE,
                                onClick = { viewModel.setShredIntensity(ShredIntensity.SECURE) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Secure DoD (DoD 5220.22-M)", fontWeight = FontWeight.Bold)
                                Text("7 passes of high grade overwriting cycles.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // Settings sliders & actions
            item {
                Text(
                    text = "ANALYTICS LIMITS & CRASH CONTROLS",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Scan Depth: ${state.scanDepth}", fontWeight = FontWeight.Bold)
                        Slider(
                            value = state.scanDepth.toFloat(),
                            onValueChange = { viewModel.setScanDepth(it.toInt()) },
                            valueRange = 1f..10f,
                            steps = 9
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Exclude System Folders", fontWeight = FontWeight.Bold)
                                Text("Skip walking Android directory files to avoid warning popups.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = state.excludeSystem,
                                onCheckedChange = { viewModel.setExcludeSystem(it) }
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "CACHING & SECURITY PERMISSIONS",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Database Cache size", fontWeight = FontWeight.Bold)
                                Text("${state.hashCacheCount} computed hashes stored", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Button(
                                onClick = { viewModel.clearHashCache() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.outline)
                            ) {
                                Text("Wipe Cache")
                            }
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Revoke Directory Permission", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                Text("Release persistable tree URIs completely.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Button(
                                onClick = { viewModel.releaseStoragePermission() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Revoke")
                            }
                        }

                        HorizontalDivider()

                        Button(
                            onClick = {
                                historyList = shredStateVal.historyLogs
                                showHistoryDialog = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(imageVector = Icons.Default.History, contentDescription = "History")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("View Destruction logs", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // About static description section
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("StoreClear v1.0", fontWeight = FontWeight.Black)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Licensed under GNU GPL v3. Privacy Principle: Zero Network Permissions. Zero External Analytics. All data shredded securely using localized DoD 5220-M iterations directly on device sandboxes.",
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // History Dialog popup
        if (showHistoryDialog) {
            AlertDialog(
                onDismissRequest = { showHistoryDialog = false },
                confirmButton = {
                    Button(onClick = { showHistoryDialog = false }) {
                        Text("Close")
                    }
                },
                title = { Text("Secured Destruction Auditing Logs", fontWeight = FontWeight.Bold) },
                text = {
                    Box(modifier = Modifier.height(300.dp)) {
                        if (historyList.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Empty log history.", style = MaterialTheme.typography.bodyMedium)
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(historyList) { item ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                            .padding(8.dp)
                                    ) {
                                        Text(text = item.fileName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(text = "Size: ${formatSize(item.fileSizeBefore)} · Method: ${item.algorithm} (${item.passCount} passes)", fontSize = 11.sp)
                                        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(item.shredAt))
                                        Text(text = "Date: $dateStr UTC", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            )
        }
    }
}
