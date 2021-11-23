import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import com.arkivanov.decompose.extensions.compose.jetbrains.Children
import smol_app.navigation.Screen
import smol_app.AppState
import smol_app.views.*

@OptIn(ExperimentalStdlibApi::class)
@Composable
@Preview
fun AppState.appView() {
    MaterialTheme(
        colors = smol_app.SmolTheme.DarkColors,
        typography = Typography(
            button = TextStyle(fontFamily = smol_app.SmolTheme.orbitronSpaceFont)
        )
    ) {
        Children(router.state) { screen ->
            when (screen.configuration) {
                is Screen.Home -> homeView()
                is Screen.Settings -> settingsView()
                is Screen.Profiles -> ProfilesView()
                is Screen.ModBrowser -> ModBrowserView()
            }.run { }
            FileDropper()
        }
    }
}