package smol_app.browser

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.GridCells
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.pop
import javafx.embed.swing.JFXPanel
import javafx.scene.web.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mod_repo.ScrapedMod
import org.tinylog.kotlin.Logger
import smol_access.Constants
import smol_access.SL
import smol_access.config.Platform
import smol_app.AppState
import smol_app.SL_UI
import smol_app.browser.chromium.CefBrowserPanel
import smol_app.browser.chromium.ChromiumBrowser
import smol_app.browser.javafx.javaFxBrowser
import smol_app.composables.*
import smol_app.themes.SmolTheme
import smol_app.util.*
import timber.ktx.Timber
import java.awt.Cursor
import java.nio.file.Path
import java.util.*
import kotlin.io.path.absolutePathString
import kotlin.io.path.name

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
                        ) { mod -> modItem(mod, linkLoader) }
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
                                    var download: DownloadItem? = null
                                    override fun onStart(suggestedFileName: String?, totalBytes: Long) {
                                        download = DownloadItem(
                                            id = UUID.randomUUID().toString(),
                                            path = getDownloadPathFor(suggestedFileName),
                                            totalBytes = totalBytes
                                        )
                                        SL_UI.downloadManager.addDownload(download!!)
                                    }

                                    override fun onProgressUpdate(
                                        progressBytes: Long?,
                                        totalBytes: Long?,
                                        speedBps: Long?,
                                        endTime: Date
                                    ) {
                                        download?.let { download ->
                                            if (progressBytes != null) download.progress.tryEmit(progressBytes)
                                            if (download.status.value is DownloadItem.Status.NotStarted)
                                                scope.launch {
                                                    download.status.emit(DownloadItem.Status.Downloading)
                                                }
                                        }
                                    }

                                    override fun onCanceled() {
                                        download?.let { download ->
                                            scope.launch {
                                                download.status.emit(DownloadItem.Status.Failed(RuntimeException("Download canceled.")))
                                            }
                                        }
                                    }

                                    override fun onCompleted() {
                                        download?.let { download ->
                                            scope.launch {
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
private fun modItem(mod: ScrapedMod, linkLoader: ((String) -> Unit)?) {
    Card(
        modifier = Modifier
            .wrapContentHeight()
            .clickable {
                mod.forumPostLink?.run { linkLoader?.invoke(this.toString()) }
            },
        shape = SmolTheme.smolFullyClippedButtonShape()
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            Column(
                modifier = Modifier.align(Alignment.CenterVertically)
                    .weight(1f)
                    .padding(end = 16.dp)
            ) {
                Text(
                    modifier = Modifier,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    text = mod.name
                )
                Text(
                    modifier = Modifier.padding(top = 8.dp),
                    fontSize = 11.sp,
                    fontStyle = FontStyle.Italic,
                    text = mod.authors
                )
            }
            browserIcon(modifier = Modifier.align(Alignment.Top), mod = mod)
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
private fun browserIcon(modifier: Modifier = Modifier, mod: ScrapedMod) {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun downloadBar(
    modifier: Modifier = Modifier,
) {
    var downloads by remember { mutableStateOf<List<DownloadItem>>(listOf()) }

    rememberCoroutineScope { Dispatchers.Default }.launch {
        SL_UI.downloadManager.downloads.collect {
            withContext(Dispatchers.Main) {
                downloads = it
            }
        }
    }

    LazyRow(modifier) {
        items(downloads) { download ->
            downloadCard(download = download)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun downloadCard(modifier: Modifier = Modifier, download: DownloadItem) {
    var progressPercent: Float? by remember { mutableStateOf(null) }
    var progress by remember { mutableStateOf(0L) }
    val status = download.status.value
    val total = download.totalBytes

    SmolTooltipArea(
        modifier = modifier,
        tooltip = {
            SmolTooltipText(text = buildString {
                appendLine(download.path.absolutePathString())
                if (status is DownloadItem.Status.Failed) appendLine(status.error.message)
            })
        }
    ) {
        Card(
            modifier = Modifier,
            backgroundColor = MaterialTheme.colors.background
        ) {
            rememberCoroutineScope { Dispatchers.Default }.launch {
                download.progress.collect { newProgress ->
                    withContext(Dispatchers.Main) {
                        progress = newProgress
                        progressPercent = if (total != null)
                            (newProgress.toFloat() / total.toFloat())
                        else 0f
                    }
                }
            }
            Row(modifier = Modifier.padding(start = 8.dp, end = 8.dp)) {
                Column(
                    modifier = Modifier
                        .padding(start = 4.dp, top = 4.dp, bottom = 4.dp, end = 8.dp)
                        .mouseClickable {
                            kotlin.runCatching { download.path.parent.openInDesktop() }
                                .onFailure { Timber.e(it) }
                        }
                ) {
                    val progressMiB = progress.bytesAsReadableMiB
                    val totalMiB = total?.bytesAsReadableMiB
                    Text(
                        modifier = Modifier,
                        text = download.path.name,
                        fontSize = 12.sp
                    )
                    Text(
                        modifier = Modifier.padding(top = 4.dp),
                        text = when (status) {
                            is DownloadItem.Status.NotStarted -> "Starting"
                            is DownloadItem.Status.Downloading -> {
                                "$progressMiB${if (totalMiB != null) " / $totalMiB" else ""}}"
                            }
                            is DownloadItem.Status.Completed -> "Completed $progressMiB"
                            is DownloadItem.Status.Failed -> "Failed: ${status.error}"
                        },
                        fontSize = 12.sp
                    )
                }
                if (progressPercent != null || status == DownloadItem.Status.Completed) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp).align(Alignment.CenterVertically),
                        progress = progressPercent ?: 1f,
                        color = MaterialTheme.colors.onSurface
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp).align(Alignment.CenterVertically),
                        color = MaterialTheme.colors.onSurface
                    )
                }
            }
        }
    }
}