package util

import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import com.squareup.moshi.Moshi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import model.ModInfo
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
import java.io.FileWriter
import java.io.RandomAccessFile
import java.util.*

@OptIn(ExperimentalStdlibApi::class)
class Archives(
    private val config: AppConfig,
    private val gamePath: GamePath,
    private val moshi: Moshi,
    private val gson: Gson,
    private val modInfoLoader: ModInfoLoader
) {
    companion object {
        const val ARCHIVES_FILENAME = "manifest.json"
    }

    val archiveMovementStatusFlow = MutableStateFlow<String>("")

    fun getArchivesPath() = config.archivesPath

    fun getArchivesManifest(): ArchivesManifest? =
        kotlin.runCatching {
            gson.fromJson<ArchivesManifest>(File(config.archivesPath!!, ARCHIVES_FILENAME).readText())
        }
            .onFailure { Logger.warn(it) }
            .recover {
                // Try to make a backup of the file
                val file = config.archivesPath?.let { File(it, ARCHIVES_FILENAME) }
                if (file?.exists() == true) {
                    kotlin.runCatching {
                        file.copyTo(File(file.parentFile, file.name + ".bak"), overwrite = true)
                    }
                        .onFailure { Logger.error(it) }
                }

                // Then return an empty manifest so it'll be created anew
                ArchivesManifest()
            }
            .getOrDefault(ArchivesManifest())

    @OptIn(ExperimentalCoroutinesApi::class)
    fun archiveModsInFolder(modFolder: File): Flow<String> {
        return callbackFlow<String> {
            if (config.archivesPath == null)
                throw RuntimeException("Not adding mods to archives folder; archives folder is null.")

            if (!modFolder.exists()) throw RuntimeException("Mod folder doesn't exist.")


            val scope = this
            val modsPath = gamePath.getModsPath()
            val manifest = getArchivesManifest()
            val mods = modInfoLoader.readModInfosFromFolderOfMods(modsPath, onlySmolManagedMods = true)

            mods
                .filter { (modFolder, modInfo) ->
                    // Only add mods to archives folder if they aren't already there
                    val key = createManifestItemKey(modInfo)
                    val doesArchiveAlreadyExist = doesArchiveExistForKey(manifest, key)
                    Logger.debug { "[${modInfo.id}, ${modInfo.version}] archive already exists? $doesArchiveAlreadyExist" }
                    !doesArchiveAlreadyExist
                }
                .forEach { (modFolder, modInfo) ->
                    val filesToArchive =
                        modFolder.walkTopDown().toList()

                    val archiveFile = File(
                        config.archivesPath,
                        createArchiveName(modInfo) + ".7z"
                    )
                        .apply { parentFile.mkdirsIfNotExist() }

                    var wasCanceled = false

                    SevenZip.openOutArchive7z().use { archive7z ->
                        fun cancelProcess() {
                            if (!wasCanceled) {
                                Logger.warn { "Canceled archive process." }
                                wasCanceled = true
                                archive7z.close()

                                if (!archiveFile.delete()) {
                                    archiveFile.deleteOnExit()
                                }
                            }
                        }

                        archive7z.createArchive(
                            RandomAccessFileOutStream(
                                RandomAccessFile(
                                    archiveFile, "rw"
                                )
                            ),
                            filesToArchive.size,
                            object : IOutCreateCallback<IOutItem7z> {
                                var currentTotal: Float = 0f
                                var currentFilepath: String = ""

                                override fun setTotal(total: Long) {
                                    if (!scope.isActive) {
                                        cancelProcess()
                                        return
                                    }

                                    currentTotal = total.toFloat()
                                }

                                override fun setCompleted(complete: Long) {
                                    if (!scope.isActive) {
                                        cancelProcess()
                                        return
                                    }

                                    val percentComplete = "%.2f".format((complete.toFloat() / currentTotal) * 100f)
                                    val progressMessage =
                                        "[$percentComplete%] Moving '${modInfo.id} v${modInfo.version}' to archives. File: $currentFilepath"
                                    trySend(progressMessage)
                                    Logger.debug { progressMessage }
                                }

                                override fun setOperationResult(wasSuccessful: Boolean) {}

                                override fun getItemInformation(
                                    index: Int,
                                    outItemFactory: OutItemFactory<IOutItem7z>
                                ): IOutItem7z? {
                                    if (!scope.isActive) {
                                        cancelProcess()
                                        return null
                                    }

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

                                override fun getStream(index: Int): ISequentialInStream? {
                                    if (!scope.isActive) {
                                        cancelProcess()
                                        return null
                                    }

                                    currentFilepath = filesToArchive[index].relativeTo(modsPath).path
                                    return ByteArrayStream(filesToArchive[index].readBytes(), true)
                                }
                            })
                    }

                    updateManifest { manifest ->
                        manifest.copy(
                            manifestItems = manifest.manifestItems.toMutableMap().apply {
                                if (!wasCanceled) {
                                    this[createManifestItemKey(modInfo)] = ManifestItemValue(
                                        archivePath = archiveFile.absolutePath,
                                        modInfo = modInfo
                                    )
                                } else {
                                    this.remove(createManifestItemKey(modInfo))
                                }
                            })
                    }
                }

            close()
        }
            .onEach { archiveMovementStatusFlow.value = it }
            .onCompletion { archiveMovementStatusFlow.value = it?.message ?: "Finished adding mods to archive." }
    }

    fun updateManifest(mutator: (archivesManifest: ArchivesManifest) -> ArchivesManifest) {
        val originalManifest = getArchivesManifest()!!
        mutator(originalManifest)
            .run {
                val file = File(config.archivesPath!!, ARCHIVES_FILENAME)
                FileWriter(file).use { writer ->
                    gson.toJson(this, writer)
                }
                Logger.info {
                    "Updated manifest at ${file.absolutePath} from ${originalManifest.manifestItems.count()} " +
                            "to ${this.manifestItems.count()} items."
                }
            }
    }

    fun doesArchiveExistForKey(manifest: ArchivesManifest?, key: Int) =
        (manifest?.manifestItems?.containsKey(key) == true
                && File(manifest.manifestItems[key]?.archivePath ?: "").exists())

    fun createManifestItemKey(modId: String, version: Version) = Objects.hash(modId, version.toString())
    fun createManifestItemKey(modInfo: ModInfo) = createManifestItemKey(modInfo.id, modInfo.version)

    private fun createArchiveName(modInfo: ModInfo) =
        modInfo.id.replace('-', '_') + "-" + modInfo.version.toString()

    data class ArchivesManifest(
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
        val archivePath: String,
        val modInfo: ModInfo
    )
}
typealias ManifestItemKey = Long