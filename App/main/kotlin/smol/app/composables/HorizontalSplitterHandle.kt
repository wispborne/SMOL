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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.SplitPaneScope
import smol.app.themes.SmolTheme.lighten
import java.awt.Cursor

@OptIn(ExperimentalSplitPaneApi::class)
//@Composable
fun SplitPaneScope.horizontalSplitter(modifier: Modifier = Modifier) {
    splitter {
        visiblePart {
            Box(
                modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colors.background.lighten())
            )
        }
        handle {
            Box(
                Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .markAsHandle()
                    .cursorForHorizontalResize()
            ) {
//                Box(
//                    Modifier
//                        .background(SolidColor(Color.Gray), alpha = 0.50f)
//                        .width(5.dp)
//                        .height(32.dp)
//                        .align(Alignment.Center)
//                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.cursorForHorizontalResize(): Modifier =
    pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
