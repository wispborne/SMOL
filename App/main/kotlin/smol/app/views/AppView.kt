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

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Typography
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.jetbrains.Children
import com.arkivanov.decompose.extensions.compose.jetbrains.animation.child.crossfade
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.tinylog.Logger
import smol.access.Constants
import smol.access.SL
import smol.access.business.KWatchEvent
import smol.access.business.asWatchChannel
import smol.app.IWindowState
import smol.app.UI
import smol.app.WindowState
import smol.app.about.AboutView
import smol.app.browser.DownloadItem
import smol.app.browser.ModBrowserView
import smol.app.home.homeView
import smol.app.modprofiles.ModProfilesView
import smol.app.navigation.Screen
import smol.app.settings.settingsView
import smol.app.themes.SmolTheme
import smol.app.themes.SmolTheme.toColors
import smol.app.toasts.DownloadToast
import smol.app.toasts.ToastContainer
import smol.app.toasts.toastInstalledCard
import smol.app.toasts.toaster
import smol.app.updater.UpdateSmolToast
import smol.app.views.DuplicateModAlertDialog
import smol.app.views.DuplicateModAlertDialogState
import smol.app.views.FileDropper
import smol.timber.ktx.Timber
import smol.updatestager.UpdateChannelManager
import smol.utilities.IOLock
import smol.utilities.exists
import smol.utilities.isAny
import kotlin.io.path.exists
import kotlin.system.exitProcess

@OptIn(ExperimentalStdlibApi::class, ExperimentalDecomposeApi::class)
@Composable
@Preview
fun WindowState.appView() {
    val theme = SL.themeManager.activeTheme.collectAsState()

    var alertDialogBuilder: @Composable ((dismiss: () -> Unit) -> Unit)? by remember { mutableStateOf(null) }
    val recomposer = currentRecomposeScope
    val appScope by remember {
        mutableStateOf(AppScope(windowState = this, recomposer = recomposer)
            .apply {
                this.alertDialogSetter = { alertDialogBuilder = it }
            })
    }

    LaunchedEffect("runonce") {
        delay(5000) // Doesn't need to contribute to startup time.
        checkForUpdates()
    }

    MaterialTheme(
        colors = theme.value.second.toColors(),
        typography = Typography(
            button = TextStyle(fontFamily = SmolTheme.orbitronSpaceFont)
        )
    ) {
        val scope = rememberCoroutineScope()
        setUpToasts()

        Box(Modifier.background(MaterialTheme.colors.background)) {
            Children(router.state, animation = crossfade()) { screen ->
                Box {
                    val configuration = screen.configuration

                    if (router.state.value.activeChild.configuration::class == configuration::class) {
                        Timber.d { "Skipping recreation of active screen." }
                    }

                    when (configuration) {
                        is Screen.Home -> appScope.homeView()
                        is Screen.Settings -> appScope.settingsView()
                        is Screen.Profiles -> appScope.ModProfilesView()
                        is Screen.ModBrowser -> appScope.ModBrowserView(defaultUrl = configuration.defaultUri)
                        is Screen.About -> appScope.AboutView()
                    }.run { }
                }
            }

            if (SL.gamePathManager.path.value.exists()) {
                appScope.FileDropper()
            }

            // Toasts
            toaster(
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 64.dp, bottom = 8.dp, end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            )

            val refreshTrigger by remember { mutableStateOf(Unit) }

            LaunchedEffect(refreshTrigger) {
                scope.launch {
                    Timber.i { "Initial mod refresh." }
                    reloadModsInner(forceRefreshVersionChecker = true, forceRefreshSaves = true)
                }
            }

            DisposableEffect(Unit) {
                val handle = scope.launch {
                    watchDirsAndReloadOnChange(scope)
                }
                onDispose { handle.cancel() }
            }

            if (alertDialogBuilder != null) {
                alertDialogBuilder?.invoke { appScope.alertDialogSetter(null) }
            }

            val duplicateModAlertDialogData = appScope.duplicateModAlertDialogState.currentData

            if (duplicateModAlertDialogData != null) {
                DuplicateModAlertDialog(duplicateModAlertDialogData.modInfo, duplicateModAlertDialogData.continuation)
            }
        }
    }
}

private suspend fun checkForUpdates() {
    withContext(Dispatchers.IO) {
        val updateChannel = UpdateChannelManager.getUpdateChannelSetting(SL.appConfig)
        val remoteConfigAsync =
            async { kotlin.runCatching { SL.UI.smolUpdater.fetchRemoteConfig(updateChannel) }.getOrNull() }
        val updaterConfigAsync =
            async { kotlin.runCatching { SL.UI.updaterUpdater.fetchRemoteConfig(updateChannel) }.getOrNull() }

        val updaterConfig = updaterConfigAsync.await()

        if (updaterConfig != null && updaterConfig.requiresUpdate()) {
            Timber.i { "Found update for the SMOL updater." }
            UpdateSmolToast().updateUpdateToast(
                updateConfig = updaterConfig,
                toasterState = SL.UI.toaster,
                smolUpdater = SL.UI.updaterUpdater
            ) {
                GlobalScope.launch {
                    checkForUpdates()
                }
            }
        } else {
            val remoteConfig = remoteConfigAsync.await()
            if (remoteConfig == null) {
                Timber.w { "Unable to fetch remote config, aborting update check." }
            } else {
                UpdateSmolToast().updateUpdateToast(
                    updateConfig = remoteConfig,
                    toasterState = SL.UI.toaster,
                    smolUpdater = SL.UI.smolUpdater
                ) {
                    GlobalScope.launch {
                        delay(400) // Time to log an error if there was one
                        exitProcess(status = 0)
                    }
                }
            }
        }

    }
}

