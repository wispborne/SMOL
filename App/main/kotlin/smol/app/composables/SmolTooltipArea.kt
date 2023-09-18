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

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import smol.app.themes.SmolTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SmolTooltipArea(
    tooltip: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    delayMillis: Int = 0,
    tooltipPlacement: TooltipPlacement = TooltipPlacement.CursorPoint(
        offset = DpOffset(x = (-8).dp, y = (-8).dp),
        alignment = Alignment.TopStart
    ),
    content: @Composable () -> Unit
) {
    TooltipArea(
        tooltip = tooltip,
        modifier = modifier,
        delayMillis = delayMillis,
        tooltipPlacement = tooltipPlacement,
        content = content
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
fun SmolTooltipBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var show by remember { mutableStateOf(true) }
    if (show) {
        Box(
            modifier = modifier
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colors.primary.copy(alpha = ContentAlpha.medium),
                    shape = SmolTheme.smolNormalButtonShape()
                )
                .shadow(elevation = 4.dp, shape = SmolTheme.smolNormalButtonShape())
                .background(color = MaterialTheme.colors.surface, shape = SmolTheme.smolNormalButtonShape())
                .padding(all = 16.dp)
                // Hide on click so if tooltip is blocking a button, you can get rid of it.
                .onClick { show = false }
        ) {
            content.invoke()
        }
    }
}

object SmolTooltipArea {
    const val shortDelay = 300
    const val longDelay = 700
}