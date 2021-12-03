package utilities

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.apache.commons.io.FileUtils
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Modifier
import java.nio.file.FileVisitOption
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.isSameFileAs
import kotlin.streams.asSequence
import kotlin.system.measureTimeMillis


@Deprecated("Use Path instead", replaceWith = ReplaceWith("toPathOrNull()"))
fun String?.toFileOrNull() = this?.let { File(it) }
fun String?.toPathOrNull() = this?.let { Path.of(it) }

@Deprecated("Use Path.createDirectories() instead")
fun File.mkdirsIfNotExist() {
    if (this.isFile) {
        this.parentFile.mkdirsIfNotExist()
    } else if (!this.exists()) {
        this.mkdirs()
    }
}

/**
 * [https://stackoverflow.com/a/35989142/1622788]
 */
fun Path.deleteRecursively(vararg options: FileVisitOption = arrayOf(FileVisitOption.FOLLOW_LINKS)) {
    if (!this.exists()) return

    Files.walk(this, *options).use { walk ->
        walk.sorted(Comparator.reverseOrder())
//            .map { obj: Path -> obj.toFile() }
//            .peek { x: File? -> println(x) }
            .forEach { obj -> obj.deleteIfExists() }
    }
}

fun Path.walk(maxDepth: Int = Int.MAX_VALUE, vararg options: FileVisitOption): Sequence<Path> =
    Files.walk(this, maxDepth, *options)
        .asSequence()
        .filter { !it.isSameFileAs(this) }

fun Throwable.rootCause(): Throwable {
    return if (this.cause != null && this !== this.cause)
        this.rootCause()
    else
        this
}

fun Any?.reflectionToString(): String {
    if (this == null) return "null"
    val s = LinkedList<String>()
    var clazz: Class<in Any>? = this.javaClass
    while (clazz != null) {
        for (prop in clazz.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }) {
            prop.isAccessible = true
            s += "${prop.name}=" + prop.get(this)?.toString()?.trim()
        }
        clazz = clazz.superclass
    }
    return "${this.javaClass.simpleName}=[${s.joinToString(", ")}]"
}

/**
 * Empty string, `""`.
 */
val String.Companion.empty
    get() = ""

/**
 * True if any of the arguments are equal; false otherwise.
 */
fun Any.equalsAny(vararg other: Any): Boolean = arrayOf(*other).any { this == it }

/**
 * Returns items matching the predicate or, if none are matching, returns the original [Collection].
 */
fun <T> Collection<T>.prefer(predicate: (item: T) -> Boolean): Collection<T> =
    this.filter { predicate(it) }
        .ifEmpty { this }

fun <T> T?.asList(): List<T> = if (this == null) emptyList() else listOf(this)

fun Float.makeFinite() =
    if (!this.isFinite()) 0f
    else this

/**
 * From FileUtils in apache commons.
 */
@Throws(IOException::class)
fun File.moveDirectory(destDir: File) {
    val srcDir = this
//    FileUtils.validateMoveParameters(srcDir, destDir)
//    FileUtils.requireDirectory(srcDir, "srcDir")
//    FileUtils.requireAbsent(destDir, "destDir")
    if (!srcDir.renameTo(destDir)) {
        if (destDir.canonicalPath.startsWith(srcDir.canonicalPath + File.separator)) {
            throw IOException("Cannot move directory: $srcDir to a subdirectory of itself: $destDir")
        }
        FileUtils.copyDirectory(srcDir, destDir)
        FileUtils.deleteDirectory(srcDir)
        if (srcDir.exists()) {
            throw IOException(
                "Failed to delete original directory '" + srcDir +
                        "' after copy to '" + destDir + "'"
            )
        }
    }
}

/**
 * [https://jivimberg.io/blog/2018/05/04/parallel-map-in-kotlin/]
 */
suspend fun <A, B> Iterable<A>.parallelMap(f: suspend (A) -> B): List<B> =
    coroutineScope {
        map { async { f(it) } }.awaitAll()
    }

fun <T, K> List<T>.diff(newList: List<T>, keyFinder: (item: T) -> K): DiffResult<T> {
    val cur = this.associateBy { keyFinder(it) }
    val new = newList.associateBy { keyFinder(it) }
    return DiffResult(
        removed = cur.filter { it.key !in new.keys }.values.toList(),
        added = new.filter { it.key !in cur.keys }.values.toList()
    )
}

data class DiffResult<T>(
    val removed: List<T>,
    val added: List<T>
)

/**
 * Time how long it takes to run [func].
 */
inline fun <T> trace(onFinished: (result: T, millis: Long) -> Unit, func: () -> T): T {
    var result: T
    val millis = measureTimeMillis { result = func() }
    onFinished(result, millis)
    return result
}

fun copyToClipboard(string: String) {
    val stringSelection = StringSelection(string)
    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    clipboard.setContents(stringSelection, null)
}

/**
 * From [java.io.InputStream], but with a delegate for progress.
 */
@Throws(IOException::class)
fun InputStream.transferTo(out: OutputStream, onProgressUpdated: (progress: Long) -> Unit): Long {
    val defaultBufferSize = 8192

    var transferred: Long = 0
    val buffer = ByteArray(defaultBufferSize)
    var read: Int

    while (this.read(buffer, 0, defaultBufferSize).also { read = it } >= 0) {
        out.write(buffer, 0, read)
        transferred += read.toLong()
        onProgressUpdated(transferred)
    }

    return transferred
}