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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.onClick
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.salomonbrys.kotson.get
import com.google.gson.JsonObject
import smol.access.Constants
import smol.access.SL
import smol.access.model.ModVariant
import smol.app.composables.*
import smol.app.navigation.Screen
import smol.app.themes.SmolTheme
import smol.app.themes.SmolTheme.lighten
import smol.app.toolbar.toolbar
import smol.utilities.copyToClipboard
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.readText

enum class TipsGrouping {
    NONE,
    MOD,
}

@Composable
@Preview
fun AppScope.TipsView(
    modifier: Modifier = Modifier
) {
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
    LaunchedEffect(Unit) {
        unfilteredTips.clear()
        unfilteredTips.addAll(mods
            .map { mod ->
                // Do each variant separately so that any duplicate tips in different mods aren't combined.
                mod.variants.mapNotNull { variant ->
                    val tipFilePath = variant.modsFolderInfo.folder.resolve(Constants.TIPS_FILE_RELATIVE_PATH)
                    if (tipFilePath.exists()) {
                        variant to runCatching {
                            SL.jsanity.fromJson<JsonObject>(
                                json = tipFilePath.readText(),
                                filename = tipFilePath.absolutePathString(),
                                shouldStripComments = true
                            )["tips"].asJsonArray
                                .map {
                                    // Tips can either be "{ freq: "1", tip: "something" }" or just "something"
                                    val isShortFormat = runCatching { it["tip"] }.isFailure
                                    if (isShortFormat) Tip(
                                        "1",
                                        it.toString()
                                    ) else Tip(it["freq"].asString, it["tip"].asString)
                                }
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
            Column(modifier = Modifier.padding(8.dp)) {
                Row(
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tips",
                        style = MaterialTheme.typography.h5
                    )
                    Spacer(Modifier.weight(1f))
                    CheckboxWithText(
                        text = { Text("Only enabled") },
                        checked = onlyEnabled,
                        onCheckedChange = { onlyEnabled = it },
                        modifier = Modifier.padding(end = 8.dp)
                    )
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
                }
                LazyVerticalGrid(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    columns = GridCells.Adaptive(370.dp)
                ) {
                    when (grouping) {
                        TipsGrouping.NONE -> {
                            tips
                                .sortedWith(compareByDescending<ModTip> { it.tipObj.tip.orEmpty().length })
                                .map { tip ->
                                    this.item {
                                        TipCard(tip = tip, showTitle = true)
                                    }
                                }
                        }

                        TipsGrouping.MOD -> tips.groupBy { it.variants.firstOrNull()?.mod(SL.access) }
                            .entries
                            // Sort by longest length of each mod's longest tip.
                            .sortedWith(compareByDescending { it.value.maxBy { it.tipObj.tip.orEmpty().length }.tipObj.tip.orEmpty().length })
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
                                            TipCard(tip = tip, showTitle = false)
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

data class Tip(
    val freq: String?,
    val tip: String?
)

data class ModTip(
    val tipObj: Tip,
    val variants: List<ModVariant>
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TipCard(modifier: Modifier = Modifier, tip: ModTip, showTitle: Boolean) {
    var isSelected by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .border(
                shape = SmolTheme.smolFullyClippedButtonShape(),
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colors.surface.lighten()
                )
            )
            .onClick { isSelected = !isSelected },
        shape = SmolTheme.smolFullyClippedButtonShape(),
        backgroundColor = if (isSelected) MaterialTheme.colors.surface.lighten() else MaterialTheme.colors.surface,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row {
                if (showTitle) {
                    Text(
                        text = (tip.variants.lastOrNull()?.modInfo?.name?.trim()
                            ?: "(unknown mod name)"), // + " (${tip.variants.joinToString { it.modInfo.version.toString() }})",
                        style = MaterialTheme.typography.subtitle1,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp).alpha(0.65f)
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { copyToClipboard(tip.tipObj.tip.orEmpty()) }, modifier = Modifier.alpha(0.7f)) {
                    Icon(
                        painter = painterResource("icon-copy.svg"),
                        contentDescription = null
                    )
                }
                Checkbox(checked = isSelected, onCheckedChange = { isSelected = it })
            }
            Text(
                text = tip.tipObj.tip?.trim()?.trim('"')
                    ?: "(author '${tip.variants.lastOrNull()?.modInfo?.author}' didn't add any text for this tip)",
                style = MaterialTheme.typography.body2
            )
        }
    }
}