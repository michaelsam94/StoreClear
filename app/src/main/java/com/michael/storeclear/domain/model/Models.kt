package com.michael.storeclear.domain.model

import android.net.Uri

data class FileNode(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val isDirectory: Boolean,
    val uriString: String,
    val mimeType: String? = null
)

data class DuplicateGroup(
    val sizeBytes: Long,
    val hash: String,
    val files: List<FileNode>
) {
    val totalRecoverableSpace: Long
        get() = sizeBytes * (files.size - 1)
}

enum class StorageCategory {
    MEDIA, APPS, DOCUMENTS, DOWNLOADS, OTHER
}

data class StorageCategoryInfo(
    val category: StorageCategory,
    val name: String,
    val bytes: Long,
    val colorHex: String
)

data class StorageSummary(
    val totalBytes: Long,
    val usedBytes: Long,
    val categories: List<StorageCategoryInfo>
)

enum class ShredStatus {
    QUEUED, SHREDDING, DONE, FAILED
}

data class ShredJob(
    val filePath: String,
    val fileName: String,
    val fileSize: Long,
    val totalPasses: Int,
    val currentPass: Int,
    val bytesWritten: Long,
    val status: ShredStatus,
    val uriString: String
)

data class DirectoryHeatNode(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val children: List<DirectoryHeatNode> = emptyList(),
    val depth: Int = 0
)

data class ScanProgress(
    val percentage: Int,
    val statusText: String,
    val filesFoundCount: Int,
    val duplicatesFoundCount: Int,
    val sizeRecoverableBytes: Long
)

sealed class ScanResult {
    object Idle : ScanResult()
    data class Scanning(val progress: ScanProgress) : ScanResult()
    data class Success(val duplicateGroups: List<DuplicateGroup>) : ScanResult()
    data class Error(val message: String) : ScanResult()
}

enum class HashAlgorithm(val javaName: String) {
    MD5("MD5"),
    SHA256("SHA-256")
}

enum class ShredIntensity(val passCount: Int) {
    QUICK(1),
    STANDARD(3),
    SECURE(7)
}
