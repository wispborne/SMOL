package smol_app.browser

import AppState
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollbarAdapter
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.GridCells
import androidx.compose.foundation.lazy.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.mouseClickable
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mod_repo.ScrapedMod
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.HorizontalSplitPane
import org.jetbrains.compose.splitpane.rememberSplitPaneState
import org.tinylog.kotlin.Logger
import smol_access.Constants
import smol_access.SL
import smol_access.config.Platform
import smol_app.ModBrowserState
import smol_app.UI
import smol_app.browser.chromium.CefBrowserPanel
import smol_app.browser.chromium.ChromiumBrowser
import smol_app.composables.*
import smol_app.themes.SmolTheme
import smol_app.toolbar.*
import smol_app.util.*
import timber.ktx.Timber
import java.awt.Cursor
import java.nio.file.Path
import java.util.*

private val modListMinWidthDp = 600.dp

@OptIn(
    ExperimentalMaterialApi::class,
    ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class, ExperimentalSplitPaneApi::class
)
@Composable
@Preview
fun AppState.ModBrowserView(
    modifier: Modifier = Modifier,
    defaultUrl: String? = null
) {
    val indexMods = remember { mutableStateListOf(elements = SL.modRepo.getModIndexItems().toTypedArray()) }
    val moddingSubforumMods =
        remember { mutableStateListOf(elements = SL.modRepo.getModdingSubforumItems().toTypedArray()) }
    val shownIndexMods = remember { mutableStateListOf<ScrapedMod?>(elements = indexMods.toTypedArray()) }
    val shownModdingSubforumMods =
        remember { mutableStateListOf<ScrapedMod?>(elements = moddingSubforumMods.toTypedArray()) }

    val browser = remember { mutableStateOf<ChromiumBrowser?>(null) }
    val linkLoader = remember { mutableStateOf<((String) -> Unit)?>(null) }
    var alertDialogMessage: String? by remember { mutableStateOf(null) }
    val showLogPanel = remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var isBrowserFullscreen by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(modifier = Modifier.height(SmolTheme.topBarHeight)) {
                launchButton()
                installModsButton()
                Spacer(Modifier.width(16.dp))
                homeButton()
                screenTitle(text = "Mod Browser")
                profilesButton()
                settingsButton()

                SmolTooltipArea(
                    modifier = Modifier
                        .padding(start = 16.dp),
                    tooltip = {
                        SmolTooltipText(text = buildString {
                            appendLine("The Mod Browser lists mods scraped from the official forum, with permission.")
                            appendLine("The list is <b>not</b> live; it is fetched from an online cache, which is updated periodically so as to avoid excessive load on the forum.")
                            append("If a mod has been added to the forum but doesn't yet show up in the list, simply navigate to it using the browser and download it.")
                        }.parseHtml())
                    }) {
                    Icon(
                        painter = painterResource("icon-help-circled.svg"),
                        contentDescription = null,
                        modifier = Modifier
                            .width(24.dp)
                            .height(24.dp),
                        tint = SmolTheme.dimmedIconColor()
                    )
                }
                Spacer(Modifier.weight(1f))

                SmolTooltipArea(
                    modifier = Modifier
                        .padding(end = 8.dp),
                    tooltip = { SmolTooltipText(text = "Fetch latest mod cache.") }) {
                    IconButton(
                        modifier = Modifier,
                        onClick = {
                            coroutineScope.launch {
                                kotlin.runCatching { SL.modRepo.refreshFromInternet() }
                                    .onFailure { Timber.w(it) }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier
                                .width(24.dp)
                                .height(24.dp),
                            tint = SmolTheme.dimmedIconColor()
                        )
                    }
                }
                SmolTooltipArea(
                    modifier = Modifier
                        .padding(end = 8.dp),
                    tooltip = { SmolTooltipText(text = "Open in a browser") }) {
                    IconButton(
                        onClick = {
                            runCatching {
                                browser.value?.currentUrl?.openAsUriInBrowser()
                            }
                                .onFailure { Logger.warn(it) }
                        }
                    ) {
                        Icon(
                            painter = painterResource("web.svg"),
                            contentDescription = null,
                            modifier = Modifier
                                .width(24.dp)
                                .height(24.dp)
                                .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)))
                        )
                    }
                }
                SmolTooltipArea(
                    modifier = Modifier,
                    tooltip = { SmolTooltipText(text = "Toggle full-width browser") }) {
                    IconButton(
                        onClick = {
                            isBrowserFullscreen = !isBrowserFullscreen
                        }
                    ) {
                        Icon(
                            painter = painterResource("icon-maximize.svg"),
                            contentDescription = null,
                            modifier = Modifier
                                .width(24.dp)
                                .height(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
            }
        }, content = {
            Column(Modifier.padding(bottom = SmolTheme.bottomBarHeight - 16.dp)) {
                Row(modifier = Modifier.padding(start = 16.dp, bottom = 16.dp, top = 8.dp)) {
                    val splitterState = rememberSplitPaneState(
                        initialPositionPercentage = SL.UI.uiConfig.modBrowserState?.modListWidthPercent ?: 0f
                    )
                    HorizontalSplitPane(splitPaneState = splitterState) {
                        if (!isBrowserFullscreen) {
                            first(modListMinWidthDp) {
                                LaunchedEffect(splitterState.positionPercentage) {
                                    // Update config file on recompose
                                    SL.UI.uiConfig.modBrowserState =
                                        SL.UI.uiConfig.modBrowserState?.copy(modListWidthPercent = splitterState.positionPercentage)
                                            ?: ModBrowserState(modListWidthPercent = splitterState.positionPercentage)
                                }
                                Column {
                                    Row {
                                        smolSearchField(
                                            modifier = Modifier
                                                .focusRequester(searchFocusRequester())
                                                .widthIn(max = 320.dp)
                                                .padding(bottom = 16.dp, end = 16.dp),
                                            tooltipText = "Hotkey: Ctrl-F",
                                            label = "Filter"
                                        ) { query ->
                                            if (query.isBlank()) {
                                                shownIndexMods.replaceAllUsingDifference(
                                                    indexMods,
                                                    doesOrderMatter = false
                                                )
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
                                        Spacer(Modifier.weight(1f))
                                        SmolSecondaryButton(
                                            modifier = Modifier.padding(start = 8.dp).align(Alignment.CenterVertically),
                                            onClick = { linkLoader.value?.invoke(Constants.FORUM_MOD_INDEX_URL) }
                                        ) { Text("Index") }
                                        SmolSecondaryButton(
                                            modifier = Modifier.padding(start = 8.dp).align(Alignment.CenterVertically),
                                            onClick = { linkLoader.value?.invoke(Constants.FORUM_MODDING_SUBFORUM_URL) }
                                        ) { Text("Modding") }
                                        IconButton(
                                            modifier = Modifier.padding(start = 8.dp).align(Alignment.CenterVertically),
                                            onClick = { browser.value?.goBack() }
                                        ) { Icon(imageVector = Icons.Default.ArrowBack, contentDescription = null) }
                                        IconButton(
                                            modifier = Modifier.padding(start = 8.dp).align(Alignment.CenterVertically),
                                            onClick = { browser.value?.goForward() }
                                        ) { Icon(imageVector = Icons.Default.ArrowForward, contentDescription = null) }
                                    }

                                    val scrollState = rememberLazyListState()
                                    Row {
                                        LazyVerticalGrid(
                                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(16.dp),
                                            cells = GridCells.Adaptive(200.dp),
                                            state = scrollState,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            this.items(
                                                items = (shownIndexMods + shownModdingSubforumMods)
                                                    .filterNotNull()
                                                    .sortedWith(compareByDescending { it.name })
                                            ) { mod -> scrapedModCard(mod, linkLoader) }
                                        }
                                        VerticalScrollbar(
                                            adapter = ScrollbarAdapter(scrollState),
                                            modifier = Modifier.padding(start = 4.dp, end = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                        second {
                            embeddedBrowser(browser, linkLoader, defaultUrl ?: Constants.FORUM_MOD_INDEX_URL)
                        }
                        horizontalSplitter()
                    }
                }
            }

            if (showLogPanel.value) {
                logPanel { showLogPanel.value = false }
            }
        },
        bottomBar = {
            BottomAppBar(
                modifier = Modifier.fillMaxWidth()
            ) {
                logButtonAndErrorDisplay(showLogPanel = showLogPanel)
            }
        }
    )

    if (alertDialogMessage != null) {
        SmolAlertDialog(
            onDismissRequest = { alertDialogMessage = null },
            text = { Text(text = alertDialogMessage ?: "", style = SmolTheme.alertDialogBody()) }
        )
    }

    LaunchedEffect(defaultUrl) {
        if (defaultUrl != null) {
            Timber.i { "Loading Mod Browser with default url $defaultUrl." }
            browser.value?.loadUrl(defaultUrl)
        }
    }
}

@Composable
private fun AppState.embeddedBrowser(
    browser: MutableState<ChromiumBrowser?>,
    linkLoader: MutableState<((String) -> Unit)?>,
    startUrl: String
) {
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
                    startURL = startUrl,
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
                                            download.progressBytes.emit(progressBytes)
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
                                            download.progressBytes.emit(download.totalBytes.value ?: 0)
                                        download.status.emit(DownloadItem.Status.Completed)
                                    }

                                    if (download.path.value != null) {
                                        GlobalScope.launch(Dispatchers.IO) {
                                            SL.access.installFromUnknownSource(
                                                inputFile = download.path.value!!,
                                                shouldCompressModFolder = true
                                            )
                                        }
                                    }
                                }
                        }

                        override fun getDownloadPathFor(filename: String?): Path =
                            Constants.TEMP_DIR.resolve(
                                filename?.ifEmpty { null } ?: UUID.randomUUID().toString())
                    }
                )
                    .also { browserPanel ->
                        browser.value = browserPanel
                        linkLoader.value = { url ->
                            browserPanel.loadUrl(url)
                        }
                    }
            }
        )
    } else {
//        javaFxBrowser(jfxpanel, background, linkLoader)
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