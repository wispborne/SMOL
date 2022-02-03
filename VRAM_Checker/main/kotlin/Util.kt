internal val Long.bytesAsReadableMB: String
    get() = "%.3f MB".format(this / 1000000f)