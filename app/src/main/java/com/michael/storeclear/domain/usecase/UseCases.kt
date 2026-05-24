package com.michael.storeclear.domain.usecase

import com.michael.storeclear.domain.model.*
import com.michael.storeclear.domain.repository.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

class GetStorageSummaryUseCase(private val fileRepository: FileRepository) {
    operator fun invoke(): StorageSummary = fileRepository.getStorageSummary()
}

class ScanStorageUseCase(private val fileRepository: FileRepository) {
    operator fun invoke(rootUriString: String): List<FileNode> {
        return fileRepository.walkFileTree(rootUriString)
    }
}

class FindDuplicatesUseCase(
    private val fileRepository: FileRepository,
    private val hashRepository: HashRepository
) {
    operator fun invoke(rootUriString: String, algorithm: HashAlgorithm): Flow<ScanResult> = flow {
        emit(ScanResult.Scanning(ScanProgress(0, "Scanning file system...", 0, 0, 0L)))

        // Phase 1: Walk tree & bucket by size
        val allFiles = try {
            fileRepository.walkFileTree(rootUriString)
        } catch (e: Exception) {
            emit(ScanResult.Error(e.message ?: "Failed to scan file system"))
            return@flow
        }

        if (allFiles.isEmpty()) {
            emit(ScanResult.Success(emptyList()))
            return@flow
        }

        emit(ScanResult.Scanning(ScanProgress(15, "Analyzing file sizes...", allFiles.size, 0, 0L)))

        // Size-bucketing filter
        val filesBySize = allFiles.filter { !it.isDirectory && it.sizeBytes > 0 }
            .groupBy { it.sizeBytes }
            .filter { it.value.size > 1 }

        val candidateFiles = filesBySize.values.flatten()
        val totalCandidates = candidateFiles.size

        if (totalCandidates == 0) {
            emit(ScanResult.Success(emptyList()))
            return@flow
        }

        emit(ScanResult.Scanning(ScanProgress(30, "Computing hashes of size-matched files...", allFiles.size, 0, 0L)))

        // Phase 2: Hash comparison
        val hashes = mutableMapOf<String, MutableList<FileNode>>()
        var completedHashCount = 0
        var duplicatesCount = 0
        var totalRecoverableSpace = 0L

        for (file in candidateFiles) {
            try {
                val hash = hashRepository.computeHash(
                    uriString = file.uriString,
                    lastModified = file.lastModified,
                    fileSize = file.sizeBytes,
                    algorithm = algorithm
                )
                val list = hashes.getOrPut(hash) { mutableListOf() }
                list.add(file)

                if (list.size > 1) {
                    duplicatesCount++
                    totalRecoverableSpace += file.sizeBytes
                }
            } catch (e: Exception) {
                // Ignore failure to access specific files
            }

            completedHashCount++
            val progressPercentage = 30 + ((completedHashCount.toFloat() / totalCandidates) * 60).toInt()
            emit(
                ScanResult.Scanning(
                    ScanProgress(
                        percentage = progressPercentage,
                        statusText = "Hashing candidate: ${file.name}",
                        filesFoundCount = allFiles.size,
                        duplicatesFoundCount = duplicatesCount,
                        sizeRecoverableBytes = totalRecoverableSpace
                    )
                )
            )
        }

        // Keep groups that actually have duplicates (count > 1)
        val duplicateGroups = hashes.filter { it.value.size > 1 }
            .map { DuplicateGroup(sizeBytes = it.value[0].sizeBytes, hash = it.key, files = it.value) }
            .sortedByDescending { it.sizeBytes * it.files.size }

        emit(ScanResult.Success(duplicateGroups))
    }
}

class BuildHeatmapUseCase(private val fileRepository: FileRepository) {
    operator fun invoke(rootUriString: String, maxDepthLimit: Int = 4): DirectoryHeatNode {
        val files = fileRepository.walkFileTree(rootUriString, maxDepthLimit + 1)
        if (files.isEmpty()) {
            return DirectoryHeatNode(rootUriString, "/", 0L)
        }

        // To build a nice tree up to maxDepthLimit, let's establish a node structure
        val rootNode = buildTreeFromNodes(files, rootUriString, maxDepthLimit)
        return rootNode
    }

