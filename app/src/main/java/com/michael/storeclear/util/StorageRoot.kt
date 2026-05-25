package com.michael.storeclear.util

import android.net.Uri
import android.os.Environment
import java.io.File
import java.net.URI

object StorageRoot {

    fun allFilesRootUri(): String =
        Environment.getExternalStorageDirectory().absolutePath

    fun isFileAccess(uriString: String): Boolean =
        uriString.startsWith("/") || uriString.startsWith("file:")

    fun isSafAccess(uriString: String): Boolean =
        uriString.startsWith("content://")

    fun toFile(uriString: String): File = when {
        uriString.startsWith("/") -> File(uriString)
        uriString.startsWith("file:") -> File(URI.create(uriString.trimEnd('/')))
        else -> throw IllegalArgumentException("Not a file path: $uriString")
    }

    /** Canonical storage key: absolute path for files, unchanged for SAF content URIs. */
    fun normalizeRoot(stored: String): String = when {
        isSafAccess(stored) -> stored
        isFileAccess(stored) -> toFile(stored).absolutePath
        else -> stored
    }

    fun fileUri(file: File): String = file.absolutePath
}
