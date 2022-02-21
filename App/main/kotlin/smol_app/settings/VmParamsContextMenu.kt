package smol_app.settings

import AppScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.GridCells
import androidx.compose.foundation.lazy.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.tinylog.kotlin.Logger
import oshi.SystemInfo
import smol_access.SL
import smol_app.UI
import smol_app.composables.SmolButton
import smol_app.composables.SmolTextField
import smol_app.composables.SmolTooltipArea
import smol_app.composables.SmolTooltipText
import smol_app.themes.SmolTheme
import kotlin.math.floor
import kotlin.math.roundToInt


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppScope.changeRamSection(modifier: Modifier = Modifier) {
    val showVmParamsMenu = remember { mutableStateOf(false) }
    Column(
        modifier = modifier.padding(start = 16.dp, bottom = 8.dp)
    ) {
        val assignedRam = SL.UI.vmParamsManager.vmparams.collectAsState().value?.xmx
        Row(Modifier.padding(bottom = 4.dp)) {
            Text(
                text = "Assigned RAM: $assignedRam",
                style = SettingsView.settingLabelStyle(),
                modifier = Modifier.align(Alignment.CenterVertically)
            )
            SmolTooltipArea(
                tooltip = {
                    SmolTooltipText(
                        "Starsector is only able to use as much RAM, or memory, as you allow. The amount required increases with more mods." +
                                "\n\nNote: RAM is different from VRAM, which is not customizable."
                    )
                }
            ) {
                Icon(
                    painter = painterResource("icon-help-circled.svg"),
                    modifier = Modifier.padding(start = 8.dp).align(Alignment.CenterVertically),
                    contentDescription = null
                )
            }
        }

        val isMissingAdmin = SL.UI.vmParamsManager.isMissingAdmin() == true
        SmolTooltipArea(
            tooltip = {
                SmolTooltipText(
                    if (isMissingAdmin) "Run SMOL as Admin to modify vmparams."
                    else "Adjust the RAM allocated to the game. Modifies vmparams."
                )
            },
            delayMillis = SmolTooltipArea.shortDelay
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isMissingAdmin) {
                    Icon(
                        painter = painterResource("icon-admin-shield.svg"),
                        tint = MaterialTheme.colors.secondary,
                        modifier = Modifier.padding(end = 8.dp),
                        contentDescription = null
                    )
                }
                SmolButton(
                    onClick = { showVmParamsMenu.value = true },
                    enabled = !isMissingAdmin
                ) {
                    Text(text = "Click to Set...")
                }
            }
        }
    }

    vmParamsContextMenu(showVmParamsMenu)
}

@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
@Composable
fun vmParamsContextMenu(
    showContextMenu: MutableState<Boolean>
) {
    val cellMinWidth = 100.dp
    val width = cellMinWidth * 2
    val gridHeight = 240.dp
    val assignedRam = SL.UI.vmParamsManager.vmparams.collectAsState().value?.xmx
    val presetsInGb = 2..6

    val availableSystemRam = runCatching { SystemInfo().hardware.memory.available }
        .onFailure { Logger.warn(it) }
        .getOrNull()
    val totalSystemRam = runCatching { SystemInfo().hardware.memory.total }
        .onFailure { Logger.warn(it) }
        .getOrNull()
    // Use gibabytes here because that's how system ram is measured
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
        expanded = showContextMenu.value,
        onDismissRequest = { showContextMenu.value = false }) {
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
                text = "System: ${"%.0f GB".format(totalSystemRam.toFloat() / bytesPerGibibyte)}",
                fontFamily = SmolTheme.fireCodeFont,
                fontSize = 12.sp
            )
        }
        if (availableSystemRam != null) {
            Text(
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
                text = "Free: ${"%.1f GB".format(availableSystemRam.toFloat() / bytesPerGibibyte)}",
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
                            SL.UI.vmParamsManager.update { it?.run { withGb(presetGb) } }
                        }
                    ) {
                        Text(
                            text = "${presetGb * 1000} MB",
                            textAlign = TextAlign.Center,
                            fontWeight = if (presetGb == recommendation) FontWeight.ExtraBold else FontWeight.Normal,
                        )
                    }

                    if (presetGb == recommendation) {
                        Column(
                            modifier = Modifier
//                                .offset(y = (-4).dp)
                                .align(Alignment.CenterHorizontally)
                        ) {
                            Text(
                                text = "Suggested",
                                style = MaterialTheme.typography.caption,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                            )
                            Text(
                                text = "for you",
                                style = MaterialTheme.typography.caption,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                            )
                        }
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
        SmolButton(
            modifier = Modifier.padding(top = 8.dp, bottom = 0.dp).fillMaxWidth().align(Alignment.CenterHorizontally),
            enabled = mb.toIntOrNull() != null,
            onClick = {
                if (mb.toIntOrNull() != null) {
                    SL.UI.vmParamsManager.update { it?.run { withMb(mb.toInt()) } }
                }
            }
        ) {
            Text(text = "Apply Custom")
        }
    }
}