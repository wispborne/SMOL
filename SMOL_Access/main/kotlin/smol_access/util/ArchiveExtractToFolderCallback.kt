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

package smol_access.util

import net.sf.sevenzipjbinding.*
import timber.ktx.Timber
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
            Timber.v { "Total extraction time: ${System.currentTimeMillis() - startTime}ms." }
        }
    }

    override fun prepareOperation(extractAskMode: ExtractAskMode?) {
    }

    override fun setOperationResult(extractOperationResult: ExtractOperationResult?) {
        if (skipExtraction) return

        if (extractOperationResult != ExtractOperationResult.OK) {
            Timber.e { "Extraction error" }
        } else {
            val filePath = inArchive.getProperty(index, PropID.PATH) as String

            Timber.v { String.format("Extracted %10sb | %s", size, filePath) }

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