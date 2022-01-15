package smol_app.browser

import kotlinx.coroutines.flow.MutableStateFlow
import java.nio.file.Path

data class DownloadItem(
    val id: String,
    val path: MutableStateFlow<Path?> = MutableStateFlow(null),
    val totalBytes: MutableStateFlow<Long?> = MutableStateFlow(null),
) {
    val fractionDone: MutableStateFlow<Float?> = MutableStateFlow(null)
    val progressBytes: MutableStateFlow<Long?> = MutableStateFlow(null)
    val bitsPerSecond: MutableStateFlow<Long?> = MutableStateFlow(null)
    val status: MutableStateFlow<Status> = MutableStateFlow(Status.NotStarted)

    sealed class Status {
        object NotStarted : Status()
        object Downloading : Status()
        object Completed : Status()
        object Cancelled : Status()

        data class Failed(val error: Throwable) : Status()
    }

    override fun toString(): String {
        return "DownloadItem(id='$id', path=${path.value}, totalBytes=${totalBytes.value}, progress=${progressBytes.value}, bitsPerSecond=${bitsPerSecond.value}, status=${status.value})"
    }

    companion object {
        val MOCK = DownloadItem(id = "")
            .apply {
                this.path.value = Path.of("C:/temp/perseanchronicles.7z")
                this.totalBytes.value = 1000
                this.progressBytes.value = 750
                this.bitsPerSecond.value = 512000
                this.status.value = DownloadItem.Status.Downloading
            }
    }
}