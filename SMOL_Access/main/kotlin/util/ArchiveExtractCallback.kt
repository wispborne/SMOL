package util

import net.sf.sevenzipjbinding.*
import org.tinylog.Logger
import java.io.File
import java.util.*


class ArchiveExtractCallback(
    private val parentFolder: File,
    private val inArchive: IInArchive
) : IArchiveExtractCallback {
    private val startTime = System.currentTimeMillis()
    private var hash = 0
    private var size = 0
    var currentFile: File? = null
    private var index = 0
    private var skipExtraction = false
    private var bytes = byteArrayOf()
    private var total = -1L

    override fun getStream(
        index: Int,
        extractAskMode: ExtractAskMode?
    ): ISequentialOutStream? {
        this.index = index
        skipExtraction = inArchive
            .getProperty(index, PropID.IS_FOLDER) as Boolean
        if (skipExtraction || extractAskMode !== ExtractAskMode.EXTRACT) {
            return null
        }

        return ISequentialOutStream { data ->
            hash = hash xor Arrays.hashCode(data)
            size += data.size
            bytes += data
            data.size // Return amount of proceed data
        }
    }

    override fun setTotal(total: Long) {
        this.total = total
    }

    override fun setCompleted(complete: Long) {
        if (complete == total) {
            Logger.info { "Total extraction time: ${System.currentTimeMillis() - startTime}ms." }
        }
    }

    override fun prepareOperation(extractAskMode: ExtractAskMode?) {
    }

    override fun setOperationResult(extractOperationResult: ExtractOperationResult?) {
        if (skipExtraction) return

        if (extractOperationResult != ExtractOperationResult.OK) {
            System.err.println("Extraction error");
        } else {
            val filePath = inArchive.getProperty(index, PropID.PATH) as String

            Logger.debug {
                String.format("Extracted %10sb | %s", size, filePath)
            }

            val file = parentFolder.resolve(filePath)
            file.parentFile.mkdirsIfNotExist()
            currentFile = file
            file.writeBytes(bytes)

            hash = 0
            size = 0
            bytes = byteArrayOf()
        }

    }
}