package smol_app.browser

import java.nio.file.Path
import java.util.*

interface DownloadHander {
    fun onStart(itemId: String, suggestedFileName: String?, totalBytes: Long)
    fun onProgressUpdate(itemId: String, progressBytes: Long?, totalBytes: Long?, speedBps: Long?, endTime: Date)
    fun onCanceled(itemId: String)
    fun onCompleted(itemId: String)
    fun getDownloadPathFor(filename: String?): Path
}