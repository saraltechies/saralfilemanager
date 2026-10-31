package com.saraltechies.saralfilemanager.ui

import android.net.Uri
import android.os.Environment
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.saraltechies.saralfilemanager.data.SettingsRepository
import com.saraltechies.saralfilemanager.files.FileCategory
import com.saraltechies.saralfilemanager.files.FileOperations
import com.saraltechies.saralfilemanager.files.FileRepository
import com.saraltechies.saralfilemanager.ui.browser.BrowserScreen
import com.saraltechies.saralfilemanager.ui.category.CategoryScreen
import com.saraltechies.saralfilemanager.ui.components.OperationMessages
import com.saraltechies.saralfilemanager.ui.home.HomeScreen
import com.saraltechies.saralfilemanager.ui.home.PermissionScreen
import com.saraltechies.saralfilemanager.util.AppShortcuts
import com.saraltechies.saralfilemanager.util.StorageAccess
import java.io.File

@Composable
fun AppNav(
    files: FileRepository,
    operations: FileOperations,
    settings: SettingsRepository,
    /** A launcher shortcut id from [AppShortcuts]. */
    launchShortcut: String? = null,
    onShortcutConsumed: () -> Unit = {},
    updateReady: Boolean = false,
    onRestartForUpdate: () -> Unit = {},
    onReviewMoment: () -> Unit = {},
) {
    val context = LocalContext.current
    var hasAccess by remember { mutableStateOf(StorageAccess.granted(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { hasAccess = StorageAccess.granted(context) }
    if (!hasAccess) {
        PermissionScreen(onCheckAgain = { hasAccess = StorageAccess.granted(context) })
        return
    }

    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val openFolder: (String) -> Unit = { nav.navigate(browseRoute(it)) }
    OperationMessages(operations, settings, snackbar, onOpenFolder = openFolder)

    LaunchedEffect(launchShortcut) {
        val shortcut = launchShortcut ?: return@LaunchedEffect
        onShortcutConsumed()
        when (shortcut) {
            AppShortcuts.ID_DOWNLOADS -> openFolder(File(files.primaryRoot, Environment.DIRECTORY_DOWNLOADS).path)
            AppShortcuts.ID_IMAGES -> nav.navigate(categoryRoute(FileCategory.IMAGE))
            AppShortcuts.ID_SEARCH -> nav.navigate(browseRoute(files.primaryRoot.path, search = true))
        }
    }

    NavHost(nav, startDestination = "home") {
        composable("home") {
            // A Play review, if one is due, is asked here: back on the home screen, not in the middle of a task.
            LaunchedEffect(Unit) { if (settings.takeReviewDue()) onReviewMoment() }
            HomeScreen(
                files = files,
                operations = operations,
                settings = settings,
                snackbar = snackbar,
                onOpenFolder = openFolder,
                onSearch = { nav.navigate(browseRoute(files.primaryRoot.path, search = true)) },
                onOpenCategory = { nav.navigate(categoryRoute(it)) },
                updateReady = updateReady,
                onRestartForUpdate = onRestartForUpdate,
            )
        }
        composable(
            "browse?path={path}&search={search}",
            arguments = listOf(
                navArgument("path") { type = NavType.StringType; defaultValue = "" },
                navArgument("search") { type = NavType.BoolType; defaultValue = false },
            ),
        ) { entry ->
            val path = entry.arguments?.getString("path").orEmpty().ifEmpty { files.primaryRoot.path }
            BrowserScreen(
                startPath = path,
                startSearch = entry.arguments?.getBoolean("search") == true,
                files = files,
                operations = operations,
                settings = settings,
                snackbar = snackbar,
                onBack = { nav.popBackStack() },
            )
        }
        composable("category/{name}") { entry ->
            val name = entry.arguments?.getString("name")
            CategoryScreen(
                category = FileCategory.entries.firstOrNull { it.name == name },
                files = files,
                operations = operations,
                settings = settings,
                snackbar = snackbar,
                onBack = { nav.popBackStack() },
                onOpenFolder = openFolder,
            )
        }
    }
}

private fun browseRoute(path: String, search: Boolean = false) = "browse?path=${Uri.encode(path)}&search=$search"

/** A null category is the recent files list. */
private fun categoryRoute(category: FileCategory?) = "category/${category?.name ?: RECENT}"

private const val RECENT = "recent"
