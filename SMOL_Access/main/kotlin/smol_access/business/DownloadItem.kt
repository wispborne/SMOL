package smol_access.business

import kotlinx.coroutines.flow.MutableStateFlow
import java.nio.file.Path

data class DownloadItem(
    val path: Path,
    val total: Long?,
) {
    val progress: MutableStateFlow<Long> = MutableStateFlow(0L)
    val status: MutableStateFlow<Status> = MutableStateFlow(Status.NotStarted)

    sealed class Status {
        object NotStarted : Status()
        object Downloading : Status()
        object Completed : Status()
        data class Failed(val error: Throwable) : Status()
    }
}