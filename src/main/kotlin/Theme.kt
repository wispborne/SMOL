import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.dp

object SmolTheme {

    private val primary = Color(java.awt.Color.decode("#184957").rgb)
    private val primaryVariant = Color(java.awt.Color.decode("#00212e").rgb)
    private val secondary = Color(java.awt.Color.decode("#FCCF00").rgb)
    private val background = Color(java.awt.Color.decode("#091A1F").rgb)
    private val onBackground = Color(java.awt.Color.decode("#2d304e").rgb)

    val DarkColors = darkColors(
        primary = primary,
        primaryVariant = primaryVariant,
        surface = Color(java.awt.Color.decode("#0A1D22").rgb),
        secondary = secondary,
        background = background,
        onPrimary = Color(java.awt.Color.decode("#A8DBFC").rgb)
    )

    val orbitronSpaceFont = FontFamily(
        Font("Font-Orbitron/Orbitron-VariableFont_wght.ttf")
    )

    val fireCodeFont = FontFamily(
        Font("Font-Fire_Mono/FiraMono-Regular.ttf")
    )
}

@Composable
@OptIn(ExperimentalMaterialApi::class)
fun SmolButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    elevation: ButtonElevation? = ButtonDefaults.elevation(),
    shape: Shape? = null,
    border: BorderStroke? = null,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        modifier = modifier,
        border = border,
        shape = shape ?: smolNormalButtonShape(),
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        elevation = elevation,
        colors = colors,
        contentPadding = contentPadding,
        content = content,
    )
}

fun smolNormalButtonShape() = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp)
fun smolFullyClippedButtonShape() = CutCornerShape(size = 8.dp)

@Composable
@OptIn(ExperimentalMaterialApi::class)
fun SmolSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    elevation: ButtonElevation? = null,
    shape: Shape? = null,
    border: BorderStroke? = null,
    colors: ButtonColors = ButtonDefaults.buttonColors(
        backgroundColor = MaterialTheme.colors.primary.copy(alpha = ContentAlpha.medium),
        contentColor = MaterialTheme.colors.onPrimary.copy(alpha = ContentAlpha.high)
    ),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    SmolButton(
        modifier = modifier,
        border = border,
        shape = shape ?: smolNormalButtonShape(),
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        elevation = elevation,
        colors = colors,
        contentPadding = contentPadding,
        content = content,
    )
}

@Composable
@ExperimentalMaterialApi
fun SmolAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    underlayModifier: Modifier = Modifier
        .fillMaxWidth()
        .fillMaxHeight()
        .background(Color.Black.copy(alpha = ContentAlpha.medium)),
    modifier: Modifier = Modifier.width(400.dp),
    dismissButton: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.medium,
    backgroundColor: Color = MaterialTheme.colors.surface,
    contentColor: Color = contentColorFor(backgroundColor),
    dialogProvider: AlertDialogProvider = PopupAlertDialogProvider
) {
    Box(
        modifier = underlayModifier
    ) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            modifier = modifier,
            dismissButton = dismissButton,
            title = title,
            text = text,
            shape = shape,
            backgroundColor = backgroundColor,
            contentColor = contentColor,
            dialogProvider = dialogProvider
        )
    }
}

@Composable
fun TiledImage(
    modifier: Modifier = Modifier,
    imageBitmap: ImageBitmap
) {
    Canvas(
        modifier = modifier
            .clipToBounds()
    ) {
        val paint = Paint().asFrameworkPaint().apply {
            isAntiAlias = true
            shader = ImageShader(imageBitmap, TileMode.Repeated, TileMode.Repeated)
        }

        drawIntoCanvas {
            it.nativeCanvas.drawPaint(paint)
        }
        paint.reset()
    }
    Box(modifier.fillMaxWidth().fillMaxHeight())
}