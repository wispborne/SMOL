import androidx.compose.desktop.DesktopMaterialTheme
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.extensions.compose.jetbrains.Children
import views.Screen
import views.homeView
import views.settingsView

@OptIn(ExperimentalStdlibApi::class)
@Composable
@Preview
fun App() {
    val router = rememberRouter<Screen>(
        initialConfiguration = { Screen.Home },
        handleBackButton = true
    )

    DesktopMaterialTheme(colors = DarkColors) {
        Children(router.state) { screen ->
            when (screen.configuration) {
                is Screen.Home -> homeView(router)
                is Screen.Settings -> settingsView(router)
            }
        }
    }
}