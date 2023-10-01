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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import smol.access.SL
import smol.access.model.ModVariant
import smol.app.composables.SmolAlertDialog
import smol.app.composables.SmolButton
import smol.app.composables.SmolSecondaryButton
import smol.app.themes.SmolTheme

@Composable
@OptIn(ExperimentalMaterialApi::class)
fun DeleteModVariantDialog(
    variantsToConfirmDeletionOf: List<ModVariant>,
    onDismiss: () -> Unit
) {
    val variantSelections = remember { mutableStateMapOf<ModVariant, Boolean>() }

    if (variantsToConfirmDeletionOf.isEmpty()) {
        onDismiss.invoke()
        return
    }

    SmolAlertDialog(
        title = {
            Text(
                text = "Delete ${variantsToConfirmDeletionOf.first().modInfo.name} ${variantsToConfirmDeletionOf.joinToString { it.modInfo.version.toString() }}?",
                style = SmolTheme.alertDialogTitle()
            )
        },
        text = {
            Column {
                Text(
                    text = "Are you sure you want to permanently delete:",
                    modifier = Modifier.padding(bottom = 8.dp),
                    style = SmolTheme.alertDialogBody()
                )

                VariantList(variantsToConfirmDeletionOf, variantSelections)
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
                    GlobalScope.launch {
                        variantsToConfirmDeletionOf
                            .filter { variantSelections[it] ?: true }
                            .forEach { variant ->
                                SL.access.deleteVariant(
                                    modVariant = variant,
                                    removeUncompressedFolder = true
                                )
                            }
                    }
                }) {
                Text(text = "Delete")
            }
        }
    )
}