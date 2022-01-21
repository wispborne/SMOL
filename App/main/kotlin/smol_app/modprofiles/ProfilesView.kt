package smol_app.modprofiles

import AppState
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.GridCells
import androidx.compose.foundation.lazy.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.HorizontalSplitPane
import org.jetbrains.compose.splitpane.rememberSplitPaneState
import smol_access.SL
import smol_access.business.SaveFile
import smol_access.model.ModVariant
import smol_access.model.UserProfile
import smol_app.composables.*
import smol_app.themes.SmolTheme
import smol_app.themes.SmolTheme.lighten
import smol_app.toolbar.*
import java.util.*

@OptIn(
    ExperimentalMaterialApi::class,
    ExperimentalFoundationApi::class, ExperimentalSplitPaneApi::class
)
@Composable
@Preview
fun AppState.ProfilesView(
    modifier: Modifier = Modifier
) {
    val recomposer = currentRecomposeScope
    val userProfile = SL.userManager.activeProfile.collectAsState().value
    val saveGames = SL.saveReader.saves.collectAsState()
    val showLogPanel = remember { mutableStateOf(false) }
    val modVariants = remember {
        mutableStateOf(SL.access.mods.value?.mods
            ?.flatMap { it.variants }
            ?.associateBy { it.smolId }
            ?: emptyMap())
    }
    val splitPageState = rememberSplitPaneState(initialPositionPercentage = 0.50f)

    Scaffold(
        topBar = {
            TopAppBar(modifier = Modifier.height(SmolTheme.topBarHeight)) {
                launchButton()
                installModsButton()
                Spacer(Modifier.width(16.dp))
                homeButton()
                modBrowserButton()
                screenTitle(text = "Mod Profiles")
                settingsButton()
            }
        }, content = {
            HorizontalSplitPane(
                modifier = modifier.padding(
                    start = 16.dp,
                    top = 16.dp,
                    end = 16.dp,
                    bottom = SmolTheme.bottomBarHeight
                ),
                splitPaneState = splitPageState
            ) {
                first {
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
                                    userProfile,
                                    modProfile,
                                    modVariants
                                )
                            }
                        }
                    }
                }

                horizontalSplitter(modifier = Modifier.padding(start = 16.dp, end = 16.dp))

                second {
                    Column {
                        Text(
                            text = "Saves",
                            style = MaterialTheme.typography.h6,
                            modifier = Modifier.padding(start = 8.dp, bottom = 16.dp)
                        )
                        LazyVerticalGrid(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            cells = GridCells.Adaptive(370.dp)
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
                                            UserProfile.ModProfile.EnabledModVariant(
                                                modId = it.id,
                                                smolVariantId = ModVariant.createSmolId(it.id, it.version),
                                                version = it.version
                                            )
                                        },
                                        saveFile = saveFile
                                    )
                                }
                            ) { modProfile ->
                                ModProfileCard(
                                    userProfile,
                                    modProfile,
                                    modVariants
                                )
                            }
                        }
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
                            ?: 0) + 1
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
    val enabledModVariants: List<UserProfile.ModProfile.EnabledModVariant>
) {
    class EditableModProfileCardInfo(
        id: String,
        name: String,
        description: String,
        sortOrder: Int,
        enabledModVariants: List<UserProfile.ModProfile.EnabledModVariant>
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
        enabledModVariants: List<UserProfile.ModProfile.EnabledModVariant>,
        val saveFile: SaveFile
    ) : ModProfileCardInfo(
        id = id,
        name = name,
        description = description,
        sortOrder = sortOrder,
        enabledModVariants = enabledModVariants,
    )
}