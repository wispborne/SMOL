package smol_app.browser

import java.nio.file.Path
import java.util.*

interface DownloadHander {
    fun onStart(suggestedFileName: String?, totalBytes: Long)
    fun onProgressUpdate(progressBytes: Long?, totalBytes: Long?, speedBps: Long?, endTime: Date)
    fun onCanceled()
    fun onCompleted()
    fun getDownloadPathFor(filename: String?): Path
}