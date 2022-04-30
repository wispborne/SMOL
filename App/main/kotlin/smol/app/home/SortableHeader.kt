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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.mouseClickable
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import smol.access.SL
import smol.access.model.UserProfile
import smol.app.composables.SmolDropdownArrow

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RowScope.SortableHeader(
    modifier: Modifier = Modifier,
    columnSortField: ModGridSortField,
    activeSortField: ModGridSortField?,
    profile: State<UserProfile>,
    content: @Composable (() -> Unit)?
) {
    val isSortActive = activeSortField == columnSortField
    Row(modifier
        .mouseClickable {
            SL.userManager.updateUserProfile {
                it.copy(
                    modGridPrefs = it.modGridPrefs.copy(
                        sortField = columnSortField.name,
                        isSortDescending = if (isSortActive) {
                            profile.value.modGridPrefs.isSortDescending.not()
                        } else {
                            true
                        }
                    )
                )
            }
        }) {
        content?.invoke()
        Box(
            modifier = Modifier
                .align(Alignment.CenterVertically)
        ) {
            SmolDropdownArrow(
                modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp, end = 12.dp)
                    .alpha(if (isSortActive) 1f else 0.25f),
                expanded = isSortActive && profile.value.modGridPrefs.isSortDescending,
                colorFilter = ColorFilter.tint(MaterialTheme.colors.onBackground)
            )
        }
    }
}