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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import smol.access.Constants
import smol.access.model.*
import smol.timber.ktx.Timber
import smol.utilities.IOLock
import smol.utilities.Jsanity
import smol.utilities.asList
import smol.utilities.exists
import smol.utilities.toPathOrNull
import java.nio.file.Path
import kotlin.io.path.*

class TipsManager internal constructor(val jsanity: Jsanity, val userManager: UserManager, modsCache: ModsCache) {
    val scope = CoroutineScope(Job())
    val dryRun = false

    init {
        scope.launch {
            modsCache.mods.collectLatest { modUpdate ->
                if (modUpdate?.mods != null) {
                    checkForModsWithPreviouslyDeletedTips(modUpdate.mods)
                        .also { deletedTips ->
                            if (deletedTips.isEmpty()) return@also

                            Timber.i {
                                "Mods with tips that user has previous removed:\n${
                                    deletedTips.joinToString(separator = "\n") {
                                        it.second.variants.joinToString { it.smolId } + it.second.tipObj.tip?.take(40)
                                    }
                                }"
                            }
                            Timber.i { "Deleting them!" }

                            deleteTips(deletedTips.map { it.second })
                        }
                }
            }
        }
    }

    fun deleteTips(tips: Collection<ModTip>) {
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
                    runCatching {
                        val (path, allTips) = loadTipsFromFile(variant) ?: return@runCatching

                        val backup = (path.absolutePathString() + ".bak").toPathOrNull()
                        if (backup != null) {
                            if (!backup.exists()) {
                                path.copyTo(backup)
                                Timber.i { "Created backup of '${path.absolutePathString()}' at '${backup.absolutePathString()}'" }
                            }
                        }
                        allTips
                            .let { tipsObj ->
                                tipsObj.tips.orEmpty()
                                    .filter { it !in variantTips }
                                    .let { tipsObj.copy(tips = it) }
                            }
                            .let { filteredTips -> jsanity.toJson(filteredTips) }
                            .also { filteredTips ->
                                if (!dryRun) {
                                    path.writeText(filteredTips)
                                }

                                Timber.i { "Wrote updated tips to '${path.absolutePathString()}'." }
                            }
                    }.onFailure { Timber.e(it) }
                }
            }

        // Add the tip hashcodes to the user profile, so we can remove them if a mod update adds them again.
        userManager.updateUserProfile { profile ->
            // Create a hashcode from the tipObj and the mod id, so the tip will be scoped to the mod.
            profile.copy(
                removedTipHashcodes = profile.removedTipHashcodes + tips.map { modTip ->
                    createTipHashcode(
                        modTip.variants.firstOrNull()?.modInfo?.id,
                        modTip.tipObj
                    )
                }
            )
        }
    }

    fun createTipHashcode(
        modId: String?,
        tip: Tip
    ): String = "$modId-${tip.hashCode()}"

    private fun loadTipsFromFile(variant: ModVariant): Pair<Path, Tips>? {
        val path = variant.modsFolderInfo.folder.resolve(Constants.TIPS_FILE_RELATIVE_PATH)

        if (path.notExists())
            return null

        val allTips = path
            .readText()
            .let { json ->
                jsanity.fromJson<Tips>(
                    json,
                    path.absolutePathString(),
                    shouldStripComments = true
                )
            }
        return Pair(path, allTips)
    }

    /**
     * The user may have removed a tip from a mod, then installed a new version of the mod that restored the tip.
     * Find any such tips.
     */
    fun checkForModsWithPreviouslyDeletedTips(modsToCheck: List<Mod>): List<Pair<String, ModTip>> {
        val removedTipHashes = userManager.activeProfile.value.removedTipHashcodes

        if (removedTipHashes.isEmpty()) {
            return emptyList()
        }

        val allModTipHashes = modsToCheck
            .flatMap { it.variants }
            .flatMap { variant ->
                runBlocking {
                    loadTipsFromFile(variant)?.second?.tips.orEmpty()
                        .map { createTipHashcode(variant.modInfo.id, it) to ModTip(it, variant.asList()) }
                }
            }

        return allModTipHashes.filter { it.first in removedTipHashes }
    }
}