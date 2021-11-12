package views

import SL
import SmolTheme
import TiledImage
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import business.findDependencies
import util.imageResource
import util.openModThread
import java.awt.Cursor

@OptIn(
    ExperimentalUnitApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class
)
@Composable
fun BoxScope.detailsPanel(
    modifier: Modifier = Modifier,
    selectedRow: ModRow?
) {
    val row = selectedRow ?: return
    val allMods = SL.access.getMods(noCache = false)

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
                val dependencies = modVariant?.findDependencies(mods = allMods) ?: emptyList()
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
                        Text(
                            text = "Forum Thread",
                            modifier = Modifier.padding(top = 16.dp)
                                .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)))
                                .mouseClickable {
                                    if (this.buttons.isPrimaryPressed) {
                                        versionCheckerInfo.modThreadId!!.openModThread()
                                    }
                                },
                            color = Color.Cyan,
                            textDecoration = TextDecoration.Underline
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