package smol_app.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.tinylog.Logger
import smol_access.Constants
import smol_access.SL
import smol_access.model.Mod
import smol_access.model.ModVariant
import smol_app.composables.SmolButton
import smol_app.composables.SmolDropdownArrow
import smol_app.composables.SmolTooltipArea
import smol_app.composables.SmolTooltipText
import smol_app.themes.SmolTheme
import smol_app.util.ModState
import smol_app.util.state


sealed class DropdownAction {
    data class ChangeToVariant(val variant: ModVariant) : DropdownAction()
    object Disable : DropdownAction()
    data class MigrateMod(val mod: Mod) : DropdownAction()
    data class ResetToArchive(val variant: ModVariant) : DropdownAction()
}

@ExperimentalFoundationApi
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun modStateDropdown(modifier: Modifier = Modifier, mod: Mod) {
    val firstEnabledVariant = mod.findFirstEnabled

    /**
     * Disable: at least one variant enabled
     * Switch to variant: other variant (in /mods, staging, or archives)
     * Reinstall: has archive
     * Snapshot (bring up dialog asking which variants to snapshot): at least one variant without archive
     */
    val dropdownMenuItems: List<DropdownAction> = mutableListOf<DropdownAction>()
        .run {
            val otherVariantsThanEnabled = mod.variants
                .filter { variant ->
                    firstEnabledVariant == null
                            || mod.enabledVariants.any { enabledVariant -> enabledVariant.smolId != variant.smolId }
                }

            if (otherVariantsThanEnabled.any()) {
                val otherVariants = otherVariantsThanEnabled
                    .map { DropdownAction.ChangeToVariant(variant = it) }
                this.addAll(otherVariants)
            }

            if (firstEnabledVariant != null) {
                this.add(DropdownAction.Disable)
            }

            // If the enabled variant has an archive, they can reset the state back to the archived state.
//            if (firstEnabledVariant?.archiveInfo != null) {
//                this.add(DropdownAction.ResetToArchive(firstEnabledVariant))
//            }

            this
        }


    var expanded by remember { mutableStateOf(false) }
    var selectedIndex by remember { mutableStateOf(0) }
    Box(modifier) {
        Box(Modifier.width(IntrinsicSize.Min)) {
            SmolButton(
                onClick = { expanded = true },
                modifier = Modifier
                    .align(Alignment.CenterStart),
                shape = SmolTheme.smolFullyClippedButtonShape(),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = when (mod.state) {
                        ModState.Enabled -> MaterialTheme.colors.primary
                        else -> MaterialTheme.colors.primaryVariant
                    }
                )
            ) {
                // Text of the dropdown menu, current state of the mod
                if (mod.enabledVariants.size > 1) {
                    SmolTooltipArea(tooltip = {
                        SmolTooltipText(
                            text = "Warning: ${mod.enabledVariants.size} versions of " +
                                    "${mod.findHighestVersion!!.modInfo.name} in the mods folder." +
                                    " Remove one.",
                            fontFamily = FontFamily.Default
                        )
                    }) {
                        Image(
                            painter = painterResource("icon-warning.svg"),
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(24.dp),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(color = MaterialTheme.colors.onPrimary)
                        )
                    }
                }
                Text(
                    text = when {
                        // If there is an enabled variant, show its version string.
                        mod.enabledVariants.isNotEmpty() -> mod.enabledVariants.joinToString { it.modInfo.version.toString() }
                        // If no enabled variant, show "Disabled"
                        else -> "Disabled"
                    },
                    fontWeight = FontWeight.Bold
                )
                SmolDropdownArrow(
                    Modifier.align(Alignment.CenterVertically),
                    expanded
                )
            }
            DropdownMenu(
                expanded = expanded,
                modifier = Modifier
                    .background(MaterialTheme.colors.background)
                    .border(1.dp, MaterialTheme.colors.primary, shape = SmolTheme.smolFullyClippedButtonShape()),
                onDismissRequest = { expanded = false }
            ) {
                val coroutineScope = rememberCoroutineScope()
                dropdownMenuItems.forEachIndexed { index, action ->
                    Box {
                        var background: Color? by remember { mutableStateOf(null) }
//                        val highlightColor = MaterialTheme.colors.surface
                        DropdownMenuItem(
                            modifier = Modifier.sizeIn(maxWidth = 400.dp)
                                .background(background ?: MaterialTheme.colors.background)
//                                .pointerMoveFilter( // doesn't work: https://github.com/JetBrains/compose-jb/issues/819
//                                    onEnter = {
//                                        Logger.debug { "Entered dropdown item" }
//                                        background = highlightColor;true
//                                    },
//                                    onExit = {
//                                        Logger.debug { "Exited dropdown item" }
//                                        background = null;true
//                                    }
                            ,
                            onClick = {
                                selectedIndex = index
                                expanded = false
                                Logger.debug { "Selected $action." }

                                // Don't use composition scope, we don't want
                                // it to cancel an operation due to a UI recomposition.
                                // A two-step operation will trigger a mod refresh and therefore recomposition and cancel
                                // the second part of the operation!
                                GlobalScope.launch {
                                    kotlin.runCatching {
                                        // Change mod state
                                        when (action) {
                                            is DropdownAction.ChangeToVariant -> {
                                                SL.access.changeActiveVariant(mod, action.variant)
                                            }
                                            is DropdownAction.Disable -> {
                                                SL.access.disableModVariant(firstEnabledVariant ?: return@runCatching)
                                            }
                                            is DropdownAction.MigrateMod -> {
                                                // TODO
//                                                SL.archives.compressModsInFolder(
//                                                    mod.modsFolderInfo?.folder ?: return@runCatching
//                                                )
                                            }
                                            is DropdownAction.ResetToArchive -> {
                                                // TODO
                                            }
                                        }
                                    }
                                        .onFailure { Logger.error(it) }
                                }
                            }) {
                            Text(
                                text = when (action) {
                                    is DropdownAction.ChangeToVariant -> action.variant.modInfo.version.toString()
                                    is DropdownAction.Disable -> "Disable"
                                    is DropdownAction.MigrateMod -> "Migrate to ${Constants.APP_NAME}"
                                    is DropdownAction.ResetToArchive -> "Reset to default"
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}