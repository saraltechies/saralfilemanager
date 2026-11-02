package com.saraltechies.saralfilemanager.ui.viewer

import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.saraltechies.saralfilemanager.R
import com.saraltechies.saralfilemanager.data.SettingsRepository
import com.saraltechies.saralfilemanager.files.FileCategory
import com.saraltechies.saralfilemanager.files.FileOperations
import com.saraltechies.saralfilemanager.files.FileRepository
import com.saraltechies.saralfilemanager.files.MediaFile
import com.saraltechies.saralfilemanager.ui.components.FileActions
import com.saraltechies.saralfilemanager.ui.components.OperationBar
import com.saraltechies.saralfilemanager.util.Thumbnails

/** What to show in the viewer, handed over in memory because a list of thousands of paths won't fit in a navigation route. */
object ViewerQueue {
    @Volatile
    var items: List<MediaFile> = emptyList()
        private set

    @Volatile
    var start: Int = 0
        private set

    fun set(list: List<MediaFile>, index: Int) {
        items = list
        start = index.coerceIn(0, (list.size - 1).coerceAtLeast(0))
    }
}

/** Swipe through photos and videos: pinch to zoom, double tap to fill, play videos, and share, delete or see details. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    files: FileRepository,
    operations: FileOperations,
    settings: SettingsRepository,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val changes by operations.changes.collectAsState()
    var items by remember { mutableStateOf(ViewerQueue.items) }
    val pager = rememberPagerState(initialPage = ViewerQueue.start) { items.size }
    var chrome by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }

    // A file deleted from here, or anywhere else, leaves the row; the next one takes its place.
    LaunchedEffect(changes) {
        val still = items.filter { it.item.file.exists() }
        if (still.size != items.size) items = still
        if (still.isEmpty()) onBack()
    }
    LaunchedEffect(pager.currentPage) { zoomed = false }

    val actions = remember { FileActions(context, scope, files, operations, settings, snackbar) }
    actions.Dialogs(currentFolder = null, onSelectionDone = {})
    val current = items.getOrNull(pager.currentPage)

    Scaffold(
        containerColor = Color.Black,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { OperationBar(operations, pasteTarget = null) },
        topBar = {
            if (chrome) {
                TopAppBar(
                    title = {
                        Text(current?.item?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.MiddleEllipsis, style = MaterialTheme.typography.titleSmall)
                    },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.6f), titleContentColor = Color.White,
                        navigationIconContentColor = Color.White, actionIconContentColor = Color.White,
                    ),
                    actions = {
                        if (current != null) {
                            IconButton(onClick = { actions.share(listOf(current.item)) }) { Icon(Icons.Filled.Share, stringResource(R.string.share)) }
                            IconButton(onClick = { actions.open(current.item.file, chooser = true) }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, stringResource(R.string.open_with)) }
                            IconButton(onClick = { actions.askDetails(listOf(current.item)) }) { Icon(Icons.Filled.Info, stringResource(R.string.details)) }
                            IconButton(onClick = { actions.askDelete(listOf(current.item)) }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
                        }
                    },
                )
            }
        },
    ) { _ ->
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize().background(Color.Black),
            userScrollEnabled = !zoomed,
            key = { items.getOrNull(it)?.path ?: it },
            beyondViewportPageCount = 1,
        ) { page ->
            val media = items.getOrNull(page) ?: return@HorizontalPager
            val isVideo = media.item.category == FileCategory.VIDEO
            if (isVideo) {
                VideoPage(media, isCurrent = page == pager.currentPage, onOpenWith = { actions.open(media.item.file, chooser = true) }, onToggleChrome = { chrome = !chrome })
            } else {
                PhotoPage(media, onZoomed = { zoomed = it }, onToggleChrome = { chrome = !chrome }, onOpenWith = { actions.open(media.item.file, chooser = true) })
            }
        }
    }
}

@Composable
private fun PhotoPage(media: MediaFile, onZoomed: (Boolean) -> Unit, onToggleChrome: () -> Unit, onOpenWith: () -> Unit) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val maxPx = with(density) { (maxOf(configuration.screenWidthDp, configuration.screenHeightDp).dp.toPx() * 2).toInt() }.coerceAtMost(4096)
    // Null while loading; a failure shows Open with.
    val loaded by produceState<Pair<ImageBitmap?, Boolean>>(null to false, media.path) {
        value = Thumbnails.full(media.item.file, maxPx) to true
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 6f)
        offset = if (scale <= 1f) Offset.Zero else offset + pan
        onZoomed(scale > 1f)
    }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(media.path) {
                detectTapGestures(
                    onTap = { onToggleChrome() },
                    onDoubleTap = {
                        val zoomIn = scale <= 1f
                        scale = if (zoomIn) 2.5f else 1f
                        offset = Offset.Zero
                        onZoomed(zoomIn)
                    },
                )
            }
            // At normal size a sideways swipe belongs to the pager, for the next photo.
            .transformable(transform, canPan = { scale > 1f }),
        contentAlignment = Alignment.Center,
    ) {
        val (bitmap, done) = loaded
        when {
            bitmap != null -> Image(
                bitmap, media.item.name, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            )
            !done -> CircularProgressIndicator(color = Color.White)
            else -> CantShow(R.string.viewer_cant_show_photo, onOpenWith)
        }
    }
}

@Composable
private fun VideoPage(media: MediaFile, isCurrent: Boolean, onOpenWith: () -> Unit, onToggleChrome: () -> Unit) {
    val context = LocalContext.current
    var playing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    // Swiping away stops the video.
    LaunchedEffect(isCurrent) { if (!isCurrent) playing = false }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            failed -> CantShow(R.string.viewer_cant_play_video, onOpenWith)
            playing -> {
                val view = remember {
                    VideoView(context).apply {
                        val controller = MediaController(context)
                        controller.setAnchorView(this)
                        setMediaController(controller)
                        setOnErrorListener { _, _, _ ->
                            failed = true
                            true
                        }
                        setVideoPath(media.path)
                        start()
                    }
                }
                DisposableEffect(view) { onDispose { view.stopPlayback() } }
                AndroidView({ view }, Modifier.fillMaxSize())
            }
            else -> {
                val sizePx = with(LocalDensity.current) { 360.dp.toPx().toInt() }
                val poster by produceState<ImageBitmap?>(null, media.path) {
                    value = Thumbnails.load(context, media.item.file, media.item.lastModified, FileCategory.VIDEO, sizePx, media.id)
                }
                poster?.let {
                    Image(
                        it, media.item.name, contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onToggleChrome() } },
                    )
                }
                val play = stringResource(R.string.viewer_play)
                IconButton(
                    onClick = { playing = true },
                    modifier = Modifier.size(72.dp).background(Color.Black.copy(alpha = 0.55f), CircleShape),
                ) { Icon(Icons.Filled.PlayArrow, play, tint = Color.White, modifier = Modifier.size(48.dp)) }
            }
        }
    }
}

@Composable
private fun CantShow(message: Int, onOpenWith: () -> Unit) {
    Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Filled.BrokenImage, null, tint = Color.White, modifier = Modifier.size(48.dp))
        Text(stringResource(message), color = Color.White, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onOpenWith) { Text(stringResource(R.string.open_with)) }
    }
}
