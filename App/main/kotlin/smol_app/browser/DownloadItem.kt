package smol_app.browser

import kotlinx.coroutines.flow.MutableStateFlow
import java.nio.file.Path

data class DownloadItem(
    val id: String,
    val path: MutableStateFlow<Path?> = MutableStateFlow(null),
    val totalBytes: MutableStateFlow<Long?> = MutableStateFlow(null),
) {
    val progress: MutableStateFlow<Long> = MutableStateFlow(0L)
    val bitsPerSecond: MutableStateFlow<Long?> = MutableStateFlow(0L)
    val status: MutableStateFlow<Status> = MutableStateFlow(Status.NotStarted)

    sealed class Status {
        object NotStarted : Status()
        object Downloading : Status()
        object Completed : Status()
        object Cancelled : Status()

        data class Failed(val error: Throwable) : Status()
    }
}