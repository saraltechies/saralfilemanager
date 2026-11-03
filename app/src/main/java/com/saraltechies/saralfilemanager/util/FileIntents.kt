package com.saraltechies.saralfilemanager.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.saraltechies.saralfilemanager.R
import java.io.File

/** Hands files to other apps through content:// links from our FileProvider (see res/xml/file_paths.xml). */
object FileIntents {

    fun uri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    fun mimeType(file: File): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"

    /** Android only lets an app pass an APK to its installer with the install permission, which this app doesn't have. */
    private fun isInstaller(file: File) = file.extension.equals("apk", ignoreCase = true)

    /** What to say when [open] returns false. */
    fun cantOpenMessage(file: File): Int = if (isInstaller(file)) R.string.apk_cant_install else R.string.no_app_to_open

    /** Opens [file] in the app the person uses for its type. False when it can't be opened that way. */
    fun open(context: Context, file: File, chooser: Boolean = false): Boolean = (chooser || !isInstaller(file)) && attempt(context) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri(context, file), mimeType(file))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        if (chooser) Intent.createChooser(intent, null) else intent
    }

    /** Android can only pass so many links between apps; well below that, to be safe on every phone. */
    const val MAX_SHARE = 500

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
    } catch (_: RuntimeException) {
        // No app for this, or more than Android can pass to one.
        false
    }
}
