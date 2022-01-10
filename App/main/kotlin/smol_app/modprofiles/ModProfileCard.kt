package smol_app.modprofiles

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerMoveFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
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
import smol_app.composables.SmolTooltipText
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalAnimationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun ModProfileCard(
    userProfile: UserProfile,
    modProfile: ModProfileCardInfo,
    modProfileIdShowingDeleteConfirmation: MutableState<Int?>,
    modVariants: MutableState<Map<SmolId, ModVariant>>
) {
    val isActiveProfile = remember { userProfile.activeModProfileId == modProfile.id }
    val isUserMade = modProfile is ModProfileCardInfo.EditableModProfileCardInfo
    val isEditMode = remember { mutableStateOf(false) }
    val modProfileName = remember { mutableStateOf(modProfile.name) }
    var isExpanded by remember { mutableStateOf(false) }

    var isBeingHovered by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxSize()
            .border(
                shape = SmolTheme.smolFullyClippedButtonShape(),
                border = if (isActiveProfile)
                // Highlight active profile
                    BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colors.onSurface.lighten()
                    )
                else
                    BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colors.surface.lighten()
                    )
            )
            .wrapContentHeight()
            .pointerMoveFilter(
                onEnter = { isBeingHovered = true; false },
                onExit = { isBeingHovered = false; false }
            ),
        shape = SmolTheme.smolFullyClippedButtonShape()
    ) {
        Box {
            Column {
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
                    Column(
                        modifier = Modifier.padding(
                            start = 16.dp,
                            top = 8.dp,
                            end = 2.dp,
                            bottom = 16.dp
                        )
                    ) {
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
                                        textStyle = TextStyle.Default.copy(fontFamily = SmolTheme.orbitronSpaceFont),
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
                                    SmolTooltipArea(tooltip = { SmolTooltipText(text = "Edit name.") }) {
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
                                            val alphaOfHoverDimmedElements =
                                                animateFloatAsState(if (isBeingHovered) 0.8f else 0.5f)
                                            Icon(
                                                painter = painterResource("pencil-outline.svg"),
                                                modifier = Modifier,
                                                contentDescription = null,
                                                tint = MaterialTheme.colors.onSurface.copy(alpha = alphaOfHoverDimmedElements.value)
                                            )
                                        }
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
                                val alphaOfHoverDimmedElements =
                                    animateFloatAsState(if (isBeingHovered) 0.8f else 0.5f).value
                                Icon(
                                    painter = painterResource(
                                        if (isExpanded) "icon-collapse.svg"
                                        else "icon-expand.svg"
                                    ),
                                    tint = MaterialTheme.colors.onSurface.copy(alpha = alphaOfHoverDimmedElements),
                                    modifier = Modifier.align(Alignment.CenterStart),
                                    contentDescription = null
                                )
                            }
                        }

                        // Control buttons
                        if (isUserMade) {
                            profileControls(
                                modifier = Modifier.padding(top = 10.dp),
                                modProfileIdShowingDeleteConfirmation = modProfileIdShowingDeleteConfirmation,
                                modProfile = modProfile,
                                isActiveProfile = isActiveProfile,
                                modVariants = modVariants,
                                isBeingHovered = isBeingHovered
                            )
                        } else {
                            saveGameProfileControls(modProfile = modProfile)
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
            modProfileIdShowingDeleteConfirmation = mutableStateOf(null),
            modProfile = mockModProfile,
            isActiveProfile = false,
            modVariants = mutableStateOf(emptyMap()),
            isBeingHovered = true
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun profileControls(
    modifier: Modifier = Modifier,
    modProfileIdShowingDeleteConfirmation: MutableState<Int?>,
    modProfile: ModProfileCardInfo,
    isActiveProfile: Boolean,
    modVariants: MutableState<Map<SmolId, ModVariant>>,
    isBeingHovered: Boolean
) {
    Row(
        modifier = modifier
    ) {
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
            Box {
                SmolTooltipArea(tooltip = {
                    SmolTooltipText(
                        text = if (isActiveProfile) "This is the active profile." else "Activate profile.",
                        // Is a dim color instead if I don't set custom color
                        color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.high)
                    )
                }) {
                    Icon(
                        painter = painterResource("icon-power.svg"),
                        tint = animateColorAsState(
                            if (isActiveProfile) {
                                if (isBeingHovered)
                                    MaterialTheme.colors.primary.lighten(40)
                                else
                                    MaterialTheme.colors.primary.lighten()
                            } else {
                                if (isBeingHovered)
                                    LocalContentColor.current.copy(alpha = 1f)
                                else
                                    LocalContentColor.current.copy(alpha = .80f)
                            }
                        ).value,
                        contentDescription = null
                    )
                }
            }

            if (isActiveProfile) {
                Box(
                    modifier = Modifier
                        .clipToBounds()
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colors.primary.lighten(50).copy(alpha = 0.35f),
                                    Color.Transparent
                                )
                            )
                        )
                        .size(32.dp)
                )
                {}
            }
        }

        SmolTooltipArea(tooltip = { SmolTooltipText(text = "Delete profile.") }) {
            IconButton(
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .height(SmolTheme.iconHeightWidth())
                    .width(SmolTheme.iconHeightWidth()),
                onClick = {
                    modProfileIdShowingDeleteConfirmation.value = modProfile.id
                }
            ) {
                val alphaOfHoverDimmedElements = animateFloatAsState(if (isBeingHovered) 0.8f else 0.5f).value
                Icon(
                    painter = painterResource("trash-can-outline.svg"),
                    contentDescription = null,
                    tint = MaterialTheme.colors.onSurface.copy(alpha = alphaOfHoverDimmedElements)
                )
            }
        }
    }
}

@Preview
@Composable
fun saveGameProfileControlsPreview() = smolPreview {
    Column {
        saveGameProfileControls(modProfile = mockModProfile)
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
                SL.userManager.createModProfile(
                    name = modProfile.name,
                    sortOrder = null,
                    enabledModVariants = modProfile.enabledModVariants
                )
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