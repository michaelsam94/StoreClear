package com.michael.storeclear.data.datasource.local

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import android.app.usage.StorageStatsManager
import android.provider.DocumentsContract
import android.util.LruCache
import androidx.documentfile.provider.DocumentFile
import com.michael.storeclear.domain.model.*
import com.michael.storeclear.domain.repository.CacheAppItem
import com.michael.storeclear.util.StoragePermissions
import com.michael.storeclear.util.StorageRoot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.security.SecureRandom

class StorageAccessFrameworkDataSource(private val context: Context) {

    private var traversalCounter = 0

    private suspend fun onTraversalStep() {
        if (++traversalCounter % 200 == 0) {
            yield()
        }
    }

    fun getStorageSummary(): StorageSummary {
        val path = Environment.getExternalStorageDirectory()
        val stat = StatFs(path.path)
        val blockSize = stat.blockSizeLong
        val totalBlocks = stat.blockCountLong
        val availableBlocks = stat.availableBlocksLong

        val totalBytes = blockSize * totalBlocks
        val freeBytes = blockSize * availableBlocks
        val usedBytes = totalBytes - freeBytes

        // Let's create categories with custom sizes to populate the initial UI
        // In clean architecture, we can populate real categorized files if we scan,
        // otherwise default to standard system layouts
        val catList = listOf(
            StorageCategoryInfo(StorageCategory.MEDIA, "Media", (usedBytes * 0.45).toLong(), "#B91C1C"),
            StorageCategoryInfo(StorageCategory.APPS, "Apps", (usedBytes * 0.25).toLong(), "#EAB308"),
            StorageCategoryInfo(StorageCategory.DOCUMENTS, "Docs", (usedBytes * 0.15).toLong(), "#06B6D4"),
            StorageCategoryInfo(StorageCategory.DOWNLOADS, "Downloads", (usedBytes * 0.10).toLong(), "#3B82F6"),
            StorageCategoryInfo(StorageCategory.OTHER, "Other", (usedBytes * 0.05).toLong(), "#6B7280")
        )

        return StorageSummary(totalBytes, usedBytes, catList)
    }

    suspend fun walkFileTree(rootUriString: String, maxDepth: Int = 10): List<FileNode> =
        withContext(Dispatchers.IO) {
            traversalCounter = 0
            if (StorageRoot.isFileAccess(rootUriString)) {
                val root = StorageRoot.toFile(rootUriString)
                if (!root.exists()) return@withContext emptyList()
                val result = mutableListOf<FileNode>()
                traverseFile(root, result, 0, maxDepth)
                result
            } else {
                val rootUri = Uri.parse(rootUriString)
                val documentFile = DocumentFile.fromTreeUri(context, rootUri) ?: return@withContext emptyList()
                val result = mutableListOf<FileNode>()
                traverse(documentFile, result, 0, maxDepth)
                result
            }
        }

    private suspend fun traverseFile(file: File, result: MutableList<FileNode>, currentDepth: Int, maxDepth: Int) {
        if (currentDepth > maxDepth) return
        val children = file.listFiles() ?: return
        for (child in children) {
            onTraversalStep()
            val isDir = child.isDirectory
            val node = FileNode(
                path = child.absolutePath,
                name = child.name,
                sizeBytes = if (isDir) 0L else child.length(),
                lastModified = child.lastModified(),
                isDirectory = isDir,
                uriString = StorageRoot.fileUri(child),
                mimeType = null
            )
            result.add(node)
            if (isDir) {
                val lowercaseName = child.name.lowercase()
                if (lowercaseName != "android" && !lowercaseName.startsWith(".")) {
                    traverseFile(child, result, currentDepth + 1, maxDepth)
                }
            }
        }
    }

    private suspend fun traverse(file: DocumentFile, result: MutableList<FileNode>, currentDepth: Int, maxDepth: Int) {
        if (currentDepth > maxDepth) return
        val list = file.listFiles()
        for (f in list) {
            onTraversalStep()
            val isDir = f.isDirectory
            val node = FileNode(
                path = f.uri.path ?: "",
                name = f.name ?: "Unknown",
                sizeBytes = f.length(),
                lastModified = f.lastModified(),
                isDirectory = isDir,
                uriString = f.uri.toString(),
                mimeType = f.type
            )
            result.add(node)
            // System folders check
            if (isDir) {
                val lowercaseName = f.name?.lowercase() ?: ""
                if (lowercaseName != "android" && !lowercaseName.startsWith(".")) {
                    traverse(f, result, currentDepth + 1, maxDepth)
                }
            }
        }
    }

