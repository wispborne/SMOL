package smol_app.modprofiles

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import smol_access.SL
import smol_access.business.SaveFile
import smol_access.model.ModVariant
import smol_access.model.SmolId
import smol_access.model.UserProfile
import smol_app.composables.SmolTextField
import smol_app.composables.SmolTooltipArea
import smol_app.composables.SmolTooltipBackground
import smol_app.themes.SmolTheme
import smol_app.themes.SmolTheme.lighten
import smol_app.util.smolPreview
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
@Preview
fun ModProfileCardPreview() = smolPreview {
    ModProfileCard(
        userProfile = mockUserProfile,
        modProfile = mockModProfile,
        modProfileIdShowingDeleteConfirmation = mutableStateOf(null),
        modVariants = mutableStateOf(emptyMap())
    )
}

private val dateFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

@OptIn(ExperimentalFoundationApi::class, ExperimentalAnimationApi::class)
@Composable
fun ModProfileCard(
    userProfile: UserProfile,
    modProfile: ModProfileCardInfo,
    modProfileIdShowingDeleteConfirmation: MutableState<Int?>,
    modVariants: MutableState<Map<SmolId, ModVariant>>
) {
    val isActiveProfile = userProfile.activeModProfileId == modProfile.id
    val isUserMade = modProfile is ModProfileCardInfo.EditableModProfileCardInfo
    val isEditMode = remember { mutableStateOf(false) }
    val modProfileName = remember { mutableStateOf(modProfile.name) }
    var isExpanded by remember { mutableStateOf(false) }

    SmolTooltipArea(
        tooltip = {
            SmolTooltipBackground {
                modList(
                    modifier = Modifier.widthIn(max = 400.dp),
                    modVariants = modVariants,
                    modProfile = modProfile
                )
            }
        },
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
                            if (isActiveProfile) this.padding(
                                start = 16.dp,
                                top = 16.dp,
                                end = 8.dp,
                                bottom = 16.dp
                            )
                            else this.padding(
                                start = 16.dp,
                                top = 8.dp,
                                end = 8.dp,
                                bottom = 16.dp
                            )
                        }) {
                    SelectionContainer {
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

                            if (isUserMade) {
                                IconToggleButton(
                                    modifier = Modifier
                                        .align(Alignment.CenterVertically)
                                        .run {
                                            if (isEditMode.value) this.border(
                                                width = 2.dp,
                                                color = MaterialTheme.colors.onSurface.lighten(),
                                                shape = SmolTheme.smolNormalButtonShape()
                                            ) else this
                                        }
                                        .height(20.dp),
                                    checked = isEditMode.value,
                                    onCheckedChange = { isEditMode.value = !isEditMode.value }
                                ) {
                                    Icon(
                                        painter = painterResource("pencil-outline.svg"),
                                        modifier = Modifier,
                                        contentDescription = null,
                                        tint = MaterialTheme.colors.onSurface
                                    )
                                }
                            }
                        }
                    }

                    Row {
                        val count = modProfile.enabledModVariants.count()
                        Text(
                            modifier = Modifier.padding(top = 8.dp),
                            text = "$count ${if (count == 1) "mod" else "mods"}",
                            fontFamily = SmolTheme.fireCodeFont,
                            fontSize = 12.sp,
                        )

                        if (modProfile is ModProfileCardInfo.SaveModProfileCardInfo) {
                            Text(
                                modifier = Modifier.padding(top = 8.dp),
                                text = " • level ${modProfile.saveFile.characterLevel}",
                                fontFamily = SmolTheme.fireCodeFont,
                                fontSize = 12.sp,
                            )
                        }
                    }

                    if (modProfile is ModProfileCardInfo.SaveModProfileCardInfo) {
                        Text(
                            modifier = Modifier.padding(top = 8.dp),
                            text = dateFormat.format(modProfile.saveFile.saveDate),
                            fontFamily = SmolTheme.fireCodeFont,
                            fontSize = 12.sp,
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

                    Row {
                        TextButton(
                            onClick = { isExpanded = isExpanded.not() },
                            modifier = Modifier
                                .padding(top = 4.dp, start = 4.dp, end = 4.dp)
                                .weight(1f)
                        ) {
                            Box(Modifier.fillMaxWidth()) {
                                Icon(
                                    painter = painterResource(
                                        if (isExpanded) "icon-collapse.svg"
                                        else "icon-expand.svg"
                                    ),
                                    modifier = Modifier.align(Alignment.CenterStart),
//                                    fontSize = 14.sp,
                                    contentDescription = null
                                )
                            }
                        }

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
                }
            }
        }
    }
}

