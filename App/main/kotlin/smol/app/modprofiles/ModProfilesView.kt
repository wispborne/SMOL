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

package smol.app.modprofiles

import AppScope
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.GridCells
import androidx.compose.foundation.lazy.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.HorizontalSplitPane
import org.jetbrains.compose.splitpane.rememberSplitPaneState
import smol.access.SL
import smol.access.business.SaveFile
import smol.access.model.ModVariant
import smol.access.model.SmolId
import smol.access.model.UserProfile
import smol.access.model.Version
import smol.app.composables.*
import smol.app.navigation.Screen
import smol.app.themes.SmolTheme
import smol.app.themes.SmolTheme.lighten
import smol.app.toolbar.toolbar
import java.util.*

@OptIn(
    ExperimentalMaterialApi::class,
    ExperimentalFoundationApi::class, ExperimentalSplitPaneApi::class
)
@Composable
@Preview
fun AppScope.ModProfilesView(
    modifier: Modifier = Modifier
) {
    val recomposer = currentRecomposeScope
    val userProfile = SL.userManager.activeProfile.collectAsState().value
    val saveGames = SL.saveReader.saves.collectAsState()
    val showLogPanel = remember { mutableStateOf(false) }
    val modVariants: Map<SmolId, ModVariant> = (SL.access.mods.collectAsState().value?.mods
        ?.flatMap { it.variants }
        ?.associateBy { it.smolId }
        ?: emptyMap())
    val shallowModVariants = modVariants.map {
        UserProfile.ModProfile.ShallowModVariant(it.value)
    }
    val splitPageState = rememberSplitPaneState(initialPositionPercentage = 0.50f)

    Scaffold(
        topBar = {
            SmolTopAppBar(modifier = Modifier.height(SmolTheme.topBarHeight)) {
                toolbar(router.state.value.activeChild.instance as Screen)
            }
        }, content = {
            val scrollState = rememberScrollState()
            HorizontalSplitPane(
                modifier = modifier.padding(
                    start = 16.dp,
                    top = 16.dp,
                    end = 16.dp,
                    bottom = SmolTheme.bottomBarHeight
                )
                    .scrollable(state = scrollState, orientation = Orientation.Vertical),
                splitPaneState = splitPageState
            ) {
                first(minSize = 300.dp) {
                    Column {
                        Text(
                            text = "Profiles",
                            style = MaterialTheme.typography.h6,
                            modifier = Modifier.padding(start = 8.dp, bottom = 16.dp)
                        )
                        LazyVerticalGrid(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            cells = GridCells.Adaptive(370.dp)
                        ) {
                            this.item {
                                NewModProfileCard(onProfileCreated = { recomposer.invalidate() })
                            }

                            this.items(items = userProfile.modProfiles
                                .sortedWith(
                                    compareByDescending<UserProfile.ModProfile> { it.id == userProfile.activeModProfileId }
                                        .thenBy { it.sortOrder })
                                .map {
                                    ModProfileCardInfo.EditableModProfileCardInfo(
                                        id = it.id,
                                        name = it.name,
                                        description = it.description,
                                        sortOrder = it.sortOrder,
                                        enabledModVariants = it.enabledModVariants
                                    )
                                }
                            ) { modProfile ->
                                ModProfileCard(
                                    userProfile = userProfile,
                                    modProfile = modProfile,
                                    modVariants = shallowModVariants
                                )
                            }
                        }
                    }
                }

                horizontalSplitter(modifier = Modifier.padding(start = 16.dp, end = 16.dp))

                second(minSize = 300.dp) {
                    Column {
                        Text(
                            text = "Saves",
                            style = MaterialTheme.typography.h6,
                            modifier = Modifier.padding(start = 8.dp, bottom = 16.dp)
                        )
                        Row {
                            val lazyListState = rememberLazyListState()
                            LazyVerticalGrid(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                cells = GridCells.Adaptive(370.dp),
                                state = lazyListState
                            ) {
                                this.items(items = saveGames.value
                                    .sortedByDescending { it.saveDate }
                                    .mapIndexed { index, saveFile ->
                                        ModProfileCardInfo.SaveModProfileCardInfo(
                                            id = UUID.randomUUID().toString(),
                                            name = saveFile.characterName,
                                            description = "",
                                            sortOrder = 1337 + index,
                                            enabledModVariants = saveFile.mods.map {
                                                UserProfile.ModProfile.ShallowModVariant(
                                                    modId = it.id,
                                                    modName = it.name,
                                                    smolVariantId = ModVariant.createSmolId(
                                                        it.id,
                                                        it.version ?: Version.parse("1.0.0")
                                                    ),
                                                    version = it.version
                                                )
                                            },
                                            saveFile = saveFile
                                        )
                                    }
                                ) { modProfile ->
                                    ModProfileCard(
                                        userProfile = userProfile,
                                        modProfile = modProfile,
                                        modVariants = shallowModVariants
                                    )
                                }
                            }
                            VerticalScrollbar(
                                modifier = Modifier.fillMaxHeight(),
                                adapter = rememberScrollbarAdapter(lazyListState)
                            )
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
                logButtonAndErrorDisplay(showLogPanel = showLogPanel)
            }
        }
    )
}

@Composable
private fun NewModProfileCard(onProfileCreated: () -> Unit) {
    var newProfileName by remember { mutableStateOf("") }
    Card(
        modifier = Modifier
            .border(
                shape = SmolTheme.smolFullyClippedButtonShape(),
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colors.surface.lighten()
                )
            ),
        shape = SmolTheme.smolFullyClippedButtonShape()
    ) {
        Column(Modifier.padding(16.dp).fillMaxWidth()) {
            SmolTextField(
                value = newProfileName,
                onValueChange = {
                    if (it.length <= 35) {
                        newProfileName = it
                    }
                },
                singleLine = true,
                maxLines = 1,
                label = { Text("Name") }
            )
            SmolButton(
                modifier = Modifier.padding(top = 16.dp),
                onClick = {
                    SL.userManager.createModProfile(
                        name = newProfileName.ifBlank { "Unnamed Profile" },
                        sortOrder = (SL.userManager.activeProfile.value.modProfiles.maxOfOrNull { it.sortOrder }
                            ?: 0) + 1,
                        enabledModVariants = SL.access.mods.value?.mods?.flatMap { it.enabledVariants }
                            ?.map { UserProfile.ModProfile.ShallowModVariant(it) } ?: emptyList()
                    )
                    newProfileName = ""
                    onProfileCreated.invoke()
                }) {
                Icon(
                    modifier = Modifier
                        .height(SmolTheme.textIconHeightWidth())
                        .width(SmolTheme.textIconHeightWidth()),
                    painter = painterResource("icon-plus.svg"),
                    contentDescription = null
                )
                Text(
                    text = "New Profile"
                )
            }
        }
    }
}

sealed class ModProfileCardInfo(
    val id: String,
    val name: String,
    val description: String,
    val sortOrder: Int,
    val enabledModVariants: List<UserProfile.ModProfile.ShallowModVariant>
) {
    class EditableModProfileCardInfo(
        id: String,
        name: String,
        description: String,
        sortOrder: Int,
        enabledModVariants: List<UserProfile.ModProfile.ShallowModVariant>
    ) : ModProfileCardInfo(
        id = id,
        name = name,
        description = description,
        sortOrder = sortOrder,
        enabledModVariants = enabledModVariants,
    )

    class SaveModProfileCardInfo(
        id: String,
        name: String,
        description: String,
        sortOrder: Int,
        enabledModVariants: List<UserProfile.ModProfile.ShallowModVariant>,
        val saveFile: SaveFile
    ) : ModProfileCardInfo(
        id = id,
        name = name,
        description = description,
        sortOrder = sortOrder,
        enabledModVariants = enabledModVariants,
    )
}