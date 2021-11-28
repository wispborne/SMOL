package smol_app.views

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
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
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.pop
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.Group
import javafx.scene.Scene
import javafx.scene.paint.Color
import javafx.scene.web.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mod_repo.ScrapedMod
import org.tinylog.kotlin.Logger
import smol_access.Constants
import smol_access.SL
import smol_access.business.DownloadItem
import smol_app.*
import smol_app.browser.ForumWebpageModifier
import smol_app.browser.javaFXPanel
import smol_app.util.bytesAsReadableMiB
import smol_app.util.openAsUriInBrowser
import smol_app.util.openInDesktop
import timber.ktx.Timber
import java.awt.Cursor
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlin.io.path.absolutePathString
import kotlin.io.path.name


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
            downloadBar(modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
            Spacer(modifier = Modifier.width(16.dp))
        }
    }) {
        Column(modifier) {
            Row(modifier = Modifier.padding(16.dp)) {
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
                        ) { mod -> modItem(mod, linkLoader) }
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
                    panel = jfxpanel
                ) {
                    val root = Group()
                    val scene = Scene(
                        root,
                        Color.rgb(background.red.toInt(), background.green.toInt(), background.blue.toInt())
                    )
                    WebView().apply {
                        jfxpanel.scene = scene

                        this.engine.apply {
                            isJavaScriptEnabled = true
                            locationProperty().addListener { _, oldLoc, newLoc ->
                                // check to see if newLoc is downloadable.
                                scope.launch {
                                    SL.downloadManager.download(url = newLoc)
                                }
                            }
                            loadWorker.exceptionProperty().addListener { _, _, throwable ->
                                Timber.i(throwable)
                            }
                            setOnError {
                                Timber.d { it.message }
                            }
                        }

                        prefWidth = jfxpanel.width.toDouble()
                        prefHeight = jfxpanel.height.toDouble()

                        linkLoader = { url ->
                            Platform.runLater {
                                this.engine.loadContent(
                                    (getData(url) ?: "")
                                        .let { html ->
                                            if (URI.create(url).host.equals(
                                                    Constants.FORUM_HOSTNAME,
                                                    ignoreCase = true
                                                )
                                            ) {
                                                ForumWebpageModifier.filterToFirstPost(html)
                                            } else {
                                                html
                                            }
                                        }
                                )
                            }
                        }

                        linkLoader?.invoke(Constants.FORUM_MOD_INDEX_URL)
                        root.children.add(this)
                    }
                }
            }
        }

        if (alertDialogMessage != null) {
            SmolAlertDialog(
                onDismissRequest = { alertDialogMessage = null },
                text = { Text(text = alertDialogMessage ?: "") }
            )
        }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun downloadBar(
    modifier: Modifier = Modifier,
) {
    var downloads by remember { mutableStateOf<List<DownloadItem>>(listOf()) }

    rememberCoroutineScope { Dispatchers.Default }.launch {
        SL.downloadManager.downloads.collect {
            withContext(Dispatchers.Main) {
                downloads = it
            }
        }
    }

    LazyRow(modifier) {
        items(downloads) { download ->
            var progressPercent: Float? by remember { mutableStateOf(null) }
            var progress by remember { mutableStateOf(0L) }
            val status = download.status.value
            val total = download.total

            TooltipArea(
                tooltip = {
                    SmolTooltipText(text = buildString {
                        appendLine(download.path.absolutePathString())
                        if (status is DownloadItem.Status.Failed) appendLine(status.error.message)
                    })
                }
            ) {
                Card(
                    modifier = Modifier.padding(start = 16.dp),
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
    }
}