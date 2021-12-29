package smol_app.views

import AppState
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.GridCells
import androidx.compose.foundation.lazy.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import smol_access.SL
import smol_access.business.UserManager
import smol_access.model.ModVariant
import smol_access.model.SmolId
import smol_access.model.UserProfile
import smol_app.composables.*
import smol_app.themes.SmolTheme
import smol_app.themes.SmolTheme.lighten
import smol_app.toolbar.*
import smol_app.util.ellipsizeAfter
import smol_app.util.smolPreview

@OptIn(
    ExperimentalMaterialApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
@Composable
@Preview
fun AppState.ProfilesView(
    modifier: Modifier = Modifier
) {
    val modProfileIdShowingDeleteConfirmation = remember { mutableStateOf<Int?>(null) }
    var userProfile = SL.userManager.activeProfile.collectAsState().value
    val showLogPanel = remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(modifier = Modifier.height(SmolTheme.topBarHeight)) {
                launchButton()
                installModsButton()
                Spacer(Modifier.width(16.dp))
                homeButton()
                screenTitle(text = "Mod Profiles")
                settingsButton()
                modBrowserButton()
            }
        }, content = {
            Box(modifier.padding(16.dp)) {
                val modVariants = remember {
                    mutableStateOf(SL.access.mods.value?.mods?.flatMap { it.variants }?.associateBy { it.smolId }
                        ?: emptyMap())
                }

                LazyVerticalGrid(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    cells = GridCells.Adaptive(370.dp)
                ) {
                    this.items(items = userProfile.modProfiles
                        .sortedWith(
                            compareByDescending<UserProfile.ModProfile> { it.id == userProfile.activeModProfileId }
                                .thenBy { it.sortOrder })
                    ) { modProfile ->
                        val isActiveProfile = userProfile.activeModProfileId == modProfile.id
                        val isEditMode = remember { mutableStateOf(false) }
                        val modProfileName = remember { mutableStateOf(modProfile.name) }
                        Card(
                            modifier = Modifier.wrapContentHeight()
                                .run {
                                    // Highlight active profile
                                    if (isActiveProfile) this
                                        .border(
                                            width = 4.dp,
                                            color = MaterialTheme.colors.onSurface.lighten(),
                                            shape = SmolTheme.smolFullyClippedButtonShape()
                                        )
                                    else this
                                },
                            shape = SmolTheme.smolFullyClippedButtonShape()
                        ) {
                            SelectionContainer {
                                Column(
                                    modifier = Modifier.run {
                                        if (isActiveProfile) this.padding(16.dp)
                                        else this.padding(
                                            top = 8.dp,
                                            bottom = 16.dp,
                                            start = 16.dp,
                                            end = 16.dp
                                        )
                                    }
                                ) {
                                    Row {
                                        if (!isEditMode.value) {
                                            Text(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(end = 16.dp)
                                                    .align(Alignment.CenterVertically),
                                                fontSize = 18.sp,
                                                fontFamily = SmolTheme.orbitronSpaceFont,
                                                text = modProfileName.value
                                            )
                                        } else {
                                            SmolTextField(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(end = 16.dp)
                                                    .align(Alignment.CenterVertically),
                                                value = modProfileName.value,
                                                label = { Text(text = "Profile Name") },
                                                singleLine = true,
                                                onValueChange = { newValue ->
                                                    modProfileName.value = newValue
                                                    SL.userManager.updateUserProfile { old ->
                                                        old.copy(modProfiles = old.modProfiles.map { profile ->
                                                            if (profile.id == modProfile.id) {
                                                                profile.copy(name = newValue)
                                                            } else profile
                                                        })
                                                    }
                                                }
                                            )
                                        }

                                        // Control buttons
                                        DisableSelection {
                                            profileControls(
                                                isEditMode = isEditMode,
                                                modProfileIdShowingDeleteConfirmation = modProfileIdShowingDeleteConfirmation,
                                                modProfile = modProfile,
                                                isActiveProfile = isActiveProfile,
                                                modVariants = modVariants
                                            )
                                        }
                                    }

                                    Text(
                                        modifier = Modifier.padding(top = 8.dp),
                                        text = "${modProfile.enabledModVariants.count()} mods",
                                        fontFamily = SmolTheme.fireCodeFont,
                                        fontSize = 12.sp,
                                    )

                                    // Mod list
                                    Box {
                                        val modNameLength = 28
                                        Text(
                                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                                                .align(Alignment.TopStart),
                                            fontFamily = SmolTheme.fireCodeFont,
                                            softWrap = false,
                                            fontWeight = FontWeight.Light,
                                            fontSize = 14.sp,
                                            text = modProfile.enabledModVariants.joinToString(separator = "\n") {
                                                val modVariant = modVariants.value[it.smolVariantId]
                                                modVariant?.modInfo?.name?.ellipsizeAfter(modNameLength)?.plus(": ")
                                                    ?: "${it.modId} (missing)\n    ${it.smolVariantId}"
                                            })
                                        Text(
                                            modifier = Modifier.padding(top = 16.dp).align(Alignment.TopEnd),
                                            fontFamily = SmolTheme.fireCodeFont,
                                            softWrap = false,
                                            fontWeight = FontWeight.Light,
                                            fontSize = 14.sp,
                                            text = modProfile.enabledModVariants.joinToString(separator = "\n") {
                                                val modVariant = modVariants.value[it.smolVariantId]
                                                modVariant?.modInfo?.version?.toString() ?: ""
                                            })
                                    }
                                }
                            }
                        }
                    }

                    this.item {
                        var newProfileName by remember { mutableStateOf("") }
                        Card(
                            shape = SmolTheme.smolFullyClippedButtonShape()
                        ) {
                            Column(Modifier.padding(16.dp).fillMaxWidth()) {
                                SmolTextField(
                                    value = newProfileName,
                                    onValueChange = { newProfileName = it },
                                    singleLine = true,
                                    label = { Text("Name") }
                                )
                                SmolButton(
                                    modifier = Modifier.padding(top = 16.dp),
                                    onClick = {
                                        if (newProfileName.isNotBlank()) {
                                            SL.userManager.createModProfile(
                                                name = newProfileName,
                                                description = null,
                                                sortOrder = SL.userManager.activeProfile.value.modProfiles.maxOf { it.sortOrder } + 1
                                            )
                                            newProfileName = ""
                                            userProfile = SL.userManager.activeProfile.value
                                        }
                                    }) {
                                    Icon(
                                        modifier = Modifier
                                            .height(SmolTheme.textIconHeightWidth())
                                            .width(SmolTheme.textIconHeightWidth()),
                                        painter = painterResource("plus.svg"),
                                        contentDescription = null
                                    )
                                    Text(
                                        text = "New Profile"
                                    )
                                }
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
                logButtonAndErrorDisplay(showLogPanel)
            }
        }
    )

    if (modProfileIdShowingDeleteConfirmation.value != null) {
        val profile =
            userProfile.modProfiles.firstOrNull { it.id == modProfileIdShowingDeleteConfirmation.value }
        SmolAlertDialog(
            modifier = Modifier,
            onDismissRequest = { modProfileIdShowingDeleteConfirmation.value = null },
            title = { Text("Confirm deletion") },
            text = {
                Text("Are you sure you want to delete \"${profile?.name}\"?")
            },
            confirmButton = {
                SmolButton(onClick = {
                    SL.userManager.removeModProfile(
                        modProfileIdShowingDeleteConfirmation.value ?: run {
                            modProfileIdShowingDeleteConfirmation.value = null

                            return@SmolButton
                        })
                    modProfileIdShowingDeleteConfirmation.value = null
                }) { Text("Delete") }
            },
            dismissButton = {
                SmolSecondaryButton(onClick = { modProfileIdShowingDeleteConfirmation.value = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Preview
@Composable
fun profileControlsPreview() = smolPreview {
    Column {
        profileControls(
            isEditMode = mutableStateOf(false),
            modProfileIdShowingDeleteConfirmation = mutableStateOf(null),
            modProfile = UserManager.defaultModProfile,
            isActiveProfile = false,
            modVariants = mutableStateOf(emptyMap())
        )
    }
}

@Composable
private fun profileControls(
    modifier: Modifier = Modifier,
    isEditMode: MutableState<Boolean>,
    modProfileIdShowingDeleteConfirmation: MutableState<Int?>,
    modProfile: UserProfile.ModProfile,
    isActiveProfile: Boolean,
    modVariants: MutableState<Map<SmolId, ModVariant>>
) {
    Row(
        modifier = modifier
    ) {
        IconToggleButton(
            modifier = Modifier
                .padding(start = 8.dp)
                .align(Alignment.CenterVertically)
                .background(
                    shape = SmolTheme.smolNormalButtonShape(),
                    color = SmolTheme.grey()
                )
                .run {
                    if (isEditMode.value) this.border(
                        width = 2.dp,
                        color = MaterialTheme.colors.onSurface.lighten(),
                        shape = SmolTheme.smolNormalButtonShape()
                    ) else this
                }
                .height(ButtonDefaults.MinHeight)
                .width(ButtonDefaults.MinHeight),
            checked = isEditMode.value,
            onCheckedChange = { isEditMode.value = !isEditMode.value }
        ) {
            Icon(
                painter = painterResource("pencil-outline.svg"),
                contentDescription = null,
                tint = MaterialTheme.colors.onSurface
            )
        }
        IconButton(
            modifier = Modifier
                .padding(start = 8.dp)
                .background(
                    shape = SmolTheme.smolNormalButtonShape(),
                    color = SmolTheme.grey()
                )
                .align(Alignment.CenterVertically)
                .height(SmolTheme.iconHeightWidth())
                .width(SmolTheme.iconHeightWidth()),
            onClick = {
                modProfileIdShowingDeleteConfirmation.value = modProfile.id
            }
        ) {
            Icon(
                painter = painterResource("trash-can-outline.svg"),
                contentDescription = null,
                tint = MaterialTheme.colors.onSurface
            )
        }

        if (!isActiveProfile) {
            SmolButton(
                modifier = Modifier.padding(start = 8.dp).align(Alignment.CenterVertically),
                enabled = !isActiveProfile,
                onClick = {
                    if (!isActiveProfile) {
                        GlobalScope.launch(Dispatchers.Default) {
                            SL.userModProfileManager.switchModProfile(modProfile.id)
                            withContext(Dispatchers.Main) {
//                                                            userProfile = SL.userManager.activeProfile.value
                                modVariants.value =
                                    (SL.access.mods.value?.mods ?: emptyList())
                                        .flatMap { it.variants }
                                        .associateBy { it.smolId }
                            }
                        }
                    }
                }) {
                if (isActiveProfile)
                    Text(
                        fontWeight = FontWeight.SemiBold,
                        text = "Active"
                    )
                else
                    Text(
                        text = "Activate"
                    )
            }
        }
    }
}