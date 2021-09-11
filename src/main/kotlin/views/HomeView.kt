package views

import ModGrid
import SL
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.Router
import com.arkivanov.decompose.push
import rememberRouter

@Composable
@Preview
fun homeView(
    router: Router<Screen, Any>,
    modifier: Modifier = Modifier
) {
    Scaffold(topBar = {
        TopAppBar {
            Button(onClick = {
                router.push(Screen.Settings)
            }) {
                Text("Settings")
            }
        }
    }) {
        ModGrid(
            SL.loader.getMods(),
            Modifier.padding(16.dp)
//                    buildList(30) {
//                        repeat(30) { this.add(Mod(ModInfo(it.toString(), "$it.5.2"))) }
//                    }
        )
    }
}