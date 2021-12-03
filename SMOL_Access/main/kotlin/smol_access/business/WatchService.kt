package smol_access.business

/**
 * Based on <https://github.com/vishna/watchservice-ktx>, licensed under Apache 2.0.
 * Modified by Wisp to use StateFlow and to be properly cancelable instead of blocking forever.
 */

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import timber.ktx.Timber
import java.nio.file.*
import java.nio.file.StandardWatchEventKinds.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.TimeUnit
import kotlin.io.path.absolutePathString
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

/**
 * Watches directory. If file is supplied it will use parent directory. If it's an intent to watch just file,
 * developers must filter for the file related events themselves.
 *
 * @param [mode] - mode in which we should observe changes, can be SingleFile, SingleDirectory, Recursive
 * @param [tag] - any kind of data that should be associated with this channel
 * @param [scope] - coroutine context for the channel, optional
 */
fun Path.asWatchChannel(
    mode: KWatchChannel.Mode? = null,
    tag: Any? = null,
    scope: CoroutineScope,
    ignorePatterns: List<Regex> = listOf(Regex(".*\\.bak"))
) = KWatchChannel(
    file = this,
    mode = mode ?: if (isRegularFile()) KWatchChannel.Mode.SingleFile else KWatchChannel.Mode.Recursive,
    scope = scope,
    tag = tag,
    ignorePatterns = ignorePatterns
)

/**
 * Channel based wrapper for Java's WatchService
 *
 * @param [file] - file or directory that is supposed to be monitored by WatchService
 * @param [scope] - CoroutineScope in within which Channel's sending loop will be running
 * @param [mode] - channel can work in one of the three modes: watching a single file,
 * watching a single directory or watching directory tree recursively
 * @param [tag] - any kind of data that should be associated with this channel, optional
 */
class KWatchChannel(
    val file: Path,
    val scope: CoroutineScope,
    val mode: Mode,
    val tag: Any? = null,
    val ignorePatterns: List<Regex>,
    private val flow: MutableSharedFlow<KWatchEvent> = MutableSharedFlow(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
) : MutableSharedFlow<KWatchEvent> by flow {

    private val watchService: WatchService = FileSystems.getDefault().newWatchService()
    private val registeredKeys = ArrayList<WatchKey>()
    private val path: Path = if (file.isRegularFile()) {
        file.parent
    } else {
        file
    }

    /**
     * Registers this channel to watch any changes in path directory and its subdirectories
     * if applicable. Removes any previous subscriptions.
     */
    private fun registerPaths() {
        registeredKeys.apply {
            forEach { it.cancel() }
            clear()
        }
        if (mode == Mode.Recursive) {
            Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(subPath: Path, attrs: BasicFileAttributes): FileVisitResult {
                    return if (ignorePatterns.any { ignorePattern -> subPath.toString().matches(ignorePattern) }) {
                        Timber.i { "Ignored change to file ${subPath.fileName} as it matched one of ${ignorePatterns.joinToString { it.pattern }}" }
                        FileVisitResult.CONTINUE
                    } else {
                        registeredKeys += subPath.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE)
                        FileVisitResult.CONTINUE
                    }
                }
            })
        } else {
            registeredKeys += path.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE)
        }
    }

    init {
        // commence emitting events from channel
        scope.launch {

            // sending channel initalization event
            flow.emit(
                KWatchEvent(
                    file = path,
                    tag = tag,
                    kind = KWatchEvent.Kind.Initialized
                )
            )

            var shouldRegisterPath = true

            while (isActive) {
                if (shouldRegisterPath) {
                    registerPaths()
                    shouldRegisterPath = false
                }

                // Wait one second for a change (gives us a chance to cancel this coroutine once per second).
                val monitorKey = watchService.poll(1, TimeUnit.SECONDS) ?: continue
                val dirPath = monitorKey.watchable() as? Path ?: break
                monitorKey.pollEvents().forEach {
                    val eventPath = dirPath.resolve(it.context() as Path)

                    if (mode == Mode.SingleFile && eventPath.absolutePathString() != file.absolutePathString()) {
                        return@forEach
                    }

                    val eventType = when (it.kind()) {
                        ENTRY_CREATE -> KWatchEvent.Kind.Created
                        ENTRY_DELETE -> KWatchEvent.Kind.Deleted
                        else -> KWatchEvent.Kind.Modified
                    }

                    val event = KWatchEvent(
                        file = eventPath,
                        tag = tag,
                        kind = eventType
                    )

                    // if any folder is created or deleted... and we are supposed
                    // to watch subtree we re-register the whole tree
                    if (mode == Mode.Recursive &&
                        event.kind in listOf(KWatchEvent.Kind.Created, KWatchEvent.Kind.Deleted) &&
                        event.file.isDirectory()
                    ) {
                        shouldRegisterPath = true
                    }

                    flow.emit(event)
                }

                if (!monitorKey.reset()) {
                    monitorKey.cancel()
                    close()
                    break
                } else if (!isActive) {
                    close()
                    break
                }
            }

            close()
        }
    }

    fun close(cause: Throwable? = null) {
        registeredKeys.apply {
            forEach { it.cancel() }
            clear()
        }

        if (cause != null) throw cause
    }

    override fun toString(): String {
        return "KWatchChannel(file=$file, mode=$mode, ignorePatterns=$ignorePatterns)"
    }

    /**
     * Describes the mode this channels is running in
     */
    enum class Mode {
        /**
         * Watches only the given file
         */
        SingleFile,

        /**
         * Watches changes in the given directory, changes in subdirectories will be
         * ignored
         */
        SingleDirectory,

        /**
         * Watches changes in subdirectories
         */
        Recursive
    }
}

/**
 * Wrapper around [WatchEvent] that comes with properly resolved absolute path
 */
data class KWatchEvent(
    /**
     * Abolute path of modified folder/file
     */
    val file: Path,

    /**
     * Kind of file system event
     */
    val kind: Kind,

    /**
     * Optional extra data that should be associated with this event
     */
    val tag: Any?
) {
    /**
     * File system event, wrapper around [WatchEvent.Kind]
     */
    enum class Kind(val kind: String) {
        /**
         * Triggered upon initialization of the channel
         */
        Initialized("initialized"),

        /**
         * Triggered when file or directory is created
         */
        Created("created"),

        /**
         * Triggered when file or directory is modified
         */
        Modified("modified"),

        /**
         * Triggered when file or directory is deleted
         */
        Deleted("deleted")
    }
}