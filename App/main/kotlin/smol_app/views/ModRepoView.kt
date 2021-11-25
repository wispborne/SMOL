package smol_app.views

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.GridCells
import androidx.compose.foundation.lazy.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.mouseClickable
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.pop
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.Group
import javafx.scene.Scene
import javafx.scene.paint.Color
import javafx.scene.web.WebView
import org.tinylog.kotlin.Logger
import smol_access.SL
import smol_app.AppState
import smol_app.SmolButton
import smol_app.SmolTheme
import smol_app.SmolTooltipText
import smol_app.browser.ForumWebpageModifier
import smol_app.browser.javaFXPanel
import smol_app.util.openAsUriInBrowser
import timber.ktx.Timber
import java.awt.Cursor
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL


@OptIn(
    ExperimentalMaterialApi::class,
    ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class
)
@Composable
@Preview
fun AppState.ModBrowserView(
    modifier: Modifier = Modifier
) {
    val jfxpanel: JFXPanel = remember { JFXPanel() }
    val indexMods by remember { mutableStateOf(SL.modRepo.getModIndexItems()) }
    val moddingSubforumMods by remember { mutableStateOf(SL.modRepo.getModdingSubforumItems()) }
    var linkLoader: ((String) -> Unit)? by remember { mutableStateOf(null) }

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
        Row(modifier.padding(16.dp)) {
            LazyVerticalGrid(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                cells = GridCells.Adaptive(200.dp),
                modifier = Modifier.widthIn(min = 200.dp, max = 600.dp)
            ) {
                for ((name, mods) in listOf("Index" to indexMods, "Modding Forum" to moddingSubforumMods)) {
                    this.item {
                        Box(modifier = Modifier.fillMaxHeight().fillMaxWidth().align(Alignment.CenterVertically)) {
                            Text(
                                modifier = Modifier.align(Alignment.Center),
                                text = name,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    this.items(
                        items = mods
                            .sortedWith(
                                compareByDescending { it.name })
                    ) { mod ->
                        Card(
                            modifier = Modifier
                                .wrapContentHeight()
                                .clickable {
                                    mod.forumPostLink?.run { linkLoader?.invoke(this.toString()) }
                                },
                            shape = SmolTheme.smolFullyClippedButtonShape()
                        ) {
                            Row(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 16.dp)
                                        .align(Alignment.CenterVertically),
                                    fontWeight = FontWeight.Bold,
                                    text = mod.name
                                )
                                if (mod.forumPostLink?.toString()?.isBlank() == false) {
                                    val descText = "Open Forum Thread"
                                    TooltipArea(tooltip = { SmolTooltipText(text = descText) }) {
                                        Icon(
                                            painter = painterResource("open-in-new.svg"),
                                            contentDescription = descText,
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
                                            tint = SmolTheme.dimmedIconColor()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            val background = MaterialTheme.colors.background

            javaFXPanel(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .sizeIn(minWidth = 200.dp),
                root = window,
                panel = jfxpanel,
                onCreate = {
                    val root = Group()
                    val scene = Scene(
                        root,
                        Color.rgb(background.red.toInt(), background.green.toInt(), background.blue.toInt())
                    )
                    WebView().apply {
                        jfxpanel.scene = scene
                        this.engine.isJavaScriptEnabled = true
                        root.children.add(this)
                        prefWidth = jfxpanel.width.toDouble()
                        prefHeight = jfxpanel.height.toDouble()

                        linkLoader = {
                            Platform.runLater {
                                this.engine.loadContent(
                                    ForumWebpageModifier.filterToFirstPost(
                                        forumHtml = getData(it) ?: ""
                                    )
                                )
                            }
                        }
                    }
                }
            )
        }
    }
}

@Throws(Exception::class)
private fun getData(address: String): String? =
    runCatching {
        (URL(address).openConnection() as HttpURLConnection).let { conn ->
            try {
                conn.connect()
                InputStreamReader(conn.content as InputStream)
                InputStreamReader(
                    conn.content as InputStream
                ).use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }
    }
        .onFailure { Timber.w(it) }
        .getOrNull()
