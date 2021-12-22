package smol_app.browser

import AppState
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.pop
import javafx.embed.swing.JFXPanel
import javafx.scene.web.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mod_repo.ScrapedMod
import org.tinylog.kotlin.Logger
import smol_access.Constants
import smol_access.SL
import smol_access.config.Platform
import smol_app.UI
import smol_app.browser.chromium.CefBrowserPanel
import smol_app.browser.chromium.ChromiumBrowser
import smol_app.browser.javafx.javaFxBrowser
import smol_app.composables.*
import smol_app.themes.SmolTheme
import smol_app.util.currentPlatform
import smol_app.util.filterModPosts
import smol_app.util.openAsUriInBrowser
import smol_app.util.replaceAllUsingDifference
import timber.ktx.Timber
import java.awt.Cursor
import java.nio.file.Path
import java.util.*

object WebViewHolder {
    var webView: WebView? = null
}

@OptIn(
    ExperimentalMaterialApi::class,
    ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class
)
@Composable
@Preview
fun AppState.ModBrowserView(
    modifier: Modifier = Modifier
) {
    val indexMods = remember { mutableStateListOf(elements = SL.modRepo.getModIndexItems().toTypedArray()) }
    val moddingSubforumMods =
        remember { mutableStateListOf(elements = SL.modRepo.getModdingSubforumItems().toTypedArray()) }
    val shownIndexMods = remember { mutableStateListOf<ScrapedMod?>(elements = indexMods.toTypedArray()) }
    val shownModdingSubforumMods =
        remember { mutableStateListOf<ScrapedMod?>(elements = moddingSubforumMods.toTypedArray()) }

    val jfxpanel: JFXPanel = remember { JFXPanel() }
    var browser: ChromiumBrowser? by remember { mutableStateOf(null) }
    var linkLoader: ((String) -> Unit)? by remember { mutableStateOf(null) }
    val scope = rememberCoroutineScope { Dispatchers.Default }
    var alertDialogMessage: String? by remember { mutableStateOf(null) }

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
            Spacer(Modifier.weight(1f))
//            downloadBar(modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
            Spacer(modifier = Modifier.width(16.dp))
        }
    }, content = {
        Column(modifier.padding(bottom = SmolTheme.bottomBarHeight - 16.dp)) {
            Row(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 8.dp)) {
                Column {
                    smolSearchField(
                        modifier = Modifier
                            .focusRequester(searchFocusRequester())
                            .widthIn(max = 320.dp)
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 16.dp),
                        tooltipText = "Hotkey: Ctrl-F",
                        label = "Filter"
                    ) { query ->
                        if (query.isBlank()) {
                            shownIndexMods.replaceAllUsingDifference(indexMods, doesOrderMatter = false)
                            shownModdingSubforumMods.replaceAllUsingDifference(
                                moddingSubforumMods,
                                doesOrderMatter = false
                            )
                        } else {
                            shownIndexMods.replaceAllUsingDifference(
                                filterModPosts(query, indexMods).ifEmpty { listOf(null) },
                                doesOrderMatter = true
                            )
                            shownModdingSubforumMods.replaceAllUsingDifference(
                                filterModPosts(query, moddingSubforumMods).ifEmpty { listOf(null) },
                                doesOrderMatter = true
                            )
                        }
                    }

                    LazyVerticalGrid(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        cells = GridCells.Adaptive(200.dp),
                        modifier = Modifier.widthIn(min = 200.dp, max = 600.dp)
                    ) {
                        this.items(
                            items = (shownIndexMods + shownModdingSubforumMods)
                                .filterNotNull()
                                .sortedWith(compareByDescending { it.name })
                        ) { mod -> scrapedModCard(mod, linkLoader) }
                    }
                }

                val background = MaterialTheme.colors.background

                val useCEF = true

                if (useCEF) {
                    SwingPanel(
                        background = MaterialTheme.colors.background,
                        modifier = Modifier
                            .padding(start = 16.dp)
                            .fillMaxHeight()
                            .fillMaxWidth()
                            .sizeIn(minWidth = 200.dp),
                        factory = {
                            CefBrowserPanel(
                                startURL = Constants.FORUM_MOD_INDEX_URL,
                                useOSR = Platform.Linux == currentPlatform,
                                isTransparent = false,
                                downloadHandler = object : DownloadHander {

                                    override fun onStart(
                                        itemId: String,
                                        suggestedFileName: String?,
                                        totalBytes: Long
                                    ) {
                                        val item = DownloadItem(
                                            id = itemId
                                        )
                                            .apply {
                                                this.path.value = getDownloadPathFor(suggestedFileName)
                                                this.totalBytes.value = totalBytes
                                            }
                                        SL.UI.downloadManager.addDownload(item)
                                    }

                                    override fun onProgressUpdate(
                                        itemId: String,
                                        progressBytes: Long?,
                                        totalBytes: Long?,
                                        speedBps: Long?,
                                        endTime: Date
                                    ) {
                                        SL.UI.downloadManager.downloads.value.firstOrNull { it.id == itemId }
                                            ?.let { download ->
                                                Timber.d { "" }
                                                runBlocking {
                                                    if (progressBytes != null)
                                                        download.progress.emit(progressBytes)
                                                    if (speedBps != null)
                                                        download.bitsPerSecond.emit(speedBps)
                                                }
                                                if (download.status.value is DownloadItem.Status.NotStarted)
                                                    runBlocking {
                                                        download.status.emit(DownloadItem.Status.Downloading)
                                                    }
                                            }
                                    }

                                    override fun onCanceled(itemId: String) {
                                        SL.UI.downloadManager.downloads.value.firstOrNull { it.id == itemId }
                                            ?.let { download ->
                                                runBlocking {
                                                    download.status.emit(
                                                        DownloadItem.Status.Failed(
                                                            RuntimeException(
                                                                "Download canceled."
                                                            )
                                                        )
                                                    )
                                                }
                                            }
                                    }

                                    override fun onCompleted(itemId: String) {
                                        SL.UI.downloadManager.downloads.value.firstOrNull { it.id == itemId }
                                            ?.let { download ->
                                                runBlocking {
                                                    if (download.totalBytes.value != null)
                                                        download.progress.emit(download.totalBytes.value ?: 0)
                                                    download.status.emit(DownloadItem.Status.Completed)
                                                }
                                            }
                                    }

                                    override fun getDownloadPathFor(filename: String?): Path =
                                        Constants.TEMP_DIR.resolve(
                                            filename?.ifEmpty { null } ?: UUID.randomUUID().toString())
                                }
                            )
                                .also { browserPanel ->
                                    browser = browserPanel
                                    linkLoader = { url ->
                                        browserPanel.loadUrl(url)
                                    }
                                }
                        }
                    )
                } else {
                    javaFxBrowser(jfxpanel, background, linkLoader)
                }
            }
        }
    },
        bottomBar = {
            BottomAppBar(
                modifier = Modifier.fillMaxWidth()
            ) {

            }
        }
    )

    if (alertDialogMessage != null) {
        SmolAlertDialog(
            onDismissRequest = { alertDialogMessage = null },
            text = { Text(text = alertDialogMessage ?: "") }
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun browserIcon(modifier: Modifier = Modifier, mod: ScrapedMod) {
    if (mod.forumPostLink?.toString()?.isBlank() == false) {
        val descText = "Open in a browser"
        SmolTooltipArea(
            modifier = modifier,
            tooltip = { SmolTooltipText(text = descText) }) {
            Icon(
                painter = painterResource("web.svg"),
                contentDescription = descText,
                modifier = Modifier
                    .width(16.dp)
                    .height(16.dp)
                    .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)))
                    .mouseClickable {
                        if (this.buttons.isPrimaryPressed) {
                            runCatching {
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

//@OptIn(ExperimentalFoundationApi::class)
//@Composable
//fun downloadBar(
//    modifier: Modifier = Modifier,
//) {
//    val downloads = SL.UI.downloadManager.downloads.collectAsState().value
//
//    LazyRow(modifier) {
//        items(downloads) { download ->
//            downloadCard(
//                download = download,
//                requestToastDismissal = {}
//            )
//        }
//    }
//}