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
import java.util.*


class ArchiveExtractToMemoryCallback(
    private val itemsToExtract: IntArray,
    private val inArchive: IInArchive,
    private val onComplete: (Map<Int, String>) -> Unit
) : IArchiveExtractCallback {
    private val startTime = System.currentTimeMillis()
    private var hash = 0
    private var size = 0
    private var index = 0
    private var skipExtraction = false
    private var bytes = byteArrayOf()
    private var total = -1L
    private val results = mutableMapOf<Int, String>()

    private var itemLeftToExtract = itemsToExtract.size

    override fun getStream(
        index: Int,
        extractAskMode: ExtractAskMode?
    ): ISequentialOutStream? {
        this.index = index
        val isFolder = inArchive
            .getProperty(index, PropID.IS_FOLDER) as Boolean
        skipExtraction = isFolder || index !in itemsToExtract

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
            onComplete(results)
        }
    }

    override fun prepareOperation(extractAskMode: ExtractAskMode?) {
    }

    override fun setOperationResult(extractOperationResult: ExtractOperationResult?) {
        if (skipExtraction) return

        itemLeftToExtract--
        if (extractOperationResult != ExtractOperationResult.OK) {
            System.err.println("Extraction error");
        } else {
            val filePath = inArchive.getProperty(index, PropID.PATH) as String

            Timber.v { String.format("Extracted %10sb | %s", size, filePath) }

            results[index] = bytes.decodeToString()

            if (itemLeftToExtract <= 0) {
                onComplete(results)
            }

            hash = 0
            size = 0
            bytes = byteArrayOf()
        }
    }
}