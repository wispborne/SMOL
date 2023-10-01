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

@file:Suppress("NOTHING_TO_INLINE")

package smol.utilities

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import org.apache.commons.io.FileUtils
import smol.timber.ktx.Timber
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.FileVisitOption
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.*
import kotlin.random.Random
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.primaryConstructor
import kotlin.streams.asSequence
import kotlin.system.measureTimeMillis


@Deprecated("Use Path instead", replaceWith = ReplaceWith("toPathOrNull()"))
fun String?.toFileOrNull() = this?.let { File(it) }
fun String?.toPathOrNull() = this?.let { runCatching { Path.of(it) }.onFailure { Timber.d(it) }.getOrNull() }

@Deprecated("Use Path.createDirectories() instead")
fun File.mkdirsIfNotExist() {
    if (this.isFile) {
        this.parentFile.mkdirsIfNotExist()
    } else if (!this.exists()) {
        this.mkdirs()
    }
}

/**
 * Deletes the folder and all files in it if it/they exist.
 * [source](https://stackoverflow.com/a/35989142/1622788)
 * @param precheckFilesForDeletability If true, performs an initial walk to verify each file is deletable in an effort to prevent deletion from stopping halfway through.
 */
fun Path.deleteRecursively(
    precheckFilesForDeletability: Boolean = true,
    vararg options: FileVisitOption = arrayOf(FileVisitOption.FOLLOW_LINKS)
) {
    if (!this.exists()) return

    if (precheckFilesForDeletability) {
        val sm = System.getSecurityManager()

        Files.walk(this, *options).use { walk ->
            walk.sorted(Comparator.reverseOrder())
                .forEach { obj ->
                    if (sm == null) {
                        if (!obj.isWritable())
                            throw SecurityException("'${obj.absolutePathString()}' cannot be deleted. Abandoning deletion of '${this.absolutePathString()}'.")
                    } else {
                        sm.checkDelete(obj.absolutePathString())
                    }
                }
        }
    }

    Files.walk(this, *options).use { walk ->
        walk.sorted(Comparator.reverseOrder())
            .forEach { obj -> obj.deleteIfExists() }
    }
}

suspend fun Path.calculateFileSize() =
    coroutineScope {
        if (this@calculateFileSize.isRegularFile()) {
            this@calculateFileSize.fileSize()
        } else {
            this@calculateFileSize.walk(options = arrayOf(FileVisitOption.FOLLOW_LINKS)).sumOf { it.fileSize() }
        }
    }

fun Path?.exists(vararg options: LinkOption): Boolean = this?.let { Files.exists(this, *options) } == true

fun Path.walk(maxDepth: Int = Int.MAX_VALUE, vararg options: FileVisitOption): Sequence<Path> =
    Files.walk(this, maxDepth, *options)
        .asSequence()
        .filter {
            runCatching { !it.isSameFileAs(this) }
                .onFailure { Timber.i(it) }
                .getOrElse { false }
        }

