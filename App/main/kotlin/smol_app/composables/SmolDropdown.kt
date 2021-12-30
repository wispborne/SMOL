package smol_app.composables

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import smol_app.themes.SmolTheme


abstract class SmolDropdownMenuItem(
    val onClick: () -> Unit,
    val backgroundColor: Color? = null,
)

class SmolDropdownMenuItemTemplate(
    val text: String,
    backgroundColor: Color? = null,
    val contentColor: Color? = null,
    onClick: () -> Unit,
) : SmolDropdownMenuItem(onClick, backgroundColor)

class SmolDropdownMenuItemCustom(
    onClick: () -> Unit,
    backgroundColor: Color? = null,
    val customItemContent: @Composable RowScope.(isMenuButton: Boolean) -> Unit
) : SmolDropdownMenuItem(onClick, backgroundColor)

@Composable
fun SmolPopupMenu(
    modifier: Modifier = Modifier,
    items: List<SmolDropdownMenuItem>,
) {
    SmolDropdownWithButton(
        modifier = modifier,
        items = items,
        shouldShowSelectedItemInMenu = false,
        canSelectItems = false,
        customButtonContent = { _, _, setExpanded ->
            IconButton(
                onClick = { setExpanded(true) },
                modifier = Modifier.size(16.dp)
            ) { Icon(imageVector = Icons.Default.MoreVert, contentDescription = null) }
        }
    )
}

@Composable
fun SmolDropdownWithButton(
    modifier: Modifier = Modifier,
    items: List<SmolDropdownMenuItem>,
    initiallySelectedIndex: Int = 0,
    shouldShowSelectedItemInMenu: Boolean = true,
    canSelectItems: Boolean = true,
    customButtonContent: (@Composable (selectedItem: SmolDropdownMenuItem, isExpanded: Boolean, setExpanded: (Boolean) -> Unit) -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedIndex by remember { mutableStateOf(initiallySelectedIndex) }
    Box(modifier) {
        val selectedItem = items[selectedIndex]
        val backgroundColor =
            selectedItem.backgroundColor ?: MaterialTheme.colors.primary
        if (customButtonContent == null) {
            SmolButton(
                onClick = { expanded = expanded.not() },
                modifier = Modifier.wrapContentWidth()
                    .align(Alignment.CenterStart),
                shape = SmolTheme.smolFullyClippedButtonShape(),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = backgroundColor
                )
            ) {
                if (selectedItem is SmolDropdownMenuItemTemplate) {
                    Text(
                        text = selectedItem.text,
                        fontWeight = FontWeight.Bold,
                        color = selectedItem.contentColor ?: contentColorFor(backgroundColor)
                    )
                } else if (selectedItem is SmolDropdownMenuItemCustom) {
                    selectedItem.customItemContent.invoke(this, true)
                }
                SmolDropdownArrow(
                    Modifier
                        .align(Alignment.CenterVertically),
                    expanded
                )
            }
        } else {
            Box(modifier = Modifier.clickable { expanded = expanded.not() }) {
                customButtonContent.invoke(selectedItem, expanded) { expanded = it }
            }
        }
        DropdownMenu(
            expanded = expanded,
            modifier = Modifier.background(
                MaterialTheme.colors.background
            ),
            onDismissRequest = { expanded = false }
        ) {
            items.forEachIndexed { index, item ->
                if (shouldShowSelectedItemInMenu || index != selectedIndex || !canSelectItems) {
                    DropdownMenuItem(
                        modifier = Modifier.let { if (item.backgroundColor != null) it.background(item.backgroundColor) else it },
                        onClick = {
                            if (canSelectItems) {
                                selectedIndex = index
                            }
                            expanded = false
                            items[index].onClick()
                        }) {
                        Row(Modifier.height(IntrinsicSize.Min)) {
                            if (item is SmolDropdownMenuItemCustom) {
                                item.customItemContent.invoke(this, false)
                            } else if (item is SmolDropdownMenuItemTemplate) {
                                Text(
                                    text = item.text,
                                    modifier = Modifier.weight(1f),
                                    fontWeight = FontWeight.Bold,
                                    color = item.contentColor ?: contentColorFor(
                                        item.backgroundColor ?: MaterialTheme.colors.surface
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SmolDropdownArrow(modifier: Modifier = Modifier, expanded: Boolean) {
    val arrowAngle by animateFloatAsState(if (expanded) 180f else 0f)
    Image(
        modifier = modifier
            .width(16.dp)
            .offset(x = 4.dp)
            .rotate(arrowAngle),
        painter = painterResource("menu-down.svg"),
        colorFilter = ColorFilter.tint(SmolTheme.dimmedIconColor()),
        contentDescription = null
    )
}