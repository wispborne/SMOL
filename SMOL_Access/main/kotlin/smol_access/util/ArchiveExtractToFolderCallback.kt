package smol_access.util

import net.sf.sevenzipjbinding.*
import org.tinylog.Logger
import java.nio.file.Path
import java.util.*
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes


class ArchiveExtractToFolderCallback(
    private val parentFolder: Path,
    private val inArchive: IInArchive
) : IArchiveExtractCallback {
    private val startTime = System.currentTimeMillis()
    private var hash = 0
    private var size = 0
    var currentFile: Path? = null
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
            Logger.trace { "Total extraction time: ${System.currentTimeMillis() - startTime}ms." }
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
            file.parent.createDirectories()
            currentFile = file
            file.writeBytes(bytes)

            hash = 0
            size = 0
            bytes = byteArrayOf()
        }

    }
}