    private fun buildTreeFromNodes(files: List<FileNode>, rootUri: String, maxDepth: Int): DirectoryHeatNode {
        // Group files by directory path
        val directories = files.filter { it.isDirectory }
        val regularFiles = files.filter { !it.isDirectory }

        // Find the base root node or derive it
        val rootNodeName = if (rootUri.contains("%2F")) {
            rootUri.split("%2F").lastOrNull() ?: "/"
        } else if (rootUri.contains("/")){
            rootUri.split("/").lastOrNull() ?: "/"
        } else {
            "Storage Root"
        }

        // Build path hierarchy
        // Let's create a map of path to size and map of parent path to children paths
        // First, add all directories to map
        val dirSizes = mutableMapOf<String, Long>()
        val dirChildren = mutableMapOf<String, MutableSet<String>>()
        val dirNames = mutableMapOf<String, String>()

        // Initialize entries
        dirSizes[rootUri] = 0L
        dirNames[rootUri] = rootNodeName

        for (dir in directories) {
            dirSizes[dir.path] = 0L
            dirNames[dir.path] = dir.name
        }

        // Sum up file sizes to their immediate parent directories, and all parent directories up to root
        for (file in regularFiles) {
            val parentPath = getParentPath(file.path, rootUri)
            dirSizes[parentPath] = (dirSizes[parentPath] ?: 0L) + file.sizeBytes
        }

        // Establish structural children connections
        for (dir in directories) {
            if (dir.path == rootUri) continue
            val parentPath = getParentPath(dir.path, rootUri)
            val childrenSet = dirChildren.getOrPut(parentPath) { mutableSetOf() }
            childrenSet.add(dir.path)
        }

        // Post-order directory size calculation: propagate directory measurements upward
        propagateSizes(rootUri, dirSizes, dirChildren)

        // Recursively build tree nodes matching depth requirements
        return buildNodeRecursively(rootUri, dirSizes, dirChildren, dirNames, 0, maxDepth)
    }

    private fun getParentPath(path: String, rootUri: String): String {
        if (path == rootUri) return rootUri
        val idx = path.lastIndexOf('/')
        if (idx <= 1) return rootUri
        val parent = path.substring(0, idx)
        return if (parent.length < rootUri.length) rootUri else parent
    }

    private fun propagateSizes(path: String, sizes: MutableMap<String, Long>, children: Map<String, Set<String>>): Long {
        val childPaths = children[path] ?: emptySet()
        var total = sizes[path] ?: 0L
        for (child in childPaths) {
            total += propagateSizes(child, sizes, children)
        }
        sizes[path] = total
        return total
    }

    private fun buildNodeRecursively(
        path: String,
        sizes: Map<String, Long>,
        children: Map<String, Set<String>>,
        names: Map<String, String>,
        currentDepth: Int,
        maxDepth: Int
    ): DirectoryHeatNode {
        val size = sizes[path] ?: 0L
        val name = names[path] ?: path.substringAfterLast("/", "Subfolder")
        if (currentDepth >= maxDepth) {
            return DirectoryHeatNode(path, name, size, emptyList(), currentDepth)
        }

        val childPaths = children[path] ?: emptySet()
        val childrenNodes = childPaths.map { childPath ->
            buildNodeRecursively(childPath, sizes, children, names, currentDepth + 1, maxDepth)
        }.filter { it.sizeBytes > 0 } // omit zero byte folders to save UI layout room
         .sortedByDescending { it.sizeBytes }

        return DirectoryHeatNode(path, name, size, childrenNodes, currentDepth)
    }
}

class ShredFilesUseCase(private val shredRepository: ShredRepository) {
    operator fun invoke(uriString: String, intensity: ShredIntensity): Flow<ShredJob> {
        return shredRepository.shredAndLog(uriString, intensity.passCount)
    }
}

class CleanEmptyDirsUseCase(private val cacheRepository: CacheRepository) {
    suspend fun findEmpty(rootUriString: String): List<FileNode> {
        return cacheRepository.findEmptyDirectories(rootUriString)
    }

    suspend fun clean(directories: List<FileNode>): Int {
        return cacheRepository.deleteDirectories(directories)
    }
}

class CleanBrokenCacheUseCase(private val cacheRepository: CacheRepository) {
    suspend fun scanCache(rootUriString: String): List<CacheAppItem> {
        return cacheRepository.scanCacheCleanItems(rootUriString)
    }

    suspend fun clean(apps: List<CacheAppItem>): Long {
        return cacheRepository.cleanCaches(apps)
    }
}