@Composable
private fun setUpToasts() {
    // Uncomment to clear out completed downloads after 10s.
//                downloads.map { it to it.status.collectAsState() }
//                    .filter { (_, status) -> status.value is DownloadItem.Status.Completed }
//                    .forEach { (item, _) ->
//                        if (!toasterState.timersByToastId.containsKey(item.id)) {
//                            toasterState.timersByToastId[item.id] = Toaster.defaultTimeoutMillis
//                        }
//                    }
    LaunchedEffect(1) {
        SL.access.mods.collect { modListUpdate ->
            val addedModVariants = modListUpdate?.added ?: return@collect

            addedModVariants
                .forEach { newModVariant ->
                    Timber.i { "Found new mod ${newModVariant.modInfo.id} ${newModVariant.modInfo.version}." }
                    val id = "new-mod-" + newModVariant.smolId
                    SL.UI.toaster.addItem(ToastContainer(
                        id = id,
                        timeoutMillis = null,
                        useStandardToastFrame = true
                    ) {
                        toastInstalledCard(
                            modVariant = newModVariant,
                            requestToastDismissal = { delayMillis ->
                                SL.UI.toaster.setTimeout(id, delayMillis)
                            }
                        )
                    })
                }
        }

    }

    LaunchedEffect(1) {
        val items = SL.UI.toaster.items
        SL.UI.downloadManager.downloads
            .filter { it.id !in items.value.map { it.id } }
            .filter {
                !it.status.value.isAny(
                    DownloadItem.Status.Completed::class,
                    DownloadItem.Status.Cancelled::class,
                    DownloadItem.Status.Failed::class
                )
            }
            .map {
                val toastId = "download-${it.id}"
                ToastContainer(id = toastId, timeoutMillis = null, useStandardToastFrame = true) {
                    DownloadToast(
                        download = it,
                        requestToastDismissal = { delayMillis ->
                            SL.UI.toaster.setTimeout(toastId, delayMillis)
                        }
                    )
                }
            }
            .also {
                SL.UI.toaster.addItems(it)
            }
    }
}

class AppScope(windowState: WindowState, private val recomposer: RecomposeScope) : IWindowState by windowState {

    /**
     * Usage: alertDialogSetter.invoke { AlertDialog(...) }
     */
    lateinit var alertDialogSetter: (@Composable ((dismiss: () -> Unit) -> Unit)?) -> Unit
    fun dismissAlertDialog() = alertDialogSetter.invoke(null)
    val duplicateModAlertDialogState = DuplicateModAlertDialogState()

    suspend fun forceReloadMods() = reloadModsInner(forceRefreshVersionChecker = true, forceRefreshSaves = true)
    fun recomposeAppUI() = recomposer.invalidate()
}

private suspend fun watchDirsAndReloadOnChange(scope: CoroutineScope) {
    val NSA: List<Flow<KWatchEvent?>> = listOfNotNull(
        SL.gamePathManager.getModsPath()?.asWatchChannel(scope = scope),
        SL.gamePathManager.getModsPath()?.resolve(Constants.ENABLED_MODS_FILENAME) // Watch enabled_mods.json
            .run { if (this?.exists() == true) this.asWatchChannel(scope = scope) else emptyFlow() },
    )
    Timber.i {
        "Started watching folders ${
            NSA.joinToString()
        }"
    }
    withContext(Dispatchers.Default) {
        NSA
            .plus(SL.manualReloadTrigger.trigger.map { null })
            .merge()
            .collectLatest {
                if (!IOLock.stateFlow.value) {
                    if (it?.kind == KWatchEvent.Kind.Initialized)
                        return@collectLatest
                    // Short delay so that if a new file change comes in during this time,
                    // this is canceled in favor of the new change. This should prevent
                    // refreshing 500 times if 500 files are changed in a few millis.
                    delay(500)
                    Timber.i { "Reloading due to file change: $it" }
                    reloadModsInner(forceRefreshVersionChecker = false, forceRefreshSaves = false)
                } else {
                    Timber.i { "Skipping mod reload while IO locked." }
                }
            }
    }
}

private suspend fun reloadModsInner(forceRefreshVersionChecker: Boolean, forceRefreshSaves: Boolean) {
    if (SL.access.areModsLoading.value) {
        Timber.i { "Skipping reload of mods as they are currently refreshing already." }
        return
    }
    try {
        coroutineScope {
            smol.utilities.trace(onFinished = { _, millis -> Timber.i { "Finished reloading mods+VC+saves in ${millis}ms." } }) {
                Timber.i { "Reloading all mods." }
                val previousVariantIds = SL.access.mods.value?.mods.orEmpty().flatMap { it.variants }.map { it.smolId }
                SL.access.reload()
                val mods = SL.access.mods.value?.mods ?: emptyList()
                val newlyAddedModIds = SL.access.mods.value?.mods.orEmpty()
                    .flatMap { it.variants }
                    .filter { newVariant -> newVariant.smolId !in previousVariantIds }
                    .map { it.modInfo.id }
                    .distinct()

                listOf(
                    async {
                        SL.versionChecker.lookUpVersions(
                            forceLookup = forceRefreshVersionChecker || newlyAddedModIds.any(),
                            mods = mods.filter { it.id in newlyAddedModIds }
                        )
                    },
                    async {
                        kotlin.runCatching { SL.saveReader.readAllSaves(forceRefresh = forceRefreshSaves) }
                            .onFailure { Timber.w(it) }
                    }
                ).awaitAll()
            }
        }
    } catch (e: Exception) {
        Logger.debug(e)
    }
}