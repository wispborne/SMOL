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

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.IconToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import smol.app.themes.SmolTheme
import smol.app.util.smolPreview

@Composable
@Preview
private fun logButtonAndErrorDisplayPreview() = smolPreview {
    logButtonAndErrorDisplay(showLogPanel = mutableStateOf(true))
}

@Composable
fun logButtonAndErrorDisplay(modifier: Modifier = Modifier, showLogPanel: MutableState<Boolean>) {
    Row(modifier) {
        IconToggleButton(
            checked = showLogPanel.value,
            modifier = Modifier.padding(start = 8.dp).run {
                if (showLogPanel.value) this.background(
                    color = Color.White.copy(alpha = 0.20f),
                    shape = CircleShape
                )
                else this
            },
            onCheckedChange = { showLogPanel.value = it }
        ) {
            Icon(
                painter = painterResource("icon-log.svg"),
                tint = if (showLogPanel.value) Color.White else SmolTheme.dimmedIconColor(),
                contentDescription = null
            )
        }

        // Shows the most recent error - removed because it kept confusing people
//        var newestLogLine by remember { mutableStateOf("") }
//        LaunchedEffect(Unit) {
//            Logging.logFlow.collectLatest {
//                if (it.logLevel >= LogLevel.ERROR) {
//                    newestLogLine = it.message.replaceTabsWithSpaces()
//                }
//            }
//        }
//        SmolText(
//            text = newestLogLine,
//            maxLines = 1,
//            modifier = Modifier.padding(start = 8.dp).align(Alignment.CenterVertically)
//        )
    }
}