fun Throwable.rootCause(): Throwable {
    return if (this.cause != null && this !== this.cause)
        this.rootCause()
    else
        this
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
fun String.equalsAny(vararg other: String, ignoreCase: Boolean = true): Boolean =
    arrayOf(*other).any { this.equals(it, ignoreCase) }

fun Any.isAny(vararg other: KClass<*>): Boolean = arrayOf(*other).asList().any { this::class == it }

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

inline fun String.nullIfBlank() = this.ifBlank { null }

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
 * From FileUtils in apache commons.
 */
@Throws(IOException::class)
fun Path.moveDirectory(destDir: Path) = this.toFile().moveDirectory(destDir.toFile())

/**
 * [https://jivimberg.io/blog/2018/05/04/parallel-map-in-kotlin/]
 */
suspend fun <A, B> Iterable<A>.parallelMap(context: CoroutineContext = Dispatchers.Default, f: suspend (A) -> B): List<B> =
    coroutineScope {
        map { async(context) { f(it) } }.awaitAll()
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

inline fun <T> trace(func: () -> T): T =
    trace(
        onFinished = { result, ms -> println("Took ${ms}ms to produce ${if (result != null) result!!::class.simpleName else "null"}.") },
        func = func
    )

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

fun <T> List<Pair<T, Float>>.weightedRandom(): T {
    val totalWeight = this.sumOf { it.second.toDouble() }
    val random = Random.nextDouble() * totalWeight
    var weightSoFar = 0.0

    this.forEach { entry ->
        weightSoFar += entry.second
        if (random <= weightSoFar) return entry.first
    }

    Timber.w { "Somehow failed to get a random item." }
    return this.random().first
}

suspend fun Path.awaitWrite(timeoutMillis: Long = 1000): Path {
    var delayAcc = 0

    while (!this@awaitWrite.isWritable() && delayAcc < timeoutMillis) {
        delayAcc += 10
        delay(timeMillis = 10)
    }

    if (this@awaitWrite.isWritable()) {
        return this@awaitWrite
    } else {
        throw RuntimeException("Timed out waiting ${timeoutMillis}ms for write access to $this.")
    }
}

fun Path.isMissingAdmin(): Boolean = this.exists() && !this.isWritable()

@Throws(IOException::class)
fun Path.mountOf(): Path? {
    val fs = Files.getFileStore(this)
    var temp = this.toAbsolutePath()
    var mountp = temp

    while (temp.parent.also { temp = it } != null && fs == Files.getFileStore(temp)) {
        mountp = temp
    }

    return mountp
}

@Suppress("NOTHING_TO_INLINE")
/**
 * Returns a list containing all elements except first [n] elements.
 *
 * @throws IllegalArgumentException if [n] is negative.
 */
inline fun <T> List<T>.skip(n: Int) = drop(n)

inline fun Any.exhaustiveWhen() = this.run { }

fun <T> mergeData(property: KProperty1<out T, Any?>, left: T, right: T): Any? {
    val leftValue = property.getter.call(left)
    val rightValue = property.getter.call(right)
    return rightValue?.let {
        if ((property.returnType.classifier as KClass<*>).isSubclassOf(Map::class)) (leftValue as? Map<*, *>)?.plus(it as Map<*, *>)
        else leftValue?.merge(it)
    } ?: rightValue ?: leftValue
}

fun <T> lastNonNull(property: KProperty1<out T, Any?>, left: T, right: T) =
    property.getter.call(right) ?: property.getter.call(left)

/**
 * <https://stackoverflow.com/a/59958123/1622788>
 */
fun <T : Any> T.merge(preferredObj: T): T {
    val nameToProperty = this::class.declaredMemberProperties.associateBy { it.name }
    val primaryConstructor = this::class.primaryConstructor!!
    val args: Map<KParameter, Any?> = primaryConstructor.parameters.associateWith { parameter ->
        val property = nameToProperty[parameter.name]!!
        val type = property.returnType.classifier as KClass<*>
        when {
            type.isData || type.isSubclassOf(Map::class) -> mergeData(property, this, preferredObj)
            else -> lastNonNull(property, this, preferredObj)
        }
    }
    return primaryConstructor.callBy(args)
}

@OptIn(ExperimentalCoroutinesApi::class)
fun <T, K> StateFlow<T>.mapState(
    scope: CoroutineScope,
    transform: (data: T) -> K
): StateFlow<K> {
    return mapLatest {
        transform(it)
    }
        .stateIn(scope, SharingStarted.Eagerly, transform(value))
}

@OptIn(ExperimentalCoroutinesApi::class)
fun <T, K> StateFlow<T>.mapState(
    initialValue: K,
    scope: CoroutineScope,
    transform: suspend (data: T) -> K
): StateFlow<K> {
    return mapLatest {
        transform(it)
    }
        .stateIn(scope, SharingStarted.Eagerly, initialValue)
}