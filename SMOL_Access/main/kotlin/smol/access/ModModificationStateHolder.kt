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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import smol.access.business.ModsCache
import smol.access.model.ModId
import smol.timber.ktx.Timber

/**
 * If any mods are having actions taken, this will hold the state of the action.
 * Mods with no actions will not be present in this map. */
internal class ModModificationStateHolder(
    private val backgroundTaskState: BackgroundTasksStateHolder,
    private val modsCache: ModsCache
) {
    val state = MutableStateFlow<Map<ModId, ModModificationState>>(emptyMap())
    private val context = CoroutineScope(Job())

    init {
        // Update the background tasks state when the mod modification state changes.
        context.launch {
            state.collectLatest { newState ->
                val mods = modsCache.mods.value?.mods.orEmpty()

                newState.forEach { (id, newModState) ->
                    backgroundTaskState.updateSingleTask(id) {
                        ModModificationStateTask(
                            id = id,
                            displayName = mods.firstOrNull { it.id == id }?.findFirstEnabledOrHighestVersion?.modInfo?.name
                                ?: id,
                            state = newModState
                        )
                    }
                    Timber.d { "Updated background task $id." }
                }

                backgroundTaskState.state.value.forEach { (id, _) ->
                    if (id !in newState) {
                        backgroundTaskState.removeTask(id)
                        Timber.d { "Removed background task $id."  }
                    }
                }
            }
        }
    }

    fun setModState(id: ModId, state: ModModificationState?) {
        this.state.update { oldState ->
            if (state == null) {
                oldState - id
            } else
                oldState.toMutableMap().apply { this[id] = state }
        }
    }

    fun remove(id: ModId) {
        state.update { oldState ->
            oldState - id
        }
    }

    inline fun doWithModState(id: ModId, state: ModModificationState, action: () -> Unit) {
        setModState(id, state)
        try {
            action()
        } finally {
            remove(id)
        }
    }
}

class ModModificationStateTask(
    override val id: ModId,
    override val displayName: String,
    override val description: String? = null,
    override val tooltip: String? = description,
    val state: ModModificationState
) : BackgroundTaskState(id, displayName, description, tooltip)

sealed class ModModificationState {
    data object DisablingVariants : ModModificationState()
    data object DeletingVariants : ModModificationState()
    data object EnablingVariant : ModModificationState()
    data object BackingUpVariant : ModModificationState()
}