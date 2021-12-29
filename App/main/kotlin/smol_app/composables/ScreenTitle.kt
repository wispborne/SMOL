package smol_app.composables

import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import smol_app.themes.SmolTheme

@Composable
fun screenTitle(modifier: Modifier = Modifier, text: String) {
    Text(
        modifier = modifier.padding(8.dp).padding(start = 16.dp),
        text = text,
        fontWeight = FontWeight.Bold,
        fontFamily = SmolTheme.orbitronSpaceFont,
        color = MaterialTheme.colors.onSurface
    )
}