@Composable
fun modList(
    modifier: Modifier = Modifier,
    modVariants: MutableState<Map<SmolId, ModVariant>>,
    modProfile: ModProfileCardInfo
) {
    // Mod list
//    val modNameLength = 28
    SelectionContainer {
        Row(modifier) {
            if (modProfile.enabledModVariants.any()) {
                Column {
                    modProfile.enabledModVariants.forEach { enabledModVariant ->
                        val modVariant = modVariants.value[enabledModVariant.smolVariantId]

                        Row {
                            Text(
                                modifier = Modifier.weight(1f),
                                fontFamily = SmolTheme.fireCodeFont,
                                fontWeight = FontWeight.Light,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                text = modVariant?.modInfo?.name?.plus(": ")
                                    ?: "${enabledModVariant.modId} (missing)\n    ${enabledModVariant.smolVariantId}"
                            )

                            Text(
                                fontFamily = SmolTheme.fireCodeFont,
                                fontWeight = FontWeight.Light,
                                softWrap = false,
                                fontSize = 14.sp,
                                maxLines = 1,
//                                overflow = TextOverflow.Ellipsis,
                                text = modVariant?.modInfo?.version?.toString() ?: ""
                            )
                        }
                    }
                }
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
            modProfile = mockModProfile,
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
    modProfile: ModProfileCardInfo,
    isActiveProfile: Boolean,
    modVariants: MutableState<Map<SmolId, ModVariant>>
) {
    Row(
        modifier = modifier
    ) {
        if (!isActiveProfile) {
            IconButton(
                modifier = Modifier
                    .padding(start = 8.dp, end = 4.dp)
                    .align(Alignment.CenterVertically)
                    .height(SmolTheme.iconHeightWidth())
                    .width(SmolTheme.iconHeightWidth()),
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
                    tint = MaterialTheme.colors.primary,
                    contentDescription = null
                )
            }
        }
        IconButton(
            modifier = Modifier
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
    }
}

@Preview
@Composable
fun saveGameProfileControlsPreview() = smolPreview {
    Column {
        saveGameProfileControls(
            modProfile = mockModProfile
        )
    }
}

@Composable
fun saveGameProfileControls(
    modifier: Modifier = Modifier,
    modProfile: ModProfileCardInfo
) {
    Row(
        modifier = modifier
    ) {
        OutlinedButton(
            modifier = Modifier
                .padding(start = 8.dp, end = 8.dp, top = 4.dp)
                .align(Alignment.CenterVertically),
            onClick = {

            }
        ) {
            Text("Create Profile")
        }
    }
}

private val mockModProfile = ModProfileCardInfo.EditableModProfileCardInfo(
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
private val mockSaveModProfile = ModProfileCardInfo.SaveModProfileCardInfo(
    id = 0,
    name = "test profile",
    description = "desc",
    sortOrder = 0,
    enabledModVariants = listOf(
        UserProfile.ModProfile.EnabledModVariant(
            modId = "mod_id",
            smolVariantId = "23444"
        )
    ),
    saveFile = SaveFile(
        id = "save",
        characterName = "The Boss",
        characterLevel = 24,
        portraitPath = "g/g/g",
        saveFileVersion = "0.5",
        saveDate = Instant.now().atZone(ZoneId.systemDefault()),
        mods = emptyList()
    )
)
private val mockUserProfile = UserProfile(
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