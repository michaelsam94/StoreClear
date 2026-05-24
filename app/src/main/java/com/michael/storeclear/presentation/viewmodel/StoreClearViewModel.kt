package com.michael.storeclear.presentation.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.michael.storeclear.domain.model.*
import com.michael.storeclear.domain.repository.CacheAppItem
import com.michael.storeclear.domain.repository.ShredHistoryLog
import com.michael.storeclear.domain.usecase.*
import com.michael.storeclear.StoreClearApp
import com.michael.storeclear.util.StoragePermissions
import com.michael.storeclear.util.StorageRoot
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DashboardState(
    val storageSummary: StorageSummary? = null,
    val isScanning: Boolean = false,
    val rootUriString: String? = null,
    val storageAccessError: String? = null
)

data class DuplicateState(
    val scanResult: ScanResult = ScanResult.Idle,
    val expandedGroupIndex: Int? = null,
    val checkedFiles: Set<String> = emptySet(), // FileNode uriStrings marked to delete
    val isDeleting: Boolean = false
)

data class HeatmapState(
    val rootNode: DirectoryHeatNode? = null,
    val currentNode: DirectoryHeatNode? = null,
    val nodeHistory: List<DirectoryHeatNode> = emptyList(),
    val isLoading: Boolean = false
)

data class ShredState(
    val activeJobs: List<ShredJob> = emptyList(),
    val selectedFiles: List<FileNode> = emptyList(),
    val currentCertificate: ShredJob? = null,
    val historyLogs: List<ShredHistoryLog> = emptyList()
)

data class EmptyDirsState(
    val emptyDirs: List<FileNode> = emptyList(),
    val checkedDirs: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val isCleaning: Boolean = false
)

data class CacheCleanerState(
    val cacheItems: List<CacheAppItem> = emptyList(),
    val checkedItems: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val isCleaning: Boolean = false,
    val needsUsageAccess: Boolean = false
)

data class SettingsState(
    val algorithm: HashAlgorithm = HashAlgorithm.SHA256,
    val intensity: ShredIntensity = ShredIntensity.STANDARD,
    val scanDepth: Int = 4,
    val excludeSystem: Boolean = true,
    val hashCacheCount: Long = 0L,
    val screenDarkTheme: Boolean = true
)

