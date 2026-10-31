package com.saraltechies.saralfilemanager.ui.category

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saraltechies.saralfilemanager.R
import com.saraltechies.saralfilemanager.data.SettingsRepository
import com.saraltechies.saralfilemanager.data.ViewPrefs
import com.saraltechies.saralfilemanager.files.FileCategory
import com.saraltechies.saralfilemanager.files.FileItem
import com.saraltechies.saralfilemanager.files.FileOperations
import com.saraltechies.saralfilemanager.files.FileRepository
import com.saraltechies.saralfilemanager.ui.components.FileActions
import com.saraltechies.saralfilemanager.ui.components.FileList
import com.saraltechies.saralfilemanager.ui.components.OperationBar
import com.saraltechies.saralfilemanager.ui.components.SelectionTopBar
import com.saraltechies.saralfilemanager.ui.components.SortDialog
import kotlinx.coroutines.launch

/** Every file of one kind on the phone (from Android's media index), or the recently changed files when [category] is null. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    category: FileCategory?,
    files: FileRepository,
    operations: FileOperations,
    settings: SettingsRepository,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onOpenFolder: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs by settings.viewPrefs.collectAsStateWithLifecycle(initialValue = ViewPrefs())
    val bookmarks by settings.bookmarks.collectAsStateWithLifecycle(initialValue = emptySet())
    val changes by operations.changes.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf(emptySet<String>()) }

    val items by produceState<List<FileItem>?>(null, category, prefs, changes) {
        value = if (category == null) files.recent() else files.category(category, prefs.showHidden, prefs.comparator)
    }
    val shown = items.orEmpty()
    val selectedItems = shown.filter { it.path in selected }

    BackHandler(enabled = selected.isNotEmpty()) { selected = emptySet() }

    val actions = remember { FileActions(context, scope, files, operations, settings, snackbar) }
    actions.Dialogs(currentFolder = null, onSelectionDone = { selected = emptySet() })
    var showSort by remember { mutableStateOf(false) }
    if (showSort) {
        SortDialog(prefs, onChange = { by, descending -> scope.launch { settings.setSort(by, descending) } }, onDismiss = { showSort = false })
    }

    Scaffold(
        topBar = {
            if (selected.isNotEmpty()) {
                SelectionTopBar(
                    count = selected.size,
                    onClose = { selected = emptySet() },
                    onCopy = {
                        operations.setClip(selected.toList(), move = false)
                        selected = emptySet()
                    },
                    onCut = {
                        operations.setClip(selected.toList(), move = true)
                        selected = emptySet()
                    },
                    onDelete = { actions.askDelete(selectedItems) },
                    onShare = { actions.share(selectedItems) },
                ) { dismiss ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.select_all)) },
                        onClick = {
                            dismiss()
                            selected = shown.map { it.path }.toSet()
                        },
                    )
                    actions.SelectionMenuItems(selectedItems, dismiss, bookmarks, onShowInFolder = onOpenFolder)
                }
            } else {
                TopAppBar(
                    title = { Text(stringResource(category.title())) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                    },
                    actions = {
                        IconButton(onClick = { scope.launch { settings.setGrid(!prefs.grid) } }) {
                            Icon(
                                if (prefs.grid) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                                stringResource(if (prefs.grid) R.string.list_view else R.string.grid_view),
                            )
                        }
                        // Recent files are always newest first.
                        if (category != null) {
                            var open by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options)) }
                                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.sort_by)) }, onClick = { open = false; showSort = true })
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.show_hidden)) },
                                        trailingIcon = { Checkbox(checked = prefs.showHidden, onCheckedChange = null) },
                                        onClick = {
                                            open = false
                                            scope.launch { settings.setShowHidden(!prefs.showHidden) }
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
            }
        },
        bottomBar = { OperationBar(operations, pasteTarget = null) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val list = items
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.category_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            else -> FileList(
                items = list,
                grid = prefs.grid,
                selected = selected,
                onClick = { item ->
                    if (selected.isNotEmpty()) {
                        selected = if (item.path in selected) selected - item.path else selected + item.path
                    } else {
                        actions.open(item.file)
                    }
                },
                onLongClick = { item -> selected = if (item.path in selected) selected - item.path else selected + item.path },
                contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
                showFolder = true,
            )
        }
    }
}

fun FileCategory?.title(): Int = when (this) {
    null -> R.string.category_recent
    FileCategory.IMAGE -> R.string.category_images
    FileCategory.VIDEO -> R.string.category_videos
    FileCategory.AUDIO -> R.string.category_audio
    FileCategory.DOCUMENT -> R.string.category_documents
    FileCategory.ARCHIVE -> R.string.category_archives
    FileCategory.APK -> R.string.category_apps
    FileCategory.OTHER -> R.string.category_other
}
