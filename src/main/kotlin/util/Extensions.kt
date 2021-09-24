package util

import java.io.File
import java.lang.reflect.Modifier
import java.util.*

fun String?.toFileOrNull() = this?.let { File(it) }

fun File.mkdirsIfNotExist() {
    if (!this.exists()) {
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