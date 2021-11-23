package smol_app.views

import smol_access.SL
import smol_app.SmolButton
import smol_app.SmolSecondaryButton
import smol_app.SmolTextField
import smol_app.SmolTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.GridCells
import androidx.compose.foundation.lazy.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.material.CursorDropdownMenu
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.tinylog.kotlin.Logger
import oshi.SystemInfo
import smol_app.util.vmParamsManager
import kotlin.math.floor
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterialApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun vmParamsContextMenu(
    showContextMenu: Boolean,
    onShowContextMenuChange: (Boolean) -> Unit,
) {
    val cellMinWidth = 88.dp
    val width = cellMinWidth * 2
    val gridHeight = 200.dp
    var assignedRam by remember { mutableStateOf(SL.vmParamsManager.read()?.xmx) }
    val presetsInGb = 2..6

    val availableSystemRam = runCatching { SystemInfo().hardware.memory.available }
        .onFailure { Logger.warn(it) }
        .getOrNull()
    val totalSystemRam = runCatching { SystemInfo().hardware.memory.total }
        .onFailure { Logger.warn(it) }
        .getOrNull()
    val bytesPerGibibyte = 1073741824
    val recommendation = if (availableSystemRam == null) null
    else {
        floor(availableSystemRam.toFloat() / bytesPerGibibyte).roundToInt()
            .minus(1) // So there's at least 1 GB free
            .coerceAtLeast(presetsInGb.first)
            .coerceAtMost(presetsInGb.last)
    }

    CursorDropdownMenu(
        modifier = Modifier.padding(16.dp).width(width),
        expanded = showContextMenu,
        onDismissRequest = { onShowContextMenuChange(false) }) {
        Text(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            text = "Set the amount of RAM assigned to the game."
        )
        Text(
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 16.dp),
            text = "Assigned: $assignedRam",
            fontFamily = SmolTheme.fireCodeFont,
            fontSize = 12.sp
        )
        if (totalSystemRam != null) {
            Text(
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
                text = "System: ${"%.0f GiB".format(totalSystemRam.toFloat() / bytesPerGibibyte)}",
                fontFamily = SmolTheme.fireCodeFont,
                fontSize = 12.sp
            )
        }
        if (availableSystemRam != null) {
            Text(
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
                text = "Free: ${"%.1f GiB".format(availableSystemRam.toFloat() / bytesPerGibibyte)}",
                fontFamily = SmolTheme.fireCodeFont,
                fontSize = 12.sp
            )
        }
        LazyVerticalGrid(
            modifier = Modifier.width(width).height(gridHeight).padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            cells = GridCells.Adaptive(cellMinWidth)
        ) {
            this.items(items = presetsInGb.toList()) { presetGb ->
                Column {
                    SmolButton(
                        modifier = Modifier.wrapContentWidth().wrapContentHeight().align(Alignment.CenterHorizontally),
                        shape = SmolTheme.smolFullyClippedButtonShape(),
                        onClick = {
                            SL.vmParamsManager.update { it?.run { withGb(presetGb) } }
                            assignedRam = SL.vmParamsManager.read()?.xmx
                        }
                    ) { Text(text = "$presetGb GB") }

                    if (presetGb == recommendation) {
                        Text(
                            text = "Suggested",
                            style = MaterialTheme.typography.caption,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        }

        Text(
            text = "- or -",
            style = MaterialTheme.typography.caption,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp)
        )

        var mb by remember { mutableStateOf("") }
        SmolTextField(
            modifier = Modifier.padding(top = 16.dp).fillMaxWidth().align(Alignment.CenterHorizontally),
            value = mb,
            onValueChange = { if (it.matches(Regex("[0-9]*"))) mb = it },
            singleLine = true,
            maxLines = 1,
            trailingIcon = { Text("MB") },
            label = { Text("Custom") }
        )
        SmolSecondaryButton(
            modifier = Modifier.padding(top = 8.dp, bottom = 0.dp).fillMaxWidth().align(Alignment.CenterHorizontally),
            onClick = {
                SL.vmParamsManager.update { it?.run { withMb(mb.toIntOrNull() ?: 1500) } }
                assignedRam = SL.vmParamsManager.read()?.xmx
            }
        ) {
            Text(text = "Apply Custom")
        }
    }
}