package com.saraltechies.saralfilemanager.ui.gallery

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saraltechies.saralfilemanager.R
import com.saraltechies.saralfilemanager.data.SettingsRepository
import com.saraltechies.saralfilemanager.data.ViewPrefs
import com.saraltechies.saralfilemanager.files.FileCategory
import com.saraltechies.saralfilemanager.files.FileItem
import com.saraltechies.saralfilemanager.files.FileOperations
import com.saraltechies.saralfilemanager.files.FileRepository
import com.saraltechies.saralfilemanager.files.Gallery
import com.saraltechies.saralfilemanager.files.GalleryAlbum
import com.saraltechies.saralfilemanager.files.GallerySort
import com.saraltechies.saralfilemanager.files.MediaFile
import com.saraltechies.saralfilemanager.files.Sizes
import com.saraltechies.saralfilemanager.ui.category.title
import com.saraltechies.saralfilemanager.ui.components.FileActions
import com.saraltechies.saralfilemanager.ui.components.FileList
import com.saraltechies.saralfilemanager.ui.components.OperationBar
import com.saraltechies.saralfilemanager.ui.components.SelectionTopBar
import com.saraltechies.saralfilemanager.ui.components.icon
import com.saraltechies.saralfilemanager.ui.components.rememberSelection
import com.saraltechies.saralfilemanager.util.Thumbnails
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** All photos or all videos: a thumbnail grid by date or as albums, with quick ways to select many. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    category: FileCategory,
    files: FileRepository,
    operations: FileOperations,
    settings: SettingsRepository,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onOpenFolder: (String) -> Unit,
    onOpenViewer: (List<MediaFile>, Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewPrefs by settings.viewPrefs.collectAsStateWithLifecycle(initialValue = ViewPrefs())
    val prefs by remember(category) { settings.galleryPrefs(category.name) }.collectAsStateWithLifecycle(initialValue = null)
    val bookmarks by settings.bookmarks.collectAsStateWithLifecycle(initialValue = emptySet())
    val changes by operations.changes.collectAsStateWithLifecycle()
    var selected by rememberSelection()
    var album by rememberSaveable { mutableStateOf<String?>(null) }
    var showAlbums by rememberSaveable { mutableStateOf(false) }
    var type by rememberSaveable { mutableStateOf<String?>(null) }

    val all by produceState<List<MediaFile>?>(null, category, viewPrefs.showHidden, changes) {
        value = files.media(category, viewPrefs.showHidden)
    }
    val sort = prefs?.sort ?: GallerySort.NEWEST
    val inAlbum = remember(all, album) { all.orEmpty().let { list -> album?.let { a -> list.filter { it.folder == a } } ?: list } }
    val types = remember(inAlbum) { Gallery.types(inAlbum) }
    val shown = remember(inAlbum, sort, type) { Gallery.sorted(inAlbum.filter { type == null || it.item.extension == type }, sort) }
    val selectedItems = remember(shown, selected) { shown.filter { it.path in selected }.map { it.item } }
    val selecting = selected.isNotEmpty()

    BackHandler(enabled = selecting || album != null || showAlbums) {
        when {
            selecting -> selected = emptySet()
            album != null -> {
                album = null
                type = null
            }
            else -> showAlbums = false
        }
    }

    val actions = remember { FileActions(context, scope, files, operations, settings, snackbar) }
    actions.Dialogs(currentFolder = null, onSelectionDone = { selected = emptySet() })

    Scaffold(
        topBar = {
            if (selecting) {
                SelectionTopBar(
                    count = selected.size,
                    subtitle = Sizes.format(selectedItems.sumOf { it.size }),
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
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.select_invert)) },
                        onClick = {
                            dismiss()
                            selected = shown.map { it.path }.toSet() - selected
                        },
                    )
                    actions.SelectionMenuItems(selectedItems, dismiss, bookmarks, onShowInFolder = onOpenFolder)
                }
            } else {
                TopAppBar(
                    title = { Text(album?.let { java.io.File(it).name } ?: stringResource(category.title()), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (album != null) {
                                album = null
                                type = null
                            } else onBack()
                        }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                    },
                    actions = {
                        val list = prefs?.list == true
                        IconButton(onClick = { scope.launch { settings.setGalleryList(category.name, !list) } }) {
                            Icon(
                                if (list) Icons.Filled.GridView else Icons.AutoMirrored.Filled.ViewList,
                                stringResource(if (list) R.string.grid_view else R.string.list_view),
                            )
                        }
                        GalleryMenu(
                            sort = sort,
                            showHidden = viewPrefs.showHidden,
                            onSort = { scope.launch { settings.setGallerySort(category.name, it) } },
                            onToggleHidden = { scope.launch { settings.setShowHidden(!viewPrefs.showHidden) } },
                        )
                    },
                )
            }
        },
        bottomBar = { OperationBar(operations, pasteTarget = null) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val list = all
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())) {
            when {
                list == null || prefs == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                list.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.category_empty), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    if (!selecting) {
                        FilterRow(
                            showAlbumsChip = album == null,
                            showAlbums = showAlbums,
                            onShowAlbums = { showAlbums = it; type = null },
                            types = if (showAlbums && album == null) emptyList() else types,
                            type = type,
                            onType = { type = it },
                        )
                    }
                    when {
                        showAlbums && album == null -> AlbumsGrid(Gallery.albums(list), category) {
                            album = it.folder
                        }
                        prefs?.list == true -> FileList(
                            items = shown.map { it.item },
                            grid = false,
                            selected = selected,
                            onClick = { item ->
                                if (selecting) selected = toggle(selected, item.path)
                                else onOpenViewer(shown, shown.indexOfFirst { it.path == item.path })
                            },
                            onLongClick = { selected = toggle(selected, it.path) },
                            contentPadding = PaddingValues(0.dp),
                            showFolder = true,
                        )
                        else -> PhotoGrid(
                            items = shown,
                            byDay = Gallery.groupsByDay(sort),
                            showSizes = sort == GallerySort.LARGEST || sort == GallerySort.SMALLEST,
                            category = category,
                            columns = prefs?.columns ?: Gallery.DEFAULT_COLUMNS,
                            onColumns = { scope.launch { settings.setGalleryColumns(category.name, it) } },
                            selection = remember { SelectionHolder({ selected }, { selected = it }) },
                            onOpen = { onOpenViewer(shown, it) },
                        )
                    }
                }
            }
        }
    }
}

