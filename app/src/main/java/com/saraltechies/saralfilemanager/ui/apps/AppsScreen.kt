package com.saraltechies.saralfilemanager.ui.apps

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.saraltechies.saralfilemanager.R
import com.saraltechies.saralfilemanager.apps.AppInfo
import com.saraltechies.saralfilemanager.apps.AppsRepository
import com.saraltechies.saralfilemanager.files.Sizes
import com.saraltechies.saralfilemanager.util.FileIntents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class AppSort { SIZE, NAME, LAST_USED, INSTALLED }

/**
 * Installed apps with their sizes, biggest first: open, uninstall (one or several), clear cache or data through
 * Android's app info, or save the installer. With usage access on, shows full sizes and apps you haven't used.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(repository: AppsRepository, startUnused: Boolean, snackbar: SnackbarHostState, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableStateOf(0) }
    var usageAccess by remember { mutableStateOf(repository.hasUsageAccess()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        usageAccess = repository.hasUsageAccess()
        refresh++
    }
    val apps by produceState<List<AppInfo>?>(null, refresh) { value = repository.installed() }
    var sort by rememberSaveable { mutableStateOf(AppSort.SIZE) }
    var showSystem by rememberSaveable { mutableStateOf(false) }
    var unusedOnly by rememberSaveable { mutableStateOf(startUnused) }
    var selected by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var sheetFor by remember { mutableStateOf<AppInfo?>(null) }
    val icons = remember { mutableStateMapOf<String, ImageBitmap?>() }

    // Several uninstalls run one after another: each Android prompt starts when the previous one closes.
    var queue by remember { mutableStateOf(emptyList<AppInfo>()) }
    val launchNext = remember { arrayOf<(List<AppInfo>) -> Unit>({}) }
    val uninstaller = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refresh++
        launchNext[0](queue.drop(1))
    }
    fun uninstallNext(list: List<AppInfo>) {
        queue = list
        list.firstOrNull()?.let { runCatching { uninstaller.launch(repository.uninstallIntent(it)) }.onFailure { queue = emptyList() } }
    }
    launchNext[0] = ::uninstallNext

    val all = apps.orEmpty().filter { showSystem || !it.isSystem }
    val listed = (if (unusedOnly && usageAccess) AppsRepository.unused(all) else all).let { list ->
        when (sort) {
            AppSort.SIZE -> list.sortedByDescending { it.totalBytes }
            AppSort.NAME -> list.sortedBy { it.label.lowercase() }
            AppSort.LAST_USED -> list.sortedBy { it.lastUsedAt ?: 0L }
            AppSort.INSTALLED -> list.sortedByDescending { it.installedAt }
        }
    }
    val selectedApps = listed.filter { it.packageName in selected }
    BackHandler(enabled = selected.isNotEmpty()) { selected = emptySet() }

    Scaffold(
        topBar = {
            if (selected.isNotEmpty()) {
                TopAppBar(
                    title = { Text(pluralStringResource(R.plurals.apps_selected, selected.size, selected.size, Sizes.format(selectedApps.sumOf { it.totalBytes }))) },
                    navigationIcon = { IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Filled.Close, stringResource(R.string.cancel)) } },
                    actions = {
                        TextButton(onClick = {
                            val list = selectedApps
                            selected = emptySet()
                            uninstallNext(list)
                        }) { Text(stringResource(R.string.apps_uninstall)) }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.apps_title)) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                    actions = {
                        var open by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options)) }
                            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                                listOf(
                                    AppSort.SIZE to R.string.apps_sort_size, AppSort.NAME to R.string.apps_sort_name,
                                    AppSort.LAST_USED to R.string.apps_sort_last_used, AppSort.INSTALLED to R.string.apps_sort_installed,
                                ).forEach { (s, label) ->
                                    if (s == AppSort.LAST_USED && !usageAccess) return@forEach
                                    DropdownMenuItem(
                                        text = { Text(stringResource(label)) },
                                        leadingIcon = { RadioButton(selected = sort == s, onClick = null) },
                                        onClick = { sort = s; open = false },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.apps_show_system)) },
                                    trailingIcon = { Checkbox(checked = showSystem, onCheckedChange = null) },
                                    onClick = { showSystem = !showSystem; open = false },
                                )
                            }
                        }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val list = apps
        if (list == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp)) {
            if (!usageAccess) item { UsageAccessCard(onAllow = repository::openUsageAccessSettings) }
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !unusedOnly, onClick = { unusedOnly = false }, label = { Text(stringResource(R.string.apps_filter_all, all.size)) })
                    if (usageAccess) {
                        FilterChip(
                            selected = unusedOnly, onClick = { unusedOnly = true },
                            label = { Text(stringResource(R.string.apps_filter_unused, AppsRepository.unused(all).size)) },
                        )
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.apps_total, Sizes.format(listed.sumOf { it.totalBytes })) +
                        if (!usageAccess) " · " + stringResource(R.string.apps_apk_sizes_only) else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            if (listed.isEmpty()) {
                item {
                    Text(
                        stringResource(if (unusedOnly) R.string.apps_none_unused else R.string.apps_none),
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(32.dp),
                    )
                }
            }
            items(listed, key = { it.packageName }) { app ->
                val icon by produceState(icons[app.packageName], app.packageName) {
                    if (app.packageName !in icons) {
                        icons[app.packageName] = withContext(Dispatchers.IO) {
                            repository.icon(app.packageName)?.let { runCatching { it.toBitmap(96, 96).asImageBitmap() }.getOrNull() }
                        }
                    }
                    value = icons[app.packageName]
                }
                AppRow(
                    app, icon, selected = app.packageName in selected,
                    onClick = {
                        if (selected.isNotEmpty()) selected = selected.toggle(app.packageName) else sheetFor = app
                    },
                    onLongClick = { selected = selected.toggle(app.packageName) },
                )
            }
        }
    }

    sheetFor?.let { app ->
        AppSheet(
            app = app,
            icon = icons[app.packageName],
            onDismiss = { sheetFor = null },
            onOpen = { sheetFor = null; repository.open(app) },
            onUninstall = { sheetFor = null; uninstallNext(listOf(app)) },
            onAppInfo = { sheetFor = null; repository.openAppInfo(app) },
            onBackUp = {
                sheetFor = null
                scope.launch {
                    val message = runCatching { repository.backUp(app) }.fold(
                        onSuccess = { context.getString(R.string.apps_backed_up, "Download/Saral Files/APKs/" + it.name) },
                        onFailure = { context.getString(R.string.apps_backup_failed) },
                    )
                    snackbar.showSnackbar(message)
                }
            },
            onShare = {
                sheetFor = null
                if (!FileIntents.share(context, listOf(File(app.apkPaths.first())))) {
                    scope.launch { snackbar.showSnackbar(context.getString(R.string.couldnt_share)) }
                }
            },
        )
    }
}

private fun Set<String>.toggle(key: String) = if (key in this) this - key else this + key

@Composable
private fun UsageAccessCard(onAllow: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.usage_access_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.usage_access_text), style = MaterialTheme.typography.bodySmall)
            Button(onClick = onAllow, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.usage_access_allow)) }
        }
    }
}

@Composable
private fun AppIcon(icon: ImageBitmap?, size: Int = 40) {
    if (icon != null) {
        Image(icon, contentDescription = null, modifier = Modifier.size(size.dp))
    } else {
        Icon(Icons.Filled.Android, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(size.dp))
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun AppRow(app: AppInfo, icon: ImageBitmap?, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    ListItem(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        leadingContent = {
            Box(contentAlignment = Alignment.Center) {
                AppIcon(icon)
                if (selected) Checkbox(checked = true, onCheckedChange = null)
            }
        },
        headlineContent = { Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(lastUsedLabel(app), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = { Text(Sizes.format(app.totalBytes), style = MaterialTheme.typography.labelLarge) },
        colors = ListItemDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent,
        ),
    )
}

@Composable
private fun lastUsedLabel(app: AppInfo): String {
    val used = app.lastUsedAt
    val now = System.currentTimeMillis()
    return when {
        used != null -> stringResource(R.string.apps_last_used, DateUtils.getRelativeTimeSpanString(used, now, DateUtils.DAY_IN_MILLIS).toString())
        app.versionName != null -> stringResource(R.string.apps_version, app.versionName)
        else -> app.packageName
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppSheet(
    app: AppInfo,
    icon: ImageBitmap?,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onUninstall: () -> Unit,
    onAppInfo: () -> Unit,
    onBackUp: () -> Unit,
    onShare: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(icon, size = 48)
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(app.versionName, app.packageName).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            if (app.appBytes != null) {
                Figure(stringResource(R.string.apps_size_app), Sizes.format(app.appBytes))
                Figure(stringResource(R.string.apps_size_data), Sizes.format(((app.dataBytes ?: 0) - (app.cacheBytes ?: 0)).coerceAtLeast(0)))
                Figure(stringResource(R.string.apps_size_cache), Sizes.format(app.cacheBytes ?: 0))
                Figure(stringResource(R.string.apps_size_total), Sizes.format(app.totalBytes))
            } else {
                Figure(stringResource(R.string.apps_size_installer), Sizes.format(app.apkBytes))
            }
        }
        SheetAction(Icons.AutoMirrored.Filled.OpenInNew, stringResource(R.string.apps_open), onOpen)
        SheetAction(Icons.Filled.Delete, stringResource(R.string.apps_uninstall), onUninstall, MaterialTheme.colorScheme.error)
        SheetAction(Icons.Filled.Info, stringResource(R.string.apps_info), onAppInfo, subtitle = stringResource(R.string.apps_info_hint))
        SheetAction(Icons.Filled.Backup, stringResource(R.string.apps_backup), onBackUp, subtitle = stringResource(R.string.apps_backup_hint))
        SheetAction(Icons.Filled.Share, stringResource(R.string.apps_share), onShare)
        Box(Modifier.padding(bottom = 24.dp))
    }
}

@Composable
private fun Figure(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SheetAction(icon: ImageVector, title: String, onClick: () -> Unit, tint: Color? = null, subtitle: String? = null) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(icon, null, tint = tint ?: MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(title, color = tint ?: Color.Unspecified) },
        supportingContent = subtitle?.let { { Text(it) } },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

