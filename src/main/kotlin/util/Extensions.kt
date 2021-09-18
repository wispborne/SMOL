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