/** A way into the screen's selection for the grid, without a copy of it going stale. */
private class SelectionHolder(val get: () -> Set<String>, val set: (Set<String>) -> Unit)

private fun toggle(selected: Set<String>, path: String) = if (path in selected) selected - path else selected + path

@Composable
private fun FilterRow(
    showAlbumsChip: Boolean,
    showAlbums: Boolean,
    onShowAlbums: (Boolean) -> Unit,
    types: List<String>,
    type: String?,
    onType: (String?) -> Unit,
) {
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showAlbumsChip) {
            item(key = "albums") {
                FilterChip(selected = showAlbums, onClick = { onShowAlbums(!showAlbums) }, label = { Text(stringResource(R.string.gallery_albums)) })
            }
        }
        if (types.size > 1) {
            items(types, key = { it }) { t ->
                FilterChip(selected = type == t, onClick = { onType(if (type == t) null else t) }, label = { Text(t.uppercase()) })
            }
        }
    }
}

@Composable
private fun GalleryMenu(sort: GallerySort, showHidden: Boolean, onSort: (GallerySort) -> Unit, onToggleHidden: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            GallerySort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.label())) },
                    leadingIcon = { RadioButton(selected = sort == option, onClick = null) },
                    onClick = { open = false; onSort(option) },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.show_hidden)) },
                leadingIcon = { Checkbox(checked = showHidden, onCheckedChange = null) },
                onClick = { open = false; onToggleHidden() },
            )
        }
    }
}

