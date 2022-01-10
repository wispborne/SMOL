package smol_app.themes

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.dp
import smol_access.SL
import smol_access.themes.Theme
import smol_app.util.hexToColor

object SmolTheme {
    val orbitronSpaceFont = FontFamily(Font("Font-Orbitron/Orbitron-VariableFont_wght.ttf"))
    val fireCodeFont = FontFamily(Font("Font-Fire_Mono/FiraMono-Regular.ttf"))
    val normalFont = FontFamily.Default

    val warningOrange = Color(java.awt.Color.decode("#F95D13").rgb)

    val cornerClipping = 8.dp
    val bottomBarHeight = 64.dp
    val topBarHeight = 72.dp
    fun smolNormalButtonShape() = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp)
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
}