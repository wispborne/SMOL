package smol_app.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.mouseClickable
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import smol_access.model.Mod
import smol_app.composables.SmolTooltipArea
import smol_app.composables.SmolTooltipText
import smol_app.util.bytesAsReadableMiB

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ModGridSectionHeader(
    contentPadding: Dp,
    isCollapsed: MutableState<Boolean>,
    groupName: String,
    modsInGroup: List<Mod>,
    vramPosition: MutableState<Dp>
) {
    Card(
        elevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = 8.dp,
                bottom = 8.dp,
                start = contentPadding,
                end = contentPadding
            )
    ) {
        Box {
            Row(modifier = Modifier.mouseClickable {
                if (this.buttons.isPrimaryPressed) {
                    isCollapsed.value = isCollapsed.value.not()
                }
            }) {
                val arrowAngle by animateFloatAsState(if (isCollapsed.value) -90f else 0f)
                Icon(
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .padding(start = 4.dp)
                        .rotate(arrowAngle),
                    imageVector = Icons.Outlined.ArrowDropDown,
                    contentDescription = null,
                )
                Text(
                    text = "$groupName (${modsInGroup.count()})",
                    color = MaterialTheme.colors.onSurface,
                    modifier = Modifier
                        .padding(8.dp),
                    fontWeight = FontWeight.Bold
                )
            }
            val allImpacts = modsInGroup.map { getVramImpactForMod(it) }
            val totalBytes =
                allImpacts.sumOf { it?.bytesForMod ?: 0L }.bytesAsReadableMiB
            val totalImages = "${allImpacts.sumOf { it?.imageCount ?: 0 }} images"
            SmolTooltipArea(
                tooltip = { SmolTooltipText(text = "All ${groupName.lowercase()} mods\n\n$totalBytes\n$totalImages") },
                modifier = Modifier
                    .padding(start = vramPosition.value + 16.dp)
                    .align(Alignment.CenterStart),
            ) {
                Text(
                    text = "Σ $totalBytes",
                    style = MaterialTheme.typography.body2,
                    modifier = Modifier.alpha(0.7f)
                )
            }
        }
    }
}