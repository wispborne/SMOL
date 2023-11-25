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
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import smol.access.SL
import smol.access.business.SaveFile
import smol.access.business.UserManager
import smol.access.model.UserProfile
import smol.access.model.Version
import smol.app.WindowState
import smol.app.composables.*
import smol.app.themes.SmolTheme
import smol.app.themes.SmolTheme.lighten
import smol.app.util.smolPreview
import smol.utilities.copyToClipboard
import smol.utilities.equalsAny
import smol.utilities.sIfPlural
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
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
            .onPointerEvent(PointerEventType.Enter) {
                isBeingHovered = true
            }
            .onPointerEvent(PointerEventType.Exit) {
                isBeingHovered = false
            },
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
                    Row {
                        if (!isEditMode.value) {
                            if (modProfile is ModProfileCardInfo.SaveModProfileCardInfo) {
                                Icon(
                                    painter = painterResource("icon-save.svg"),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(20.dp)
                                        .align(Alignment.CenterVertically)
                                        .alpha(0.7f)
                                )
                            }
                            SelectionContainer {
                                Text(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 16.dp)
                                        .align(Alignment.CenterVertically),
                                    fontSize = 18.sp,
                                    fontFamily = SmolTheme.orbitronSpaceFont,
                                    text = modProfileName.value
                                )
                            }
                        } else {
                            SelectionContainer {
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
                        }

                        Spacer(Modifier.weight(1f))

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
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colors.onSurface.copy(alpha = alphaOfHoverDimmedElements.value)
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

                    val date = if (modProfile is ModProfileCardInfo.SaveModProfileCardInfo)
                        modProfile.saveFile.saveDate
                    else
                        modProfile.dateModified ?: modProfile.dateCreated
                    if (date != null) {
                        SmolTooltipArea(
                            tooltip = {
                                SmolTooltipText(
                                    text = "Created: ${
                                        if (modProfile.dateCreated != null) dateFormat.format(
                                            modProfile.dateCreated
                                        ) else "(none)"
                                    }"
                                )
                            }
                        ) {
                            Text(
                                modifier = Modifier.padding(top = 8.dp),
                                text = dateFormat.format(date),
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
                        SelectionContainer {
                            modList(
                                modifier = Modifier.padding(top = 16.dp, start = 8.dp, end = 8.dp),
                                allModVariants = modVariants,
                                variantsInProfile = modProfile.enabledModVariants
                            )
                        }
                    }

                    Row {
                        SmolTooltipArea(
                            modifier = Modifier
                                .weight(1f),
                            tooltip = {
                                if (!isExpanded) {
                                    SmolTooltipBackground {
                                        // Don't make this selectable due to a bug introduced in Compose 1.4
                                        // where if the selectable text in the tooltip goes offscreen,
                                        // the program crashes.
                                        modList(
                                            modifier = Modifier.widthIn(max = 400.dp),
                                            allModVariants = modVariants,
                                            variantsInProfile = modProfile.enabledModVariants
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
    allModVariants: List<UserProfile.ModProfile.ShallowModVariant>,
    variantsInProfile: List<UserProfile.ModProfile.ShallowModVariant>
) {
    // Mod list
//    val modNameLength = 28
    Box(modifier) {
        CompositionLocalProvider(
            LocalTextStyle provides TextStyle(
                fontFamily = SmolTheme.fireCodeFont,
                fontWeight = FontWeight.Light,
                fontSize = 14.sp,
            )
        ) {
            if (variantsInProfile.any()) {
                Column {
                    val missingVariants = mutableListOf<UserProfile.ModProfile.ShallowModVariant>()

                    data class FoundVariant(
                        val inSave: UserProfile.ModProfile.ShallowModVariant,
                        val onDisk: UserProfile.ModProfile.ShallowModVariant
                    )

                    val foundVariants = mutableListOf<FoundVariant>()

                    variantsInProfile.forEach { variantInProfile ->
                        val foundVariantOnDisk = allModVariants.firstOrNull {
                            // Quick comparison
                            it.smolVariantId == variantInProfile.smolVariantId
                                    // More robust comparison that understands Version scheme.
                                    || (it.modId == variantInProfile.modId && it.version == variantInProfile.version)
                        }

                        if (foundVariantOnDisk != null) {
                            foundVariants += FoundVariant(inSave = variantInProfile, onDisk = foundVariantOnDisk)
                        } else {
                            missingVariants += variantInProfile
                        }
                    }

                    if (missingVariants.any()) {
                        Row {
                            Text(
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                fontWeight = FontWeight.ExtraBold,
                                overflow = TextOverflow.Ellipsis,
                                text = "Missing On Disk"
                            )
                        }
                        Row {
                            Text(
                                modifier = Modifier.weight(1f).padding(bottom = 8.dp),
                                fontWeight = FontWeight.Light,
                                overflow = TextOverflow.Ellipsis,
                                text = "(note: mods are set when the save is first created; updates are not tracked)"
                            )
                        }

                        missingVariants.forEach { variantInProfile ->
                            Row {
                                Text(
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    text = variantInProfile.modId
                                )
                                Text(
                                    softWrap = false,
                                    maxLines = 1,
                                    text = variantInProfile.version.toString()
                                )
                            }
                        }

                        Divider(Modifier.padding(vertical = 8.dp).height(1.dp).fillMaxWidth())
                        Row {
                            Text(
                                modifier = Modifier.weight(1f).padding(bottom = 8.dp),
                                maxLines = 1,
                                fontWeight = FontWeight.ExtraBold,
                                overflow = TextOverflow.Ellipsis,
                                text = "Not Missing"
                            )
                        }
                    }

                    if (foundVariants.any()) {
                        foundVariants.forEach { (modVariant, variantInProfile) ->
                            Row {
                                Text(
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    text = modVariant.modName.plus(": ")
                                )

                                Text(
                                    softWrap = false,
                                    maxLines = 1,
                                    text = variantInProfile.version?.raw
                                        ?: modVariant.version?.toString() ?: ""
                                )
                            }
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

@OptIn(ExperimentalFoundationApi::class)
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
        val areAllModsSettled =
            modsModificationState.value.all { it.value == smol.access.ModModificationState.Ready }

        IconButton(
            modifier = Modifier
                .padding(start = 8.dp, end = 4.dp)
                .align(Alignment.CenterVertically)
                .height(SmolTheme.iconHeightWidth())
                .width(SmolTheme.iconHeightWidth()),
            enabled = !isActiveProfile && areAllModsSettled,
            onClick = {
                if (!isActiveProfile) {
                    val allModVariantIds = SL.access.modsFlow.value?.mods
                        ?.flatMap { it.variants }
                        ?.map { it.smolId } ?: emptyList()

                    val missingVariants =
                        modProfile.enabledModVariants.filter { it.smolVariantId !in allModVariantIds }

                    if (missingVariants.any()) {
                        alertDialogSetter.invoke {
                            MissingModVariantsAlertDialog(missingVariants, modProfile)
                        }
                    } else {
                        swapModProfile(modProfile.id, emptyList())
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
    // See if any missing variants have newer versions that do exist.
    val newerVersionsAvailable = SL.access.modsFlow.value?.mods.orEmpty()
        .mapNotNull { it.findHighestVersion }
        .filter { newestVariant -> newestVariant.modInfo.id in missingVariants.map { it.modId } }
        .map { UserProfile.ModProfile.ShallowModVariant(it) }

    var useNewerModVariantsForMissing by remember { mutableStateOf(true) }

    SmolScrollableDialog(
        title = {
            Text(
                text = "Warning",
                style = SmolTheme.alertDialogTitle()
            )
        },
        content = {
            Column {
                Text(
                    text = "The following mods are missing:",
                    style = SmolTheme.alertDialogBody()
                )
                SelectionContainer {
                    modList(
                        modifier = Modifier.padding(top = 16.dp),
                        allModVariants = missingVariants,
                        variantsInProfile = missingVariants
                    )
                }
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

                if (newerVersionsAvailable.isNotEmpty()) {
                    Text(
                        text = "However, some mods have newer versions installed:",
                        style = SmolTheme.alertDialogBody(),
                        modifier = Modifier.padding(top = 32.dp)
                    )
                    SelectionContainer {
                        modList(
                            modifier = Modifier.padding(top = 16.dp),
                            allModVariants = newerVersionsAvailable,
                            variantsInProfile = newerVersionsAvailable
                        )
                    }

                    Row {
                        Text(
                            text = "Use newer versions for missing mods",
                            style = SmolTheme.alertDialogBody(),
                            modifier = Modifier.padding(end = 8.dp).align(Alignment.CenterVertically)
                        )
                        Spacer(Modifier.weight(1f))
                        Switch(
                            checked = useNewerModVariantsForMissing,
                            onCheckedChange = { useNewerModVariantsForMissing = it },
                            modifier = Modifier.padding(start = 8.dp).align(Alignment.CenterVertically)
                        )
                    }
                }
            }
        },
        onDismissRequest = { alertDialogSetter.invoke(null) },
        confirmButton = {
            SmolButton(
                onClick = {
                    swapModProfile(
                        modProfileId = modProfile.id,
                        modVariantOverrides = if (useNewerModVariantsForMissing) newerVersionsAvailable else emptyList()
                    )
                    alertDialogSetter.invoke(null)
                }
            ) {
                val count = missingVariants.count()
                if (useNewerModVariantsForMissing)
                    Text("Swap and use $count newer version${missingVariants.sIfPlural()}")
                else
                    Text("Swap without $count mod${missingVariants.sIfPlural()}")
            }
        },
        dismissButton = {
            SmolSecondaryButton(
                onClick = { alertDialogSetter.invoke(null) }
            ) { Text("Cancel") }
        }
    )
}

/**
 * @param modVariantOverrides List of mod variants to use in the mod profile, irrespective of what the mod profile uses for those mods.
 */
private fun swapModProfile(modProfileId: String, modVariantOverrides: List<UserProfile.ModProfile.ShallowModVariant>) {
    GlobalScope.launch(Dispatchers.Default) {
        SL.userModProfileManager.switchModProfile(modProfileId, modVariantOverrides)
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
    ),
    dateCreated = ZonedDateTime.now(),
    dateModified = ZonedDateTime.now(),
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
    ),
    dateCreated = ZonedDateTime.now(),
    dateModified = ZonedDateTime.now(),
)
private val mockUserProfile = UserManager.defaultProfile

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