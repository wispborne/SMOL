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

package smol.access

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Holds the state of the background tasks.
 * Maps the task ID to the task state.
 */
class BackgroundTasksStateHolder {
    val state = MutableStateFlow<Map<String, BackgroundTaskState>>(emptyMap())

    fun updateSingleTask(taskId: String, updater: (BackgroundTaskState?) -> BackgroundTaskState?) {
        state.update { oldState ->
            val oldTaskState = oldState[taskId]
            val newTaskState = updater(oldTaskState)

            if (newTaskState == null) {
                oldState - taskId
            } else
                oldState + (taskId to newTaskState)
        }
    }

    fun removeTask(taskId: String) {
        state.update { oldState ->
            oldState - taskId
        }
    }
}

open class BackgroundTaskState(
    open val id: String,
    open val displayName: String,
    open val description: String?,
    open val tooltip: String? = description,
    open val progressMin: Int = 0,
    open val progressMax: Int = 100,
    /**
     * If null, the progress bar is not shown.
     * If -1, the progress bar is shown as indeterminate.
     */
    open val progress: Int? = null
)