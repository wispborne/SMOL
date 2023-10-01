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

package smol.app.home

import AppScope
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerMoveFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.tinylog.Logger
import smol.access.Access
import smol.access.SL
import smol.access.model.Mod
import smol.access.model.ModVariant
import smol.app.composables.*
import smol.app.themes.SmolTheme
import smol.app.util.ModState
import smol.app.util.state
import smol.utilities.asList


sealed class DropdownAction {
    data class ChangeToVariant(val variant: ModVariant) : DropdownAction()
    object Disable : DropdownAction()
}

@ExperimentalFoundationApi
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AppScope.ModVariantsDropdown(
    modifier: Modifier = Modifier,
    mod: Mod
) {
    val firstEnabledVariant = mod.findFirstEnabled
    val font = SmolTheme.orbitronSpaceFont

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
                .sortedByDescending { it.bestVersion }

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
    val modState =
        SL.access.modModificationState.collectAsState().value[mod.id] ?: smol.access.Access.ModModificationState.Ready

    Box(modifier) {
        Box(Modifier.width(IntrinsicSize.Min)) {
            val hasEnabledVariant = mod.enabledVariants.isNotEmpty()
            val hasSingleVariant = mod.variants.size == 1

            SmolButton(
                onClick = {
                    if (hasSingleVariant) {
                        // If only one variant, one-click to enable and disable
                        GlobalScope.launch {
                            runCatching {
                                if (hasEnabledVariant) {
                                    SL.access.disableMod(mod)
                                } else {
                                    SL.access.changeActiveVariant(mod, mod.variants.firstOrNull())
                                }
                            }
                        }
                    } else {
                        expanded = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterStart),
                shape = SmolTheme.smolFullyClippedButtonShape(),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = when (mod.state) {
                        ModState.Enabled -> MaterialTheme.colors.primary
                        else -> MaterialTheme.colors.primaryVariant
                    },
                )
            ) {
                if (modState != smol.access.Access.ModModificationState.Ready) {
                    SmolTooltipArea(tooltip = {
                        SmolTooltipText(
                            when (modState) {
                                Access.ModModificationState.DisablingVariants -> "Disabling..."
                                Access.ModModificationState.EnablingVariant -> "Enabling..."
                                Access.ModModificationState.BackingUpVariant -> "Backup in progress"
                                Access.ModModificationState.DeletingVariants -> "Deleting..."
                                Access.ModModificationState.Ready -> "Ready"
                            }
                        )
                    }
                    ) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = MaterialTheme.colors.onPrimary)
                    }
                } else {
                    // Text of the dropdown menu, current state of the mod
                    if (mod.enabledVariants.size > 1) {
                        SmolTooltipArea(tooltip = {
                            SmolTooltipText(
                                text = "Warning: ${mod.enabledVariants.size} versions of " +
                                        "${mod.findHighestVersion!!.modInfo.name} in the mods folder." +
                                        " Remove one.",
                                fontFamily = SmolTheme.normalFont
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
                            hasSingleVariant && hasEnabledVariant -> "Disable"
                            // If there is an enabled variant, show its version string.
                            hasEnabledVariant -> mod.enabledVariants.joinToString {
                                it.modInfo.version.toString().replace(' ', ' ')
                            }

                            hasSingleVariant && !hasEnabledVariant -> "Enable"
                            // If no enabled variant, show "Disabled"
                            else -> "Enable"
                        },
                        fontWeight = FontWeight.Bold,
                        fontFamily = font,
                        maxLines = 1
                    )
                    if (mod.variants.size > 1) {
                        SmolDropdownArrow(
                            modifier = Modifier.align(Alignment.CenterVertically),
                            expanded = expanded
                        )
                    }
                }

                if (expanded) {
                    val background = MaterialTheme.colors.background
                    MaterialTheme(shapes = Shapes(medium = SmolTheme.smolFullyClippedButtonShape())) {
                        DropdownMenu(
                            expanded = expanded,
                            modifier = Modifier
                                .background(background)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colors.primary,
                                    shape = SmolTheme.smolFullyClippedButtonShape()
                                ),
                            onDismissRequest = { expanded = false }
                        ) {
                            dropdownMenuItems.forEach { action ->
                                Box {
                                    var isHovered by remember { mutableStateOf(false) }

                                    DropdownMenuItem(
                                        modifier = Modifier.sizeIn(maxWidth = 400.dp)
                                            .pointerMoveFilter(
                                                onEnter = { isHovered = true; false },
                                                onExit = { isHovered = false; false }
                                            )
                                            .background(background),
                                        onClick = {
                                            expanded = false
                                            Logger.debug { "Selected $action." }

                                            // Don't use composition scope, we don't want
                                            // it to cancel an operation due to a UI recomposition.
                                            // A two-step operation will trigger a mod refresh and therefore recomposition and cancel
                                            // the second part of the operation!
                                            GlobalScope.launch {
                                                runCatching {
                                                    // Change mod state
                                                    when (action) {
                                                        is DropdownAction.ChangeToVariant -> {
                                                            SL.access.changeActiveVariant(mod, action.variant)
                                                        }

                                                        is DropdownAction.Disable -> {
                                                            SL.access.disableMod(mod = mod)
                                                        }
                                                    }
                                                }
                                                    .onFailure { Logger.error(it) }
                                            }
                                        }) {
                                        Row {
                                            @Composable
                                            fun shield() {
                                                SmolTooltipArea(
                                                    tooltip = {
                                                        SmolTooltipText(text = "Run SMOL as Admin.")
                                                    },
                                                    delayMillis = SmolTooltipArea.shortDelay
                                                ) {
                                                    Icon(
                                                        painter = painterResource("icon-admin-shield.svg"),
                                                        tint = MaterialTheme.colors.secondary,
                                                        modifier = Modifier.padding(end = 8.dp),
                                                        contentDescription = null
                                                    )
                                                }
                                            }
                                            when (action) {
                                                is DropdownAction.ChangeToVariant -> {
                                                    if (action.variant.isMissingAdmin()) {
                                                        shield()
                                                    }
                                                }

                                                is DropdownAction.Disable -> {
                                                    if (firstEnabledVariant?.isMissingAdmin() == true) {
                                                        shield()
                                                    }
                                                }
                                            }
                                            Text(
                                                modifier = Modifier
                                                    .align(Alignment.CenterVertically)
                                                    .weight(1f),
                                                text = when (action) {
                                                    is DropdownAction.ChangeToVariant -> action.variant.modInfo.version.toString()
                                                    is DropdownAction.Disable -> "Disable"
                                                },
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = font
                                            )

                                            val trashIconSize = 18.dp
                                            if (isHovered && action is DropdownAction.ChangeToVariant) {
                                                SmolIconButton(
                                                    onClick = {
                                                        expanded = false
                                                        this@ModVariantsDropdown.alertDialogSetter.invoke {
                                                            DeleteModVariantDialog(
                                                                variantsToConfirmDeletionOf = action.variant.asList(),
                                                                onDismiss = this@ModVariantsDropdown::dismissAlertDialog
                                                            )
                                                        }
                                                    },
                                                    modifier = Modifier
                                                        .align(Alignment.CenterVertically),
                                                    rippleRadius = 20.dp
                                                ) {
                                                    Icon(
                                                        painter = painterResource("icon-trash.svg"),
                                                        modifier = Modifier
                                                            .size(trashIconSize),
                                                        contentDescription = null
                                                    )
                                                }
                                            } else {
                                                Spacer(Modifier.padding(start = 12.dp).size(trashIconSize))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}