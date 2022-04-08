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

package smol_app.modprofiles

import AppScope
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
import androidx.compose.ui.input.key.*
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
import smol_access.Access
import smol_access.SL
import smol_access.business.SaveFile
import smol_access.model.UserProfile
import smol_access.model.Version
import smol_app.WindowState
import smol_app.composables.*
import smol_app.themes.SmolTheme
import smol_app.themes.SmolTheme.lighten
import smol_app.util.smolPreview
import utilities.copyToClipboard
import utilities.equalsAny
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
@Preview
fun ModProfileCardPreview() = smolPreview {
    AppScope(windowState = WindowState(), recomposer = currentRecomposeScope).ModProfileCard(
        userProfile = mockUserProfile,
        modProfile = mockModProfile,
        modVariants = emptyList()
    )
}

private val dateFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

@OptIn(ExperimentalFoundationApi::class, ExperimentalAnimationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun AppScope.ModProfileCard(
    userProfile: UserProfile,
    modProfile: ModProfileCardInfo,
    modVariants: List<UserProfile.ModProfile.ShallowModVariant>,
) {
    val isActiveProfile = userProfile.activeModProfileId == modProfile.id
    val isUserMade = modProfile is ModProfileCardInfo.EditableModProfileCardInfo
    val isEditMode = remember { mutableStateOf(false) }
    val modProfileName = remember { mutableStateOf(modProfile.name) }
    modProfileName.value = modProfile.name
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
                                        .onKeyEvent { event ->
                                            if (event.type == KeyEventType.KeyUp
                                                && (event.key.equalsAny(
                                                    Key.Escape,
                                                    Key.Enter,
                                                    Key.NumPadEnter
                                                ))
                                            ) {
                                                isEditMode.value = false
                                                true
                                            } else
                                                false
                                        }
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
                                SmolTooltipArea(tooltip = {
                                    SmolTooltipText(
                                        text = if (isEditMode.value) "Done." else "Edit name."
                                    )
                                }) {
                                    IconToggleButton(
                                        modifier = Modifier
                                            .padding(top = 8.dp)
                                            .align(Alignment.CenterVertically)
                                            .height(20.dp),
                                        checked = isEditMode.value,
                                        onCheckedChange = { isEditMode.value = !isEditMode.value }
                                    ) {
                                        val alphaOfHoverDimmedElements =
                                            animateFloatAsState(if (isBeingHovered) 0.8f else 0.5f)
                                        Icon(
                                            painter = if (isEditMode.value)
                                                painterResource("icon-done.svg")
                                            else
                                                painterResource("icon-edit.svg"),
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
                            modVariantsToDisplay = modProfile.enabledModVariants
                        )
                    }

                    Row {
                        SmolTooltipArea(
                            modifier = Modifier
                                .weight(1f),
                            tooltip = {
                                if (!isExpanded) {
                                    SmolTooltipBackground {
                                        modList(
                                            modifier = Modifier.widthIn(max = 400.dp),
                                            modVariants = modVariants,
                                            modVariantsToDisplay = modProfile.enabledModVariants
                                        )
                                    }
                                }
                            }
                        ) {
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
                        }

                        // Control buttons
                        if (isUserMade) {
                            profileControls(
                                modifier = Modifier.padding(top = 10.dp),
                                modProfile = modProfile,
                                isActiveProfile = isActiveProfile,
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
    modVariants: List<UserProfile.ModProfile.ShallowModVariant>,
    modVariantsToDisplay: List<UserProfile.ModProfile.ShallowModVariant>
) {
    // Mod list
//    val modNameLength = 28
    SelectionContainer {
        Box(modifier) {
            if (modVariantsToDisplay.any()) {
                Column {
                    modVariantsToDisplay.forEach { enabledModVariant ->
                        val modVariant = modVariants.firstOrNull { it.smolVariantId == enabledModVariant.smolVariantId }

                        Row {
                            Text(
                                modifier = Modifier.weight(1f),
                                fontFamily = SmolTheme.fireCodeFont,
                                fontWeight = FontWeight.Light,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                text = modVariant?.modName?.plus(": ")
                                    ?: "${enabledModVariant.modId} (missing)\n    ${enabledModVariant.smolVariantId}"
                            )

                            Text(
                                fontFamily = SmolTheme.fireCodeFont,
                                fontWeight = FontWeight.Light,
                                softWrap = false,
                                fontSize = 14.sp,
                                maxLines = 1,
//                                overflow = TextOverflow.Ellipsis,
                                text = enabledModVariant.version?.raw
                                    ?: modVariant?.version?.toString() ?: ""
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
            modProfile = mockModProfile,
            isActiveProfile = false,
            isBeingHovered = true
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
@Composable
fun AppScope.profileControls(
    modifier: Modifier = Modifier,
    modProfile: ModProfileCardInfo,
    isActiveProfile: Boolean,
    isBeingHovered: Boolean
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val modsModificationState = SL.access.modModificationState.collectAsState()
        val areAllModsSettled = modsModificationState.value.all { it.value == Access.ModModificationState.Ready }

        IconButton(
            modifier = Modifier
                .padding(start = 8.dp, end = 4.dp)
                .align(Alignment.CenterVertically)
                .height(SmolTheme.iconHeightWidth())
                .width(SmolTheme.iconHeightWidth()),
            enabled = !isActiveProfile && areAllModsSettled,
            onClick = {
                if (!isActiveProfile) {
                    val allModVariantIds = SL.access.mods.value?.mods
                        ?.flatMap { it.variants }
                        ?.map { it.smolId } ?: emptyList()

                    val missingVariants =
                        modProfile.enabledModVariants.filter { it.smolVariantId !in allModVariantIds }

                    if (missingVariants.any()) {
                        alertDialogSetter.invoke {
                            MissingModVariantsAlertDialog(missingVariants, modProfile)
                        }
                    } else {
                        swapModProfile(modProfile)
                    }
                }
            }) {
            Box {
                SmolTooltipArea(tooltip = {
                    SmolTooltipText(
                        text = when {
                            !areAllModsSettled -> listOf("Swapping loadout.", "Refitting", "Processing")
                                .random()
                            isActiveProfile -> "This is the active profile."
                            else -> "Activate profile."
                        },
                        // Is a dim color instead if I don't set custom color
                        color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.high)
                    )
                }) {
                    if (areAllModsSettled) {
                        Icon(
                            painter = painterResource("icon-power.svg"),
                            tint = animateColorAsState(
                                if (isActiveProfile) {
                                    if (isBeingHovered)
                                        LocalContentColor.current.lighten(40)
                                    else
                                        LocalContentColor.current.lighten()
                                } else {
                                    if (isBeingHovered)
                                        LocalContentColor.current.copy(alpha = 1f)
                                    else
                                        LocalContentColor.current.copy(alpha = .80f)
                                }
                            ).value,
                            contentDescription = null
                        )
                    } else {
                        CircularProgressIndicator(Modifier.size(24.dp))
                    }
                }
            }

            if (isActiveProfile && areAllModsSettled) {
                // Add glow
                Box(
                    modifier = Modifier
                        .clipToBounds()
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colors.secondary.lighten(50).copy(alpha = 0.40f),
                                    Color.Transparent
                                )
                            )
                        )
                        .size(32.dp)
                )
                {}
            }
        }

        val alphaOfHoverDimmedElements = animateFloatAsState(if (isBeingHovered) 0.8f else 0.5f).value

        SmolTooltipArea(tooltip = { SmolTooltipText(text = "Copy mod list.") }) {
            IconButton(
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .size(20.dp),
                onClick = {
                    copyModProfile(modProfile = modProfile)
                }
            ) {
                Icon(
                    painter = painterResource("icon-copy.svg"),
                    contentDescription = null,
                    tint = MaterialTheme.colors.onSurface.copy(alpha = alphaOfHoverDimmedElements),
                )
            }
        }

        if (!isActiveProfile) {
            SmolTooltipArea(tooltip = { SmolTooltipText(text = "Delete profile.") }) {
                IconButton(
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .size(SmolTheme.iconHeightWidth()),
                    onClick = {
                        alertDialogSetter.invoke {
                            val profile = modProfile
                            SmolAlertDialog(
                                onDismissRequest = { alertDialogSetter.invoke(null) },
                                title = { Text("Confirm deletion", style = SmolTheme.alertDialogTitle()) },
                                text = {
                                    Text(
                                        "Are you sure you want to delete \"${profile.name}\"?",
                                        style = SmolTheme.alertDialogBody()
                                    )
                                },
                                confirmButton = {
                                    SmolButton(onClick = {
                                        SL.userManager.removeModProfile(profile.id)
                                        alertDialogSetter.invoke(null)
                                    }) { Text("Delete") }
                                },
                                dismissButton = {
                                    SmolSecondaryButton(onClick = { alertDialogSetter.invoke(null) }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }
                    }
                ) {
                    Icon(
                        painter = painterResource("icon-trash.svg"),
                        contentDescription = null,
                        tint = MaterialTheme.colors.onSurface.copy(alpha = alphaOfHoverDimmedElements)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun AppScope.MissingModVariantsAlertDialog(
    missingVariants: List<UserProfile.ModProfile.ShallowModVariant>,
    modProfile: ModProfileCardInfo
) {
    SmolAlertDialog(
        title = {
            Text(
                text = "Warning",
                style = SmolTheme.alertDialogTitle()
            )
        },
        text = {
            Column {
                Text(
                    text = "The following mods are missing:",
                    style = SmolTheme.alertDialogBody()
                )
                modList(
                    modifier = Modifier.padding(top = 16.dp),
                    modVariants = missingVariants,
                    modVariantsToDisplay = missingVariants
                )
                SmolButton(
                    modifier = Modifier.padding(top = 32.dp),
                    onClick = {
                        copyToClipboard(
                            missingVariants.joinToString(separator = "\n") { "${it.modName} (${it.modId}) ${it.version}" }
                        )
                    }
                ) {
                    Text("Copy to clipboard")
                }
            }
        },
        onDismissRequest = { alertDialogSetter.invoke(null) },
        confirmButton = {
            SmolButton(
                onClick = {
                    swapModProfile(modProfile)
                    alertDialogSetter.invoke(null)
                }
            ) {
                Text("Swap without ${missingVariants.count()} mod${if (missingVariants.count() != 1) "s" else ""}")
            }
        },
        dismissButton = {
            SmolSecondaryButton(
                onClick = { alertDialogSetter.invoke(null) }
            ) { Text("Cancel") }
        }
    )
}

private fun swapModProfile(modProfile: ModProfileCardInfo) {
    GlobalScope.launch(Dispatchers.Default) {
        SL.userModProfileManager.switchModProfile(modProfile.id)
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
            Text(
                text = "Create Profile",
                color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
            )
        }
    }
}

private val mockModProfile = ModProfileCardInfo.EditableModProfileCardInfo(
    id = "0",
    name = "test profile",
    description = "desc",
    sortOrder = 0,
    enabledModVariants = listOf(
        UserProfile.ModProfile.ShallowModVariant(
            modId = "mod_id",
            modName = "Mod Name",
            smolVariantId = "23444",
            version = null
        )
    )
)
private val mockSaveModProfile = ModProfileCardInfo.SaveModProfileCardInfo(
    id = "0",
    name = "test profile",
    description = "desc",
    sortOrder = 0,
    enabledModVariants = listOf(
        UserProfile.ModProfile.ShallowModVariant(
            modId = "mod_id",
            modName = "Mod Name",
            smolVariantId = "23444",
            version = Version.parse("1.2.3")
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
    activeModProfileId = "0",
    versionCheckerIntervalMillis = null,
    modProfiles = emptyList(), //listOf(modProfile),
    profileVersion = 1,
    theme = null,
    favoriteMods = emptyList(),
    modGridPrefs = UserProfile.ModGridPrefs(
        sortField = null,
        columnSettings = mapOf(
            UserProfile.ModGridHeader.ChangeVariantButton to UserProfile.ModGridColumnSetting(
                position = 0
            ),
            UserProfile.ModGridHeader.Name to UserProfile.ModGridColumnSetting(
                position = 1
            ),
            UserProfile.ModGridHeader.Author to UserProfile.ModGridColumnSetting(
                position = 2
            ),
            UserProfile.ModGridHeader.Version to UserProfile.ModGridColumnSetting(
                position = 3
            ),
            UserProfile.ModGridHeader.VramImpact to UserProfile.ModGridColumnSetting(
                position = 4
            ),
            UserProfile.ModGridHeader.Icons to UserProfile.ModGridColumnSetting(
                position = 5
            ),
            UserProfile.ModGridHeader.GameVersion to UserProfile.ModGridColumnSetting(
                position = 6
            ),
            UserProfile.ModGridHeader.Category to UserProfile.ModGridColumnSetting(
                position = 7
            ),
        )
    ),
    showGameLauncherWarning = true,
    launchButtonAction = UserProfile.LaunchButtonAction.OpenFolder
)

fun copyModProfile(modProfile: ModProfileCardInfo) {
    val modList = buildString {
        modProfile.name.ifBlank { null }?.run { appendLine(modProfile.name) }
        modProfile.description.ifBlank { null }?.run { appendLine(modProfile.description) }
        appendLine("⎯⎯⎯⎯⎯⎯⎯⎯⎯")
        appendLine(modProfile.enabledModVariants.joinToString(separator = "\n") { mod ->
            "${mod.modName?.ifBlank { null } ?: mod.modId}: ${mod.version?.raw ?: "(version unknown)"}"
        })
    }
    copyToClipboard(modList)
}