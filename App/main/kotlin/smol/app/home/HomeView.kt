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

@file:OptIn(ExperimentalFoundationApi::class)

package smol.app.home

//import smol.app.cli.SmolCLI
import AppScope
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.replaceCurrent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import smol.access.SL
import smol.access.model.Mod
import smol.app.composables.*
import smol.app.navigation.Screen
import smol.app.themes.SmolTheme
import smol.app.toolbar.toolbar
import smol.app.util.filterModGrid
import smol.app.util.replaceAllUsingDifference
import smol.timber.ktx.Timber
import smol.utilities.IOLock
import kotlin.random.Random

private typealias ModListFilter = suspend (List<Mod>) -> List<Mod>

@OptIn(
    ExperimentalFoundationApi::class
)
@Composable
@Preview
fun AppScope.homeView(
    modifier: Modifier = Modifier
) {
    val allMods: SnapshotStateList<Mod> = remember { SL.access.mods.value?.mods.orEmpty().toMutableStateList() }
    var modListFilter: ModListFilter? by remember { mutableStateOf(null) }
    val shownMods: SnapshotStateList<Mod> = remember { SnapshotStateList() }
    var modlistUpdateTrigger by remember { mutableStateOf(0) }
    val isWriteLocked = IOLock.stateFlow.collectAsState()

    LaunchedEffect(modListFilter, modlistUpdateTrigger) {
        val newList = modListFilter?.invoke(allMods) ?: allMods
        Timber.d { "Replacing ${shownMods.count()} shown mods with ${newList.count()} new mods." }
        shownMods.replaceAllUsingDifference(newList, doesOrderMatter = true)
    }

    val showLogPanel = remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            SmolTopAppBar(modifier = Modifier.height(SmolTheme.topBarHeight)) {
                toolbar(router.state.value.activeChild.instance as Screen)

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SmolFilterField(
                        modifier = Modifier
                            .focusRequester(searchFocusRequester())
                            .widthIn(min = 100.dp, max = 300.dp)
                            .padding(end = 16.dp)
                            .offset(y = (-3).dp)
                            .weight(1f, fill = false)
                            .align(Alignment.CenterVertically),
                        tooltipText = "Hotkey: Ctrl-F",
                        label = "Filter"
                    ) { query ->
                        if (query.isBlank()) {
                            modListFilter = null
//                            shownModIds.replaceAllUsingDifference(allMods.map { it.id }, doesOrderMatter = false)
                        } else {
                            modListFilter = { filterModGrid(query, allMods, access = SL.access) }
//                                    shownModIds.replaceAllUsingDifference(
//                                        newModGrid.map { it?.id },
//                                        doesOrderMatter = true
//                                    )
                        }
                    }

                    // Hide console for now, it's not useful
//                    if (false) {
//                        consoleTextField(
//                            modifier = Modifier
//                                .widthIn(max = 300.dp)
//                                .padding(end = 16.dp)
//                                .offset(y = (-3).dp)
//                                .align(Alignment.CenterVertically)
//                        )
//                    }

                    SmolTooltipArea(tooltip = { SmolTooltipText("About") }) {
                        IconButton(
                            onClick = { router.replaceCurrent(Screen.About) }
                        ) {
                            Box(
                                modifier = Modifier.align(Alignment.CenterVertically)
                            ) {
                                Icon(
                                    painter = painterResource("icon-info.svg"),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                                if (isWriteLocked.value) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp)
                                    )
//                                SmolText(
//                                    text = SL.access.modModificationState.collectAsState().value
//                                        .firstNotNullOfOrNull { it.value != smol.access.Access.ModModificationState.Ready }
//                                        ?.toString() ?: "",
//                                )
                                }
                            }
                        }
                    }
                }
            }
        }, content = {
            Box {
                val validationResult = SL.access.validatePaths()

                if (validationResult.isSuccess) {
                    ModGridView(
                        modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(bottom = 40.dp),
                        mods = shownMods
                    )
                } else {
                    Column(
                        Modifier.fillMaxWidth().fillMaxHeight(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val errors = validationResult.failure?.flatMap { it.value }

                        if (errors?.any() == true) {
                            Text(text = errors.joinToString(separator = "\n\n") { "Error: $it" })
                        }
                        SmolButton(
                            onClick = { router.replaceCurrent(Screen.Settings) },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text("Settings")
                        }
                    }
                }
            }

            if (showLogPanel.value) {
                logPanel { showLogPanel.value = false }
            }
        },
        bottomBar = {
            SmolBottomAppBar(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    logButtonAndErrorDisplay(showLogPanel = showLogPanel)
                }
            }
        }
    )

    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) {
            SL.access.mods.collectLatest { freshMods ->
                if (freshMods != null) {
                    withContext(Dispatchers.Main) {
                        allMods.replaceAllUsingDifference(freshMods.mods, doesOrderMatter = true)
                        Timber.d { "Updating mod grid with ${freshMods.mods.count()} mods (${shownMods.count()} shown)." }
                        modlistUpdateTrigger = Random.nextInt()
                    }
                }
            }
        }
    }
}

//@Composable
//private fun AppScope.consoleTextField(
//    modifier: Modifier = Modifier
//) {
//    Column(modifier) {
//        Row {
//            var consoleText by remember { mutableStateOf("") }
//            SmolOutlinedTextField(
//                value = consoleText,
//                label = { Text("Console") },
//                maxLines = 1,
//                singleLine = true,
//                onValueChange = { newStr ->
//                    consoleText = newStr
//                },
//                leadingIcon = { Icon(painter = painterResource("icon-console.svg"), contentDescription = null) },
//                modifier = Modifier
//                    .onEnterKeyPressed {
//                        kotlin.runCatching {
//                            SmolCLI(
//                                userManager = SL.userManager,
//                                userModProfileManager = SL.userModProfileManager,
//                                vmParamsManager = SL.UI.vmParamsManager,
//                                access = SL.access,
//                                gamePathManager = SL.gamePathManager
//                            )
//                                .parse(consoleText)
//                            consoleText = ""
//                        }
//                            .onFailure { Logger.warn(it) }
//                        true
//                    }
//            )
//        }
//    }
//}