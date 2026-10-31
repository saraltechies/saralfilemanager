package com.saraltechies.saralfilemanager.ui.components

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.saraltechies.saralfilemanager.R
import com.saraltechies.saralfilemanager.data.SettingsRepository
import com.saraltechies.saralfilemanager.files.FileItem
import com.saraltechies.saralfilemanager.files.FileOperations
import com.saraltechies.saralfilemanager.files.FileRepository
import com.saraltechies.saralfilemanager.util.FileIntents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

/**
 * What can be done to files, with the dialogs it needs, shared by the folder and category screens.
 * Remember one per screen and call [Dialogs] once in its composition.
 */
@Stable
class FileActions(
    private val context: Context,
    private val scope: CoroutineScope,
    private val files: FileRepository,
    private val operations: FileOperations,
    private val settings: SettingsRepository,
    private val snackbar: SnackbarHostState,
) {
    private var deleting by mutableStateOf<List<FileItem>?>(null)
    private var renaming by mutableStateOf<FileItem?>(null)
    private var zipping by mutableStateOf<List<FileItem>?>(null)
    private var details by mutableStateOf<List<FileItem>?>(null)
    private var creatingFolder by mutableStateOf(false)
    private var creatingFile by mutableStateOf(false)

    fun askDelete(items: List<FileItem>) {
        if (items.isNotEmpty()) deleting = items
    }

    fun askNewFolder() {
        creatingFolder = true
    }

    fun askNewFile() {
        creatingFile = true
    }

    fun open(file: File, chooser: Boolean = false) {
        if (!FileIntents.open(context, file, chooser)) say(R.string.no_app_to_open)
    }

    fun share(items: List<FileItem>) {
        if (!FileIntents.share(context, items.map { it.file })) say(R.string.couldnt_share)
    }

    /**
     * The "more" menu while items are selected. [onShowInFolder] is for lists that mix folders (categories,
     * search) and is left out where you're already in the folder.
     */
    @Composable
    fun SelectionMenuItems(
        items: List<FileItem>,
        dismiss: () -> Unit,
        bookmarks: Set<String>,
        onShowInFolder: ((String) -> Unit)? = null,
    ) {
        val single = items.singleOrNull()
        if (single != null && !single.isDirectory) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.open_with)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                onClick = { dismiss(); open(single.file, chooser = true) },
            )
        }
        if (single != null && onShowInFolder != null) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.show_in_folder)) },
                leadingIcon = { Icon(Icons.Filled.Folder, null) },
                onClick = { dismiss(); onShowInFolder(single.file.parent ?: single.path) },
            )
        }
        if (single != null) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.rename)) },
                leadingIcon = { Icon(Icons.Filled.Edit, null) },
                onClick = { dismiss(); renaming = single },
            )
        }
        if (items.isNotEmpty()) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.compress)) },
                leadingIcon = { Icon(Icons.Filled.Archive, null) },
                onClick = { dismiss(); zipping = items },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.details)) },
                leadingIcon = { Icon(Icons.Filled.Info, null) },
                onClick = { dismiss(); details = items },
            )
        }
        if (single != null && single.isDirectory) {
            val bookmarked = single.path in bookmarks
            DropdownMenuItem(
                text = { Text(stringResource(if (bookmarked) R.string.remove_bookmark else R.string.bookmark_folder)) },
                leadingIcon = { Icon(Icons.Filled.Bookmark, null) },
                onClick = {
                    dismiss()
                    scope.launch { settings.setBookmarked(single.path, !bookmarked) }
                },
            )
        }
    }

    /** [currentFolder] is where new files, folders and zips go; null on screens that aren't a folder. */
    @Composable
    fun Dialogs(currentFolder: String?, onSelectionDone: () -> Unit) {
        deleting?.let { items ->
            ConfirmDeleteDialog(
                names = items.map { it.name },
                onConfirm = {
                    deleting = null
                    onSelectionDone()
                    operations.delete(items.map { it.path })
                },
                onDismiss = { deleting = null },
            )
        }
        renaming?.let { item ->
            NameDialog(
                title = stringResource(R.string.rename),
                initial = item.name,
                confirmLabel = stringResource(R.string.rename),
                keepExtension = !item.isDirectory,
                onConfirm = { name ->
                    renaming = null
                    onSelectionDone()
                    if (name != item.name) {
                        change(R.string.couldnt_rename) {
                            val renamed = files.rename(item.file, name)
                            operations.notifyChanged(item.file, renamed)
                        }
                    }
                },
                onDismiss = { renaming = null },
            )
        }
        zipping?.let { items ->
            val defaultName = (items.singleOrNull()?.name?.let { if (it.contains('.') && !items[0].isDirectory) it.substringBeforeLast('.') else it }
                ?: stringResource(R.string.default_archive_name)) + ".zip"
            NameDialog(
                title = stringResource(R.string.compress),
                initial = defaultName,
                confirmLabel = stringResource(R.string.compress),
                onConfirm = { name ->
                    zipping = null
                    onSelectionDone()
                    val zipName = if (name.endsWith(".zip", ignoreCase = true)) name else "$name.zip"
                    val folder = currentFolder ?: items[0].file.parent ?: return@NameDialog
                    operations.zip(items.map { it.path }, folder, zipName)
                },
                onDismiss = { zipping = null },
            )
        }
        details?.let { items ->
            DetailsDialog(items.map { it.file }, files, onDismiss = { details = null })
        }
        if (creatingFolder && currentFolder != null) {
            NameDialog(
                title = stringResource(R.string.new_folder),
                initial = stringResource(R.string.default_folder_name),
                confirmLabel = stringResource(R.string.create),
                keepExtension = false,
                onConfirm = { name ->
                    creatingFolder = false
                    change(R.string.couldnt_create) { operations.notifyChanged(files.createFolder(currentFolder, name)) }
                },
                onDismiss = { creatingFolder = false },
            )
        }
        if (creatingFile && currentFolder != null) {
            NameDialog(
                title = stringResource(R.string.new_file),
                initial = stringResource(R.string.default_file_name),
                confirmLabel = stringResource(R.string.create),
                onConfirm = { name ->
                    creatingFile = false
                    change(R.string.couldnt_create) { operations.notifyChanged(files.createFile(currentFolder, name)) }
                },
                onDismiss = { creatingFile = false },
            )
        }
    }

    private fun change(errorMessage: Int, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (_: FileAlreadyExistsException) {
                snackbar.showSnackbar(context.getString(R.string.name_taken))
            } catch (_: IOException) {
                snackbar.showSnackbar(context.getString(errorMessage))
            } catch (_: SecurityException) {
                snackbar.showSnackbar(context.getString(errorMessage))
            }
        }
    }

    private fun say(message: Int) {
        scope.launch { snackbar.showSnackbar(context.getString(message)) }
    }
}
