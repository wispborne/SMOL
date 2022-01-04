package smol_app.toasts

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import smol_access.SL
import smol_access.model.ModVariant
import smol_app.composables.SmolButton
import smol_app.util.smolPreview

@Composable
fun toastInstalledCard(
    modVariant: ModVariant,
    requestToastDismissal: () -> Unit
) {
    Row {
        Column {
            Text(
                modifier = Modifier,
                text = "${modVariant.modInfo.name} ${modVariant.modInfo.version} found.",
                fontSize = 12.sp
            )

            if (!modVariant.mod(SL.access).isEnabled(modVariant)) {
                SmolButton(
                    modifier = Modifier
                        .padding(top = 8.dp),
                    onClick = {
                        GlobalScope.launch { SL.access.changeActiveVariant(
                            mod = modVariant.mod(SL.access),
                            modVariant = modVariant
                        ) }
                        requestToastDismissal.invoke()
                    }
                ) {
                    Text("Enable")
                }
            }
        }

        IconButton(
            modifier = Modifier
                .padding(start = 8.dp)
                .align(Alignment.CenterVertically)
                .size(16.dp),
            onClick = {
                requestToastDismissal.invoke()
            }
        ) {
            Icon(imageVector = Icons.Default.Close, contentDescription = null)
        }
    }
}

@Preview
@Composable
fun toastInstalledCardPreview() = smolPreview {
    toastInstalledCard(ModVariant.MOCK, requestToastDismissal = {})
}