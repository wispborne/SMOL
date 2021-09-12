package views

import AppState
import SL
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.push
import navigation.Screen

@Composable
@Preview
fun AppState.homeView(
    modifier: Modifier = Modifier
) {
    Scaffold(topBar = {
        TopAppBar {
            Button(
                onClick = { router.push(Screen.Settings) },
                modifier = Modifier.padding(start = 16.dp)
            ) {
                Text("Settings")
            }
        }
    }) {
        if (SL.gamePath.isValidGamePath(SL.appConfig.gamePath ?: "")) {
            ModGridView(
                SL.gamePath.getMods(),
                Modifier.padding(16.dp)
            )
        } else {
            Column(
                Modifier.fillMaxWidth().fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "I can't find any mods! Did you set your game path yet?")
                OutlinedButton(
                    onClick = { router.push(Screen.Settings) },
                    Modifier.padding(top = 8.dp)
                ) {
                    Text("Settings")
                }
            }
        }
    }
}