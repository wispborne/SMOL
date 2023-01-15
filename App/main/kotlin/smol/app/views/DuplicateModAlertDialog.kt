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

package smol.app.views

import androidx.compose.foundation.layout.Column
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import smol.access.model.ModInfo
import smol.app.composables.SmolAlertDialog
import smol.app.composables.SmolButton
import smol.app.composables.SmolSecondaryButton
import smol.app.themes.SmolTheme

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun DuplicateModAlertDialog(
    modInfo: ModInfo,
    continuation: CancellableContinuation<DuplicateModAlertDialogState.DuplicateModAlertDialogResult>
) {
    SmolAlertDialog(
        onDismissRequest = { continuation.resumeWith(Result.success(DuplicateModAlertDialogState.DuplicateModAlertDialogResult.Dismissed)) },
        title = {
            Text(
                text = "Mod already installed",
                style = SmolTheme.alertDialogTitle()
            )
        },
        text = {
            Column {
                Text(
                    text = "'${modInfo.name}' version '${modInfo.version}' is already installed. Do you want to replace it?" +
                            "\n\nWarning: If you have manually edited files in this mod, those changes will be lost.",
                    style = SmolTheme.alertDialogBody()
                )
            }
        },
        confirmButton = {
            SmolButton(onClick = {
                continuation.resumeWith(Result.success(DuplicateModAlertDialogState.DuplicateModAlertDialogResult.ReplaceModFolder))
            }) { Text("Replace") }
        },
        dismissButton = {
            SmolSecondaryButton(onClick = { continuation.resumeWith(Result.success(DuplicateModAlertDialogState.DuplicateModAlertDialogResult.Dismissed)) }) {
                Text(
                    "Cancel"
                )
            }
        },
    )
}

class DuplicateModAlertDialogState {
    private val mutex = Mutex()
    var currentData by mutableStateOf<DuplicateModAlertDialogData?>(null)
        private set

    suspend fun showDialog(modInfo: ModInfo): DuplicateModAlertDialogResult = mutex.withLock {
        try {
            return suspendCancellableCoroutine { continuation ->
                currentData = DuplicateModAlertDialogData(continuation, modInfo)
            }
        } finally {
            currentData = null
        }
    }

    suspend fun showDialogBooleo(modInfo: ModInfo): Boolean =
        when (showDialog(modInfo)) {
            DuplicateModAlertDialogResult.Dismissed -> false
            DuplicateModAlertDialogResult.ReplaceModFolder -> true
        }

    data class DuplicateModAlertDialogData(
        val continuation: CancellableContinuation<DuplicateModAlertDialogResult>,
        val modInfo: ModInfo
    )

    enum class DuplicateModAlertDialogResult {
        Dismissed,
        ReplaceModFolder
    }
}