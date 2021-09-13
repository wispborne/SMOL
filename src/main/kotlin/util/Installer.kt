package util

import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import model.Mod
import model.Version
import net.sf.sevenzipjbinding.IOutCreateCallback
import net.sf.sevenzipjbinding.IOutItem7z
import net.sf.sevenzipjbinding.ISequentialInStream
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.impl.OutItemFactory
import net.sf.sevenzipjbinding.impl.RandomAccessFileOutStream
import net.sf.sevenzipjbinding.util.ByteArrayStream
import org.tinylog.Logger
import java.io.File
import java.io.RandomAccessFile
import java.util.*

@OptIn(ExperimentalStdlibApi::class)
class Installer(
    private val config: AppConfig,
    private val gamePath: GamePath,
    private val moshi: Moshi
) {
    companion object {
        const val MANIFEST_FILENAME = "manifest.json"
    }

    fun getStagingPath() = config.stagingPath

    fun loadManifest() =
        kotlin.runCatching {
            moshi.adapter<StagingManifest>()
                .fromJson(File(config.stagingPath!!, MANIFEST_FILENAME).readText())
        }
            .recover {
                // Try to make a backup of the file
                val file = config.stagingPath?.let { File(it, MANIFEST_FILENAME) }
                if (file?.exists() == true) {
                    kotlin.runCatching {
                        file.copyTo(File(file.parentFile, file.name + ".bak"), overwrite = true)
                    }
                        .onFailure { Logger.error(it) }
                }

                // Then return an empty manifest so it'll be created anew
                StagingManifest()
            }
            .getOrDefault(StagingManifest())

    fun addModsFolderToStagingFolder() {
        if (config.stagingPath == null) {
            Logger.warn { "Not adding mods to staging folder; staging folder is null." }
            return
        }

        val manifest = loadManifest()
        val modsPath = gamePath.getModsPath()

        gamePath.getMods()
            .filter { mod ->
                // Only add mods to staging folder if they aren't already there
                val key = createManifestItemKey(mod)
                val doesArchiveAlreadyExist = manifest?.manifestItems?.containsKey(key) == true
                        && File(manifest.manifestItems[key]?.archivePath ?: "").exists()
                Logger.debug { "[${mod.modInfo.id}, ${mod.modInfo.version}] archive already exists? $doesArchiveAlreadyExist" }
                !doesArchiveAlreadyExist
            }
            .forEach { mod ->
                val filesToArchive = mod.folder.walkTopDown().toList()

                val stagingArchiveFile = File(
                    config.stagingPath,
                    createStagingArchiveName(mod) + ".7z"
                )
                    .apply { parentFile.mkdirs() }

                SevenZip.openOutArchive7z()
                    .createArchive(RandomAccessFileOutStream(
                        RandomAccessFile(
                            stagingArchiveFile, "rw"
                        )
                    ),
                        filesToArchive.size,
                        object : IOutCreateCallback<IOutItem7z> {
                            var currentTotal: Float = 0f
                            var currentFilepath: String = ""

                            override fun setTotal(total: Long) {
                                currentTotal = total.toFloat()
                            }

                            override fun setCompleted(complete: Long) {
                                Logger.debug {
                                    val percentComplete = "%.2f".format((complete.toFloat() / currentTotal) * 100f)
                                    "[$percentComplete%] Moving '${mod.modInfo.id} v${mod.modInfo.version}' to staging. File: $currentFilepath"
                                }
                            }

                            override fun setOperationResult(wasSuccessful: Boolean) {
//                                Logger.debug { "Wrote archive ${stagingArchiveFile.absolutePath}" }
                            }

                            override fun getItemInformation(
                                index: Int,
                                outItemFactory: OutItemFactory<IOutItem7z>
                            ): IOutItem7z {
                                val item = outItemFactory.createOutItem()
                                val file = filesToArchive[index]

                                if (file.isDirectory) {
                                    item.propertyIsDir = true
                                } else {
                                    item.dataSize = file.readBytes().size.toLong()
                                }

                                item.propertyPath = file.toRelativeString(modsPath)
                                return item
                            }

                            override fun getStream(index: Int): ISequentialInStream {
                                currentFilepath = filesToArchive[index].relativeTo(modsPath).path
                                return ByteArrayStream(filesToArchive[index].readBytes(), true)
                            }
                        })

                updateManifest { manifest ->
                    manifest.copy(
                        manifestItems = manifest.manifestItems.toMutableMap().apply {
                            this[createManifestItemKey(mod)] = ManifestItemValue(
                                modId = mod.modInfo.id,
                                version = mod.modInfo.version,
                                archivePath = stagingArchiveFile.absolutePath
                            )
                        })
                }
            }
    }

    fun updateManifest(mutator: (stagingManifest: StagingManifest) -> StagingManifest) {
        mutator(loadManifest()!!)
            .run { moshi.adapter<StagingManifest>().toJson(this) }
            .run { File(config.stagingPath!!, MANIFEST_FILENAME).writeText(this) }
    }

    private fun createStagingArchiveName(mod: Mod) =
        mod.modInfo.id.replace('-', '_') + "-" + mod.modInfo.version.toString()

    private fun createManifestItemKey(mod: Mod) = Objects.hash(mod.modInfo.id, mod.modInfo.version.toString())

    data class StagingManifest(
        val manifestItems: Map<Int, ManifestItemValue> = emptyMap()
    )

//    data class ManifestItemKey(
//        val modId: String,
//        val version: Version
//    ) {
//        constructor(mod: Mod) : this(mod.modInfo.id, mod.modInfo.version)
//
//        override fun toString(): String {
//            return modId.replace('-', '_') + "-" + version.toString()
//        }
//    }


    data class ManifestItemValue(
        val modId: String,
        val version: Version,
        val archivePath: String?
    )
}
typealias ManifestItemKey = Long