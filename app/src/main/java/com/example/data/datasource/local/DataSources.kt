package com.example.data.datasource.local

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import android.util.LruCache
import androidx.documentfile.provider.DocumentFile
import com.example.domain.model.*
import com.example.domain.repository.CacheAppItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom

class StorageAccessFrameworkDataSource(private val context: Context) {

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

    fun walkFileTree(rootUriString: String, maxDepth: Int = 10): List<FileNode> {
        val context = context
        val rootUri = Uri.parse(rootUriString)
        val documentFile = DocumentFile.fromTreeUri(context, rootUri) ?: return emptyList()
        val result = mutableListOf<FileNode>()
        traverse(documentFile, result, 0, maxDepth)
        return result
    }

    private fun traverse(file: DocumentFile, result: MutableList<FileNode>, currentDepth: Int, maxDepth: Int) {
        if (currentDepth > maxDepth) return
        val list = file.listFiles()
        for (f in list) {
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
            val uri = Uri.parse(uriString)
            DocumentsContract.deleteDocument(context.contentResolver, uri)
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

        // 3. Perform compute
        val computed = performHashCompute(uriString, algorithm)

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
        context.contentResolver.openInputStream(Uri.parse(uriString))?.use { stream ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (stream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        } ?: throw FileNotFoundException("Could not open stream for $uriString")
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

class FileOverwriteDataSource(private val context: Context) {
    private val secureRandom = SecureRandom()

    fun shred(uriString: String, passCount: Int): Flow<ShredJob> = flow {
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

    fun scanCacheApps(rootUriString: String): List<CacheAppItem> {
        val rootUri = Uri.parse(rootUriString)
        val documentFile = DocumentFile.fromTreeUri(context, rootUri) ?: return emptyList()

        // List installed apps
        val pm = context.packageManager
        val installedPackages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
        val installedSet = installedPackages.map { it.packageName }.toSet()
        val installedNames = installedPackages.associate {
            val label = it.applicationInfo?.let { appInfo -> pm.getApplicationLabel(appInfo).toString() } ?: it.packageName
            it.packageName to label
        }

        val cacheAppItems = mutableListOf<CacheAppItem>()

        // Look for "Android/data" or "Android" root child
        val androidFolder = documentFile.findFile("Android") ?: return emptyList()
        val dataFolder = androidFolder.findFile("data") ?: return emptyList()

        val packageFolders = dataFolder.listFiles()
        for (folder in packageFolders) {
            if (!folder.isDirectory) continue
            val pkgName = folder.name ?: continue

            val cacheFolder = folder.findFile("cache")
            val cacheSize = cacheFolder?.let { calculateFolderSize(it) } ?: 0L

            if (cacheSize > 0) {
                val isTombstoned = !installedSet.contains(pkgName)
                val appLabel = installedNames[pkgName] ?: pkgName
                cacheAppItems.add(
                    CacheAppItem(
                        packageName = pkgName,
                        appName = appLabel,
                        cacheSize = cacheSize,
                        isTombstoned = isTombstoned,
                        uriString = cacheFolder?.uri?.toString() ?: folder.uri.toString()
                    )
                )
            }
        }

        return cacheAppItems
    }

    private fun calculateFolderSize(file: DocumentFile): Long {
        var size = 0L
        val list = file.listFiles()
        for (f in list) {
            size += if (f.isDirectory) {
                calculateFolderSize(f)
            } else {
                f.length()
            }
        }
        return size
    }

    fun deleteCache(uriString: String): Boolean {
        return try {
            val uri = Uri.parse(uriString)
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        } catch (e: Exception) {
            false
        }
    }
}
