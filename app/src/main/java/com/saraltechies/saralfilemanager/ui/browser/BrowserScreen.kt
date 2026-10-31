package com.saraltechies.saralfilemanager.ui.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saraltechies.saralfilemanager.R
import com.saraltechies.saralfilemanager.data.SettingsRepository
import com.saraltechies.saralfilemanager.data.ViewPrefs
import com.saraltechies.saralfilemanager.files.FileItem
import com.saraltechies.saralfilemanager.files.FileOperations
import com.saraltechies.saralfilemanager.files.FileRepository
import com.saraltechies.saralfilemanager.files.Listing
import com.saraltechies.saralfilemanager.ui.components.FileActions
import com.saraltechies.saralfilemanager.ui.components.FileList
import com.saraltechies.saralfilemanager.ui.components.OperationBar
import com.saraltechies.saralfilemanager.ui.components.SelectionTopBar
import com.saraltechies.saralfilemanager.ui.components.SortDialog
import com.saraltechies.saralfilemanager.ui.components.ZipDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * One storage volume, folder by folder. Back goes up a folder until the volume's top, then leaves the screen.
 * The search field looks through the current folder and everything below it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    startPath: String,
    startSearch: Boolean,
    files: FileRepository,
    operations: FileOperations,
    settings: SettingsRepository,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val root = remember(startPath) { files.volumeRootOf(startPath) }
    val rootName = remember(root) { files.volumes().firstOrNull { it.path == root }?.name ?: root }
    var path by rememberSaveable { mutableStateOf(startPath) }

    val prefs by settings.viewPrefs.collectAsStateWithLifecycle(initialValue = ViewPrefs())
    val bookmarks by settings.bookmarks.collectAsStateWithLifecycle(initialValue = emptySet())
    val changes by operations.changes.collectAsStateWithLifecycle()

    var searching by rememberSaveable { mutableStateOf(startSearch) }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf(emptySet<String>()) }

    val listing by produceState<Listing>(Listing.Loading, path, prefs, changes) {
        value = files.list(path, prefs.showHidden, prefs.comparator)
    }
    val results by produceState<List<FileItem>?>(null, searching, query, path, prefs.showHidden, changes) {
        if (!searching || query.isBlank()) {
            value = null
            return@produceState
        }
        delay(SEARCH_DELAY_MS)
        value = emptyList()
        files.search(path, query, prefs.showHidden).collect { value = it }
    }
    val shown: List<FileItem> = results ?: (listing as? Listing.Loaded)?.items.orEmpty()

    // Selection belongs to the folder it was made in.
    LaunchedEffect(path) { selected = emptySet() }

    BackHandler(enabled = selected.isNotEmpty() || searching || path != root) {
        when {
            selected.isNotEmpty() -> selected = emptySet()
            searching -> {
                searching = false
                query = ""
            }
            else -> path = File(path).parent ?: root
        }
    }

    val actions = remember { FileActions(context, scope, files, operations, settings, snackbar) }
    actions.Dialogs(currentFolder = path, onSelectionDone = { selected = emptySet() })

    var zipToOpen by remember { mutableStateOf<FileItem?>(null) }
    zipToOpen?.let { zip ->
        ZipDialog(
            name = zip.name,
            onExtract = {
                zipToOpen = null
                operations.extract(zip.path, path)
            },
            onOpenWith = {
                zipToOpen = null
                actions.open(zip.file, chooser = true)
            },
            onDismiss = { zipToOpen = null },
        )
    }
    var showSort by remember { mutableStateOf(false) }
    if (showSort) {
        SortDialog(
            prefs = prefs,
            onChange = { by, descending -> scope.launch { settings.setSort(by, descending) } },
            onDismiss = { showSort = false },
        )
    }

    val selectedItems = shown.filter { it.path in selected }
    Scaffold(
        topBar = {
            when {
                selected.isNotEmpty() -> SelectionTopBar(
                    count = selected.size,
                    onClose = { selected = emptySet() },
                    onCopy = {
                        operations.setClip(selected.toList(), move = false)
                        selected = emptySet()
                        searching = false
                    },
                    onCut = {
                        operations.setClip(selected.toList(), move = true)
                        selected = emptySet()
                        searching = false
                    },
                    onDelete = { actions.askDelete(selectedItems) },
                    onShare = if (selectedItems.none { it.isDirectory }) ({ actions.share(selectedItems) }) else null,
                ) { dismiss ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.select_all)) },
                        onClick = {
                            dismiss()
                            selected = shown.map { it.path }.toSet()
                        },
                    )
                    actions.SelectionMenuItems(selectedItems, dismiss, bookmarks)
                }
                searching -> SearchTopBar(query, onQueryChange = { query = it }, onClose = {
                    searching = false
                    query = ""
                })
                else -> TopAppBar(
                    title = {
                        Text(
                            if (path == root) rootName else File(path).name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                    },
                    actions = {
                        IconButton(onClick = { searching = true }) { Icon(Icons.Filled.Search, stringResource(R.string.search)) }
                        IconButton(onClick = { scope.launch { settings.setGrid(!prefs.grid) } }) {
                            Icon(
                                if (prefs.grid) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                                stringResource(if (prefs.grid) R.string.list_view else R.string.grid_view),
                            )
                        }
                        FolderMenu(
                            prefs = prefs,
                            bookmarked = path in bookmarks,
                            onNewFile = { actions.askNewFile() },
                            onSort = { showSort = true },
                            onToggleHidden = { scope.launch { settings.setShowHidden(!prefs.showHidden) } },
                            onToggleBookmark = { scope.launch { settings.setBookmarked(path, path !in bookmarks) } },
                        )
                    },
                )
            }
        },
        floatingActionButton = {
            if (selected.isEmpty() && !searching) {
                FloatingActionButton(onClick = { actions.askNewFolder() }) {
                    Icon(Icons.Filled.CreateNewFolder, stringResource(R.string.new_folder))
                }
            }
        },
        bottomBar = { OperationBar(operations, pasteTarget = if (searching) null else path) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            if (!searching) {
                Breadcrumbs(root, rootName, path, onOpen = { path = it })
                HorizontalDivider()
            }
            val bodyPadding = PaddingValues(bottom = padding.calculateBottomPadding())
            val onClick: (FileItem) -> Unit = { item ->
                when {
                    selected.isNotEmpty() -> selected = if (item.path in selected) selected - item.path else selected + item.path
                    item.isDirectory -> {
                        searching = false
                        query = ""
                        path = item.path
                    }
                    item.extension == "zip" -> zipToOpen = item
                    else -> actions.open(item.file)
                }
            }
            val onLongClick: (FileItem) -> Unit = { item ->
                selected = if (item.path in selected) selected - item.path else selected + item.path
            }
            val found = results
            when {
                found != null && found.isEmpty() -> Message(stringResource(R.string.search_nothing_found, query))
                found != null -> FileList(shown, prefs.grid, selected, onClick, onLongClick, bodyPadding, showFolder = true)
                searching && query.isBlank() -> Message(stringResource(R.string.search_hint, File(path).name.ifEmpty { rootName }))
                listing is Listing.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                listing is Listing.Unreadable -> Message(stringResource(R.string.folder_unreadable))
                shown.isEmpty() -> Message(stringResource(R.string.folder_empty))
                else -> FileList(shown, prefs.grid, selected, onClick, onLongClick, bodyPadding)
            }
        }
    }
}

