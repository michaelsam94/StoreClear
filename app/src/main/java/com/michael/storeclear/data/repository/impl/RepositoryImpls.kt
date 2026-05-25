package com.michael.storeclear.data.repository.impl

import com.michael.storeclear.data.datasource.local.*
import com.michael.storeclear.domain.model.*
import com.michael.storeclear.domain.repository.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class FileRepositoryImpl(
    private val safDataSource: StorageAccessFrameworkDataSource
) : FileRepository {
    override fun getStorageSummary(): StorageSummary {
        return safDataSource.getStorageSummary()
    }

    override suspend fun walkFileTree(rootUriString: String, maxDepth: Int): List<FileNode> {
        return safDataSource.walkFileTree(rootUriString, maxDepth)
    }

    override suspend fun deleteFiles(files: List<FileNode>): Int = withContext(Dispatchers.IO) {
        var count = 0
        for (file in files) {
            if (safDataSource.deleteFile(file.uriString)) {
                count++
            }
        }
        count
    }
}

class HashRepositoryImpl(
    private val hashDataSource: FileHashingDataSource,
    private val dao: StoreClearDao
) : HashRepository {
    override suspend fun computeHash(
        uriString: String,
        lastModified: Long,
        fileSize: Long,
        algorithm: HashAlgorithm
    ): String {
        return hashDataSource.computeHash(uriString, lastModified, fileSize, algorithm)
    }

    override suspend fun clearCache() {
        dao.clearHashCache()
    }

    override suspend fun getCacheSize(): Long {
        return dao.getHashCacheCount()
    }
}

class ShredRepositoryImpl(
    private val overwriteDataSource: FileOverwriteDataSource,
    private val dao: StoreClearDao
) : ShredRepository {
    override fun shredAndLog(uriString: String, passCount: Int): Flow<ShredJob> {
        return overwriteDataSource.shred(uriString, passCount)
    }

    override suspend fun saveToHistory(
        fileName: String,
        fileSizeBefore: Long,
        algorithm: String,
        passCount: Int
    ) {
        dao.insertShredHistory(
            ShredHistoryEntity(
                fileName = fileName,
                fileSizeBefore = fileSizeBefore,
                algorithm = algorithm,
                passCount = passCount,
                shredAt = System.currentTimeMillis()
            )
        )
    }

    override fun getShredHistory(): Flow<List<ShredHistoryLog>> {
        return dao.getShredHistory().map { list ->
            list.map { entity ->
                ShredHistoryLog(
                    id = entity.id,
                    fileName = entity.fileName,
                    fileSizeBefore = entity.fileSizeBefore,
                    algorithm = entity.algorithm,
                    passCount = entity.passCount,
                    shredAt = entity.shredAt
                )
            }
        }
    }
}

class CacheRepositoryImpl(
    private val cacheDataSource: CacheDataSource,
    private val safDataSource: StorageAccessFrameworkDataSource
) : CacheRepository {

    override suspend fun findEmptyDirectories(rootUriString: String): List<FileNode> =
        withContext(Dispatchers.IO) {
            val allNodes = safDataSource.walkFileTree(rootUriString)
            val directories = allNodes.filter { it.isDirectory }
            val emptyDirs = mutableListOf<FileNode>()

            for (dir in directories) {
                val prefix = dir.path
                val hasFiles = allNodes.any { !it.isDirectory && it.path.startsWith(prefix) }
                if (!hasFiles) {
                    emptyDirs.add(dir)
                }
            }
            emptyDirs
        }

    override suspend fun deleteDirectories(directories: List<FileNode>): Int = withContext(Dispatchers.IO) {
        var count = 0
        for (dir in directories) {
            if (safDataSource.deleteFile(dir.uriString)) {
                count++
            }
        }
        count
    }

    override suspend fun scanCacheCleanItems(rootUriString: String): List<CacheAppItem> {
        return cacheDataSource.scanCacheApps(rootUriString)
    }

    override suspend fun cleanCaches(apps: List<CacheAppItem>): Long = withContext(Dispatchers.IO) {
        var cleanedBytes = 0L
        for (app in apps) {
            val size = app.cacheSize
            if (cacheDataSource.deleteAppCache(app)) {
                cleanedBytes += size
            }
        }
        cleanedBytes
    }
}
