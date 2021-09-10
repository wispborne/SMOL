import androidx.compose.material.darkColors
import androidx.compose.ui.graphics.Color

private val dark1 = Color(15, 26, 66)
private val dark2 = Color(32, 57, 146)
private val light1 = Color(73, 222, 240)

val DarkColors = darkColors(
    primary = dark1,
    secondary = dark2,
    onPrimary = light1,
    onSurface = light1,
    background = dark1,
    surface = dark1
)