/** Where you are, from the top of the storage; tap a part to go back to it. */
@Composable
private fun Breadcrumbs(root: String, rootName: String, path: String, onOpen: (String) -> Unit) {
    val parts = remember(root, path) {
        val below = path.removePrefix(root).split('/').filter { it.isNotEmpty() }
        listOf(rootName to root) + below.mapIndexed { i, name -> name to (root.trimEnd('/') + "/" + below.take(i + 1).joinToString("/")) }
    }
    val state = rememberLazyListState()
    LaunchedEffect(parts.size) { state.scrollToItem(parts.lastIndex) }
    LazyRow(
        state = state,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(parts) { index, (name, target) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (index > 0) Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                val last = index == parts.lastIndex
                Text(
                    name,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (last) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(2.dp)
                        .clickable(enabled = !last) { onOpen(target) }
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun FolderMenu(
    prefs: ViewPrefs,
    bookmarked: Boolean,
    onNewFile: () -> Unit,
    onSort: () -> Unit,
    onToggleHidden: () -> Unit,
    onToggleBookmark: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.new_file)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.NoteAdd, null) },
                onClick = { open = false; onNewFile() },
            )
            DropdownMenuItem(text = { Text(stringResource(R.string.sort_by)) }, onClick = { open = false; onSort() })
            DropdownMenuItem(
                text = { Text(stringResource(R.string.show_hidden)) },
                trailingIcon = { Checkbox(checked = prefs.showHidden, onCheckedChange = null) },
                onClick = { open = false; onToggleHidden() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(if (bookmarked) R.string.remove_bookmark else R.string.bookmark_folder)) },
                onClick = { open = false; onToggleBookmark() },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    TopAppBar(
        navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.search_placeholder)) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.focusRequester(focus),
            )
        },
        actions = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Filled.Close, stringResource(R.string.clear_search)) }
            }
        },
    )
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private const val SEARCH_DELAY_MS = 300L
