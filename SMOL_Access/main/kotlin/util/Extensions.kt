package util

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.IOException
import java.lang.reflect.Modifier
import java.util.*

fun String?.toFileOrNull() = this?.let { File(it) }

fun File.mkdirsIfNotExist() {
    if (this.isFile) {
        this.parentFile.mkdirsIfNotExist()
    } else if (!this.exists()) {
        this.mkdirs()
    }
}

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
 * <https://jivimberg.io/blog/2018/05/04/parallel-map-in-kotlin/>
 */
suspend fun <A, B> Iterable<A>.parallelMap(f: suspend (A) -> B): List<B> =
    coroutineScope {
        map { async { f(it) } }.awaitAll()
    }