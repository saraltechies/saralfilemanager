package com.saraltechies.saralfilemanager.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.saraltechies.saralfilemanager.R
import com.saraltechies.saralfilemanager.data.ViewPrefs
import com.saraltechies.saralfilemanager.files.FileNames
import com.saraltechies.saralfilemanager.files.FileRepository
import com.saraltechies.saralfilemanager.files.Sizes
import com.saraltechies.saralfilemanager.files.SortBy
import com.saraltechies.saralfilemanager.util.FileIntents
import java.io.File

/** Asks for a file or folder name. The part before the extension starts selected, ready to type over. */
@Composable
fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    keepExtension: Boolean = true,
) {
    val selectionEnd = initial.lastIndexOf('.').takeIf { keepExtension && it > 0 } ?: initial.length
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, selectionEnd))) }
    val problem = FileNames.problem(value.text.trim())
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val confirm = { if (problem == null) onConfirm(value.text.trim()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                isError = problem != null && value.text.isNotEmpty(),
                supportingText = {
                    if (problem != null && value.text.isNotEmpty()) Text(stringResource(problem.message()))
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { confirm() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = confirm, enabled = problem == null) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun FileNames.Problem.message(): Int = when (this) {
    FileNames.Problem.EMPTY -> R.string.name_empty
    FileNames.Problem.RESERVED -> R.string.name_reserved
    FileNames.Problem.INVALID_CHARACTER -> R.string.name_invalid_character
    FileNames.Problem.TOO_LONG -> R.string.name_too_long
}

@Composable
fun ConfirmDeleteDialog(names: List<String>, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (names.size == 1) stringResource(R.string.delete_one_title, names[0])
                else pluralStringResource(R.plurals.delete_many_title, names.size, names.size),
            )
        },
        text = { Text(stringResource(R.string.delete_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Name, place, size (counted for folders), date and type of one item, or the total of several. */
@Composable
fun DetailsDialog(files: List<File>, repository: FileRepository, onDismiss: () -> Unit) {
    val measured by produceState<Pair<Long, Int>?>(null, files) { value = repository.measure(files) }
    val single = files.singleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(single?.name ?: pluralStringResource(R.plurals.item_count, files.size, files.size)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (single != null) {
                    Detail(stringResource(R.string.details_location), single.parent ?: "/")
                    Detail(stringResource(R.string.details_modified), dateLabel(single.lastModified()))
                    if (single.isFile) Detail(stringResource(R.string.details_type), FileIntents.mimeType(single))
                }
                val size = measured?.let { (bytes, count) ->
                    if (single?.isFile == true) Sizes.format(bytes)
                    else "${Sizes.format(bytes)} · ${pluralStringResource(R.plurals.file_count, count, count)}"
                } ?: stringResource(R.string.details_counting)
                Detail(stringResource(R.string.details_size), size)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun Detail(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun SortDialog(prefs: ViewPrefs, onChange: (SortBy, Boolean) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sort_by)) },
        text = {
            Column {
                SortBy.entries.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = prefs.sortBy == option, role = Role.RadioButton) { onChange(option, prefs.descending) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = prefs.sortBy == option, onClick = null)
                        Text(stringResource(option.label()), Modifier.padding(start = 12.dp))
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(
                    Modifier.fillMaxWidth().clickable { onChange(prefs.sortBy, !prefs.descending) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.sort_descending), Modifier.weight(1f))
                    Switch(checked = prefs.descending, onCheckedChange = null)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}

private fun SortBy.label(): Int = when (this) {
    SortBy.NAME -> R.string.sort_name
    SortBy.DATE -> R.string.sort_date
    SortBy.SIZE -> R.string.sort_size
    SortBy.TYPE -> R.string.sort_type
}

/** Shown when a zip file is tapped. */
@Composable
fun ZipDialog(name: String, onExtract: () -> Unit, onOpenWith: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name) },
        text = { Text(stringResource(R.string.zip_message)) },
        confirmButton = { TextButton(onClick = onExtract) { Text(stringResource(R.string.extract_here)) } },
        dismissButton = { TextButton(onClick = onOpenWith) { Text(stringResource(R.string.open_with)) } },
    )
}
