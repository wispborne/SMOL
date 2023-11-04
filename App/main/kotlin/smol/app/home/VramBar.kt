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

@file:OptIn(ExperimentalFoundationApi::class)

package smol.app.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import smol.access.SL
import smol.access.model.Mod
import smol.app.composables.SmolTooltipArea
import smol.app.composables.SmolTooltipText
import smol.app.themes.SmolTheme
import smol.utilities.bytesAsReadableMB

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RowScope.vramBar(mod: Mod, largestVramUsage: Long?) {
    val variant = mod.findFirstEnabledOrHighestVersion
    val vramResult =
        SL.vramChecker.vramUsage.collectAsState().value?.get(variant?.smolId)
    SmolTooltipArea(tooltip = {
        SmolTooltipText(
            text = when {
                vramResult?.bytesForMod?.bytesAsReadableMB != null -> {
                    buildString {
                        appendLine("Version: ${vramResult.version}\n")
                        appendLine(vramResult.bytesForMod.bytesAsReadableMB
                            .let { if (vramResult.bytesForMod == 0L) "No impact" else it })
                        append("${vramResult.imageCount} images")
                    }
                }
                variant?.modsFolderInfo == null ->
                    "Install mod before checking VRAM.\nUnable to scan a compressed archive."
                else -> "Not yet scanned."
            }
        )
    }) {
        // VRAM relative size bar
        if (vramResult?.bytesForMod != null && vramResult.bytesForMod > 0 && largestVramUsage != null) {
            val widthWeight =
                (vramResult.bytesForMod.toFloat() / largestVramUsage)
                    .coerceIn(0.01f, 0.99f)
            Row(Modifier.height(28.dp).padding(end = 16.dp)) {
                Box(
                    Modifier
                        .background(
                            color = MaterialTheme.colors.primary,
                            shape = SmolTheme.smolNormalButtonShape()
                        )
                        .weight(widthWeight)
                        .height(8.dp)
                        .align(Alignment.Bottom)
                )
                Spacer(Modifier.weight(1f - widthWeight))
            }
        }
        Text(
            text =
            vramResult?.bytesForMod?.bytesAsReadableMB
                ?.let { if (vramResult.bytesForMod == 0L) "None" else it }
                ?: "scan needed",
            modifier = Modifier.fillMaxSize()
                .align(Alignment.CenterVertically),
            color =  if (vramResult == null) SmolTheme.dimmedTextColor().copy(alpha = .5f) else SmolTheme.dimmedTextColor(),
            fontStyle = if (vramResult == null) FontStyle.Italic else FontStyle.Normal
        )
    }
}