    fun deleteFile(uriString: String): Boolean {
        return try {
            if (StorageRoot.isFileAccess(uriString)) {
                StorageRoot.toFile(uriString).delete()
            } else {
                DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(uriString))
            }
        } catch (e: Exception) {
            false
        }
    }
}

class FileHashingDataSource(
    private val context: Context,
    private val dao: StoreClearDao
) {
    private val memoryCache = LruCache<String, String>(500)

    suspend fun computeHash(
        uriString: String,
        lastModified: Long,
        fileSize: Long,
        algorithm: HashAlgorithm
    ): String {
        val cacheKey = "$uriString:$lastModified:$fileSize:${algorithm.javaName}"
        
        // 1. Memory Cache
        memoryCache.get(cacheKey)?.let { return it }

        // 2. Database Cache
        val cachedEntity = dao.getHashCache(uriString, algorithm.javaName)
        if (cachedEntity != null && cachedEntity.lastModified == lastModified && cachedEntity.fileSize == fileSize) {
            memoryCache.put(cacheKey, cachedEntity.hash)
            return cachedEntity.hash
        }

        // 3. Perform compute on a background thread
        val computed = withContext(Dispatchers.IO) {
            performHashCompute(uriString, algorithm)
        }

        // 4. Save
        val newEntity = HashCacheEntity(
            uriString = uriString,
            hash = computed,
            algorithm = algorithm.javaName,
            lastModified = lastModified,
            fileSize = fileSize,
            computedAt = System.currentTimeMillis()
        )
        dao.insertHashCache(newEntity)
        memoryCache.put(cacheKey, computed)

        return computed
    }

    private fun performHashCompute(uriString: String, algorithm: HashAlgorithm): String {
        val digest = MessageDigest.getInstance(algorithm.javaName)
        val stream = if (StorageRoot.isFileAccess(uriString)) {
            FileInputStream(StorageRoot.toFile(uriString))
        } else {
            context.contentResolver.openInputStream(Uri.parse(uriString))
        }
        stream?.use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        } ?: throw FileNotFoundException("Could not open stream for $uriString")
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

class FileOverwriteDataSource(private val context: Context) {
    private val secureRandom = SecureRandom()

    fun shred(uriString: String, passCount: Int): Flow<ShredJob> = flow {
        if (StorageRoot.isFileAccess(uriString)) {
            shredFilePath(uriString, passCount).collect { emit(it) }
        } else {
            shredSafUri(uriString, passCount).collect { emit(it) }
        }
    }

    private fun shredFilePath(uriString: String, passCount: Int): Flow<ShredJob> = flow {
        val file = StorageRoot.toFile(uriString)
        val fileName = file.name
        val size = file.length()

        emit(
            ShredJob(
                filePath = file.absolutePath,
                fileName = fileName,
                fileSize = size,
                totalPasses = passCount,
                currentPass = 0,
                bytesWritten = 0L,
                status = ShredStatus.QUEUED,
                uriString = uriString
            )
        )

        try {
            RandomAccessFile(file, "rw").use { raf ->
                val bufferSize = minOf(size, 1024L * 1024L).toInt().coerceAtLeast(8192)
                val buffer = ByteArray(bufferSize)

                for (pass in 1..passCount) {
                    raf.seek(0L)
                    emit(
                        ShredJob(
                            filePath = file.absolutePath,
                            fileName = fileName,
                            fileSize = size,
                            totalPasses = passCount,
                            currentPass = pass,
                            bytesWritten = 0L,
                            status = ShredStatus.SHREDDING,
                            uriString = uriString
                        )
                    )

                    when (pass) {
                        1 -> buffer.fill(0x00.toByte())
                        2 -> buffer.fill(0xFF.toByte())
                        else -> secureRandom.nextBytes(buffer)
                    }

                    var bytesWrittenThisPass = 0L
                    while (bytesWrittenThisPass < size) {
                        val toWrite = minOf(bufferSize.toLong(), size - bytesWrittenThisPass).toInt()
                        if (pass >= 3) secureRandom.nextBytes(buffer)
                        raf.write(buffer, 0, toWrite)
                        bytesWrittenThisPass += toWrite
                        emit(
                            ShredJob(
                                filePath = file.absolutePath,
                                fileName = fileName,
                                fileSize = size,
                                totalPasses = passCount,
                                currentPass = pass,
                                bytesWritten = bytesWrittenThisPass,
                                status = ShredStatus.SHREDDING,
                                uriString = uriString
                            )
                        )
                    }
                }
            }

            val deleted = file.delete()
            emit(
                ShredJob(
                    filePath = file.absolutePath,
                    fileName = fileName,
                    fileSize = size,
                    totalPasses = passCount,
                    currentPass = passCount,
                    bytesWritten = size,
                    status = if (deleted) ShredStatus.DONE else ShredStatus.FAILED,
                    uriString = uriString
                )
            )
        } catch (e: Exception) {
            emit(
                ShredJob(
                    filePath = file.absolutePath,
                    fileName = fileName,
                    fileSize = size,
                    totalPasses = passCount,
                    currentPass = 0,
                    bytesWritten = 0,
                    status = ShredStatus.FAILED,
                    uriString = uriString
                )
            )
        }
    }

    private fun shredSafUri(uriString: String, passCount: Int): Flow<ShredJob> = flow {
        val uri = Uri.parse(uriString)
        val pfd = context.contentResolver.openFileDescriptor(uri, "rw")
            ?: throw FileNotFoundException("Failed to open file descriptor in rw mode")

        val fileName = DocumentFile.fromSingleUri(context, uri)?.name ?: "Unknown"
        val size = pfd.statSize

        try {
            val fileOutputStream = FileOutputStream(pfd.fileDescriptor)
            val channel = fileOutputStream.channel
            val bufferSize = minOf(size, 1024L * 1024L).toInt().coerceAtLeast(8192)
            val buffer = ByteArray(bufferSize)

            emit(
                ShredJob(
                    filePath = uri.path ?: "",
                    fileName = fileName,
                    fileSize = size,
                    totalPasses = passCount,
                    currentPass = 0,
                    bytesWritten = 0L,
                    status = ShredStatus.QUEUED,
                    uriString = uriString
                )
            )

            for (pass in 1..passCount) {
                channel.position(0L)
                emit(
                    ShredJob(
                        filePath = uri.path ?: "",
                        fileName = fileName,
                        fileSize = size,
                        totalPasses = passCount,
                        currentPass = pass,
                        bytesWritten = 0L,
                        status = ShredStatus.SHREDDING,
                        uriString = uriString
                    )
                )

                // Fill buffer based on pass
                when (pass) {
                    1 -> buffer.fill(0x00.toByte())
                    2 -> buffer.fill(0xFF.toByte())
                    else -> secureRandom.nextBytes(buffer)
                }

                var bytesWrittenThisPass = 0L
                while (bytesWrittenThisPass < size) {
                    val toWrite = minOf(bufferSize.toLong(), size - bytesWrittenThisPass).toInt()
                    // Re-randomize for pass >= 3 to be cryptographically compliant
                    if (pass >= 3) {
                        secureRandom.nextBytes(buffer)
                    }
                    fileOutputStream.write(buffer, 0, toWrite)
                    bytesWrittenThisPass += toWrite

                    emit(
                        ShredJob(
                            filePath = uri.path ?: "",
                            fileName = fileName,
                            fileSize = size,
                            totalPasses = passCount,
                            currentPass = pass,
                            bytesWritten = bytesWrittenThisPass,
                            status = ShredStatus.SHREDDING,
                            uriString = uriString
                        )
                    )
                }
                fileOutputStream.flush()
            }

            // Close stream and file descriptors before calling delete
            fileOutputStream.close()
            pfd.close()

            // Safe Delete
            val deleted = DocumentsContract.deleteDocument(context.contentResolver, uri)
            if (deleted) {
                emit(
                    ShredJob(
                        filePath = uri.path ?: "",
                        fileName = fileName,
                        fileSize = size,
                        totalPasses = passCount,
                        currentPass = passCount,
                        bytesWritten = size,
                        status = ShredStatus.DONE,
                        uriString = uriString
                    )
                )
            } else {
                emit(
                    ShredJob(
                        filePath = uri.path ?: "",
                        fileName = fileName,
                        fileSize = size,
                        totalPasses = passCount,
                        currentPass = passCount,
                        bytesWritten = size,
                        status = ShredStatus.FAILED,
                        uriString = uriString
                    )
                )
            }

        } catch (e: Exception) {
            try { pfd.close() } catch (ex: Exception) {}
            emit(
                ShredJob(
                    filePath = uri.path ?: "",
                    fileName = fileName,
                    fileSize = size,
                    totalPasses = passCount,
                    currentPass = 0,
                    bytesWritten = 0,
                    status = ShredStatus.FAILED,
                    uriString = uriString
                )
            )
        }
    }
}

class CacheDataSource(private val context: Context) {

    suspend fun scanCacheApps(rootUriString: String): List<CacheAppItem> =
        withContext(Dispatchers.IO) {
            val merged = linkedMapOf<String, CacheAppItem>()
            val hasAllFiles = Environment.isExternalStorageManager()
            val hasUsage = StoragePermissions.hasUsageAccess(context)

            if (hasAllFiles) {
                scanExternalCachePerPackage().forEach { merged[it.packageName] = it }
                scanOrphanFolders().forEach { merged[it.packageName] = it }
            }

            if (hasUsage) {
                scanViaStorageStats().forEach { item ->
                    merged.merge(item.packageName, item) { existing, found ->
                        when {
                            found.cacheSize > existing.cacheSize -> found.copy(
                                uriString = existing.uriString ?: found.uriString
                            )
                            else -> existing.copy(
                                cacheSize = maxOf(existing.cacheSize, found.cacheSize),
                                uriString = existing.uriString ?: found.uriString
                            )
                        }
                    }
                }
            }

            merged.values
                .filter { it.cacheSize > 0 }
                .sortedByDescending { it.cacheSize }
        }

    private fun scanExternalCachePerPackage(): List<CacheAppItem> {
        val dataDir = resolveAndroidDataDir() ?: return emptyList()
        val items = mutableListOf<CacheAppItem>()
        val packageFolders = dataDir.listFiles() ?: return emptyList()
        for (folder in packageFolders) {
            if (!folder.isDirectory) continue
            val pkgName = folder.name
            val cacheDir = File(dataDir, "$pkgName/cache")
            val codeCacheDir = File(dataDir, "$pkgName/code_cache")
            var size = 0L
            var targetDir: File? = null

            if (cacheDir.isDirectory) {
                val cacheSize = measureCacheDir(cacheDir, pkgName, "cache")
                if (cacheSize > 0) {
                    size += cacheSize
                    targetDir = cacheDir
                }
            }
            if (codeCacheDir.isDirectory) {
                val codeSize = measureCacheDir(codeCacheDir, pkgName, "code_cache")
                if (codeSize > 0) {
                    size += codeSize
                    if (targetDir == null) targetDir = codeCacheDir
                }
            }

            if (size > 0 && targetDir != null) {
                items.add(
                    CacheAppItem(
                        packageName = pkgName,
                        appName = appLabel(pkgName),
                        cacheSize = size,
                        isTombstoned = false,
                        uriString = StorageRoot.fileUri(targetDir)
                    )
                )
            }
        }
        return items
    }

    private fun measureCacheDir(dir: File, packageName: String, folderName: String): Long {
        val fileSize = calculateFileFolderSize(dir)
        if (fileSize > 0L) return fileSize
        return measureCacheViaDocument(packageName, folderName)
    }

    private fun measureCacheViaDocument(packageName: String, folderName: String): Long {
        val docId = "primary:Android/data/$packageName/$folderName"
        val uri = DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            docId
        )
        val doc = DocumentFile.fromSingleUri(context, uri) ?: return 0L
        if (!doc.exists() || !doc.isDirectory) return 0L
        return calculateFolderSize(doc)
    }

    private fun scanViaRawFiles(): List<CacheAppItem> {
        val dataFolder = resolveAndroidDataDir() ?: return emptyList()
        return scanPackageFolders(
            dataFolder,
            measureCache = { folder ->
                val cacheFolder = File(folder, "cache")
                if (!cacheFolder.isDirectory) 0L else calculateFileFolderSize(cacheFolder)
            },
            toItem = { folder, cacheFolder, size ->
                CacheAppItem(
                    packageName = folder.name,
                    appName = appLabel(folder.name),
                    cacheSize = size,
                    isTombstoned = false,
                    uriString = StorageRoot.fileUri(cacheFolder)
                )
            }
        )
    }

    private fun scanViaDocumentProvider(): List<CacheAppItem> {
        val dataDoc = openAndroidDataDocument() ?: return emptyList()
        val items = mutableListOf<CacheAppItem>()
        for (folder in dataDoc.listFiles()) {
            if (!folder.isDirectory) continue
            val pkgName = folder.name ?: continue
            val cacheFolder = folder.findFile("cache") ?: continue
            val cacheSize = calculateFolderSize(cacheFolder)
            if (cacheSize > 0) {
                items.add(
                    CacheAppItem(
                        packageName = pkgName,
                        appName = appLabel(pkgName),
                        cacheSize = cacheSize,
                        isTombstoned = false,
                        uriString = cacheFolder.uri.toString()
                    )
                )
            }
        }
        return items
    }

    private fun scanViaStorageStats(): List<CacheAppItem> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return emptyList()
        val storageStatsManager =
            context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
        val items = mutableListOf<CacheAppItem>()
        val dataDir = resolveAndroidDataDir() ?: return emptyList()
        val packageFolders = dataDir.listFiles() ?: return emptyList()
        for (folder in packageFolders) {
            if (!folder.isDirectory) continue
            val packageName = folder.name
            try {
                val stats = storageStatsManager.queryStatsForPackage(
                    StorageManager.UUID_DEFAULT,
                    packageName,
                    Process.myUserHandle()
                )
                if (stats.cacheBytes <= 0L) continue
                val externalCache = externalCacheDir(packageName)
                items.add(
                    CacheAppItem(
                        packageName = packageName,
                        appName = appLabel(packageName),
                        cacheSize = stats.cacheBytes,
                        isTombstoned = false,
                        uriString = externalCache?.takeIf { it.exists() }?.let { StorageRoot.fileUri(it) }
                    )
                )
            } catch (_: Exception) {
            }
        }
        return items
    }

    private fun scanOrphanFolders(): List<CacheAppItem> {
        return emptyList()
    }

    private inline fun scanPackageFolders(
        dataFolder: File,
        crossinline measureCache: (File) -> Long,
        crossinline toItem: (File, File, Long) -> CacheAppItem
    ): List<CacheAppItem> {
        val packageFolders = dataFolder.listFiles() ?: return emptyList()
        val items = mutableListOf<CacheAppItem>()
        for (folder in packageFolders) {
            if (!folder.isDirectory) continue
            val cacheSize = measureCache(folder)
            if (cacheSize > 0) {
                items.add(toItem(folder, File(folder, "cache"), cacheSize))
            }
        }
        return items
    }

    private fun resolveAndroidDataDir(): File? {
        val candidates = buildList {
            add(File(Environment.getExternalStorageDirectory(), "Android/data"))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
                storageManager.primaryStorageVolume.directory?.let {
                    add(File(it, "Android/data"))
                }
            }
            add(File("/storage/emulated/0/Android/data"))
        }
        return candidates.firstOrNull { dir ->
            dir.isDirectory && !dir.listFiles().isNullOrEmpty()
        } ?: candidates.firstOrNull { it.isDirectory }
    }

    private fun openAndroidDataDocument(): DocumentFile? {
        val treeUri = DocumentsContract.buildTreeDocumentUri(
            "com.android.externalstorage.documents",
            "primary:Android/data"
        )
        return DocumentFile.fromTreeUri(context, treeUri)
    }

    private fun externalCacheDir(packageName: String): File? {
        val dataDir = resolveAndroidDataDir() ?: return null
        return File(dataDir, "$packageName/cache")
    }

    private fun appLabel(packageName: String): String {
        return try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private fun calculateFileFolderSize(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        var size = 0L
        val list = file.listFiles() ?: return 0L
        for (f in list) {
            size += if (f.isDirectory) calculateFileFolderSize(f) else f.length()
        }
        return size
    }

    private fun calculateFolderSize(file: DocumentFile): Long {
        var size = 0L
        for (f in file.listFiles()) {
            size += if (f.isDirectory) calculateFolderSize(f) else f.length()
        }
        return size
    }

    fun deleteAppCache(item: CacheAppItem): Boolean {
        var success = false
        if (item.uriString != null) {
            success = deleteCache(item.uriString) || success
        }
        val dataDir = resolveAndroidDataDir() ?: return success
        val cacheDir = File(dataDir, "${item.packageName}/cache")
        val codeCacheDir = File(dataDir, "${item.packageName}/code_cache")
        if (cacheDir.exists()) success = deleteRecursive(cacheDir) || success
        if (codeCacheDir.exists()) success = deleteRecursive(codeCacheDir) || success
        if (item.isTombstoned) {
            val orphanDir = File(dataDir, item.packageName)
            if (orphanDir.exists()) success = deleteRecursive(orphanDir) || success
        }
        return success
    }

    fun deleteCache(uriString: String): Boolean {
        return try {
            if (StorageRoot.isFileAccess(uriString)) {
                deleteRecursive(StorageRoot.toFile(uriString))
            } else {
                DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(uriString))
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun deleteRecursive(file: File): Boolean {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursive(it) }
        }
        return file.delete()
    }
}
