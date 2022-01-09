package smol_app.modprofiles

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import smol_app.composables.SmolButton
import smol_app.composables.SmolTextField
import smol_app.composables.SmolTooltipArea
import smol_app.composables.SmolTooltipBackground
import smol_app.themes.SmolTheme
import smol_app.themes.SmolTheme.lighten
import smol_app.util.ellipsizeAfter
import smol_app.util.smolPreview


@Composable
@Preview
fun ModProfileCardPreview() = smolPreview {
    val modProfile = UserProfile.ModProfile(
        id = 0,
        name = "test profile",
        description = "desc",
        sortOrder = 0,
        enabledModVariants = listOf(
            UserProfile.ModProfile.EnabledModVariant(
                modId = "mod_id",
                smolVariantId = "23444"
            )
        )
    )
    val userProfile = UserProfile(
        id = 1337,
        username = "user",
        activeModProfileId = 0,
        versionCheckerIntervalMillis = null,
        modProfiles = emptyList(), //listOf(modProfile),
        profileVersion = 1,
        theme = null,
        favoriteMods = emptyList(),
        modGridPrefs = UserProfile.ModGridPrefs(
            sortField = null
        )
    )
    ModProfileCard(
        userProfile = userProfile,
        modProfile = modProfile,
        modProfileIdShowingDeleteConfirmation = mutableStateOf(null),
        modVariants = mutableStateOf(emptyMap())
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalAnimationApi::class)
@Composable
fun ModProfileCard(
    userProfile: UserProfile,
    modProfile: UserProfile.ModProfile,
    modProfileIdShowingDeleteConfirmation: MutableState<Int?>,
    modVariants: MutableState<Map<SmolId, ModVariant>>
) {
    val isActiveProfile = userProfile.activeModProfileId == modProfile.id
    val isUserMade = modProfile in userProfile.modProfiles
    val isEditMode = remember { mutableStateOf(false) }
    val modProfileName = remember { mutableStateOf(modProfile.name) }
    var isExpanded by remember { mutableStateOf(false) }

    SmolTooltipArea(
        tooltip = { SmolTooltipBackground { modList(modVariants = modVariants, modProfile = modProfile) } },
        delayMillis = SmolTooltipArea.shortDelay
    ) {
        Card(
            modifier = Modifier.wrapContentHeight(),
            border = if (isActiveProfile)
            // Highlight active profile
                BorderStroke(
                    width = 4.dp,
                    color = MaterialTheme.colors.onSurface.lighten()
                )
            else
                BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colors.surface.lighten()
                ),
            shape = SmolTheme.smolFullyClippedButtonShape()
        ) {
            Column {
                Column(
                    modifier = Modifier
                        .run {
                            if (isActiveProfile) this.padding(16.dp)
                            else this.padding(
                                top = 8.dp,
                                bottom = 16.dp,
                                start = 16.dp,
                                end = 16.dp
                            )
                        }) {
                    Row {
                        if (!isEditMode.value) {
                            Text(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 16.dp)
                                    .align(Alignment.CenterVertically),
                                fontSize = 18.sp,
                                fontFamily = SmolTheme.orbitronSpaceFont,
                                text = if (!isUserMade) "Save: " + modProfileName.value else modProfileName.value
                            )
                        } else {
                            SelectionContainer {
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
                        }
                    }

                    Text(
                        modifier = Modifier.padding(top = 4.dp),
                        text = "${modProfile.enabledModVariants.count()} mods",
                        fontFamily = SmolTheme.fireCodeFont,
                        fontSize = 12.sp,
                    )

                    // Control buttons
                    if (isUserMade) {
                        profileControls(
                            modifier = Modifier.padding(top = 8.dp),
                            isEditMode = isEditMode,
                            modProfileIdShowingDeleteConfirmation = modProfileIdShowingDeleteConfirmation,
                            modProfile = modProfile,
                            isActiveProfile = isActiveProfile,
                            modVariants = modVariants
                        )
                    } else {
                        saveGameProfileControls(
                            modProfile = modProfile
                        )
                    }
                }

                Divider(Modifier.height(1.dp).fillMaxWidth())

                Column(modifier = Modifier
                    .run {
                        if (isActiveProfile) this.padding(start = 8.dp, bottom = 8.dp, end = 8.dp)
                        else this.padding(start = 4.dp, bottom = 4.dp, end = 4.dp)
                    }) {
                    AnimatedVisibility(visible = isExpanded) {
                        modList(
                            modifier = Modifier.padding(top = 16.dp, start = 8.dp, end = 8.dp),
                            modVariants = modVariants,
                            modProfile = modProfile
                        )
                    }

                    TextButton(
                        onClick = { isExpanded = isExpanded.not() },
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = if (isExpanded) "Collapse" else "Expand",
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun modList(
    modifier: Modifier = Modifier,
    modVariants: MutableState<Map<SmolId, ModVariant>>,
    modProfile: UserProfile.ModProfile
) {
    // Mod list
    val modNameLength = 28
    SelectionContainer {
        Row(modifier) {
            if (modProfile.enabledModVariants.any()) {
                Text(
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
                    fontFamily = SmolTheme.fireCodeFont,
                    softWrap = false,
                    fontWeight = FontWeight.Light,
                    fontSize = 14.sp,
                    text = modProfile.enabledModVariants.joinToString(separator = "\n") {
                        val modVariant = modVariants.value[it.smolVariantId]
                        modVariant?.modInfo?.version?.toString() ?: ""
                    })
            } else {
                Text(
                    text = "No mods.",
                    fontFamily = SmolTheme.fireCodeFont,
                    fontWeight = FontWeight.Light,
                    fontSize = 14.sp,
                )
            }
        }
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
fun profileControls(
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
            IconButton(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .background(
                        shape = SmolTheme.smolNormalButtonShape(),
                        color = ButtonDefaults.buttonColors().backgroundColor(enabled = true).value
                    )
                    .height(SmolTheme.iconHeightWidth())
                    .width(SmolTheme.iconHeightWidth())
                    .align(Alignment.CenterVertically),
                enabled = !isActiveProfile,
                onClick = {
                    if (!isActiveProfile) {
                        GlobalScope.launch(Dispatchers.Default) {
                            SL.userModProfileManager.switchModProfile(modProfile.id)
                            withContext(Dispatchers.Main) {
                                modVariants.value =
                                    (SL.access.mods.value?.mods ?: emptyList())
                                        .flatMap { it.variants }
                                        .associateBy { it.smolId }
                            }
                        }
                    }
                }) {
                Icon(
                    painter = painterResource("icon-swap.svg"),
                    tint = ButtonDefaults.buttonColors().contentColor(enabled = true).value,
                    contentDescription = null
                )
            }
        }
    }
}

@Preview
@Composable
fun saveGameProfileControlsPreview() = smolPreview {
    Column {
        saveGameProfileControls(
            modProfile = UserManager.defaultModProfile
        )
    }
}

@Composable
fun saveGameProfileControls(
    modifier: Modifier = Modifier,
    modProfile: UserProfile.ModProfile
) {
    Row(
        modifier = modifier
    ) {
        SmolButton(
            modifier = Modifier
                .padding(start = 8.dp)
                .align(Alignment.CenterVertically),
            onClick = {

            }
        ) {
            Text("Create Profile")
        }
    }
}