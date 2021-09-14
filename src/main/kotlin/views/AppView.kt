import androidx.compose.desktop.DesktopMaterialTheme
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.extensions.compose.jetbrains.Children
import navigation.Screen
import views.homeView
import views.settingsView

@OptIn(ExperimentalStdlibApi::class)
@Composable
@Preview
fun AppState.appView() {
    DesktopMaterialTheme(colors = DarkColors) {
        Children(router.state) { screen ->
            when (screen.configuration) {
                is Screen.Home -> homeView()
                is Screen.Settings -> settingsView()
            }
        }
    }
}