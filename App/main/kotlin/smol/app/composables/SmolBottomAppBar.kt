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

package smol.app.composables

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import smol.access.SL
import smol.utilities.IOLock

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SmolBottomAppBar(
    modifier: Modifier = Modifier,
    showLogPanel: MutableState<Boolean>,
    backgroundColor: Color = MaterialTheme.colors.primarySurface,
    contentColor: Color = contentColorFor(backgroundColor),
    cutoutShape: Shape? = null,
    elevation: Dp = AppBarDefaults.BottomAppBarElevation,
    contentPadding: PaddingValues = AppBarDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit = {}
) {
    val isWriteLocked = IOLock.stateFlow.collectAsState()
    BottomAppBar(
        modifier = modifier,
        backgroundColor = backgroundColor,
        contentColor = contentColor,
        cutoutShape = cutoutShape,
        elevation = elevation,
        contentPadding = contentPadding,
        content = {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colors.onSurface) {
                logButtonAndErrorDisplay(showLogPanel = showLogPanel)
                content.invoke(this)
                Spacer(Modifier.weight(1f))

                val modsBeingModified = SL.access.modModificationState.collectAsState().value
                    .filter { it.value != smol.access.Access.ModModificationState.Ready }

                if (modsBeingModified.isNotEmpty()) {
                    SmolTooltipArea(tooltip = {
                        SmolTooltipText(text =
                        modsBeingModified
                            .entries
                            .joinToString(separator = "\n") { (modId, state) ->
                                val modName =
                                    SL.access.mods.firstOrNull { it.id == modId }?.findFirstEnabledOrHighestVersion?.modInfo?.name
                                val stateStr = when (state) {
                                    smol.access.Access.ModModificationState.Ready -> "ready"
                                    smol.access.Access.ModModificationState.DisablingVariants -> "disabling"
                                    smol.access.Access.ModModificationState.DeletingVariants -> "deleting"
                                    smol.access.Access.ModModificationState.EnablingVariant -> "enabling"
                                    smol.access.Access.ModModificationState.BackingUpVariant -> "backing up"
                                }
                                "$modName: $stateStr"
                            })
                    }) {
                        CircularProgressIndicator(Modifier.padding(end = 16.dp).size(36.dp))
                    }
                }
            }
        },
    )
}