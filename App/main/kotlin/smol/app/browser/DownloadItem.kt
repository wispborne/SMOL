/*
 * This file is distributed under the GPLv3. An informal description follows:
 * - Anyone can copy, modify and distribute this software as long as the other points are followed.
 * - You must include the license and copyright notice with each and every distribution.
 * - You may this software for commercial purposes.
 * - If you modify it, you must indicate changes made to the code.
 * - Any modifications of this code base MUST be distributed with the same license, GPLv3.
 * - This software is provided without warranty.
 * - The software author or license can not be held liable for any damages inflicted by the software.
 * The full license is available from <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package smol.app.browser

import kotlinx.coroutines.flow.MutableStateFlow
import java.nio.file.Path

/**
 * @param url Url to display in the UI. Actual download can be anything. Used to check if the url is already downloading.
 */
data class DownloadItem(
    val id: String,
    val name: String,
    val url: String?,
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
        return "DownloadItem(id='$id', name='$name', url='$url', path=${path.value}, totalBytes=${totalBytes.value}, progress=${progressBytes.value}, bitsPerSecond=${bitsPerSecond.value}, status=${status.value})"
    }

    companion object {
        val MOCK = DownloadItem(id = "", "mock", url = "https://google.com")
            .apply {
                this.path.value = Path.of("C:/temp/perseanchronicles.7z")
                this.totalBytes.value = 1000
                this.progressBytes.value = 750
                this.bitsPerSecond.value = 512000
                this.status.value = DownloadItem.Status.Downloading
            }
    }
}