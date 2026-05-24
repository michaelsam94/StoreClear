package com.example.domain.repository

import com.example.domain.model.*
import kotlinx.coroutines.flow.Flow

data class ShredHistoryLog(
    val id: Long,
    val fileName: String,
    val fileSizeBefore: Long,
    val algorithm: String,
    val passCount: Int,
    val shredAt: Long
)

data class CacheAppItem(
    val packageName: String,
    val appName: String,
    val cacheSize: Long,
    val isTombstoned: Boolean, // Uninstalled app remnants
    val uriString: String? = null // For SAF external cache
)

interface FileRepository {
    fun getStorageSummary(): StorageSummary
    fun walkFileTree(rootUriString: String, maxDepth: Int = 10): List<FileNode>
    suspend fun deleteFiles(files: List<FileNode>): Int
}

interface HashRepository {
    suspend fun computeHash(uriString: String, lastModified: Long, fileSize: Long, algorithm: HashAlgorithm): String
    suspend fun clearCache()
    suspend fun getCacheSize(): Long
}

interface ShredRepository {
    fun shredAndLog(uriString: String, passCount: Int): Flow<ShredJob>
    suspend fun saveToHistory(fileName: String, fileSizeBefore: Long, algorithm: String, passCount: Int)
    fun getShredHistory(): Flow<List<ShredHistoryLog>>
}

interface CacheRepository {
    suspend fun findEmptyDirectories(rootUriString: String): List<FileNode>
    suspend fun deleteDirectories(directories: List<FileNode>): Int
    suspend fun scanCacheCleanItems(rootUriString: String): List<CacheAppItem>
    suspend fun cleanCaches(apps: List<CacheAppItem>): Long
}
