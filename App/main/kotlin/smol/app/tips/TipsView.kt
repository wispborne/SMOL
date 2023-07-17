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

package smol.app.tips

import AppScope
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import smol.access.Constants
import smol.access.SL
import smol.access.model.ModTip
import smol.access.model.Tips
import smol.app.composables.*
import smol.app.navigation.Screen
import smol.app.themes.SmolTheme
import smol.app.themes.SmolTheme.lighten
import smol.app.toolbar.toolbar
import smol.app.util.openInDesktop
import smol.timber.ktx.Timber
import smol.utilities.copyToClipboard
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.readText

enum class TipsGrouping {
    NONE,
    MOD,
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
@Preview
fun AppScope.TipsView(
    modifier: Modifier = Modifier
) {
    var tipsChangedNotifier by remember { mutableStateOf(0) }
    val showLogPanel = remember { mutableStateOf(false) }
    val mods = SL.access.mods.collectAsState().value?.mods.orEmpty()
    var grouping by remember { mutableStateOf(TipsGrouping.NONE) }
    var onlyEnabled by remember { mutableStateOf(false) }
    val unfilteredTips = remember { mutableStateListOf<ModTip>() }
    val tips = unfilteredTips
        .filter {
            if (onlyEnabled)
                it.variants.firstOrNull()?.mod(SL.access)?.hasEnabledVariant ?: true
            else true
        }
        .associateBy { it.hashCode() }
    val tipSelectionStates =
        remember { mutableStateMapOf(*tips.map { it.hashCode() to false }.toTypedArray()) }

    LaunchedEffect(tipsChangedNotifier) {
        unfilteredTips.clear()
        unfilteredTips.addAll(mods
            .map { mod ->
                // Do each variant separately so that any duplicate tips in different mods aren't combined.
                mod.variants.mapNotNull { variant ->
                    val tipFilePath = variant.modsFolderInfo.folder.resolve(Constants.TIPS_FILE_RELATIVE_PATH)
                    if (tipFilePath.exists()) {
                        variant to runCatching {
                            SL.jsanity.fromJson<Tips>(
                                json = tipFilePath.readText(),
                                filename = tipFilePath.absolutePathString(),
                                shouldStripComments = true
                            ).tips
                        }
                            .getOrElse { emptyList() }
                    } else {
                        null
                    }
                }
                    .flatMap { (variant, tips) -> tips.map { tip -> tip to variant } }
                    .groupBy({ it.first }, { it.second })
                    .map { (tip, variants) -> ModTip(tip, variants) }
            }
            .flatten())
    }

    Scaffold(
        topBar = {
            SmolTopAppBar(modifier = Modifier.height(SmolTheme.topBarHeight)) {
                toolbar(router.state.value.activeChild.instance as Screen)
            }
        }, content = {
            Column(
                modifier = Modifier.padding(
                    start = 8.dp,
                    top = 8.dp,
                    end = 8.dp,
                    bottom = SmolTheme.bottomBarHeight - 8.dp
                )
            ) {
                Row(
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tips",
                        style = MaterialTheme.typography.h5
                    )
                    Spacer(Modifier.weight(1f))
                    Text(text = "Only enabled", modifier = Modifier.onClick { onlyEnabled = !onlyEnabled })
                    Checkbox(
                        checked = onlyEnabled,
                        onCheckedChange = { onlyEnabled = it },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    SmolButton(
                        modifier = Modifier.padding(end = 8.dp),
                        onClick = {
                            if (tipSelectionStates.count() == unfilteredTips.count() && tipSelectionStates.all { it.value })
                                tipSelectionStates.putAll(unfilteredTips.associate { it.hashCode() to false })
                            else
                                tipSelectionStates.putAll(unfilteredTips.associate { it.hashCode() to true })
                        }
                    ) {
                        Text(text = "Select All")
                    }
                    SmolDropdownWithButton(
                        items = listOf(
                            SmolDropdownMenuItemTemplate(text = "No grouping") {
                                grouping = TipsGrouping.NONE
                            },
                            SmolDropdownMenuItemTemplate(text = "Group by mod") {
                                grouping = TipsGrouping.MOD
                            },
                        ),
                    )
                    SmolButton(
                        modifier = Modifier.padding(start = 32.dp),
                        onClick = {
                            SL.tipsManager.deleteTips(tipSelectionStates.filter { it.value }
                                .mapNotNull { tips[it.key] })
                            tipsChangedNotifier++
                        }
                    ) {
                        Icon(painter = painterResource("icon-trash.svg"), contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(text = "Delete Selected")
                    }
                }
                LazyVerticalGrid(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    columns = GridCells.Adaptive(370.dp)
                ) {
                    when (grouping) {
                        TipsGrouping.NONE -> {
                            tips
                                .values
                                .sortedWith(compareByDescending { it.tipObj.tip.orEmpty().length })
                                .map { tip ->
                                    this.item {
                                        TipCard(tip = tip, showTitle = true,
                                            isSelected = tipSelectionStates[tip.hashCode()] ?: false,
                                            onTipChanged = { tipsChangedNotifier++ },
                                            onSelectedChanged = { tipSelectionStates[tip.hashCode()] = it }
                                        )
                                    }
                                }
                        }

                        TipsGrouping.MOD -> tips.values.groupBy { it.variants.firstOrNull()?.mod(SL.access) }
                            .entries
                            .sortedWith(compareBy { it.key?.findFirstEnabledOrHighestVersion?.modInfo?.name })
                            .forEach { (mod, tipList) ->
                                this.item(span = { GridItemSpan(maxCurrentLineSpan) }) { Spacer(Modifier.height(0.dp)) }
                                this.item(span = { GridItemSpan(maxCurrentLineSpan) }) {
                                    Text(
                                        text = ((mod?.variants?.lastOrNull()?.modInfo?.name?.trim()
                                            ?: "(unknown mod name)")) + " (${tipList.size})",
                                        style = MaterialTheme.typography.subtitle1,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                                            .align(Alignment.CenterHorizontally)
                                    )
                                }
                                tipList
                                    .map { tip ->
                                        this.item {
                                            TipCard(tip = tip, showTitle = false,
                                                isSelected = tipSelectionStates[tip.hashCode()] ?: false,
                                                onTipChanged = { tipsChangedNotifier++ },
                                                onSelectedChanged = { tipSelectionStates[tip.hashCode()] = it })
                                        }
                                    }
                            }
                    }
                }
            }

            if (showLogPanel.value) {
                logPanel { showLogPanel.value = false }
            }
        },
        bottomBar = {
            SmolBottomAppBar(
                modifier = Modifier.fillMaxWidth()
            ) {
                logButtonAndErrorDisplay(showLogPanel = showLogPanel)
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TipCard(
    modifier: Modifier = Modifier,
    tip: ModTip,
    showTitle: Boolean,
    isSelected: Boolean,
    onTipChanged: () -> Unit,
    onSelectedChanged: ((Boolean) -> Unit),
) {
    var showContextMenu by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .border(
                shape = SmolTheme.smolFullyClippedButtonShape(),
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colors.surface.lighten(if (isSelected) 100 else 20)
                )
            )
            .onClick {
                onSelectedChanged(!isSelected)
            }
            .onClick(matcher = PointerMatcher.mouse(PointerButton.Secondary)) {
                showContextMenu = !showContextMenu
            },
        shape = SmolTheme.smolFullyClippedButtonShape(),
        backgroundColor = if (isSelected) MaterialTheme.colors.surface.lighten() else MaterialTheme.colors.surface,
    ) {
        Column(Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)) {
            Row(modifier = Modifier.padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showTitle) {
                    Text(
                        text = (tip.variants.lastOrNull()?.modInfo?.name?.trim()
                            ?: "(unknown mod name)"), // + " (${tip.variants.joinToString { it.modInfo.version.toString() }})",
                        style = MaterialTheme.typography.subtitle1,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.alpha(0.65f).padding(end = 8.dp),
                        color = if (tip.tipObj.freq?.toFloatOrNull() == 0f)
                            LocalContentColor.current.copy(alpha = 0.65f)
                        else LocalContentColor.current
                    )
                }
                Spacer(Modifier.weight(1f))
                Checkbox(checked = isSelected,
                    modifier = Modifier.scale(0.75f).requiredSize(16.dp),
                    onCheckedChange = {
                        onSelectedChanged(it)
                    })
            }
            Text(
                text = tip.tipObj.tip?.trim()?.trim('"')
                    ?: "(author '${tip.variants.lastOrNull()?.modInfo?.author}' didn't add any text for this tip)",
                style = MaterialTheme.typography.body2
            )
            SmolTooltipArea(tooltip = { SmolTooltipText("Tip frequency (1 is normal frequency).") }) {
                Text(
                    text = "%.1f".format(tip.tipObj.freq?.toFloatOrNull() ?: 1f),
                    modifier = Modifier.alpha(0.6f).padding(top = 4.dp),
                    style = MaterialTheme.typography.caption
                )
            }
        }
    }

    CursorDropdownMenu(
        expanded = showContextMenu,
        onDismissRequest = { showContextMenu = false }) {
        DropdownMenuItem(onClick = {
            copyToClipboard(tip.tipObj.tip.orEmpty())
            showContextMenu = false
        }) {
            Image(
                painter = painterResource("icon-copy.svg"),
                colorFilter = ColorFilter.tint(MaterialTheme.colors.onSurface),
                modifier = Modifier.padding(end = 12.dp).size(24.dp),
                contentDescription = null
            )
            Text("Copy")
        }
        DropdownMenuItem(onClick = {
            SL.tipsManager.deleteTips(listOf(tip))
            onTipChanged()
            showContextMenu = false
        }) {
            Image(
                painter = painterResource("icon-trash.svg"),
                colorFilter = ColorFilter.tint(MaterialTheme.colors.onSurface),
                modifier = Modifier.padding(end = 12.dp).size(24.dp),
                contentDescription = null
            )
            Text("Delete")
        }
        tip.variants.forEach { variant ->
            DropdownMenuItem(onClick = {
                runCatching {
                    variant.modsFolderInfo.folder.resolve(Constants.TIPS_FILE_RELATIVE_PATH).parent.openInDesktop()
                }.onFailure { Timber.w(it) }
                showContextMenu = false
            }) {
                Image(
                    painter = painterResource("icon-folder.svg"),
                    colorFilter = ColorFilter.tint(MaterialTheme.colors.onSurface),
                    modifier = Modifier.padding(end = 12.dp).size(24.dp),
                    contentDescription = null
                )
                Text("Open ${variant.modInfo.version}")
            }
        }
    }
}