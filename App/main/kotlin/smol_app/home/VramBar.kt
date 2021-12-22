package smol_app.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import smol_access.SL
import smol_access.model.Mod
import smol_app.composables.SmolTooltipArea
import smol_app.composables.SmolTooltipText
import smol_app.themes.SmolTheme
import smol_app.util.bytesAsReadableMiB

@Composable
fun RowScope.vramBar(mod: Mod, largestVramUsage: Long?) {
    val vramResult =
        SL.vramChecker.vramUsage.value?.get(mod.findHighestVersion?.smolId)
    SmolTooltipArea(tooltip = {
        if (vramResult != null) {
            val impactText =
                vramResult.bytesForMod.bytesAsReadableMiB
                    .let { if (vramResult.bytesForMod == 0L) "No impact" else it }
            SmolTooltipText(
                text = buildString {
                    appendLine("Version: ${vramResult.version}")
                    appendLine(impactText)
                    append("${vramResult.imageCount} images")
                }
            )
        }
    }) {
        // VRAM relative size bar
        if (vramResult?.bytesForMod != null && vramResult.bytesForMod > 0 && largestVramUsage != null) {
            val widthWeight =
                (vramResult.bytesForMod.toFloat() / largestVramUsage)
                    .coerceIn(0.01f, 0.99f)
            Row(Modifier.height(28.dp)) {
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
            vramResult?.bytesForMod?.bytesAsReadableMiB
                ?.let { if (vramResult.bytesForMod == 0L) "None" else it }
                ?: "Unavailable",
            modifier = Modifier.fillMaxSize()
                .align(Alignment.CenterVertically),
            color = SmolTheme.dimmedTextColor()
        )
    }
}