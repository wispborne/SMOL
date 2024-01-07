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

package smol.app.themes

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import smol.access.SL
import smol.access.themes.Theme
import smol.app.util.hexToColor

object SmolTheme {
    val orbitronSpaceFont = FontFamily(Font("Font-Orbitron/Orbitron-VariableFont_wght.ttf"))
    val fireCodeFont = FontFamily(Font("Font-Fire_Mono/FiraMono-Regular.ttf"))
    val normalFont = FontFamily.Default

    val warningOrange = Color(java.awt.Color.decode("#F95D13").rgb)

    val cornerClipping = 8.dp
    val bottomBarHeight = 64.dp
    val topBarHeight = 72.dp
    const val modUpdateIconSize = 28
    fun smolNormalButtonShape() = RoundedCornerShape(size = cornerClipping)
    fun smolFullyClippedButtonShape() = CutCornerShape(size = cornerClipping)

    @Composable
    fun Color.lighten(amount: Int? = null) =
        this.withAdjustedBrightness(amount = amount ?: 20)

    fun Color.darken(amount: Int? = null) =
        this.withAdjustedBrightness(amount = amount ?: -20)

    fun Color.withAdjustedBrightness(amount: Int): Color {
        val r = ((this.red * 255) + amount).coerceIn(0f, 255f).toInt().toString(radix = 16)
        val g = ((this.green * 255) + amount).coerceIn(0f, 255f).toInt().toString(radix = 16)
        val b = ((this.blue * 255) + amount).coerceIn(0f, 255f).toInt().toString(radix = 16)

        val rr = (if (r.length < 2) "0" else "") + r
        val gg = (if (g.length < 2) "0" else "") + g
        val bb = (if (b.length < 2) "0" else "") + b

        return "$rr$gg$bb".hexToColor() ?: this.copy()
    }

    @Composable
    fun dimmedIconColor() = LocalContentColor.current.copy(alpha = .65f)

    @Composable
    fun dimmedTextColor() = LocalContentColor.current.copy(alpha = .65f)

    @Composable
    fun grey() = if (MaterialTheme.colors.isLight) "#DDDDDD".hexToColor()!! else "#333333".hexToColor()!!

    @Composable
    fun Color.highlight(amount: Int? = null): Color =
        if (MaterialTheme.colors.isLight) this.darken(amount) else this.lighten(amount)

    @Composable
    fun alertDialogTitle() = MaterialTheme.typography.h6

    @Composable
    fun alertDialogBody() = MaterialTheme.typography.body1

    fun iconHeightWidth() = ButtonDefaults.MinHeight
    fun textIconHeightWidth() = ButtonDefaults.IconSize

    fun Theme.toColors(): Colors {
        val theme = this
        var builder = if (theme.isDark) darkColors() else lightColors()
        theme.primary?.hexToColor()?.run { builder = builder.copy(primary = this) }
        theme.primaryVariant?.hexToColor()?.run { builder = builder.copy(primaryVariant = this) }
        theme.secondary?.hexToColor()?.run { builder = builder.copy(secondary = this) }
        theme.secondaryVariant?.hexToColor()?.run { builder = builder.copy(secondaryVariant = this) }
        theme.background?.hexToColor()?.run { builder = builder.copy(background = this) }
        theme.surface?.hexToColor()?.run { builder = builder.copy(surface = this) }
        theme.error?.hexToColor()?.run { builder = builder.copy(error = this) }
        theme.onPrimary?.hexToColor()?.run { builder = builder.copy(onPrimary = this) }
        theme.onSecondary?.hexToColor()?.run { builder = builder.copy(onSecondary = this) }
        theme.onBackground?.hexToColor()?.run { builder = builder.copy(onBackground = this) }
        theme.onSurface?.hexToColor()?.run { builder = builder.copy(onSurface = this) }
        theme.onError?.hexToColor()?.run { builder = builder.copy(onError = this) }
        return builder
    }

    val Colors.hyperlink: Color
        get() = SL.themeManager.activeTheme.value.second.hyperlink?.hexToColor() ?: this.secondary

    val materialTheme = MaterialTheme


    @Composable
    fun GlowingBorderColor(
        borderColors: List<Color>? = null,
        animationDurationInMillis: Int = 750,
        easing: Easing = LinearEasing
    ): Color {
        val colors =
            borderColors ?: listOf(MaterialTheme.colors.secondary, MaterialTheme.colors.primary)
        var color by remember { mutableStateOf(colors.first()) }
        val colorState by animateColorAsState(color, animationSpec = tween(animationDurationInMillis, easing = easing))
        LaunchedEffect(0f) {
            var i = 0
            while (true) {
                color = colors[i++ % colors.size]
                delay(timeMillis = animationDurationInMillis.toLong())
            }
        }
        return colorState
    }
}

data class UsableBounds(val height: Dp = 0.dp, val width: Dp = 0.dp)

var LocalUsableBounds = UsableBounds()