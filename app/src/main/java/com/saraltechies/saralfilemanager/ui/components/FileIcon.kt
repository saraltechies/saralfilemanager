package com.saraltechies.saralfilemanager.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.saraltechies.saralfilemanager.files.FileCategory
import com.saraltechies.saralfilemanager.files.FileItem
import com.saraltechies.saralfilemanager.util.Thumbnails

/** One colour per kind of file, used for icons here and for the category tiles on the home screen. */
fun FileCategory.color(): Color = when (this) {
    FileCategory.IMAGE -> Color(0xFF16A34A)
    FileCategory.VIDEO -> Color(0xFFDB2777)
    FileCategory.AUDIO -> Color(0xFFF59E0B)
    FileCategory.DOCUMENT -> Color(0xFF0D6EFD)
    FileCategory.ARCHIVE -> Color(0xFF9333EA)
    FileCategory.APK -> Color(0xFF0D9488)
    FileCategory.OTHER -> Color(0xFF64748B)
}

fun FileCategory.icon(): ImageVector = when (this) {
    FileCategory.IMAGE -> Icons.Filled.Image
    FileCategory.VIDEO -> Icons.Filled.Movie
    FileCategory.AUDIO -> Icons.Filled.MusicNote
    FileCategory.DOCUMENT -> Icons.Filled.Description
    FileCategory.ARCHIVE -> Icons.Filled.Archive
    FileCategory.APK -> Icons.Filled.Android
    FileCategory.OTHER -> Icons.AutoMirrored.Filled.InsertDriveFile
}

/** A round badge with the file's kind, or a photo/video preview; a check mark while selected. */
@Composable
fun FileIcon(item: FileItem, selected: Boolean, size: Dp = 40.dp, rounded: Boolean = false) {
    val shape = if (rounded) RoundedCornerShape(12.dp) else CircleShape
    if (selected) {
        Box(Modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
        }
        return
    }
    val category = item.category
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val thumbnail by produceState<ImageBitmap?>(null, item.path, item.lastModified, sizePx) {
        value = if (!item.isDirectory && Thumbnails.supports(category)) Thumbnails.load(item.file, category, sizePx) else null
    }
    val preview = thumbnail
    if (preview != null) {
        Image(preview, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(shape))
        return
    }
    val (icon, tint) = if (item.isDirectory) Icons.Filled.Folder to MaterialTheme.colorScheme.primary else category.icon() to category.color()
    Box(Modifier.size(size).clip(shape).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}
