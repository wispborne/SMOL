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

@OptIn(ExperimentalStdlibApi::class)
class Installer(
    private val config: AppConfig,
    private val gamePath: GamePath,
    private val moshi: Moshi
) {
    fun createStagingArchiveName(mod: Mod) =
        mod.modInfo.id.replace('-', '_') + "-" + mod.modInfo.version.toString()

    fun loadManifest() =
        kotlin.runCatching {
            moshi.adapter<StagingManifest>()
                .fromJson(File(config.stagingPath!!).readText())
        }
            .recover {
                // Try to make a backup of the file
                val file = config.stagingPath?.let { File(it) }
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

        val modsPath = gamePath.getModsPath()

        gamePath.getMods()
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
                            override fun setTotal(total: Long) {
                            }

                            override fun setCompleted(complete: Long) {

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

                            override fun getStream(index: Int): ISequentialInStream =
                                ByteArrayStream(filesToArchive[index].readBytes(), true)

                        })
                mod.folder
            }
    }

    fun updateManifest(mutator: (stagingManifest: StagingManifest) -> StagingManifest) {
        mutator(loadManifest()!!)
            .run { moshi.adapter<StagingManifest>().toJson(this) }
            .run { File(config.stagingPath!!).writeText(this) }
    }

    data class StagingManifest(
        val manifestItems: Map<ManifestItemKey, ManifestItemValue> = emptyMap()
    )

    data class ManifestItemKey(
        val modId: String,
        val version: Version
    )


    data class ManifestItemValue(
        val modId: String,
        val version: Version
    )
}