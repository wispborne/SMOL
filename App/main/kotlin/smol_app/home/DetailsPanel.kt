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

package smol_app.home

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import smol_access.SL
import smol_access.model.Mod
import smol_app.composables.*
import smol_app.themes.SmolTheme
import smol_app.themes.SmolTheme.withAdjustedBrightness
import smol_app.util.getModThreadId
import smol_app.util.getModThreadUrl
import smol_app.util.getNexusId
import smol_app.util.getNexusModsUrl
import utilities.asList


@Composable
@Preview
fun detailsPanelPreview() {
    Box {
        detailsPanel(selectedRow = mutableStateOf(ModRow(Mod.MOCK)), mods = listOf(Mod.MOCK))
    }
}

@OptIn(
    ExperimentalUnitApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class
)
@Composable
fun BoxScope.detailsPanel(
    modifier: Modifier = Modifier,
    selectedRow: MutableState<ModRow?>,
    mods: List<Mod>
) {
    val row = selectedRow ?: return
    val borderColor = MaterialTheme.colors.surface.withAdjustedBrightness(-15)

    Card(
        modifier.width(400.dp)
            .background(MaterialTheme.colors.surface)
            .align(Alignment.CenterEnd)
            .clickable(enabled = false) { }
            .fillMaxHeight()
            .shadow(4.dp)
            .drawWithContent {
                drawContent()
                val y = size.height
                val x = size.width
                val strokeWidth = 2f
                drawLine(
                    strokeWidth = strokeWidth,
                    color = borderColor,
                    cap = StrokeCap.Square,
                    start = Offset.Zero,
                    end = Offset(x = 0f, y = y)
                )
                drawLine(
                    strokeWidth = strokeWidth,
                    color = borderColor,
                    cap = StrokeCap.Square,
                    start = Offset.Zero,
                    end = Offset(x = x, y = 0f)
                )
                drawLine(
                    strokeWidth = strokeWidth,
                    color = borderColor,
                    cap = StrokeCap.Square,
                    start = Offset(x = 0f, y = y),
                    end = Offset(x = x, y = y)
                )
            },
        shape = RectangleShape
    ) {
        Box {
            val state = rememberScrollState()
            SelectionContainer {
                Column(
                    Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp).verticalScroll(state)
                ) {
                    val modVariant = row.value?.mod?.findFirstEnabled ?: row.value?.mod?.findHighestVersion
                    val modInfo = modVariant?.modInfo ?: return@Column
                    Text(
                        modInfo.name ?: "(Unknown Mod)",
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = SmolTheme.orbitronSpaceFont,
                        fontSize = TextUnit(18f, TextUnitType.Sp)
                    )
                    Text(
                        "${modInfo.id} ${modInfo.version}",
                        modifier = Modifier.padding(top = 4.dp),
                        fontSize = TextUnit(12f, TextUnitType.Sp),
                        fontFamily = SmolTheme.fireCodeFont
                    )
                    if (!modInfo.gameVersion.isNullOrBlank()) {
                        Text(
                            "Starsector ${modInfo.gameVersion}",
                            modifier = Modifier.padding(top = 8.dp),
                            fontSize = TextUnit(12f, TextUnitType.Sp),
                            fontFamily = SmolTheme.fireCodeFont
                        )
                    }
                    if (!modInfo.requiredMemoryMB.isNullOrBlank()) {
                        Text(
                            "Required RAM: ${modInfo.requiredMemoryMB} MB",
                            modifier = Modifier.padding(top = 8.dp),
                            fontSize = TextUnit(12f, TextUnitType.Sp),
                            fontFamily = SmolTheme.fireCodeFont
                        )
                    }
                    if (modInfo.isUtilityMod) {
                        SmolTooltipArea(tooltip = { SmolTooltipText(text = "Utility mods may be added or removed from a save at will.") }) {
                            Row(
                                modifier = Modifier.padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource("icon-utility-mod.svg"),
                                    modifier = Modifier.padding(end = 4.dp).size(24.dp),
                                    contentDescription = null
                                )
                                Text("Utility Mod", fontSize = 15.sp)
                            }
                        }
                    }
                    if (modInfo.isTotalConversion) {
                        SmolTooltipArea(tooltip = { SmolTooltipText(text = "Total Conversion mods should not be run with any other mods, except for Utility Mods, unless explicitly stated to be compatible.") }) {
                            Row(
                                modifier = Modifier.padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource("icon-death-star.svg"),
                                    modifier = Modifier.padding(end = 4.dp).size(24.dp),
                                    contentDescription = null
                                )
                                Text("Total Conversion", fontSize = 15.sp)
                            }
                        }
                    }
                    Text("Author", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
                    Text(modInfo.author ?: "(no author specified)", modifier = Modifier.padding(top = 2.dp))
                    Text("Description", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
                    Text(modInfo.description ?: "", modifier = Modifier.padding(top = 2.dp))
                    val dependencies =
                        modVariant.run { SL.dependencyFinder.findDependencies(modVariant = this) }
                            ?: emptyList()
                    if (dependencies.isNotEmpty()) {
                        Text("Dependencies", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
                        Text(
                            dependencies
                                .joinToString {
                                    val depName: String =
                                        it.second?.findHighestVersion?.modInfo?.name ?: it.second?.id ?: it.first.id
                                        ?: ""
                                    depName + if (it.first.version != null) " v${it.first.version?.raw}" else ""
                                },
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    val metadata = SL.modMetadata.mergedData.collectAsState().value
//                    Text("Categories", fontWeight = FontWeight.Bold, modifier = Modifier)
                    SmolTextField(
                        value = metadata[modInfo.id]?.category ?: "",
                        modifier = Modifier.padding(top = 16.dp),
                        label = { Text("Category") },
                        singleLine = true,
                        maxLines = 1,
                        onValueChange = { newStr ->
                            SL.modMetadata.update(modInfo.id) { it.copy(category = newStr) }
                        }
                    )

                    val modThreadId = row.value?.mod?.getModThreadId()
                    if (modThreadId != null) {
                        DisableSelection {
                            SmolLinkText(
                                linkTextData = LinkTextData(
                                    text = "Forum Thread",
                                    url = modThreadId.getModThreadUrl()
                                ).asList(),
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        }
                    }

                    val nexusId = row.value?.mod?.getNexusId()
                    if (nexusId != null) {
                        DisableSelection {
                            SmolLinkText(
                                linkTextData = LinkTextData(
                                    text = "NexusMods",
                                    url = nexusId.getNexusModsUrl()
                                ).asList(),
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = { selectedRow.value = null },
                modifier = Modifier
                    .padding(start = 8.dp, end = 8.dp, top = 8.dp)
                    .align(Alignment.TopEnd)
            ) {
                Icon(imageVector = Icons.Default.Close, contentDescription = null)
            }
        }
    }
}

//@OptIn(
//    ExperimentalUnitApi::class,
//    androidx.compose.ui.ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class
//)
//@Composable
//fun BoxScope.detailsPanelGameStyled(
//    modifier: Modifier = Modifier,
//    selectedRow: ModRow?
//) {
//    val row = selectedRow ?: return
//    val allMods = SL.access.mods.value?.mods ?: emptyList()
//
//    Box(
//        modifier.width(400.dp)
//            .align(Alignment.CenterEnd)
//            .clickable(enabled = false) { }
//            .fillMaxHeight(),
//    ) {
//        TiledImage(
//            modifier = Modifier.align(Alignment.Center)
//                .fillMaxWidth()
//                .padding(24.dp)
//                .fillMaxHeight(),
//            imageBitmap = imageResource("panel00_center.png")
//        )
//        val state = rememberScrollState()
//        SelectionContainer {
//            Column(
//                Modifier.padding(36.dp).verticalScroll(state)
//            ) {
//                val modVariant = row.mod.findFirstEnabled ?: row.mod.findHighestVersion
//                val modInfo = modVariant?.modInfo
//                Text(
//                    modInfo?.name ?: "VNSector",
//                    fontWeight = FontWeight.ExtraBold,
//                    fontFamily = SmolTheme.orbitronSpaceFont,
//                    fontSize = TextUnit(18f, TextUnitType.Sp)
//                )
//                Text(
//                    "${modInfo?.id ?: "vnsector"} ${modInfo?.version?.toString() ?: "no version"}",
//                    modifier = Modifier.padding(top = 4.dp),
//                    fontSize = TextUnit(12f, TextUnitType.Sp),
//                    fontFamily = SmolTheme.fireCodeFont
//                )
//                Text("Author", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
//                Text(modInfo?.author ?: "It's always Techpriest", modifier = Modifier.padding(top = 2.dp, start = 8.dp))
//                Text("Description", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
//                Text(modInfo?.description ?: "", modifier = Modifier.padding(top = 2.dp))
//                val dependencies =
//                    modVariant?.run { SL.dependencyFinder.findDependencies(modVariant = this, mods = allMods) }
//                        ?: emptyList()
//                if (dependencies.isNotEmpty()) {
//                    Text("Dependencies", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
//                    Text(
//                        dependencies
//                            .joinToString {
//                                val depName: String =
//                                    it.second?.findHighestVersion?.modInfo?.name ?: it.second?.id ?: it.first.id ?: ""
//                                depName + if (it.first.version != null) " v${it.first.version?.raw}" else ""
//                            },
//                        modifier = Modifier.padding(top = 2.dp)
//                    )
//                }
//
//                val modThreadId = row.mod.getModThreadId()
//                if (modThreadId != null) {
//                    DisableSelection {
//                        SmolLinkText(
//                            text = "Forum Thread",
//                            modifier = Modifier.padding(top = 16.dp)
//                                .mouseClickable {
//                                    if (this.buttons.isPrimaryPressed) {
//                                        modThreadId.openModThread()
//                                    }
//                                }
//                        )
//                    }
//                }
//
//                val nexusId = row.mod.getNexusId()
//                if (nexusId != null) {
//                    DisableSelection {
//                        SmolLinkText(
//                            text = "NexusMods",
//                            modifier = Modifier.padding(top = 16.dp)
//                                .mouseClickable {
//                                    if (this.buttons.isPrimaryPressed) {
//                                        nexusId.getNexusModsUrl().openAsUriInBrowser()
//                                    }
//                                }
//                        )
//                    }
//                }
//            }
//        }
//        TiledImage(
//            modifier = Modifier.align(Alignment.CenterStart).width(32.dp).fillMaxHeight()
//                .padding(top = 32.dp, bottom = 32.dp),
//            imageBitmap = imageResource("panel00_left.png")
//        )
//        TiledImage(
//            modifier = Modifier.align(Alignment.CenterEnd).width(32.dp).fillMaxHeight()
//                .padding(top = 32.dp, bottom = 32.dp),
//            imageBitmap = imageResource("panel00_right.png")
//        )
//        TiledImage(
//            modifier = Modifier.align(Alignment.TopCenter).height(32.dp).fillMaxWidth()
//                .padding(start = 32.dp, end = 32.dp),
//            imageBitmap = imageResource("panel00_top.png")
//        )
//        TiledImage(
//            modifier = Modifier.align(Alignment.BottomCenter).height(32.dp).fillMaxWidth()
//                .padding(start = 32.dp, end = 32.dp),
//            imageBitmap = imageResource("panel00_bot.png")
//        )
//        Image(
//            painter = painterResource("panel00_top_left.png"),
//            contentDescription = null,
//            modifier = Modifier.align(Alignment.TopStart).width(32.dp).height(32.dp),
//            contentScale = ContentScale.None
//        )
//        Image(
//            painter = painterResource("panel00_bot_left.png"),
//            contentDescription = null,
//            modifier = Modifier.align(Alignment.BottomStart).width(32.dp).height(32.dp),
//            contentScale = ContentScale.None
//        )
//        Image(
//            painter = painterResource("panel00_top_right.png"),
//            contentDescription = null,
//            modifier = Modifier.align(Alignment.TopEnd).width(32.dp).height(32.dp),
//            contentScale = ContentScale.None
//        )
//        Image(
//            painter = painterResource("panel00_bot_right.png"),
//            contentDescription = null,
//            modifier = Modifier.align(Alignment.BottomEnd).width(32.dp).height(32.dp),
//            contentScale = ContentScale.None
//        )
//    }
//}