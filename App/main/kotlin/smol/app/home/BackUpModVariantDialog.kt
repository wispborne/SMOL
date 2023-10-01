/*
 * This file is distributed under the GPLv3. An informal description follows:
 * - Anyone can copy, modify and distribute this software as long as the other points are followed.
 * - You must include the license and copyright notice with each and every distribution.
 * - You may this software for commercial purposes.
 * - If you modify it, you must indicate changes made to the code.
 * - Any modifications of this code base MUST be distributed with the same license, GPLv3.
 * - This software is provided without warranty.
 * - The software author or license can not be held liable for any damages inflicted by the software.
 * The full license is available from <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package smol.app.home

import AppScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import smol.access.SL
import smol.access.model.ModVariant
import smol.app.composables.CheckboxWithText
import smol.app.composables.SmolAlertDialog
import smol.app.composables.SmolButton
import smol.app.composables.SmolSecondaryButton
import smol.app.themes.SmolTheme
import smol.timber.ktx.Timber
import smol.utilities.bytesAsShortReadableMB
import smol.utilities.calculateFileSize
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.name

@Composable
fun AppScope.BackUpModVariantDialog(
    variants: List<ModVariant>,
    onDismiss: () -> Unit
) {
    val variantSelections = remember {
        mutableStateMapOf<ModVariant, Boolean>(
            *variants.map { it to (it.backupFile?.exists() != true) }.toTypedArray()
        )
    }

    if (variants.isEmpty()) {
        onDismiss.invoke()
        return
    }

    SmolAlertDialog(
        title = {
            Text(
                text = "Back Up ${variants.lastOrNull()?.modInfo?.name ?: "(null)"}",
                style = SmolTheme.alertDialogTitle()
            )
        },
        text = {
            SelectionContainer {
                Column {
                    Text(
                        text = "Which folders do you want to back up?",
                        modifier = Modifier.padding(bottom = 8.dp),
                        style = SmolTheme.alertDialogBody()
                    )

                    VariantList(variants, variantSelections, showIfModArchiveAlreadyExists = true)
                    Text(
                        text = "Backups will be saved to\n${SL.appConfig.modBackupPath}.",
                        modifier = Modifier.padding(top = 8.dp),
                        style = SmolTheme.alertDialogBody()
                    )
                }
            }
        },
        onDismissRequest = { onDismiss.invoke() },
        dismissButton = {
            SmolSecondaryButton(onClick = { onDismiss.invoke() }) {
                Text("Cancel")
            }
        },
        confirmButton = {
            SmolButton(
                modifier = Modifier.padding(end = 4.dp),
                onClick = {
                    onDismiss.invoke()
                    // If you use local scope, when the dialog closes the backup will stop.
                    GlobalScope.launch {
                        variants
                            .filter { variantSelections[it] ?: true }
                            .map { variant ->
                                SL.access.backupMod(
                                    modVariant = variant,
                                    overwriteExisting = true
                                )
                            }
                            .also { results ->
                                if (results.any { it?.errors?.any() == true }) {
                                    Timber.w { "Errors during backup found, showing dialog." }
                                    alertDialogSetter.invoke {
                                        SmolAlertDialog(
                                            title = {
                                                Text(
                                                    text = "Backup errors",
                                                    style = SmolTheme.alertDialogTitle()
                                                )
                                            },
                                            text = {
                                                SelectionContainer {
                                                    Column {
                                                        Text(
                                                            text = "The following backups had errors:",
                                                            modifier = Modifier.padding(bottom = 8.dp),
                                                            style = SmolTheme.alertDialogBody()
                                                        )
                                                        results
                                                            .filterNotNull()
                                                            .filter { it.errors.any() }
                                                            .forEach { result ->
                                                                Text(
                                                                    text = "${result.modVariant.modName} ${result.modVariant.version}",
                                                                    style = SmolTheme.alertDialogBody()
                                                                )
                                                                result.errors
                                                                    .mapNotNull { it.message.takeUnless { it.isNullOrBlank() } }
                                                                    .forEach { errorMessage ->
                                                                        Text(
                                                                            text = errorMessage,
                                                                            style = SmolTheme.alertDialogBody(),
                                                                            color = MaterialTheme.colors.error
                                                                        )
                                                                    }
                                                            }
                                                    }
                                                }
                                            },
                                            onDismissRequest = { onDismiss.invoke() },
                                        )
                                    }
                                }
                            }
                    }
                }) {
                Text(text = "Back up")
            }
        }
    )
}

@Suppress("FunctionName")
@Composable
fun VariantList(
    variants: List<ModVariant>,
    variantSelections: SnapshotStateMap<ModVariant, Boolean>,
    showIfModArchiveAlreadyExists: Boolean = false
) {
    variants.forEach { variant ->
        val looseFilesToShow = variant.modsFolderInfo.folder

        if (looseFilesToShow.exists()) {
            val isChecked = variantSelections[variant] ?: true

            Row {
                var folderSize by remember { mutableStateOf<String?>("calculating") }

                LaunchedEffect(looseFilesToShow.absolutePathString()) {
                    withContext(Dispatchers.Default) {
                        folderSize = runCatching {
                            looseFilesToShow.calculateFileSize()
                        }
                            .onFailure { Timber.w(it) }
                            .getOrNull()?.bytesAsShortReadableMB
                    }
                }

                val doesModBackupExist = variant.backupFile?.exists() == true

                CheckboxWithText(
                    modifier = Modifier.align(Alignment.CenterVertically),
                    checked = isChecked,
                    onCheckedChange = { variantSelections[variant] = isChecked.not() }
                ) {
//                    val folderSize = folderSize.let { "($it)" }
                    val backupExistsText =
                        if (showIfModArchiveAlreadyExists && doesModBackupExist) "Backup already exists"
                        else null
                    Column(modifier = it.align(Alignment.CenterVertically)) {
                        Text(
                            text = "${looseFilesToShow.name} ($folderSize)",
                            style = SmolTheme.alertDialogBody()
                        )
                        if (backupExistsText != null)
                            Text(
                                text = backupExistsText,
                                style = SmolTheme.alertDialogBody().copy(fontStyle = FontStyle.Italic)
                            )
                    }
                }

            }
        }
    }
}