package smol_app.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.SplitPaneScope
import java.awt.Cursor

@OptIn(ExperimentalSplitPaneApi::class)
//@Composable
fun SplitPaneScope.horizontalSplitter() {
    splitter {
        visiblePart {
            Box(
                Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colors.background)
            )
        }
        handle {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .markAsHandle()
                    .cursorForHorizontalResize()
            ) {
                Box(
                    Modifier
                        .background(SolidColor(Color.Gray), alpha = 0.50f)
                        .width(3.dp)
                        .height(32.dp)
                        .align(Alignment.Center)
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
//@Composable
private fun Modifier.cursorForHorizontalResize(): Modifier =
    pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
