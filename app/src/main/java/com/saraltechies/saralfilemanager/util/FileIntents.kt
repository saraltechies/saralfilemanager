package com.saraltechies.saralfilemanager.util

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

/** Hands files to other apps through content:// links from our FileProvider (see res/xml/file_paths.xml). */
object FileIntents {

    fun uri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    fun mimeType(file: File): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"

    /** Opens [file] in the app the person uses for its type. False when no app can open it. */
    fun open(context: Context, file: File, chooser: Boolean = false): Boolean = attempt(context) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri(context, file), mimeType(file))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        if (chooser) Intent.createChooser(intent, null) else intent
    }

    fun share(context: Context, files: List<File>): Boolean = files.isNotEmpty() && attempt(context) {
        val uris = files.map { uri(context, it) }
        val types = files.map(::mimeType).distinct()
        val type = types.singleOrNull()
            ?: types.map { it.substringBefore('/') }.distinct().singleOrNull()?.let { "$it/*" }
            ?: "*/*"
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        intent.type = type
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // With several files, the receiving app only gets read access through ClipData.
        intent.clipData = ClipData.newRawUri(null, uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        Intent.createChooser(intent, null)
    }

    private fun attempt(context: Context, build: () -> Intent): Boolean = try {
        context.startActivity(build().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: IllegalArgumentException) {
        // FileProvider can't make a link for this path (a folder outside shared storage).
        false
    }
}
