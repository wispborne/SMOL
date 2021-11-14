internal val Long.bytesAsReadableMiB: String
    get() = "%.3f MiB".format(this / 1048576f)