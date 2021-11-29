package smol_app.themes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import smol_access.themes.Theme
import smol_app.util.hexToColor

object SmolTheme {

    val orbitronSpaceFont = FontFamily(
        Font("Font-Orbitron/Orbitron-VariableFont_wght.ttf")
    )

    val fireCodeFont = FontFamily(
        Font("Font-Fire_Mono/FiraMono-Regular.ttf")
    )

    val warningOrange = Color(java.awt.Color.decode("#F95D13").rgb)

    val cornerClipping = 8.dp
    fun smolNormalButtonShape() = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp)
    fun smolFullyClippedButtonShape() = CutCornerShape(size = cornerClipping)

    @Composable
    fun highlight() = MaterialTheme.colors.onSurface.copy(alpha = .70f)

    @Composable
    fun dimmedIconColor() = LocalContentColor.current.copy(alpha = .65f)

    @Composable
    fun dimmedTextColor() = LocalContentColor.current.copy(alpha = .65f)

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

@Composable
@OptIn(ExperimentalMaterialApi::class)
fun SmolSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    elevation: ButtonElevation? = ButtonDefaults.elevation(),
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

@Composable
@ExperimentalMaterialApi
fun SmolAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit = { SmolButton(onClick = { onDismissRequest() }) { Text("Ok") } },
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
fun SmolTooltipText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current
) {
    Text(
        text = text,
        modifier = modifier.background(MaterialTheme.colors.background).padding(8.dp),
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        onTextLayout = onTextLayout,
        style = style
    )
}

@Composable
fun SmolOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shape: Shape = MaterialTheme.shapes.small,
    colors: TextFieldColors = TextFieldDefaults.outlinedTextFieldColors(
        focusedLabelColor = SmolTheme.dimmedTextColor()
    )
) {
    OutlinedTextField(
        value,
        onValueChange,
        modifier,
        enabled,
        readOnly,
        textStyle,
        label,
        placeholder,
        leadingIcon,
        trailingIcon,
        isError,
        visualTransformation,
        keyboardOptions,
        keyboardActions,
        singleLine,
        maxLines,
        interactionSource,
        shape,
        colors,
    )
}

@Composable
fun SmolTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shape: Shape = MaterialTheme.shapes.small,
    colors: TextFieldColors = TextFieldDefaults.outlinedTextFieldColors(
        focusedLabelColor = SmolTheme.dimmedTextColor()
    )
) {
    TextField(
        value,
        onValueChange,
        modifier,
        enabled,
        readOnly,
        textStyle,
        label,
        placeholder,
        leadingIcon,
        trailingIcon,
        isError,
        visualTransformation,
        keyboardOptions,
        keyboardActions,
        singleLine,
        maxLines,
        interactionSource,
        shape,
        colors,
    )
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