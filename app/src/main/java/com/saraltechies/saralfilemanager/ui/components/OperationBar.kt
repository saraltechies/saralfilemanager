package com.saraltechies.saralfilemanager.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saraltechies.saralfilemanager.R
import com.saraltechies.saralfilemanager.data.SettingsRepository
import com.saraltechies.saralfilemanager.files.FileOperations
import com.saraltechies.saralfilemanager.files.OperationKind
import com.saraltechies.saralfilemanager.files.OperationResult
import java.io.File

/**
 * Bottom bar for file work: the running operation with its progress and Cancel, or, when [pasteTarget] is set
 * and something was copied or cut, "Paste here".
 */
@Composable
fun OperationBar(operations: FileOperations, pasteTarget: String?) {
    val progress by operations.progress.collectAsStateWithLifecycle()
    val clip by operations.clip.collectAsStateWithLifecycle()
    val running = progress
    val waiting = clip
    if (running == null && (waiting == null || pasteTarget == null)) return
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
            if (running != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            pluralStringResource(running.kind.progressLabel(), running.itemCount, running.itemCount),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        if (running.currentName.isNotEmpty()) {
                            Text(running.currentName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                        }
                    }
                    TextButton(onClick = operations::cancel) { Text(stringResource(R.string.cancel)) }
                }
                val fraction = running.fraction
                if (fraction != null) {
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().padding(end = 8.dp, top = 4.dp))
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(end = 8.dp, top = 4.dp))
                }
            } else if (waiting != null && pasteTarget != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        pluralStringResource(if (waiting.move) R.plurals.ready_to_move else R.plurals.ready_to_copy, waiting.paths.size, waiting.paths.size),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = operations::clearClip) { Text(stringResource(R.string.cancel)) }
                    Button(onClick = { operations.paste(pasteTarget) }) {
                        Text(stringResource(if (waiting.move) R.string.move_here else R.string.paste_here))
                    }
                }
            }
        }
    }
}

private fun OperationKind.progressLabel(): Int = when (this) {
    OperationKind.COPY -> R.plurals.copying
    OperationKind.MOVE -> R.plurals.moving
    OperationKind.DELETE -> R.plurals.deleting
    OperationKind.ZIP -> R.plurals.compressing
    OperationKind.EXTRACT -> R.plurals.extracting
}

/**
 * Turns finished operations into snackbars ("3 items copied", "Extracted to Photos" with Open). Collected once,
 * at the top of the app, so a message shows on whichever screen is open.
 */
@Composable
fun OperationMessages(
    operations: FileOperations,
    settings: SettingsRepository,
    snackbar: SnackbarHostState,
    onOpenFolder: (String) -> Unit,
) {
    val resources = LocalContext.current.resources
    LaunchedEffect(operations) {
        operations.results.collect { result ->
            when (result) {
                is OperationResult.Done -> {
                    if (result.kind != OperationKind.DELETE) settings.recordGoodMoment()
                    val output = result.output
                    // Zips and extracted folders are new things worth opening; copies and moves are where the person already is.
                    val openable = output != null && (result.kind == OperationKind.ZIP || result.kind == OperationKind.EXTRACT)
                    val message = when (result.kind) {
                        OperationKind.COPY -> resources.getQuantityString(R.plurals.copied, result.count, result.count)
                        OperationKind.MOVE -> resources.getQuantityString(R.plurals.moved, result.count, result.count)
                        OperationKind.DELETE -> resources.getQuantityString(R.plurals.deleted, result.count, result.count)
                        OperationKind.ZIP -> resources.getString(R.string.compressed, output?.name.orEmpty())
                        OperationKind.EXTRACT -> resources.getString(R.string.extracted, output?.name.orEmpty())
                    }
                    val shown = snackbar.showSnackbar(
                        message,
                        actionLabel = if (openable) resources.getString(R.string.open) else null,
                        duration = SnackbarDuration.Short,
                    )
                    if (shown == SnackbarResult.ActionPerformed && output != null) {
                        onOpenFolder(if (output.isDirectory) output.path else output.parent ?: File("/").path)
                    }
                }
                is OperationResult.Cancelled -> snackbar.showSnackbar(resources.getString(R.string.cancelled))
                is OperationResult.Failed -> snackbar.showSnackbar(
                    resources.getString(
                        when (result.reason) {
                            OperationResult.Reason.INTO_ITSELF -> R.string.error_into_itself
                            OperationResult.Reason.NO_SPACE -> R.string.error_no_space
                            OperationResult.Reason.NOT_ALLOWED -> R.string.error_not_allowed
                            OperationResult.Reason.OTHER -> R.string.error_operation_failed
                        },
                    ),
                    duration = SnackbarDuration.Long,
                )
            }
        }
    }
}
