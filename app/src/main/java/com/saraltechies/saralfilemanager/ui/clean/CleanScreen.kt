package com.saraltechies.saralfilemanager.ui.clean

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saraltechies.saralfilemanager.R
import com.saraltechies.saralfilemanager.apps.AppInfo
import com.saraltechies.saralfilemanager.apps.AppsRepository
import com.saraltechies.saralfilemanager.clean.CleanKind
import com.saraltechies.saralfilemanager.clean.CleanSuggestion
import com.saraltechies.saralfilemanager.clean.Cleaner
import com.saraltechies.saralfilemanager.files.FileOperations
import com.saraltechies.saralfilemanager.files.FileRepository
import com.saraltechies.saralfilemanager.files.Sizes
import com.saraltechies.saralfilemanager.files.Volume
import com.saraltechies.saralfilemanager.ui.components.ConfirmDeleteDialog
import com.saraltechies.saralfilemanager.ui.components.FileRow
import com.saraltechies.saralfilemanager.ui.components.OperationBar
import com.saraltechies.saralfilemanager.util.FileIntents

/** Ways to free up space, each with how much it would free: copies, large files, old screenshots, downloads, unused apps. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanScreen(
    files: FileRepository,
    operations: FileOperations,
    apps: AppsRepository,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onOpen: (CleanKind) -> Unit,
    onOpenApps: (unused: Boolean) -> Unit,
) {
    val changes by operations.changes.collectAsStateWithLifecycle()
    val cleaner = remember(files) { Cleaner(files) }
    // Read straight away (it is quick), so the card is there before the list is laid out.
    val volume by produceState(remember { files.volumes().firstOrNull { it.isPrimary } }, changes) { value = files.volumes().firstOrNull { it.isPrimary } }
    var usageAccess by remember { mutableStateOf(apps.hasUsageAccess()) }
    var resumed by remember { mutableStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        usageAccess = apps.hasUsageAccess()
        resumed++
    }
    val unused by produceState<List<AppInfo>?>(null, usageAccess, resumed) {
        value = if (usageAccess) AppsRepository.unused(apps.installed()) else null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.clean_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
        bottomBar = { OperationBar(operations, pasteTarget = null) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            volume?.let { v -> item { FreeSpaceCard(v) } }
            CleanKind.entries.forEach { kind ->
                item(key = kind.name) {
                    val suggestion by produceState<CleanSuggestion?>(null, kind, changes) { value = cleaner.suggestion(kind) }
                    SuggestionCard(
                        icon = kind.icon(),
                        title = stringResource(kind.title()),
                        text = stringResource(kind.text()),
                        loading = suggestion == null,
                        count = suggestion?.files?.size ?: 0,
                        bytes = suggestion?.freeableBytes ?: 0,
                        onClick = { onOpen(kind) },
                    )
                }
            }
            item(key = "apps") {
                val list = unused
                SuggestionCard(
                    icon = Icons.Filled.Apps,
                    title = stringResource(R.string.clean_unused_apps),
                    text = stringResource(if (usageAccess) R.string.clean_unused_apps_text else R.string.clean_unused_apps_allow),
                    loading = usageAccess && list == null,
                    count = list?.size ?: 0,
                    bytes = list?.sumOf { it.totalBytes } ?: 0,
                    onClick = { if (usageAccess) onOpenApps(true) else apps.openUsageAccessSettings() },
                    action = if (!usageAccess) stringResource(R.string.usage_access_allow) else null,
                )
            }
        }
    }
}

fun CleanKind.title(): Int = when (this) {
    CleanKind.DUPLICATES -> R.string.clean_duplicates
    CleanKind.LARGE -> R.string.clean_large
    CleanKind.SCREENSHOTS -> R.string.clean_screenshots
    CleanKind.DOWNLOADS -> R.string.clean_downloads
}

private fun CleanKind.text(): Int = when (this) {
    CleanKind.DUPLICATES -> R.string.clean_duplicates_text
    CleanKind.LARGE -> R.string.clean_large_text
    CleanKind.SCREENSHOTS -> R.string.clean_screenshots_text
    CleanKind.DOWNLOADS -> R.string.clean_downloads_text
}

private fun CleanKind.icon(): ImageVector = when (this) {
    CleanKind.DUPLICATES -> Icons.Filled.ContentCopy
    CleanKind.LARGE -> Icons.Filled.SdStorage
    CleanKind.SCREENSHOTS -> Icons.Filled.Screenshot
    CleanKind.DOWNLOADS -> Icons.Filled.Download
}

@Composable
private fun FreeSpaceCard(volume: Volume) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.clean_free, Sizes.format(volume.freeBytes)), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            LinearProgressIndicator(
                progress = { if (volume.totalBytes > 0) volume.usedBytes.toFloat() / volume.totalBytes else 0f },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.storage_usage, Sizes.format(volume.usedBytes), Sizes.format(volume.totalBytes)),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SuggestionCard(
    icon: ImageVector,
    title: String,
    text: String,
    loading: Boolean,
    count: Int,
    bytes: Long,
    onClick: () -> Unit,
    action: String? = null,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), shape = MaterialTheme.shapes.medium, modifier = Modifier.size(44.dp)) {}
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                when {
                    action != null -> TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp)) { Text(action) }
                    loading -> Text(stringResource(R.string.clean_looking), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    count == 0 -> Text(stringResource(R.string.clean_nothing), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> Text(
                        pluralStringResource(R.plurals.clean_found, count, count, Sizes.format(bytes)),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
    }
}

/**
 * The files of one suggestion, to tick and delete. Tap ticks a file; touch and hold opens it. Duplicates come in
 * groups with the extra copies ticked and the oldest left alone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanListScreen(
    kind: CleanKind,
    files: FileRepository,
    operations: FileOperations,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val changes by operations.changes.collectAsStateWithLifecycle()
    val cleaner = remember(files) { Cleaner(files) }
    val suggestion by produceState<CleanSuggestion?>(null, kind, changes) { value = cleaner.suggestion(kind) }
    var selected by rememberSaveable { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(suggestion) {
        val s = suggestion ?: return@LaunchedEffect
        val present = s.files.map { it.path }.toSet()
        selected = selected?.intersect(present) ?: s.preselected
    }
    val ticked = selected.orEmpty()
    val tickedBytes = suggestion?.files?.filter { it.path in ticked }?.sumOf { it.size } ?: 0
    var confirm by remember { mutableStateOf(false) }

    if (confirm) {
        val names = suggestion?.files?.filter { it.path in ticked }?.map { it.name }.orEmpty()
        ConfirmDeleteDialog(
            names = names,
            onConfirm = {
                confirm = false
                operations.delete(ticked.toList())
                selected = emptySet()
            },
            onDismiss = { confirm = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(kind.title())) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    val all = suggestion?.files?.map { it.path }?.toSet().orEmpty()
                    if (all.isNotEmpty()) {
                        TextButton(onClick = { selected = if (ticked.size == all.size) emptySet() else all }) {
                            Text(stringResource(if (ticked.size == all.size) R.string.clean_select_none else R.string.select_all))
                        }
                    }
                },
            )
        },
        bottomBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.background)) {
                OperationBar(operations, pasteTarget = null)
                if (ticked.isNotEmpty()) {
                    Button(
                        onClick = { confirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    ) { Text(pluralStringResource(R.plurals.clean_delete, ticked.size, ticked.size, Sizes.format(tickedBytes))) }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val s = suggestion
        when {
            s == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.clean_looking), modifier = Modifier.padding(top = 12.dp))
                }
            }
            s.files.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.clean_nothing_here), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp)) {
                item {
                    Text(
                        stringResource(R.string.clean_list_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
                s.groups.forEachIndexed { index, group ->
                    if (kind == CleanKind.DUPLICATES) {
                        item(key = "group:$index") {
                            Text(
                                pluralStringResource(R.plurals.clean_copies, group.size, group.size, Sizes.format(group.first().size)),
                                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
                            )
                        }
                    }
                    items(group, key = { it.path }) { item ->
                        FileRow(
                            item, selected = item.path in ticked, showFolder = true,
                            onClick = { selected = if (item.path in ticked) ticked - item.path else ticked + item.path },
                            onLongClick = { FileIntents.open(context, item.file) },
                        )
                    }
                }
            }
        }
    }
}

