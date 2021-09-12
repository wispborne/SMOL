package views

import AppState
import SL
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.pop
import java.io.File
import javax.swing.JFileChooser

@OptIn(
    ExperimentalMaterialApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
@Composable
@Preview
fun AppState.settingsView(
    modifier: Modifier = Modifier
) {

    Scaffold(topBar = {
        TopAppBar {
            Button(onClick = router::pop, modifier = Modifier.padding(start = 16.dp)) {
                Text("Back")
            }
        }
    }) {
        Box(modifier) {
            Column(Modifier.padding(16.dp)) {
                var gamePath by remember { mutableStateOf(SL.appConfig.gamePath ?: "") }

                fun save() {
                    SL.appConfig.gamePath = gamePath
                }

                fun isValidGamePath(path: String) = SL.gamePath.isValidGamePath(path)

                LazyColumn(Modifier.weight(1f)) {
                    item {
                        Column {
                            var isGamePathError by remember { mutableStateOf(!isValidGamePath(gamePath)) }
                            Row {
                                TextField(
                                    value = gamePath,
                                    modifier = Modifier
                                        .weight(1f)
                                        .align(Alignment.CenterVertically),
                                    label = { Text("Game path") },
                                    isError = isGamePathError,
                                    onValueChange = {
                                        gamePath = it
                                        isGamePathError = !isValidGamePath(it)
                                    })
                                Button(
                                    modifier = Modifier
                                        .align(Alignment.CenterVertically)
                                        .padding(start = 16.dp),
                                    onClick = {
                                        JFileChooser().apply {
                                            currentDirectory =
                                                File(gamePath.ifBlank { null } ?: System.getProperty("user.home"))
                                            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY

                                            when (showOpenDialog(window)) {
                                                JFileChooser.APPROVE_OPTION -> gamePath = this.selectedFile.absolutePath
                                            }
                                        }
                                    }) {
                                    Text("Open")
                                }
                            }
                            if (isGamePathError) {
                                Text("Invalid game path", color = MaterialTheme.colors.error)
                            }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(modifier = Modifier.padding(end = 16.dp), onClick = {
                        save()
                        router.pop()
                    }) { Text("Ok") }
                    OutlinedButton(
                        modifier = Modifier.padding(end = 16.dp),
                        onClick = { router.pop() }) { Text("Cancel") }
                    OutlinedButton(onClick = { save() }) { Text("Apply") }
                }
            }
        }
    }
}