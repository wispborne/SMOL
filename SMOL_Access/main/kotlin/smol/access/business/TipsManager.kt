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

import smol.access.Constants
import smol.access.model.ModTip
import smol.access.model.Tips
import smol.timber.ktx.Timber
import smol.utilities.IOLock
import smol.utilities.Jsanity
import smol.utilities.exists
import smol.utilities.toPathOrNull
import kotlin.io.path.absolutePathString
import kotlin.io.path.copyTo
import kotlin.io.path.readText
import kotlin.io.path.writeText

class TipsManager(val jsanity: Jsanity) {
    fun deleteTips(tips: Collection<ModTip>) {
        val dryRun = false
        val tipsByVariant = tips
            .flatMap { tip -> tip.variants.map { it to tip.tipObj } }
            .groupBy({ it.first }, { it.second })

        tipsByVariant
            .forEach { (variant, variantTips) ->
                Timber.i {
                    "Removing from variant ${variant.smolId} tips: ${
                        variantTips.map { "'${it.tip?.take(40)}'" }
                    }"
                }
                IOLock.write {
                    val path = variant.modsFolderInfo.folder.resolve(Constants.TIPS_FILE_RELATIVE_PATH)
                    val backup = (path.absolutePathString() + ".bak").toPathOrNull()

                    if (backup != null) {
                        if (!backup.exists()) {
                            path.copyTo(backup)
                            Timber.i { "Created backup of '${path.absolutePathString()}' at '${backup.absolutePathString()}'" }
                        }
                    }

                    path
                        .readText()
                        .let { json ->
                            jsanity.fromJson<Tips>(
                                json,
                                path.absolutePathString(),
                                shouldStripComments = true
                            )
                        }
                        .let { tipsObj ->
                            tipsObj.tips
                                .filter { it !in variantTips }
                                .let { tipsObj.copy(tips = it) }
                        }
                        .let { filteredTips -> jsanity.toJson(filteredTips) }
                        .also { filteredTips ->
                            if (!dryRun) {
                                path.writeText(filteredTips)
                            }

                            Timber.i { "Wrote to '${path.absolutePathString()}':\n$filteredTips" }
                        }
                }
            }
    }
}