package smol_app.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.*
import androidx.compose.material.MaterialTheme.colors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ExperimentalGraphicsApi
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.impl.Stats.enabled
import smol_app.themes.SmolTheme
import smol_app.themes.SmolTheme.darken
import smol_app.util.hexToColor

@Composable
@OptIn(ExperimentalMaterialApi::class, ExperimentalGraphicsApi::class)
fun SmolSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    elevation: ButtonElevation? = ButtonDefaults.elevation(),
    shape: Shape? = null,
    border: BorderStroke? = BorderStroke(2.dp, SmolTheme.grey().darken(amount = -10)),
    colors: ButtonColors = ButtonDefaults.buttonColors(
        backgroundColor = SmolTheme.grey(),
//        contentColor = MaterialTheme.colors.onPrimary.copy(alpha = ContentAlpha.high)
    ),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    SmolButton(
        modifier = modifier,
        border = border,
        shape = shape ?: SmolTheme.smolNormalButtonShape(),
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        elevation = elevation,
        colors = colors,
        contentPadding = contentPadding,
        content = content,
    )
}