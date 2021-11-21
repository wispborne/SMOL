package views

import AppState
import SmolAlertDialog
import SmolButton
import SmolSecondaryButton
import SmolTextField
import SmolTheme
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
import com.arkivanov.decompose.pop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import smol_access.SL
import smol_access.model.UserProfile
import util.ellipsizeAfter

@OptIn(
    ExperimentalMaterialApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
@Composable
@Preview
fun AppState.ProfilesView(
    modifier: Modifier = Modifier
) {
    var modProfileIdShowingDeleteConfirmation: Int? by remember { mutableStateOf(null) }
    var userProfile by remember { mutableStateOf(SL.userManager.getUserProfile()) }

    Scaffold(topBar = {
        TopAppBar {
            SmolButton(onClick = router::pop, modifier = Modifier.padding(start = 16.dp)) {
                Text("Back")
            }
            Text(
                modifier = Modifier.padding(8.dp).padding(start = 16.dp),
                text = "Mod Profiles",
                fontWeight = FontWeight.Bold
            )
        }
    }) {
        Box(modifier.padding(16.dp)) {
            var modVariants by remember {
                mutableStateOf(SL.access.mods.value?.flatMap { it.variants }?.associateBy { it.smolId } ?: emptyMap())
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
                    var isEditMode by remember { mutableStateOf(false) }
                    var modProfileName by remember { mutableStateOf(modProfile.name) }
                    Card(
                        modifier = Modifier.wrapContentHeight()
                            .run {
                                // Highlight active profile
                                if (isActiveProfile) this.border(
                                    width = 4.dp,
                                    color = SmolTheme.highlight(),
                                    shape = SmolTheme.smolFullyClippedButtonShape()
                                ) else this
                            },
                        shape = SmolTheme.smolFullyClippedButtonShape()
                    ) {
                        SelectionContainer {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row {
                                    if (!isEditMode) {
                                        Text(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(end = 16.dp)
                                                .align(Alignment.CenterVertically),
                                            fontWeight = FontWeight.Bold,
                                            text = modProfileName
                                        )
                                    } else {
                                        SmolTextField(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(end = 16.dp)
                                                .align(Alignment.CenterVertically),
                                            value = modProfileName,
                                            label = { Text(text = "Profile Name") },
                                            singleLine = true,
                                            onValueChange = { newValue ->
                                                modProfileName = newValue
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
                                    Text(
                                        modifier = Modifier.align(Alignment.CenterVertically),
                                        text = "${modProfile.enabledModVariants.count()} mods",
                                        fontFamily = SmolTheme.fireCodeFont,
                                        fontSize = 12.sp,
                                    )
                                }
                                Box {
                                    Text(
                                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp).align(Alignment.Center),
                                        fontFamily = SmolTheme.fireCodeFont,
                                        fontWeight = FontWeight.Light,
                                        fontSize = 14.sp,
                                        text = modProfile.enabledModVariants.joinToString(separator = "\n") {
                                            val modVariant = modVariants[it.smolVariantId]
                                            "${modVariant?.modInfo?.name?.ellipsizeAfter(28)}: "
                                        })
                                    Text(
                                        modifier = Modifier.padding(top = 16.dp).align(Alignment.CenterEnd),
                                        fontFamily = SmolTheme.fireCodeFont,
                                        fontWeight = FontWeight.Light,
                                        fontSize = 14.sp,
                                        text = modProfile.enabledModVariants.joinToString(separator = "\n") {
                                            val modVariant = modVariants[it.smolVariantId]
                                            modVariant?.modInfo?.version.toString()
                                        })
                                }
                                DisableSelection {
                                    Row(
                                        modifier = Modifier.align(Alignment.Start)
                                            .padding(top = 16.dp)
                                    ) {
                                        IconToggleButton(
                                            modifier = Modifier
                                                .padding(start = 8.dp)
                                                .background(
                                                    shape = SmolTheme.smolNormalButtonShape(),
                                                    color = MaterialTheme.colors.primary
                                                )
                                                .run {
                                                    if (isEditMode) this.border(
                                                        width = 2.dp,
                                                        color = SmolTheme.highlight(),
                                                        shape = SmolTheme.smolNormalButtonShape()
                                                    ) else this
                                                }
                                                .height(ButtonDefaults.MinHeight)
                                                .width(ButtonDefaults.MinHeight),
                                            checked = isEditMode,
                                            onCheckedChange = { isEditMode = !isEditMode }
                                        ) {
                                            Icon(
                                                painter = painterResource("pencil-outline.svg"),
                                                contentDescription = null,
                                                tint = SmolTheme.dimmedIconColor()
                                            )
                                        }
                                        IconButton(
                                            modifier = Modifier
                                                .padding(start = 8.dp)
                                                .background(
                                                    shape = SmolTheme.smolNormalButtonShape(),
                                                    color = MaterialTheme.colors.primary
                                                )
                                                .height(SmolTheme.iconHeightWidth())
                                                .width(SmolTheme.iconHeightWidth()),
                                            onClick = {
                                                modProfileIdShowingDeleteConfirmation = modProfile.id
                                            }
                                        ) {
                                            Icon(
                                                painter = painterResource("trash-can-outline.svg"),
                                                contentDescription = null,
                                                tint = SmolTheme.dimmedIconColor()
                                            )
                                        }

                                        Spacer(modifier = Modifier.weight(1f))

                                        SmolButton(
                                            enabled = !isActiveProfile,
                                            onClick = {
                                                if (!isActiveProfile) {
                                                    GlobalScope.launch(Dispatchers.Default) {
                                                        SL.userManager.switchModProfile(modProfile.id)
                                                        withContext(Dispatchers.Main) {
                                                            userProfile = SL.userManager.getUserProfile()
                                                            modVariants =
                                                                (SL.access.mods.value ?: emptyList())
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
                                            sortOrder = SL.userManager.getUserProfile().modProfiles.maxOf { it.sortOrder } + 1
                                        )
                                        newProfileName = ""
                                        userProfile = SL.userManager.getUserProfile()
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
    }

    if (modProfileIdShowingDeleteConfirmation != null) {
        val profile =
            SL.userManager.getUserProfile().modProfiles.firstOrNull { it.id == modProfileIdShowingDeleteConfirmation }
        SmolAlertDialog(
            modifier = Modifier,
            onDismissRequest = { modProfileIdShowingDeleteConfirmation = null },
            title = { Text("Confirm deletion") },
            text = {
                Text("Are you sure you want to delete \"${profile?.name}\"?")
            },
            confirmButton = {
                SmolButton(onClick = {
                    SL.userManager.removeModProfile(
                        modProfileIdShowingDeleteConfirmation ?: run {
                            modProfileIdShowingDeleteConfirmation = null

                            return@SmolButton
                        })
                    modProfileIdShowingDeleteConfirmation = null
                    userProfile = SL.userManager.getUserProfile()
                }) { Text("Delete") }
            },
            dismissButton = {
                SmolSecondaryButton(onClick = { modProfileIdShowingDeleteConfirmation = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}