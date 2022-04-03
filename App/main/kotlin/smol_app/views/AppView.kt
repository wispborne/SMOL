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
import smol_access.Constants
import smol_access.SL
import smol_access.business.KWatchEvent
import smol_access.business.asWatchChannel
import smol_app.IWindowState
import smol_app.UI
import smol_app.WindowState
import smol_app.about.AboutView
import smol_app.browser.ModBrowserView
import smol_app.home.homeView
import smol_app.modprofiles.ModProfilesView
import smol_app.navigation.Screen
import smol_app.settings.settingsView
import smol_app.themes.SmolTheme
import smol_app.themes.SmolTheme.toColors
import smol_app.toasts.Toast
import smol_app.toasts.downloadToast
import smol_app.toasts.toastInstalledCard
import smol_app.toasts.toaster
import smol_app.updater.UpdateSmolToast
import smol_app.views.FileDropper
import timber.ktx.Timber
import updatestager.BaseAppUpdater
import utilities.IOLock
import utilities.exists
import kotlin.io.path.exists

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
        withContext(Dispatchers.IO) {
//            UpdateApp.writeLocalUpdateConfig(
//                onlineUrl = SL.UI.updater.getUpdateConfigUrl(),
//                localPath = Path.of("dist\\main\\app\\SMOL")
//            )
            val updateChannel = BaseAppUpdater.getUpdateChannelSetting(SL.appConfig)
            val remoteConfigAsync =
                async { kotlin.runCatching { SL.UI.smolUpdater.fetchRemoteConfig(updateChannel) }.getOrNull() }
            val updaterConfigAsync =
                async { kotlin.runCatching { SL.UI.updaterUpdater.fetchRemoteConfig(updateChannel) }.getOrNull() }

            val remoteConfig = remoteConfigAsync.await()
            if (remoteConfig == null) {
                Timber.w { "Unable to fetch remote config, aborting update check." }
            } else {
                UpdateSmolToast().updateUpdateToast(
                    updateConfig = remoteConfig,
                    toasterState = SL.UI.toaster,
                    smolUpdater = SL.UI.smolUpdater
                )
            }

            val updaterConfig = updaterConfigAsync.await()

            if (updaterConfig != null && updaterConfig.requiresUpdate()) {
                Timber.i { "Found update for the SMOL updater, updating it in the background." }
                kotlin.runCatching {
                    SL.UI.updaterUpdater.downloadUpdateZip(updaterConfig)
                    SL.UI.updaterUpdater.installUpdate()
                }
            }
        }
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
                    reloadModsInner()
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
                    SL.UI.toaster.addItem(Toast(
                        id = id,
                        timeoutMillis = null,
                        useStandardToastFrame = true
                    ) {
                        toastInstalledCard(
                            modVariant = newModVariant,
                            requestToastDismissal = { delayMillis ->
                                if (!SL.UI.toaster.timersByToastId.containsKey(id)) {
                                    Timber.i { "Changed toast timer id $id to ${delayMillis}ms." }
                                    SL.UI.toaster.timersByToastId[id] = delayMillis
                                }
                            }
                        )
                    })
                }
        }

    }

    LaunchedEffect(1) {
        val items = SL.UI.toaster.items
        SL.UI.downloadManager.downloads.collect { downloads ->
            downloads
                .filter { it.id !in items.value.map { it.id } }
                .map {
                    val toastId = "download-${it.id}"
                    Toast(id = toastId, timeoutMillis = null, useStandardToastFrame = true) {
                        downloadToast(
                            download = it,
                            requestToastDismissal = { delayMillis ->
                                if (!SL.UI.toaster.timersByToastId.containsKey(toastId)) {
                                    Timber.i { "Changed toast timer id $toastId to ${delayMillis}ms." }
                                    SL.UI.toaster.timersByToastId[toastId] = delayMillis
                                }
                            })
                    }
                }
                .also {
                    SL.UI.toaster.addItems(it)
                }
        }
    }
}

class AppScope(windowState: WindowState, private val recomposer: RecomposeScope) : IWindowState by windowState {


    /**
     * Usage: alertDialogSetter.invoke { AlertDialog(...) }
     */
    lateinit var alertDialogSetter: (@Composable ((dismiss: () -> Unit) -> Unit)?) -> Unit

    fun dismissAlertDialog() = alertDialogSetter.invoke(null)

    suspend fun reloadMods() = reloadModsInner()
    fun recomposeAppUI() = recomposer.invalidate()
}


@OptIn(ExperimentalCoroutinesApi::class)
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
                    delay(1000)
                    Logger.info { "Trying to reload to due to file change: $it" }
                    reloadModsInner()
                } else {
                    Logger.info { "Skipping mod reload while IO locked." }
                }
            }
    }
}

private suspend fun reloadModsInner() {
    if (SL.access.areModsLoading.value) {
        Logger.info { "Skipping reload of mods as they are currently refreshing already." }
        return
    }
    try {
        coroutineScope {
            utilities.trace(onFinished = { _, millis -> Timber.i { "Finished reloading everything in ${millis}ms (this is not how long it took to reload just the mods)." } }) {
                Timber.i { "Reloading mods." }
                SL.access.reload()
                val mods = SL.access.mods.value?.mods ?: emptyList()

                listOf(
                    async {
                        SL.versionChecker.lookUpVersions(
                            forceLookup = true,
                            mods = mods
                        )
                    },
                    async {
                        kotlin.runCatching { SL.saveReader.readAllSaves() }
                            .onFailure { Timber.w(it) }
                    }
                ).awaitAll()
            }
        }
    } catch (e: Exception) {
        Logger.debug(e)
    }
}