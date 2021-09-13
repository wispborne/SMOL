package util

import java.io.File

fun String?.toFileOrNull() = this?.let { File(it) }