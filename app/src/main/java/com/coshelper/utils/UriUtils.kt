package com.coshelper.utils

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Copies a content:// URI into the app's private files directory and returns the destination file.
 * Returns null if the copy fails. Native libraries cannot open content:// URIs, so model pickers
 * must copy the selected file here before loading.
 */
fun copyUriToPrivateFile(context: Context, uri: Uri, relativePath: String): File? {
    return try {
        val dest = File(context.filesDir, relativePath)
        dest.parentFile?.mkdirs()
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        dest
    } catch (e: Exception) {
        null
    }
}

fun copyModelUriToFilesDir(context: Context, uri: Uri, fileName: String): File? {
    return copyUriToPrivateFile(context, uri, "models/$fileName")
}
