/*
 * Copyright 2018 Mordechai Meisels
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * 		http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package smol_app.updater

import org.update4j.FileMetadata
import org.update4j.UpdateContext
import org.update4j.service.UpdateHandler
import org.update4j.util.FileUtils
import org.update4j.util.StringUtils
import timber.ktx.Timber
import java.io.PrintStream
import java.nio.file.Path
import java.util.*

open class SmolUpdateHandler : UpdateHandler {
    override fun version(): Long {
        return Long.MIN_VALUE
    }

    private var context: UpdateContext? = null
    override fun init(context: UpdateContext) {
        this.context = context
        out = out()
    }

    @Throws(Throwable::class)
    override fun startDownloads() {
        total = context!!.requiresUpdate.size
        ordinalWidth = total.toString().length * 2 + 1
        initProgress()
    }

    @Throws(Throwable::class)
    override fun startDownloadFile(file: FileMetadata) {
        index++
        Timber.d { renderFilename(file) }
        resetProgress(file.size)
    }

    @Throws(Throwable::class)
    override fun updateDownloadFileProgress(file: FileMetadata?, frac: Float) {
        currentFrac = frac
    }

    @Throws(Throwable::class)
    override fun doneDownloadFile(file: FileMetadata, tempFile: Path) {
        clear()
    }

    override fun failed(t: Throwable) {
        clearln()
        t.printStackTrace(out)
    }

    override fun stop() {
        stopTimer = true
    }

    override fun getResult(): UpdateContext {
        return context!!
    }

    //------- Progress rendering, highly inspired by https://github.com/ctongfei/progressbar
    private lateinit var out: PrintStream
    private var timer: Timer? = null
    private var totalWidth = 0
    private var msgWidth = 0
    private var rateWidth = 0
    private var percentWidth = 0
    private var timeWidth = 0
    private var clear: String? = null
    private var ordinalWidth = 0
    private var total = 0
    private var index = 0
    private var totalBytes: Long = 0
    private var lastFrac = 0f
    private var currentFrac = 0f
    private var start: Long = 0
    private var stopTimer = false
    protected fun initProgress() {
        totalWidth = consoleWidth()
        msgWidth = "Downloading".length
        rateWidth = "@ 100.0 kB/s".length
        percentWidth = "100%".length
        timeWidth = "0:00:00".length
        clear = "\r" + StringUtils.repeat(totalWidth, " ") + "\r"
        timer = Timer("Progress Printer", true)
        timer!!.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                if (stopTimer) {
                    timer!!.cancel()
                    return
                }
                print(renderProgress())
                lastFrac = currentFrac
            }
        }, 0, 1000)
    }

    protected fun resetProgress(bytes: Long) {
        currentFrac = 0f
        lastFrac = 0f
        totalBytes = bytes
        start = System.currentTimeMillis()
    }

    protected fun out(): PrintStream {
        return System.out
    }

    protected fun consoleWidth(): Int {
        return 80
    }

    private fun clear() {
        out.print(clear)
    }

    private fun clearln() {
        out.println(clear)
    }

    private fun print(str: String) {
        out.print("\r")
        out.print(StringUtils.padRight(totalWidth, str))

        Timber.d { "\r" }
        Timber.d { StringUtils.padRight(totalWidth, str) }
    }

    protected fun renderProgress(): String {
        val sb = StringBuilder()
        sb.append("Downloading ")
        val humanReadableBytes = StringUtils.humanReadableByteCount(totalBytes)
        sb.append(humanReadableBytes)
        sb.append(" ")
        if (lastFrac == 0f && currentFrac == 0f) {
            sb.append(StringUtils.repeat(rateWidth + 1, " "))
        } else {
            sb.append("@ ")
            sb.append(
                StringUtils.padRight(
                    rateWidth - 2,
                    StringUtils.humanReadableByteCount(((currentFrac - lastFrac) * totalBytes).toLong()) + "/s"
                )
            )
            sb.append(" ")
        }
        sb.append(StringUtils.padLeft(percentWidth, (currentFrac * 100).toInt().toString() + "%"))
        sb.append(" [")
        val progressWidth = (totalWidth - msgWidth - humanReadableBytes.length - rateWidth - percentWidth - timeWidth
                - 7) // spaces
        val pieces = ((progressWidth - 2) * currentFrac).toInt()
        var line = StringUtils.repeat(pieces, "=")
        if (pieces < progressWidth - 2) line += ">"
        sb.append(StringUtils.padRight(progressWidth - 2, line))
        sb.append("]")
        val elapsed = System.currentTimeMillis() - start
        if (currentFrac > 0) {
            sb.append(" (")
            sb.append(StringUtils.formatSeconds(((elapsed / currentFrac).toLong() - elapsed) / 1000))
            sb.append(")")
        }
        return sb.toString()
    }

    protected fun renderFilename(file: FileMetadata): String {
        return StringUtils.padLeft(ordinalWidth, "$index/$total") + " " + compactName(file.path)
    }

    private fun compactName(name: Path): String {
        val relative = FileUtils.relativize(context!!.configuration.basePath, name)
        return if (relative.isAbsolute) relative.fileName.toString() else relative.toString()
    }
}