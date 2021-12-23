package smol_app.views

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import smol_access.SL
import smol_access.model.Mod
import smol_app.composables.SmolLinkText
import smol_app.composables.TiledImage
import smol_app.home.ModRow
import smol_app.themes.SmolTheme
import smol_app.themes.SmolTheme.withAdjustedBrightness
import smol_app.util.imageResource
import smol_app.util.openModThread


@Composable
@Preview
fun detailsPanelPreview() {
    Box {
        detailsPanel(selectedRow = mutableStateOf(ModRow(Mod.mock)), mods = listOf(Mod.mock))
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
                    val modInfo = modVariant?.modInfo
                    Text(
                        modInfo?.name ?: "VNSector",
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = SmolTheme.orbitronSpaceFont,
                        fontSize = TextUnit(18f, TextUnitType.Sp)
                    )
                    Text(
                        "${modInfo?.id ?: "vnsector"} ${modInfo?.version?.toString() ?: "no version"}",
                        modifier = Modifier.padding(top = 4.dp),
                        fontSize = TextUnit(12f, TextUnitType.Sp),
                        fontFamily = SmolTheme.fireCodeFont
                    )
                    if (!modInfo?.gameVersion.isNullOrBlank()) {
                        Text(
                            "Starsector ${modInfo?.gameVersion}",
                            modifier = Modifier.padding(top = 8.dp),
                            fontSize = TextUnit(12f, TextUnitType.Sp),
                            fontFamily = SmolTheme.fireCodeFont
                        )
                    }
                    Text("Author", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
                    Text(modInfo?.author ?: "It's always Techpriest", modifier = Modifier.padding(top = 2.dp))
                    Text("Description", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
                    Text(modInfo?.description ?: "", modifier = Modifier.padding(top = 2.dp))
                    val dependencies =
                        modVariant?.run { SL.dependencies.findDependencies(modVariant = this, mods = mods) }
                            ?: emptyList()
                    if (dependencies.isNotEmpty()) {
                        Text("Dependencies", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
                        Text(
                            dependencies
                                .joinToString {
                                    val depName: String =
                                        it.second?.findHighestVersion?.modInfo?.name ?: it.second?.id ?: it.first.id
                                    depName + if (it.first.versionString != null) " v${it.first.versionString}" else ""
                                },
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    val versionCheckerInfo = row.value?.mod?.findHighestVersion?.versionCheckerInfo
                    if (versionCheckerInfo?.modThreadId != null) {
                        DisableSelection {
                            SmolLinkText(
                                text = "Forum Thread",
                                modifier = Modifier.padding(top = 16.dp)
                                    .mouseClickable {
                                        if (this.buttons.isPrimaryPressed) {
                                            versionCheckerInfo.modThreadId!!.openModThread()
                                        }
                                    }
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

@OptIn(
    ExperimentalUnitApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class
)
@Composable
fun BoxScope.detailsPanelGameStyled(
    modifier: Modifier = Modifier,
    selectedRow: ModRow?
) {
    val row = selectedRow ?: return
    val allMods = SL.access.mods.value ?: emptyList()

    Box(
        modifier.width(400.dp)
            .align(Alignment.CenterEnd)
            .clickable(enabled = false) { }
            .fillMaxHeight(),
    ) {
        TiledImage(
            modifier = Modifier.align(Alignment.Center)
                .fillMaxWidth()
                .padding(24.dp)
                .fillMaxHeight(),
            imageBitmap = imageResource("panel00_center.png")
        )
        val state = rememberScrollState()
        SelectionContainer {
            Column(
                Modifier.padding(36.dp).verticalScroll(state)
            ) {
                val modVariant = row.mod.findFirstEnabled ?: row.mod.findHighestVersion
                val modInfo = modVariant?.modInfo
                Text(
                    modInfo?.name ?: "VNSector",
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = SmolTheme.orbitronSpaceFont,
                    fontSize = TextUnit(18f, TextUnitType.Sp)
                )
                Text(
                    "${modInfo?.id ?: "vnsector"} ${modInfo?.version?.toString() ?: "no version"}",
                    modifier = Modifier.padding(top = 4.dp),
                    fontSize = TextUnit(12f, TextUnitType.Sp),
                    fontFamily = SmolTheme.fireCodeFont
                )
                Text("Author", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                Text(modInfo?.author ?: "It's always Techpriest", modifier = Modifier.padding(top = 2.dp, start = 8.dp))
                Text("Description", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                Text(modInfo?.description ?: "", modifier = Modifier.padding(top = 2.dp))
                val dependencies =
                    modVariant?.run { SL.dependencies.findDependencies(modVariant = this, mods = allMods) }
                        ?: emptyList()
                if (dependencies.isNotEmpty()) {
                    Text("Dependencies", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                    Text(
                        dependencies
                            .joinToString {
                                val depName: String =
                                    it.second?.findHighestVersion?.modInfo?.name ?: it.second?.id ?: it.first.id
                                depName + if (it.first.versionString != null) " v${it.first.versionString}" else ""
                            },
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                val versionCheckerInfo = row.mod.findHighestVersion?.versionCheckerInfo
                if (versionCheckerInfo?.modThreadId != null) {
                    DisableSelection {
                        SmolLinkText(
                            text = "Forum Thread",
                            modifier = Modifier.padding(top = 16.dp)
                                .mouseClickable {
                                    if (this.buttons.isPrimaryPressed) {
                                        versionCheckerInfo.modThreadId!!.openModThread()
                                    }
                                }
                        )
                    }
                }
            }
        }
        TiledImage(
            modifier = Modifier.align(Alignment.CenterStart).width(32.dp).fillMaxHeight()
                .padding(top = 32.dp, bottom = 32.dp),
            imageBitmap = imageResource("panel00_left.png")
        )
        TiledImage(
            modifier = Modifier.align(Alignment.CenterEnd).width(32.dp).fillMaxHeight()
                .padding(top = 32.dp, bottom = 32.dp),
            imageBitmap = imageResource("panel00_right.png")
        )
        TiledImage(
            modifier = Modifier.align(Alignment.TopCenter).height(32.dp).fillMaxWidth()
                .padding(start = 32.dp, end = 32.dp),
            imageBitmap = imageResource("panel00_top.png")
        )
        TiledImage(
            modifier = Modifier.align(Alignment.BottomCenter).height(32.dp).fillMaxWidth()
                .padding(start = 32.dp, end = 32.dp),
            imageBitmap = imageResource("panel00_bot.png")
        )
        Image(
            painter = painterResource("panel00_top_left.png"),
            contentDescription = null,
            modifier = Modifier.align(Alignment.TopStart).width(32.dp).height(32.dp),
            contentScale = ContentScale.None
        )
        Image(
            painter = painterResource("panel00_bot_left.png"),
            contentDescription = null,
            modifier = Modifier.align(Alignment.BottomStart).width(32.dp).height(32.dp),
            contentScale = ContentScale.None
        )
        Image(
            painter = painterResource("panel00_top_right.png"),
            contentDescription = null,
            modifier = Modifier.align(Alignment.TopEnd).width(32.dp).height(32.dp),
            contentScale = ContentScale.None
        )
        Image(
            painter = painterResource("panel00_bot_right.png"),
            contentDescription = null,
            modifier = Modifier.align(Alignment.BottomEnd).width(32.dp).height(32.dp),
            contentScale = ContentScale.None
        )
    }
}