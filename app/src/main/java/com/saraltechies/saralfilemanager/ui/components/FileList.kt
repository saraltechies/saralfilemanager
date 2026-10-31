package com.saraltechies.saralfilemanager.ui.components

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saraltechies.saralfilemanager.R
import com.saraltechies.saralfilemanager.files.FileItem
import com.saraltechies.saralfilemanager.files.Sizes
import java.io.File

/** Files as a list or a grid. A tap opens, a long press starts selecting; while selecting, taps toggle. */
@Composable
fun FileList(
    items: List<FileItem>,
    grid: Boolean,
    selected: Set<String>,
    onClick: (FileItem) -> Unit,
    onLongClick: (FileItem) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    /** Search and category results come from many folders, so each row says which. */
    showFolder: Boolean = false,
) {
    if (grid) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(GRID_CELL),
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 8.dp,
                end = 8.dp,
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + FAB_SPACE,
            ),
        ) {
            items(items, key = { it.path }) { item ->
                FileCell(item, item.path in selected, { onClick(item) }, { onLongClick(item) })
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + FAB_SPACE,
            ),
        ) {
            items(items, key = { it.path }) { item ->
                FileRow(item, item.path in selected, showFolder, { onClick(item) }, { onLongClick(item) })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRow(item: FileItem, selected: Boolean, showFolder: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    ListItem(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        leadingContent = { FileIcon(item, selected) },
        headlineContent = { Text(item.name, maxLines = 1, overflow = TextOverflow.MiddleEllipsis) },
        supportingContent = {
            Text(
                if (showFolder) folderLabel(item) else detailsLabel(item),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        ),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileCell(item: FileItem, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        Modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { FileIcon(item, selected, size = 72.dp, rounded = true) }
        Text(
            item.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** "12 items · 3 Oct 2026" for folders, "2.4 MB · 3 Oct 2026" for files. */
@Composable
fun detailsLabel(item: FileItem): String {
    val what = when {
        !item.isDirectory -> Sizes.format(item.size)
        item.childCount < 0 -> stringResource(R.string.folder)
        else -> pluralStringResource(R.plurals.item_count, item.childCount, item.childCount)
    }
    return "$what · ${dateLabel(item.lastModified)}"
}

@Composable
private fun folderLabel(item: FileItem): String =
    (File(item.path).parent ?: "").removePrefix("/storage/emulated/0").ifEmpty { "/" } +
        if (item.isDirectory) "" else " · ${Sizes.format(item.size)}"

@Composable
fun dateLabel(millis: Long): String {
    val context = LocalContext.current
    return DateUtils.formatDateTime(
        context,
        millis,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_ABBREV_MONTH,
    )
}

private val GRID_CELL = 104.dp
/** Room at the end of a list so the last items aren't hidden behind the floating button. */
private val FAB_SPACE = 88.dp
