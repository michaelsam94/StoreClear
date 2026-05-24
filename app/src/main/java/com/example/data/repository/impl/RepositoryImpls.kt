package com.example.data.repository.impl

import com.example.data.datasource.local.*
import com.example.domain.model.*
import com.example.domain.repository.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FileRepositoryImpl(
    private val safDataSource: StorageAccessFrameworkDataSource
) : FileRepository {
    override fun getStorageSummary(): StorageSummary {
        return safDataSource.getStorageSummary()
    }

    override fun walkFileTree(rootUriString: String, maxDepth: Int): List<FileNode> {
        return safDataSource.walkFileTree(rootUriString, maxDepth)
    }

    override suspend fun deleteFiles(files: List<FileNode>): Int {
        var count = 0
        for (file in files) {
            if (safDataSource.deleteFile(file.uriString)) {
                count++
            }
        }
        return count
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

    override suspend fun findEmptyDirectories(rootUriString: String): List<FileNode> {
        val allNodes = safDataSource.walkFileTree(rootUriString)
        val directories = allNodes.filter { it.isDirectory }
        val emptyDirs = mutableListOf<FileNode>()

        // A directory qualifies as empty if it contains zero regular files and zero non-empty subdirectories.
        for (dir in directories) {
            // Find any child file belonging to this path
            // Compare URIs or paths
            val prefix = dir.path
            val hasFiles = allNodes.any { !it.isDirectory && it.path.startsWith(prefix) }
            if (!hasFiles) {
                emptyDirs.add(dir)
            }
        }
        return emptyDirs
    }

    override suspend fun deleteDirectories(directories: List<FileNode>): Int {
        var count = 0
        for (dir in directories) {
            if (safDataSource.deleteFile(dir.uriString)) {
                count++
            }
        }
        return count
    }

    override suspend fun scanCacheCleanItems(rootUriString: String): List<CacheAppItem> {
        return cacheDataSource.scanCacheApps(rootUriString)
    }

    override suspend fun cleanCaches(apps: List<CacheAppItem>): Long {
        var cleanedBytes = 0L
        for (app in apps) {
            if (app.uriString != null) {
                val size = app.cacheSize
                if (cacheDataSource.deleteCache(app.uriString)) {
                    cleanedBytes += size
                }
            }
        }
        return cleanedBytes
    }
}
