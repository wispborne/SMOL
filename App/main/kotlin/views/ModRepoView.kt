package views

import AppState
import SmolButton
import SmolTheme
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.GridCells
import androidx.compose.foundation.lazy.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.mouseClickable
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.pop
import org.tinylog.kotlin.Logger
import smol_access.SL
import util.openAsUriInBrowser
import java.awt.Cursor

@OptIn(
    ExperimentalMaterialApi::class,
    ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class
)
@Composable
@Preview
fun AppState.ModBrowserView(
    modifier: Modifier = Modifier
) {
    val indexMods by remember { mutableStateOf(SL.modRepo.getModIndexItems()) }
    val moddingSubforumMods by remember { mutableStateOf(SL.modRepo.getModdingSubforumItems()) }

    Scaffold(topBar = {
        TopAppBar {
            SmolButton(onClick = router::pop, modifier = Modifier.padding(start = 16.dp)) {
                Text("Back")
            }
            Text(
                modifier = Modifier.padding(8.dp).padding(start = 16.dp),
                text = "Mod Browser",
                fontWeight = FontWeight.Bold
            )
        }
    }) {
        Box(modifier.padding(16.dp)) {
            LazyVerticalGrid(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                cells = GridCells.Adaptive(200.dp)
            ) {
                for ((name, mods) in listOf("Index" to indexMods, "Modding Forum" to moddingSubforumMods)) {
                    this.item {
                        Text(
                            modifier = Modifier.fillMaxWidth(),
                            text = name,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    this.items(
                        items = mods
                            .sortedWith(
                                compareByDescending { it.name })
                    ) { mod ->
                        Card(
                            modifier = Modifier.wrapContentHeight(),
                            shape = SmolTheme.smolFullyClippedButtonShape()
                        ) {
                            SelectionContainer {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row {
                                        Text(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(end = 16.dp)
                                                .align(Alignment.CenterVertically),
                                            fontWeight = FontWeight.Bold,
                                            text = mod.name
                                        )
                                    }
                                    if (mod.forumPostLink?.toString()?.isBlank() == false) {
                                        Row {
                                            DisableSelection {
                                                Text(
                                                    text = "Forum Thread",
                                                    modifier = Modifier.padding(top = 8.dp)
                                                        .align(Alignment.CenterVertically)
                                                        .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)))
                                                        .mouseClickable {
                                                            if (this.buttons.isPrimaryPressed) {
                                                                kotlin.runCatching {
                                                                    mod.forumPostLink?.toString()?.openAsUriInBrowser()
                                                                }
                                                                    .onFailure { Logger.warn(it) }
                                                            }
                                                        },
                                                    color = Color.Cyan,
                                                    textDecoration = TextDecoration.Underline
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}