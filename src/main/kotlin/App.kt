import androidx.compose.desktop.DesktopMaterialTheme
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalStdlibApi::class)
@Composable
@Preview
fun App() {
    var text by remember { mutableStateOf("Hello, World!") }

    DesktopMaterialTheme(colors = DarkColors) {
        Scaffold(topBar = { TopAppBar { } }) {
            Column(
                Modifier.padding(16.dp)
            ) {
                Button(onClick = {
                    text = "Hello, Desktop!"
                }) {
                    Text(text)
                }
                ModGrid(
                    SL.loader.getMods()
//                    buildList(30) {
//                        repeat(30) { this.add(Mod(ModInfo(it.toString(), "$it.5.2"))) }
//                    }
                )
            }
        }
    }
}