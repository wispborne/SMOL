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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import smol.app.themes.SmolTheme


abstract class SmolDropdownMenuItem(
    val onClick: () -> Unit,
    val backgroundColor: Color? = null,
    val border: Border? = null,
) {
    data class Border(
        val borderStroke: BorderStroke,
        val shape: Shape
    )
}

class SmolDropdownMenuItemTemplate(
    val text: String,
    val iconPath: String? = null,
    val isEnabled: Boolean = true,
    backgroundColor: Color? = null,
    border: Border? = null,
    val contentColor: Color? = null,
    onClick: () -> Unit,
) : SmolDropdownMenuItem(onClick, backgroundColor, border)

class SmolDropdownMenuItemCustom(
    onClick: () -> Unit,
    backgroundColor: Color? = null,
    border: Border? = null,
    val customItemContent: @Composable RowScope.(isMenuButton: Boolean) -> Unit
) : SmolDropdownMenuItem(onClick, backgroundColor, border)

@Composable
fun SmolOverflowMenu(
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
            ) { Icon(painter = painterResource("icon-more-horz.svg"), contentDescription = null) }
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
    neverShowFirstItemInPopupMenu: Boolean = false,
    customButtonContent: (@Composable (selectedItem: SmolDropdownMenuItem, isExpanded: Boolean, setExpanded: (Boolean) -> Unit) -> Unit)? = null
) {
    val expanded = remember { mutableStateOf(false) }
    val selectedIndex = remember { mutableStateOf(initiallySelectedIndex) }
    val selectedItem = items.getOrNull(selectedIndex.value)
    if (selectedItem != null) {
        Box(modifier) {
            val backgroundColor =
                selectedItem.backgroundColor ?: MaterialTheme.colors.primary
            if (customButtonContent == null) {
                SmolButton(
                    onClick = { expanded.value = expanded.value.not() },
                    modifier = Modifier.wrapContentWidth()
                        .align(Alignment.CenterStart),
                    shape = SmolTheme.smolFullyClippedButtonShape(),
                    enabled = (selectedItem as? SmolDropdownMenuItemTemplate)?.isEnabled ?: true,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = backgroundColor
                    )
                ) {
                    if (selectedItem is SmolDropdownMenuItemTemplate) {
                        if (!selectedItem.iconPath.isNullOrBlank()) {
                            Icon(
                                painter = painterResource(selectedItem.iconPath),
                                modifier = Modifier.padding(end = 12.dp),
                                contentDescription = null
                            )
                        }
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
                        expanded.value
                    )
                }
            } else {
                Box(
                    modifier = Modifier.clip(MaterialTheme.shapes.small)
                        .clickable { expanded.value = expanded.value.not() }) {
                    customButtonContent.invoke(selectedItem, expanded.value) { expanded.value = it }
                }
            }
            SmolDropdownMenu(
                expanded = expanded,
                items = items,
                shouldShowSelectedItemInMenu = shouldShowSelectedItemInMenu,
                selectedIndex = selectedIndex,
                canSelectItems = canSelectItems,
                neverShowFirstItemInPopupMenu = neverShowFirstItemInPopupMenu
            )
        }
    }
}

@Composable
fun SmolDropdownMenu(
    expanded: MutableState<Boolean>,
    modifier: Modifier = Modifier,
    items: List<SmolDropdownMenuItem>,
    shouldShowSelectedItemInMenu: Boolean = true,
    selectedIndex: MutableState<Int> = mutableStateOf(0),
    canSelectItems: Boolean = true,
    neverShowFirstItemInPopupMenu: Boolean = false,
    focusable: Boolean = true
) {
    DropdownMenu(
        expanded = expanded.value,
        focusable = focusable,
        modifier = modifier
            .background(MaterialTheme.colors.background),
        onDismissRequest = { expanded.value = false }
    ) {
        items.forEachIndexed { index, item ->
            if (shouldShowSelectedItemInMenu || index != selectedIndex.value || !canSelectItems) {
                if (!(neverShowFirstItemInPopupMenu && index == 0)) {
                    DropdownMenuItem(
                        modifier = Modifier.let {
                            if (item.backgroundColor != null)
                                it.background(item.backgroundColor) else it
                        }
                            .run {
                                if (item.border != null) this.border(
                                    item.border.borderStroke,
                                    item.border.shape
                                ) else this
                            },
                        onClick = {
                            if (canSelectItems) {
                                selectedIndex.value = index
                            }
                            expanded.value = false
                            items[index].onClick()
                        }) {
                        if (item is SmolDropdownMenuItemCustom) {
                            item.customItemContent.invoke(this, false)
                        } else if (item is SmolDropdownMenuItemTemplate) {
                            if (!item.iconPath.isNullOrBlank()) {
                                Icon(
                                    painter = painterResource(item.iconPath),
                                    // For some reason, setting the size prevents the text from wrapping prematurely.
                                    modifier = Modifier.padding(end = 12.dp).size(24.dp),
                                    contentDescription = null
                                )
                            }
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

@Composable
fun SmolDropdownArrow(
    modifier: Modifier = Modifier,
    expanded: Boolean,
    colorFilter: ColorFilter = ColorFilter.tint(SmolTheme.dimmedIconColor())
) {
    val arrowAngle by animateFloatAsState(if (expanded) 180f else 0f)
    Image(
        modifier = modifier
            .width(16.dp)
            .offset(x = 4.dp)
            .rotate(arrowAngle),
        painter = painterResource("icon-menu-down.svg"),
        colorFilter = colorFilter,
        contentDescription = null
    )
}