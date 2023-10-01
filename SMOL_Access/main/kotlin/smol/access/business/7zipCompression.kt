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

package smol.access.business

import io.ktor.util.*
import net.sf.sevenzipjbinding.*
import net.sf.sevenzipjbinding.impl.OutItemFactory
import net.sf.sevenzipjbinding.util.ByteArrayStream
import java.nio.file.Path
import kotlin.io.path.*

internal class ArchiveFile(
    val path: Path
) {
    val content: ByteArray? by lazy {
        if (path.isDirectory()) null
        else path.readBytes()
    }
}

/**
 * The callback provides information about archive items.
 */
internal class Compress7zFilesCallback(val items: List<ArchiveFile>, val relativeTo: Path) :
    IOutCreateCallback<IOutItem7z> {
    val errors = mutableListOf<Throwable>()

    @Throws(SevenZipException::class)
    override fun setOperationResult(operationResultOk: Boolean) {
        // Track each operation result here
    }

    @Throws(SevenZipException::class)
    override fun setTotal(total: Long) {
        // Track operation progress here
    }

    @Throws(SevenZipException::class)
    override fun setCompleted(complete: Long) {
        // Track operation progress here
    }

    override fun getItemInformation(
        index: Int,
        outItemFactory: OutItemFactory<IOutItem7z>
    ): IOutItem7z {
        runCatching {
            val item = outItemFactory.createOutItem()
            val content = items[index].content
            if (content == null) {
                // Directory
                item.propertyIsDir = true
            } else {
                // File
                item.dataSize = content.size.toLong()
            }
            item.propertyPath = items[index].path.relativeTo(relativeTo).normalizeAndRelativize().toString()
            return item
        }
            .onFailure {
                errors.add(it)
            }

        return outItemFactory.createOutItem()
    }

    @Throws(SevenZipException::class)
    override fun getStream(i: Int): ISequentialInStream? {
        return if (items[i].content == null) null
        else ByteArrayStream(items[i].content, true)
    }
}