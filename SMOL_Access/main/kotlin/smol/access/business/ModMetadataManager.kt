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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import smol.access.config.ModMetadata
import smol.access.config.ModMetadataStore
import smol.access.config.VariantMetadata
import smol.access.model.Mod
import smol.access.model.ModId
import smol.timber.ktx.Timber
import java.time.Instant

class ModMetadataManager internal constructor(
    private val modMetadataStore: ModMetadataStore,
    private val modsCache: ModsCache,
) {
    fun update(modId: ModId, transformer: (ModMetadata) -> ModMetadata) {
        val oldData = mergedData.value[modId] ?: ModMetadata()
        val newData = transformer.invoke(oldData)
        modMetadataStore.userMetadata.update {
            it.toMutableMap()
                .apply { this[modId] = newData }
        }
        Timber.i { "Updated mod metadata for '$modId' from $oldData to $newData." }
    }

    private val _mergedData = MutableStateFlow<Map<ModId, ModMetadata>>(emptyMap())
    val mergedData: StateFlow<Map<ModId, ModMetadata>> = _mergedData.asStateFlow()

    private val scope = CoroutineScope(Job())

    init {
        scope.launch(Dispatchers.Default) {
            modMetadataStore.baseMetadata
                .combine(modMetadataStore.userMetadata) { baseMap, userMap ->
                    (baseMap.entries + userMap.entries)
                        .groupBy(keySelector = { it.key })
                        .map { it.key to it.value.map { it.value } }
                        .map { (key, entries) ->
                            key to entries
                                .reduce { base, user ->
                                    base.copy(
                                        category = user.category ?: base.category
                                    )
                                }
                        }
                }
                .collectLatest { mergedMetadata ->
                    _mergedData.value = mergedMetadata.toMap()
                }

        }
        scope.launch(Dispatchers.Default) {
            modsCache.mods.collect() { update ->
                if (update != null) {
                    val added = update.added + update.mods.flatMap { it.variants }

                    val updatedMetadata = modMetadataStore.baseMetadata.value.toMutableMap()

                    added.forEach { modVariant ->
                        val modMetadata = updatedMetadata[modVariant.modInfo.id] ?: ModMetadata()

                        if (modMetadata.variantsMetadata?.get(modVariant.smolId)?.lastAdded == null) {
                            updatedMetadata[modVariant.modInfo.id] =
                                modMetadata.copy(
                                    variantsMetadata = modMetadata.variantsMetadata.orEmpty().toMutableMap().apply {
                                        this[modVariant.smolId] =
                                            (modMetadata.variantsMetadata.orEmpty()[modVariant.smolId]
                                                ?: VariantMetadata())
                                                .copy(lastAdded = Instant.now().toEpochMilli())
                                    }
                                )
                        }
                    }

                    if (modMetadataStore.baseMetadata.value != updatedMetadata.toMap()) {
                        Timber.i { "${updatedMetadata.values.joinToString { it.variantsMetadata.orEmpty().keys.joinToString() }} added at ${Instant.now()} to metadata." }
                        modMetadataStore.baseMetadata.value = updatedMetadata.toMap()
                    }
                }
            }
        }
    }
}

fun Mod.metadata(metadataManager: ModMetadataManager): ModMetadata? = metadataManager.mergedData.value[this.id]