private fun GallerySort.label(): Int = when (this) {
    GallerySort.NEWEST -> R.string.gallery_sort_newest
    GallerySort.OLDEST -> R.string.gallery_sort_oldest
    GallerySort.LARGEST -> R.string.gallery_sort_largest
    GallerySort.SMALLEST -> R.string.gallery_sort_smallest
    GallerySort.NAME -> R.string.gallery_sort_name
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoGrid(
    items: List<MediaFile>,
    byDay: Boolean,
    showSizes: Boolean,
    category: FileCategory,
    columns: Int,
    onColumns: (Int) -> Unit,
    selection: SelectionHolder,
    onOpen: (Int) -> Unit,
) {
    val state = rememberLazyGridState()
    val order = remember(items) { items.map { it.path } }
    val indexOf = remember(order) { order.withIndex().associate { (i, p) -> p to i } }
    val days = remember(items, byDay) { if (byDay) Gallery.days(items) else emptyList() }
    val speed = remember { mutableFloatStateOf(0f) }
    val thresholdPx = with(LocalDensity.current) { 64.dp.toPx() }
    val haptics = LocalHapticFeedback.current
    val current = selection.get()
    val selecting = current.isNotEmpty()
    val columnsNow by rememberUpdatedState(columns)
    val onColumnsNow by rememberUpdatedState(onColumns)

    // While a drag rests near the top or bottom edge, the grid keeps moving.
    LaunchedEffect(speed.floatValue) {
        if (speed.floatValue != 0f) while (true) {
            state.scrollBy(speed.floatValue / 4)
            delay(16)
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = state,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Two fingers change the number of columns; one finger is left alone for scrolling and tapping.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var zoom = 1f
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.count { it.pressed } >= 2) {
                            zoom *= event.calculateZoom()
                            event.changes.forEach { it.consume() }
                            if (zoom > 1.3f || zoom < 0.77f) {
                                onColumnsNow(Gallery.columns(columnsNow + if (zoom > 1f) -1 else 1))
                                zoom = 1f
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(order) { dragToSelect(state, order, selection, speed, thresholdPx) { haptics.performHapticFeedback(HapticFeedbackType.LongPress) } },
        contentPadding = PaddingValues(bottom = 88.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val cell: @Composable (MediaFile) -> Unit = { media ->
            PhotoCell(
                media = media,
                category = category,
                columns = columns,
                selected = media.path in current,
                selecting = selecting,
                showSize = showSizes,
                onClick = {
                    if (selecting) selection.set(toggle(current, media.path))
                    else onOpen(indexOf[media.path] ?: 0)
                },
                onLongClick = { selection.set(current + media.path) },
            )
        }
        if (byDay) {
            days.forEach { day ->
                day.month?.takeIf { it.atDay(1) != LocalDate.now().withDayOfMonth(1) }?.let { month ->
                    item(key = "m:$month", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 2.dp),
                        )
                    }
                }
                item(key = "d:" + day.date, span = { GridItemSpan(maxLineSpan) }) {
                    val paths = day.items.map { it.path }
                    val allIn = paths.all { it in current }
                    DayHeader(day.date, allIn) {
                        selection.set(if (allIn) current - paths.toSet() else current + paths)
                    }
                }
                items(day.items, key = { it.path }) { cell(it) }
            }
        } else {
            items(items, key = { it.path }) { cell(it) }
        }
    }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.dragToSelect(
    state: LazyGridState,
    order: List<String>,
    selection: SelectionHolder,
    speed: androidx.compose.runtime.MutableFloatState,
    thresholdPx: Float,
    onStart: () -> Unit,
) {
    fun itemAt(point: Offset): Int? = state.layoutInfo.visibleItemsInfo.firstOrNull { info ->
        val p = IntOffset(point.x.toInt(), point.y.toInt()) - info.offset
        p.x in 0 until info.size.width && p.y in 0 until info.size.height
    }?.key?.let { key -> (key as? String)?.let(order::indexOf) }?.takeIf { it >= 0 }

    var start = -1
    var before = emptySet<String>()
    detectDragGesturesAfterLongPress(
        onDragStart = { offset ->
            itemAt(offset)?.let { index ->
                start = index
                before = selection.get()
                selection.set(before + order[index])
                onStart()
            }
        },
        onDragEnd = {
            start = -1
            speed.floatValue = 0f
        },
        onDragCancel = {
            start = -1
            speed.floatValue = 0f
        },
        onDrag = { change, _ ->
            if (start >= 0) {
                val y = change.position.y
                val fromBottom = state.layoutInfo.viewportSize.height - y
                speed.floatValue = when {
                    fromBottom < thresholdPx -> thresholdPx - fromBottom
                    y < thresholdPx -> -(thresholdPx - y)
                    else -> 0f
                }
                itemAt(change.position)?.let { selection.set(Gallery.dragSelection(order, before, start, it)) }
            }
        },
    )
}

@Composable
private fun DayHeader(date: LocalDate, allSelected: Boolean, onToggle: () -> Unit) {
    val label = dayLabel(date)
    val description = stringResource(if (allSelected) R.string.gallery_unselect_day else R.string.gallery_select_day, label)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .semantics { contentDescription = description }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        Icon(
            if (allSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, null,
            tint = if (allSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun dayLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> stringResource(R.string.gallery_today)
        today.minusDays(1) -> stringResource(R.string.gallery_yesterday)
        else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoCell(
    media: MediaFile,
    category: FileCategory,
    columns: Int,
    selected: Boolean,
    selecting: Boolean,
    showSize: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val name = media.item.name
    Box(
        Modifier
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .semantics {
                contentDescription = name
                this.selected = selected
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Thumbnail(media.item, media.id, category, columns, Modifier.fillMaxSize().padding(if (selected) 10.dp else 0.dp).clip(if (selected) RoundedCornerShape(8.dp) else RoundedCornerShape(0.dp)))
        if (category == FileCategory.VIDEO || media.durationMs > 0) {
            Row(
                Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(14.dp))
                if (media.durationMs > 0) Text(Gallery.duration(media.durationMs), color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
        if (showSize) {
            Text(
                Sizes.format(media.item.size), color = Color.White, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp),
            )
        }
        if (selecting) {
            Icon(
                if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, null,
                tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(4.dp).size(22.dp)
                    .background(if (selected) Color.White else Color.Black.copy(alpha = 0.25f), CircleShape),
            )
        }
    }
}

@Composable
private fun Thumbnail(item: FileItem, mediaId: Long, category: FileCategory, columns: Int, modifier: Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sizePx = with(density) { (360.dp.toPx() / columns).toInt() }
    val image: State<ImageBitmap?> = produceState(Thumbnails.cached(item.path, item.lastModified, sizePx), item.path, item.lastModified, sizePx) {
        if (value == null) value = Thumbnails.load(context, item.file, item.lastModified, category, sizePx, mediaId)
    }
    val bitmap = image.value
    if (bitmap != null) {
        Image(bitmap, null, contentScale = ContentScale.Crop, modifier = modifier)
    } else {
        Box(modifier, contentAlignment = Alignment.Center) {
            Icon(category.icon(), null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun AlbumsGrid(albums: List<GalleryAlbum>, category: FileCategory, onOpen: (GalleryAlbum) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 88.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(albums, key = { it.folder }) { album ->
            Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable { onOpen(album) }) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                    Thumbnail(album.cover.item, album.cover.id, category, 2, Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(0.dp))
                Text(album.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                Text(
                    pluralStringResource(R.plurals.item_count, album.items.size, album.items.size) + " · " + Sizes.format(album.bytes),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                )
            }
        }
    }
}
