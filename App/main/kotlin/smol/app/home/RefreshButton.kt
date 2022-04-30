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
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import smol.access.SL
import smol.app.composables.SmolTooltipArea
import smol.app.composables.SmolTooltipText

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppScope.refreshButton(onRefresh: () -> Unit) {
    SmolTooltipArea(
        tooltip = { SmolTooltipText(text = "Refresh mod list.") },
        delayMillis = SmolTooltipArea.shortDelay
    ) {
        val areModsLoading = SL.access.areModsLoading.collectAsState().value
        IconButton(
            onClick = { if (!areModsLoading) onRefresh.invoke() },
            modifier = Modifier.padding(start = 16.dp)
        ) {

            Icon(
                painter = painterResource("icon-refresh.svg"),
                modifier = Modifier
                    .run {
                        if (areModsLoading) {
                            // Moving this animation code outside of the conditional causes SMOL to eat an entire CPU core
                            // and allocate 4.62 GB over 60 seconds. Don't do that!
                            val infiniteTransition = rememberInfiniteTransition()
                            val angle by infiniteTransition.animateFloat(
                                initialValue = 0F,
                                targetValue = 360F,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1000, easing = FastOutLinearInEasing)
                                )
                            )

                            this.graphicsLayer { rotationZ = angle }
                        } else this
                    },
                contentDescription = "Refresh"
            )
        }
    }
}