class StoreClearViewModel(
    private val context: Context,
    private val getStorageSummaryUseCase: GetStorageSummaryUseCase,
    private val scanStorageUseCase: ScanStorageUseCase,
    private val findDuplicatesUseCase: FindDuplicatesUseCase,
    private val buildHeatmapUseCase: BuildHeatmapUseCase,
    private val shredFilesUseCase: ShredFilesUseCase,
    private val cleanEmptyDirsUseCase: CleanEmptyDirsUseCase,
    private val cleanBrokenCacheUseCase: CleanBrokenCacheUseCase,
    private val hashRepository: com.michael.storeclear.domain.repository.HashRepository,
    private val shredRepository: com.michael.storeclear.domain.repository.ShredRepository,
    @get:VisibleForTesting internal val playStorePreviewMode: Boolean = false,
) : ViewModel() {

    // SharedPreferences for URI persistence
    private val prefs: SharedPreferences by lazy {
        try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                "storeclear_secure_prefs",
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            context.getSharedPreferences("storeclear_fallback_prefs", Context.MODE_PRIVATE)
        }
    }

    private val _dashboardState = MutableStateFlow(DashboardState())
    val dashboardState: StateFlow<DashboardState> = _dashboardState.asStateFlow()

    private val _duplicateState = MutableStateFlow(DuplicateState())
    val duplicateState: StateFlow<DuplicateState> = _duplicateState.asStateFlow()

    private val _heatmapState = MutableStateFlow(HeatmapState())
    val heatmapState: StateFlow<HeatmapState> = _heatmapState.asStateFlow()

    private val _shredState = MutableStateFlow(ShredState())
    val shredState: StateFlow<ShredState> = _shredState.asStateFlow()

    private val _emptyDirsState = MutableStateFlow(EmptyDirsState())
    val emptyDirsState: StateFlow<EmptyDirsState> = _emptyDirsState.asStateFlow()

    private val _cacheCleanerState = MutableStateFlow(CacheCleanerState())
    val cacheCleanerState: StateFlow<CacheCleanerState> = _cacheCleanerState.asStateFlow()

    private val _settingsState = MutableStateFlow(SettingsState())
    val settingsState: StateFlow<SettingsState> = _settingsState.asStateFlow()

    init {
        if (playStorePreviewMode) {
            applyPlayStoreFixtures()
        } else {
        val cachedUri = prefs.getString("root_tree_uri", null)?.let { StorageRoot.normalizeRoot(it) }
        if (cachedUri != null) {
            if (cachedUri != prefs.getString("root_tree_uri", null)) {
                prefs.edit().putString("root_tree_uri", cachedUri).apply()
            }
            _dashboardState.update { it.copy(rootUriString = cachedUri) }
        } else if (StoragePermissions.hasAllFilesAccess(context)) {
            enableAllFilesAccess()
        }
        
        val cachedAlgo = prefs.getString("hash_algorithm", HashAlgorithm.SHA256.name)
        val cachedIntensity = prefs.getString("shred_intensity", ShredIntensity.STANDARD.name)
        val cachedDepth = prefs.getInt("scan_depth", 4)
        val cachedSystem = prefs.getBoolean("exclude_system", true)
        val cachedDarkTheme = prefs.getBoolean("dark_theme_enabled", true)

        _settingsState.update {
            it.copy(
                algorithm = HashAlgorithm.valueOf(cachedAlgo ?: HashAlgorithm.SHA256.name),
                intensity = ShredIntensity.valueOf(cachedIntensity ?: ShredIntensity.STANDARD.name),
                scanDepth = cachedDepth,
                excludeSystem = cachedSystem,
                screenDarkTheme = cachedDarkTheme
            )
        }

        refreshSummary()
        observeShredHistory()
        updateHashCacheCount()
        }
    }

    fun enableAllFilesAccess() {
        if (!StoragePermissions.hasAllFilesAccess(context)) return
        val root = StorageRoot.normalizeRoot(StorageRoot.allFilesRootUri())
        prefs.edit().putString("root_tree_uri", root).apply()
        _dashboardState.update { it.copy(rootUriString = root, storageAccessError = null) }
        refreshSummary()
    }

    fun onStorageAccessResume() {
        if (_dashboardState.value.rootUriString == null && StoragePermissions.hasAllFilesAccess(context)) {
            enableAllFilesAccess()
        }
    }

    fun setRootUri(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            _dashboardState.update {
                it.copy(
                    storageAccessError =
                        "Could not save folder access. In the picker, tap \"Use this folder\" and allow access to all files inside it."
                )
            }
            return
        }
        prefs.edit().putString("root_tree_uri", uri.toString()).apply()
        _dashboardState.update { it.copy(rootUriString = uri.toString(), storageAccessError = null) }
        refreshSummary()
    }

    fun clearStorageAccessError() {
        _dashboardState.update { it.copy(storageAccessError = null) }
    }

    fun releaseStoragePermission() {
        val cachedUri = _dashboardState.value.rootUriString
        if (cachedUri != null && StorageRoot.isSafAccess(cachedUri)) {
            try {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(cachedUri),
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {}
        }
        prefs.edit().remove("root_tree_uri").apply()
        _dashboardState.update { it.copy(rootUriString = null) }
    }

    fun refreshSummary() {
        _dashboardState.update { it.copy(isScanning = true) }
        viewModelScope.launch {
            try {
                val summary = getStorageSummaryUseCase()
                _dashboardState.update { it.copy(storageSummary = summary, isScanning = false) }
            } catch (e: Exception) {
                _dashboardState.update { it.copy(isScanning = false) }
            }
        }
    }

    // --- DUPLICATE FINDER ---
    fun runDuplicateFinder() {
        val rootUri = _dashboardState.value.rootUriString ?: return
        viewModelScope.launch {
            findDuplicatesUseCase(rootUri, _settingsState.value.algorithm).collect { result ->
                _duplicateState.update { state ->
                    val cleanChecked = if (result is ScanResult.Success) {
                        // Pre-calculate: select all except the original oldest path.
                        // Or let the user toggle dynamically. Let's select duplicates to help the user clear with one tap!
                        val toCheck = mutableSetOf<String>()
                        for (group in result.duplicateGroups) {
                            // Find oldest path, keep it, assert others to check
                            val sortedByNameOrMod = group.files.sortedBy { it.lastModified }
                            if (sortedByNameOrMod.size > 1) {
                                for (i in 1 until sortedByNameOrMod.size) {
                                    toCheck.add(sortedByNameOrMod[i].uriString)
                                }
                            }
                        }
                        toCheck
                    } else {
                        state.checkedFiles
                    }
                    state.copy(scanResult = result, checkedFiles = cleanChecked)
                }
            }
        }
    }

    fun toggleDuplicateChecked(uriString: String) {
        _duplicateState.update { state ->
            val updated = state.checkedFiles.toMutableSet()
            if (updated.contains(uriString)) {
                updated.remove(uriString)
            } else {
                updated.add(uriString)
            }
            state.copy(checkedFiles = updated)
        }
    }

    fun toggleGroupExpanded(groupIndex: Int) {
        _duplicateState.update { state ->
            val next = if (state.expandedGroupIndex == groupIndex) null else groupIndex
            state.copy(expandedGroupIndex = next)
        }
    }

    fun deleteSelectedDuplicates() {
        val rootUri = _dashboardState.value.rootUriString ?: return
        viewModelScope.launch {
            _duplicateState.update { it.copy(isDeleting = true) }
            
            val state = _duplicateState.value
            val scanResult = state.scanResult
            if (scanResult is ScanResult.Success) {
                // Collect file nodes to delete
                val filesToDelete = scanResult.duplicateGroups.flatMap { it.files }
                    .filter { state.checkedFiles.contains(it.uriString) }

                // Trigger delete in repo
                // Let's call the repository clean
                viewModelScope.launch {
                    val containerApp = context.applicationContext as StoreClearApp
                    val repo = containerApp.container.fileRepository
                    repo.deleteFiles(filesToDelete)
                    
                    _duplicateState.update { it.copy(isDeleting = false) }
                    runDuplicateFinder() // Re-run search
                    refreshSummary() // Refresh storage chart
                }
            }
        }
    }

    // --- STORAGE HEATMAP ---
    fun loadHeatmap() {
        val rootUri = _dashboardState.value.rootUriString ?: return
        _heatmapState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val tree = buildHeatmapUseCase(rootUri, _settingsState.value.scanDepth)
                _heatmapState.update { it.copy(rootNode = tree, currentNode = tree, nodeHistory = emptyList(), isLoading = false) }
            } catch (e: Exception) {
                _heatmapState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun navigateIntoHeatNode(node: DirectoryHeatNode) {
        _heatmapState.update { state ->
            val curr = state.currentNode ?: return
            val history = state.nodeHistory.toMutableList()
            history.add(curr)
            state.copy(currentNode = node, nodeHistory = history)
        }
    }

    fun navigateUpHeatNode() {
        _heatmapState.update { state ->
            if (state.nodeHistory.isEmpty()) return
            val history = state.nodeHistory.toMutableList()
            val previous = history.removeAt(history.size - 1)
            state.copy(currentNode = previous, nodeHistory = history)
        }
    }


    // --- SECURE SHREDDER ---
    fun runShredFile(uri: Uri) {
        viewModelScope.launch {
            val fileNode = DocumentFile.fromSingleUri(context, uri) ?: return@launch
            val fileName = fileNode.name ?: "Unknown"
            val uriString = uri.toString()
            val intensity = _settingsState.value.intensity

            shredFilesUseCase(uriString, intensity).collect { job ->
                // Update active list
                _shredState.update { state ->
                    val updated = state.activeJobs.toMutableList()
                    val idx = updated.indexOfFirst { it.uriString == uriString }
                    if (idx >= 0) {
                        updated[idx] = job
                    } else {
                        updated.add(job)
                    }
                    state.copy(activeJobs = updated)
                }

                if (job.status == ShredStatus.DONE) {
                    shredRepository.saveToHistory(
                        fileName = fileName,
                        fileSizeBefore = job.fileSize,
                        algorithm = _settingsState.value.algorithm.javaName,
                        passCount = job.totalPasses
                    )
                    _shredState.update { state ->
                        state.copy(currentCertificate = job)
                    }
                    refreshSummary()
                }
            }
        }
    }

    fun clearCertificate() {
        _shredState.update { it.copy(currentCertificate = null) }
    }

    private fun observeShredHistory() {
        viewModelScope.launch {
            shredRepository.getShredHistory().collect { list ->
                _shredState.update { it.copy(historyLogs = list) }
            }
        }
    }


    // --- EMPTY FOLDERS ---
    fun scanEmptyFolders() {
        val rootUri = _dashboardState.value.rootUriString ?: return
        _emptyDirsState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val list = cleanEmptyDirsUseCase.findEmpty(rootUri)
                _emptyDirsState.update { state ->
                    state.copy(
                        emptyDirs = list,
                        checkedDirs = list.map { it.uriString }.toSet(),
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _emptyDirsState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun toggleEmptyDirChecked(uriString: String) {
        _emptyDirsState.update { state ->
            val checked = state.checkedDirs.toMutableSet()
            if (checked.contains(uriString)) {
                checked.remove(uriString)
            } else {
                checked.add(uriString)
            }
            state.copy(checkedDirs = checked)
        }
    }

    fun deleteSelectedEmptyDirs() {
        _emptyDirsState.update { it.copy(isCleaning = true) }
        viewModelScope.launch {
            val list = _emptyDirsState.value.emptyDirs.filter {
                _emptyDirsState.value.checkedDirs.contains(it.uriString)
            }
            cleanEmptyDirsUseCase.clean(list)
            _emptyDirsState.update { it.copy(isCleaning = false) }
            scanEmptyFolders()
            refreshSummary()
        }
    }


    // --- BROKEN CACHES ---
    fun scanCacheApps() {
        val rootUri = _dashboardState.value.rootUriString ?: return
        if (!StoragePermissions.hasUsageAccess(context)) {
            _cacheCleanerState.update {
                it.copy(
                    isLoading = false,
                    needsUsageAccess = true,
                    cacheItems = emptyList(),
                    checkedItems = emptySet()
                )
            }
            return
        }
        _cacheCleanerState.update { it.copy(isLoading = true, needsUsageAccess = false) }
        viewModelScope.launch {
            try {
                val list = cleanBrokenCacheUseCase.scanCache(rootUri)
                _cacheCleanerState.update { state ->
                    state.copy(
                        cacheItems = list,
                        checkedItems = list.filter { it.isTombstoned }.map { it.packageName }.toSet(),
                        isLoading = false,
                        needsUsageAccess = false
                    )
                }
            } catch (e: Exception) {
                _cacheCleanerState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onCacheCleanerResume() {
        if (_cacheCleanerState.value.isLoading) return
        if (!StoragePermissions.hasUsageAccess(context) ||
            _cacheCleanerState.value.needsUsageAccess ||
            _cacheCleanerState.value.cacheItems.isEmpty()
        ) {
            scanCacheApps()
        }
    }

    fun toggleCacheChecked(pkgName: String) {
        _cacheCleanerState.update { state ->
            val checked = state.checkedItems.toMutableSet()
            if (checked.contains(pkgName)) {
                checked.remove(pkgName)
            } else {
                checked.add(pkgName)
            }
            state.copy(checkedItems = checked)
        }
    }

    fun deleteSelectedCaches() {
        _cacheCleanerState.update { it.copy(isCleaning = true) }
        viewModelScope.launch {
            val toClean = _cacheCleanerState.value.cacheItems.filter {
                _cacheCleanerState.value.checkedItems.contains(it.packageName)
            }
            cleanBrokenCacheUseCase.clean(toClean)
            _cacheCleanerState.update { it.copy(isCleaning = false) }
            scanCacheApps()
            refreshSummary()
        }
    }


    // --- SETTINGS CONTROLS ---
    fun setHashAlgorithm(algo: HashAlgorithm) {
        _settingsState.update { it.copy(algorithm = algo) }
        prefs.edit().putString("hash_algorithm", algo.name).apply()
    }

    fun setShredIntensity(intensity: ShredIntensity) {
        _settingsState.update { it.copy(intensity = intensity) }
        prefs.edit().putString("shred_intensity", intensity.name).apply()
    }

    fun setScanDepth(depth: Int) {
        _settingsState.update { it.copy(scanDepth = depth) }
        prefs.edit().putInt("scan_depth", depth).apply()
    }

    fun setExcludeSystem(exclude: Boolean) {
        _settingsState.update { it.copy(excludeSystem = exclude) }
        prefs.edit().putBoolean("exclude_system", exclude).apply()
    }

    fun toggleDarkTheme(enable: Boolean) {
        _settingsState.update { it.copy(screenDarkTheme = enable) }
        prefs.edit().putBoolean("dark_theme_enabled", enable).apply()
    }

    fun clearHashCache() {
        viewModelScope.launch {
            hashRepository.clearCache()
            updateHashCacheCount()
        }
    }

    private fun updateHashCacheCount() {
        viewModelScope.launch {
            val count = hashRepository.getCacheSize()
            _settingsState.update { it.copy(hashCacheCount = count) }
        }
    }

    @VisibleForTesting
    internal fun applyPlayStoreFixtures() {
        val gb = 1024L * 1024 * 1024
        val summary = StorageSummary(
            totalBytes = 128L * gb,
            usedBytes = 96L * gb,
            categories = listOf(
                StorageCategoryInfo(StorageCategory.MEDIA, "Media", 43L * gb, "#B91C1C"),
                StorageCategoryInfo(StorageCategory.APPS, "Apps", 24L * gb, "#EAB308"),
                StorageCategoryInfo(StorageCategory.DOCUMENTS, "Docs", 14L * gb, "#06B6D4"),
                StorageCategoryInfo(StorageCategory.DOWNLOADS, "Downloads", 10L * gb, "#3B82F6"),
                StorageCategoryInfo(StorageCategory.OTHER, "Other", 5L * gb, "#6B7280"),
            ),
        )
        _dashboardState.value = DashboardState(
            storageSummary = summary,
            rootUriString = "file:///storage/emulated/0",
            isScanning = false,
        )

        val dupFiles = listOf(
            FileNode("/storage/emulated/0/DCIM/IMG_2048.jpg", "IMG_2048.jpg", 4_800_000, 1_700_000_000, false, "uri:dcim1", "image/jpeg"),
            FileNode("/storage/emulated/0/Download/IMG_2048.jpg", "IMG_2048.jpg", 4_800_000, 1_600_000_000, false, "uri:dl1", "image/jpeg"),
            FileNode("/storage/emulated/0/Movies/clip.mp4", "clip.mp4", 120_000_000, 1_500_000_000, false, "uri:mov1", "video/mp4"),
            FileNode("/storage/emulated/0/Download/clip_copy.mp4", "clip_copy.mp4", 120_000_000, 1_400_000_000, false, "uri:mov2", "video/mp4"),
        )
        val groups = listOf(
            DuplicateGroup(4_800_000, "sha256-a", dupFiles.take(2)),
            DuplicateGroup(120_000_000, "sha256-b", dupFiles.drop(2)),
        )
        _duplicateState.value = DuplicateState(
            scanResult = ScanResult.Success(groups),
            expandedGroupIndex = 0,
            checkedFiles = setOf("uri:dl1", "uri:mov2"),
        )

        val heatChildren = listOf(
            DirectoryHeatNode("/storage/emulated/0/DCIM", "DCIM", 18L * gb, emptyList(), 1),
            DirectoryHeatNode("/storage/emulated/0/Download", "Download", 12L * gb, emptyList(), 1),
            DirectoryHeatNode("/storage/emulated/0/Android", "Android", 8L * gb, emptyList(), 1),
            DirectoryHeatNode("/storage/emulated/0/Movies", "Movies", 6L * gb, emptyList(), 1),
        )
        val heatRoot = DirectoryHeatNode("/storage/emulated/0", "Internal Storage", 96L * gb, heatChildren, 0)
        _heatmapState.value = HeatmapState(
            rootNode = heatRoot,
            currentNode = heatRoot,
            isLoading = false,
        )

        _shredState.value = ShredState(
            activeJobs = listOf(
                ShredJob(
                    filePath = "/storage/emulated/0/Download/old_backup.zip",
                    fileName = "old_backup.zip",
                    fileSize = 256_000_000,
                    totalPasses = 3,
                    currentPass = 2,
                    bytesWritten = 170_000_000,
                    status = ShredStatus.SHREDDING,
                    uriString = "uri:shred1",
                ),
                ShredJob(
                    filePath = "/storage/emulated/0/DCIM/private_scan.pdf",
                    fileName = "private_scan.pdf",
                    fileSize = 12_400_000,
                    totalPasses = 3,
                    currentPass = 3,
                    bytesWritten = 12_400_000,
                    status = ShredStatus.DONE,
                    uriString = "uri:shred2",
                ),
            ),
            historyLogs = listOf(
                ShredHistoryLog(1, "cache_dump.tmp", 48_000_000, "SHA-256", 3, System.currentTimeMillis() - 86_400_000),
            ),
        )

        _cacheCleanerState.value = CacheCleanerState(
            cacheItems = listOf(
                CacheAppItem("com.example.social", "Social Hub", 820_000_000, isTombstoned = false),
                CacheAppItem("com.example.browser", "Web Browser", 410_000_000, isTombstoned = false),
            ),
            checkedItems = setOf("com.example.social"),
            isLoading = false,
        )

        _settingsState.value = SettingsState(
            algorithm = HashAlgorithm.SHA256,
            intensity = ShredIntensity.STANDARD,
            scanDepth = 6,
            excludeSystem = true,
            hashCacheCount = 1284,
            screenDarkTheme = true,
        )
    }
}

class StoreClearViewModelFactory(
    private val context: Context,
    private val getStorageSummaryUseCase: GetStorageSummaryUseCase,
    private val scanStorageUseCase: ScanStorageUseCase,
    private val findDuplicatesUseCase: FindDuplicatesUseCase,
    private val buildHeatmapUseCase: BuildHeatmapUseCase,
    private val shredFilesUseCase: ShredFilesUseCase,
    private val cleanEmptyDirsUseCase: CleanEmptyDirsUseCase,
    private val cleanBrokenCacheUseCase: CleanBrokenCacheUseCase,
    private val hashRepository: com.michael.storeclear.domain.repository.HashRepository,
    private val shredRepository: com.michael.storeclear.domain.repository.ShredRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StoreClearViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StoreClearViewModel(
                context,
                getStorageSummaryUseCase,
                scanStorageUseCase,
                findDuplicatesUseCase,
                buildHeatmapUseCase,
                shredFilesUseCase,
                cleanEmptyDirsUseCase,
                cleanBrokenCacheUseCase,
                hashRepository,
                shredRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
