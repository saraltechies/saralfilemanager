package com.saraltechies.saralfilemanager.ui.home

import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saraltechies.saralfilemanager.BuildConfig
import com.saraltechies.saralfilemanager.R
import com.saraltechies.saralfilemanager.data.SettingsRepository
import com.saraltechies.saralfilemanager.files.FileCategory
import com.saraltechies.saralfilemanager.files.FileItem
import com.saraltechies.saralfilemanager.files.FileOperations
import com.saraltechies.saralfilemanager.files.FileRepository
import com.saraltechies.saralfilemanager.files.Sizes
import com.saraltechies.saralfilemanager.files.Volume
import com.saraltechies.saralfilemanager.ui.category.title
import com.saraltechies.saralfilemanager.ui.components.FileRow
import com.saraltechies.saralfilemanager.ui.components.OperationBar
import com.saraltechies.saralfilemanager.ui.components.color
import com.saraltechies.saralfilemanager.ui.components.icon
import com.saraltechies.saralfilemanager.util.FileIntents
import com.saraltechies.saralfilemanager.util.PlayStore
import kotlinx.coroutines.launch
import java.io.File

/** Storage volumes, file categories, bookmarked folders and recent files. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    files: FileRepository,
    operations: FileOperations,
    settings: SettingsRepository,
    snackbar: SnackbarHostState,
    onOpenFolder: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenCategory: (FileCategory?) -> Unit,
    updateReady: Boolean = false,
    onRestartForUpdate: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val changes by operations.changes.collectAsStateWithLifecycle()
    val bookmarks by settings.bookmarks.collectAsStateWithLifecycle(initialValue = emptySet())
    val volumes by produceState(emptyList<Volume>(), changes) { value = files.volumes() }
    val recent by produceState(emptyList<FileItem>(), changes) { value = files.recent() }
    val primary = files.primaryRoot

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, stringResource(R.string.search)) }
                    AppMenu()
                },
            )
        },
        bottomBar = { OperationBar(operations, pasteTarget = null) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp)) {
            if (updateReady) item { UpdateReady(onRestartForUpdate) }

            item { SectionTitle(stringResource(R.string.section_storage)) }
            items(volumes, key = { it.path }) { volume -> VolumeCard(volume, onClick = { onOpenFolder(volume.path) }) }

            item { SectionTitle(stringResource(R.string.section_categories)) }
            item { CategoryGrid(onOpenCategory) }

            item { SectionTitle(stringResource(R.string.section_quick_access)) }
            item {
                QuickFolders(
                    listOf(
                        Triple(R.string.folder_downloads, Icons.Filled.Download, File(primary, Environment.DIRECTORY_DOWNLOADS)),
                        Triple(R.string.folder_camera, Icons.Filled.CameraAlt, File(primary, Environment.DIRECTORY_DCIM)),
                        Triple(R.string.folder_documents, Icons.AutoMirrored.Filled.MenuBook, File(primary, Environment.DIRECTORY_DOCUMENTS)),
                    ),
                    onOpenFolder,
                )
            }

            if (bookmarks.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.section_bookmarks)) }
                items(bookmarks.sortedBy { File(it).name.lowercase() }, key = { "bookmark:$it" }) { path ->
                    ListItem(
                        modifier = Modifier.clickable { onOpenFolder(path) },
                        leadingContent = { Icon(Icons.Filled.Bookmark, null, tint = MaterialTheme.colorScheme.primary) },
                        headlineContent = { Text(File(path).name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(path.removePrefix(primary.path).ifEmpty { "/" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingContent = {
                            IconButton(onClick = { scope.launch { settings.setBookmarked(path, false) } }) {
                                Icon(Icons.Filled.Close, stringResource(R.string.remove_bookmark))
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }

            if (recent.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { SectionTitle(stringResource(R.string.section_recent)) }
                        TextButton(onClick = { onOpenCategory(null) }, modifier = Modifier.padding(end = 8.dp, top = 12.dp)) {
                            Text(stringResource(R.string.see_all))
                        }
                    }
                }
                items(recent.take(RECENT_ON_HOME), key = { "recent:${it.path}" }) { item ->
                    FileRow(item, selected = false, showFolder = true, onClick = {
                        if (!FileIntents.open(context, item.file)) {
                            scope.launch { snackbar.showSnackbar(context.getString(R.string.no_app_to_open)) }
                        }
                    }, onLongClick = { onOpenFolder(item.file.parent ?: primary.path) })
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun VolumeCard(volume: Volume, onClick: () -> Unit) {
    val icon = when {
        volume.isPrimary -> Icons.Filled.PhoneAndroid
        volume.path.contains("usb", ignoreCase = true) -> Icons.Filled.Usb
        else -> Icons.Filled.SdCard
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                Surface(color = MaterialTheme.colorScheme.primary, shape = CircleShape, modifier = Modifier.size(44.dp)) {}
                Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimary)
            }
            Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(volume.name, style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(
                    progress = { if (volume.totalBytes > 0) volume.usedBytes.toFloat() / volume.totalBytes else 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.storage_usage, Sizes.format(volume.usedBytes), Sizes.format(volume.totalBytes)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val HOME_CATEGORIES = listOf(
    FileCategory.IMAGE, FileCategory.VIDEO, FileCategory.AUDIO,
    FileCategory.DOCUMENT, FileCategory.ARCHIVE, FileCategory.APK,
)

@Composable
private fun CategoryGrid(onOpen: (FileCategory) -> Unit) {
    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HOME_CATEGORIES.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { category ->
                    Tile(
                        label = stringResource(category.title()),
                        icon = category.icon(),
                        tint = category.color(),
                        onClick = { onOpen(category) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickFolders(folders: List<Triple<Int, ImageVector, File>>, onOpen: (String) -> Unit) {
    Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        folders.forEach { (label, icon, folder) ->
            Tile(
                label = stringResource(label),
                icon = icon,
                tint = MaterialTheme.colorScheme.primary,
                onClick = { onOpen(folder.path) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Tile(label: String, icon: ImageVector, tint: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.10f)),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(30.dp))
            Spacer(Modifier.size(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun UpdateReady(onRestart: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.update_ready_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.update_ready), style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onRestart) { Text(stringResource(R.string.update_restart)) }
        }
    }
}

/** Saral Techies' other apps (on Google Play) and website. */
@Composable
private fun MoreAppsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.more_apps_title)) },
        text = {
            Column {
                MoreAppRow(Icons.AutoMirrored.Filled.MenuBook, stringResource(R.string.more_saralreader), stringResource(R.string.more_saralreader_summary)) {
                    PlayStore.openListing(context, PlayStore.SARALREADER)
                }
                MoreAppRow(Icons.Filled.QrCodeScanner, stringResource(R.string.more_saralqrscan), stringResource(R.string.more_saralqrscan_summary)) {
                    PlayStore.openListing(context, PlayStore.SARALQRSCAN)
                }
                MoreAppRow(Icons.Filled.Security, stringResource(R.string.more_authenticator), stringResource(R.string.more_authenticator_summary)) {
                    PlayStore.openListing(context, PlayStore.SARAL_AUTHENTICATOR)
                }
                MoreAppRow(Icons.Filled.Language, stringResource(R.string.more_website), stringResource(R.string.more_website_summary)) {
                    PlayStore.openUrl(context, PlayStore.WEBSITE)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun MoreAppRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/** Rate, other apps, source code, the privacy policy (Play requires it to be reachable in the app) and the version. */
@Composable
private fun AppMenu() {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var showMoreApps by remember { mutableStateOf(false) }
    if (showMoreApps) MoreAppsDialog(onDismiss = { showMoreApps = false })
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.rate_app)) },
                onClick = {
                    open = false
                    PlayStore.openListing(context, PlayStore.ownPackage(context))
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.more_apps_title)) },
                onClick = {
                    open = false
                    showMoreApps = true
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.source_code)) },
                leadingIcon = { Icon(Icons.Filled.Code, null) },
                onClick = {
                    open = false
                    PlayStore.openUrl(context, PlayStore.SOURCE_CODE)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.privacy_policy)) },
                onClick = {
                    open = false
                    // No internet permission: the policy opens in the person's own browser.
                    PlayStore.openUrl(context, PlayStore.PRIVACY_POLICY)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME)) },
                enabled = false,
                onClick = {},
            )
        }
    }
}

private const val RECENT_ON_HOME = 8
