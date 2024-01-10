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

package smol.app.toasts

import AppScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import smol.access.ModModificationState
import smol.access.ModModificationStateTask
import smol.access.SL
import smol.app.UI
import smol.app.browser.DownloadItem
import smol.timber.ktx.Timber
import smol.utilities.isAny

object ToastMasterDave {
    private val context = CoroutineScope(Job())
    private const val TAG = "ToastMasterDave"

    @Composable
    fun addBackgroundTasksToastWatcher() {
        LaunchedEffect(12345565) {
            SL.backgroundTasksStateHolder.state.collectLatest { newState ->
                val toastsStillInTasks = SL.UI.toaster.items.value.filter { toast ->
                    toast.id in newState.map { createBackgroundTaskToastId(it.key) }
                }
                val toastsToRemove = SL.UI.toaster.items.value
                    .filter { toast ->
                        toast.id.startsWith(TAG) &&
                                toast.id !in newState.map { createBackgroundTaskToastId(it.key) }
                    }
                    .map { it.id }
                SL.UI.toaster.burnAll(toastsToRemove, delay = 500)

                val tasksToAdd = newState.values.filter { task ->
                    createBackgroundTaskToastId(task.id) !in SL.UI.toaster.items.value.map { it.id }
                }

                val taskToasts = tasksToAdd
                    .map { task ->
                        ToastContainer(
                            id = createBackgroundTaskToastId(task.id),
                            timeoutMillis = null,
                            useStandardToastFrame = true
                        ) {
                            Row(Modifier.padding(8.dp)) {
                                Column {
                                    Text(
                                        text = task.displayName,
                                        style = MaterialTheme.typography.subtitle1,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.alpha(0.65f).padding(end = 8.dp),
                                    )
                                    if (task is ModModificationStateTask) {
                                        Text(
                                            text = when (task.state) {
                                                ModModificationState.DisablingVariants -> "Disabling"
                                                ModModificationState.BackingUpVariant -> "Backing up"
                                                ModModificationState.DeletingVariants -> "Deleting"
                                                ModModificationState.EnablingVariant -> "Enabling"
                                            }
                                        )
                                    } else if (!task.description.isNullOrBlank()) {
                                        Text(
                                            text = task.description!!,
                                            style = MaterialTheme.typography.body2
                                        )
                                    }
                                }
                                if (task.progress != null) {
                                    if (task.progress == -1) {
                                        CircularProgressIndicator(Modifier.padding(end = 16.dp).size(36.dp))
                                    } else {
                                        CircularProgressIndicator(
                                            progress = (task.progress?.toFloat() ?: 0f) / 100f,
                                            modifier = Modifier.padding(end = 16.dp).size(36.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                SL.UI.toaster.addItems(taskToasts)
            }
        }
    }

    private fun createBackgroundTaskToastId(taskId: String) = TAG + taskId

    @Composable
    fun addFoundNewModToastWatcher() {
//        context.launch {

        LaunchedEffect(12342345) {
            SL.access.modsFlow.collectLatest { modListUpdate ->
                val addedModVariants = modListUpdate?.added ?: return@collectLatest

                addedModVariants
                    .forEach { newModVariant ->
                        Timber.i { "Found new mod ${newModVariant.modInfo.id} ${newModVariant.modInfo.version}." }
                        val id = "new-mod-" + newModVariant.smolId
                        SL.UI.toaster.addItem(ToastContainer(
                            id = id,
                            timeoutMillis = null,
                            useStandardToastFrame = true
                        ) {
                            toastInstalledCard(
                                modVariant = newModVariant,
                                requestToastDismissalAfter = { delayMillis ->
                                    SL.UI.toaster.setTimeout(id, delayMillis)
                                }
                            )
                        })
                    }
            }
        }
//        }
    }

    @Composable
    fun addDownloadingModsToastWatcher() {
        val items = SL.UI.toaster.items

        remember { SL.UI.downloadManager.downloadsInner }
            .filter { it.id !in items.value.map { it.id } }
            .filter {
                !it.status.value.isAny(
                    DownloadItem.Status.Completed::class,
                    DownloadItem.Status.Cancelled::class
                )
            }
            .map {
                val toastId = "download-${it.id}"
                ToastContainer(id = toastId, timeoutMillis = null, useStandardToastFrame = true) {
                    DownloadToast(
                        download = it,
                        requestToastDismissal = { delayMillis ->
                            SL.UI.toaster.setTimeout(toastId, delayMillis)
                            SL.UI.downloadManager.downloadsInner.remove(it)
                        }
                    )
                }
            }
            .also {
                SL.UI.toaster.addItems(it)
            }
    }

    @Composable
    fun AppScope.addJre8NagToast() {
        val toastId = "jre8-nag"
        if (toastId !in SL.UI.toaster.items.value.map { it.id }) {
            SL.UI.toaster.addItem(ToastContainer(
                id = toastId,
                timeoutMillis = null,
                useStandardToastFrame = true
            ) {
                jre8NagToast(
                    requestToastDismissalAfter = { delayMillis ->
                        SL.UI.toaster.setTimeout(toastId, delayMillis)
                    }
                )
            })
        }
    }
}