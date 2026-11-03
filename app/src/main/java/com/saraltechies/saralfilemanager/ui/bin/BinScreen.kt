package com.saraltechies.saralfilemanager.ui.bin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saraltechies.saralfilemanager.R
import com.saraltechies.saralfilemanager.data.SettingsRepository
import com.saraltechies.saralfilemanager.files.Bin
import com.saraltechies.saralfilemanager.files.BinEntry
import com.saraltechies.saralfilemanager.files.FileItem
import com.saraltechies.saralfilemanager.files.FileOperations
import com.saraltechies.saralfilemanager.files.FileRepository
import com.saraltechies.saralfilemanager.files.Sizes
import com.saraltechies.saralfilemanager.ui.components.FileIcon
import com.saraltechies.saralfilemanager.ui.components.OperationBar
import com.saraltechies.saralfilemanager.ui.components.rememberSelection
import kotlinx.coroutines.launch

/** Deleted files, kept for 30 days: restore them, delete them for good, or turn the Bin off. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BinScreen(
    files: FileRepository,
    operations: FileOperations,
    settings: SettingsRepository,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val changes by operations.changes.collectAsStateWithLifecycle()
    val useBin by settings.useBin.collectAsStateWithLifecycle(initialValue = true)
    val entries by produceState<List<BinEntry>?>(null, changes) { value = files.binEntries() }
    var selected by rememberSelection()
    var confirmEmpty by remember { mutableStateOf<List<BinEntry>?>(null) }
    val all = entries.orEmpty()
    val chosen = all.filter { it.folder.path in selected }

    BackHandler(enabled = selected.isNotEmpty()) { selected = emptySet() }

    confirmEmpty?.let { list ->
        AlertDialog(
            onDismissRequest = { confirmEmpty = null },
            title = { Text(pluralStringResource(R.plurals.bin_delete_title, list.size, list.size)) },
            text = { Text(stringResource(R.string.bin_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmEmpty = null
                    selected = emptySet()
                    operations.deleteForever(list)
                }) { Text(stringResource(R.string.bin_delete_forever), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmEmpty = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    Scaffold(
        topBar = {
            if (selected.isNotEmpty()) {
                TopAppBar(
                    title = { Text(stringResource(R.string.selected_count, selected.size)) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    navigationIcon = { IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Filled.Close, stringResource(R.string.clear_selection)) } },
                    actions = {
                        IconButton(onClick = { selected = all.map { it.folder.path }.toSet() }) { Icon(Icons.Filled.SelectAll, stringResource(R.string.select_all)) }
                        IconButton(onClick = {
                            selected = emptySet()
                            operations.restore(chosen)
                        }) { Icon(Icons.Filled.RestoreFromTrash, stringResource(R.string.bin_restore)) }
                        IconButton(onClick = { confirmEmpty = chosen }) { Icon(Icons.Filled.DeleteForever, stringResource(R.string.bin_delete_forever)) }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.bin_title)) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                    actions = {
                        if (all.isNotEmpty()) TextButton(onClick = { confirmEmpty = all }) { Text(stringResource(R.string.bin_empty)) }
                    },
                )
            }
        },
        bottomBar = { OperationBar(operations, pasteTarget = null) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.bin_use), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(if (useBin) R.string.bin_use_on else R.string.bin_use_off, Bin.KEEP_DAYS),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = useBin, onCheckedChange = { scope.launch { settings.setUseBin(it) } })
            }
            if (all.isNotEmpty()) {
                Text(
                    pluralStringResource(R.plurals.bin_summary, all.size, all.size, Sizes.format(all.sumOf { it.bytes })),
                    style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            when {
                entries == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                all.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.bin_empty_text), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    items(all, key = { it.folder.path }) { entry ->
                        val isSelected = entry.folder.path in selected
                        val now = System.currentTimeMillis()
                        val days = entry.daysLeft(now)
                        val toggle = { selected = if (isSelected) selected - entry.folder.path else selected + entry.folder.path }
                        ListItem(
                            modifier = Modifier.combinedClickable(onClick = toggle, onLongClick = toggle),
                            leadingContent = { FileIcon(remember(entry.item.path) { FileItem.from(entry.item) }, isSelected) },
                            headlineContent = { Text(entry.name, maxLines = 1, overflow = TextOverflow.MiddleEllipsis) },
                            supportingContent = {
                                Text(
                                    entry.originalFolder.removePrefix("/storage/emulated/0").ifEmpty { "/" } + " · " + Sizes.format(entry.bytes) + " · " +
                                        pluralStringResource(R.plurals.bin_days_left, days, days),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
                        )
                    }
                }
            }
        }
    }
}
