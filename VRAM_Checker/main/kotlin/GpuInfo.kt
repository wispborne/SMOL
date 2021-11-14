/**
 * File adapted from <https://github.com/LazyWizard/console-commands/blob/ebfaeb5b0f6425745ffd039bdf0f98ffe7502734/src/main/kotlin/org/lazywizard/console/ext/SystemInfoExtGPU.kt>
 */

import org.lwjgl.opengl.ATIMeminfo
import org.lwjgl.opengl.Display
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.NVXGpuMemoryInfo
import java.util.*

private fun trimVendorString(vendor: String) = vendor.split("/".toRegex(), 2)[0]
    .replace(""" ?(series|\(r\)|\(tm\)|opengl engine)""", "")

internal fun getGPUInfo(): GPUInfo? {
//    Display.getAvailableDisplayModes().forEach {
//        println("${it.width} x ${it.height}")
//    }
    Display.getAvailableDisplayModes().minByOrNull { it.height }?.let { Display.setDisplayMode(it) }
    Display.setTitle("VRAM Counter: Checking VRAM...")
    Display.create()

    val vendor = glGetString(GL_VENDOR)?.lowercase(Locale.ROOT) ?: return null
    return when {
        vendor.startsWith("nvidia") -> NvidiaGPUInfo()
        vendor.startsWith("ati") || vendor.startsWith("amd") -> ATIGPUInfo()
        vendor.startsWith("intel") -> IntelGPUInfo()
        else -> GenericGPUInfo()
    }
}

internal abstract class GPUInfo {
    abstract fun getFreeVRAM(): Long
    open fun getGPUString(): List<String>? =
        listOf(
            "GPU Model: ${trimVendorString(glGetString(GL_RENDERER) ?: run { return null })}",
            "Vendor: ${glGetString(GL_VENDOR)}",
            "Driver version: ${glGetString(GL_VERSION)}",
            "Available VRAM: ${getFreeVRAM().bytesAsReadableMiB}"
        )
}

// https://www.khronos.org/registry/OpenGL/extensions/NVX/NVX_gpu_memory_info.txt
private class NvidiaGPUInfo : GPUInfo() {
    override fun getFreeVRAM(): Long =
        glGetInteger(NVXGpuMemoryInfo.GL_GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX) * 1024L

    override fun getGPUString(): List<String>? {
        val dedicatedVram =
            (glGetInteger(NVXGpuMemoryInfo.GL_GPU_MEMORY_INFO_DEDICATED_VIDMEM_NVX).toLong() * 1024).bytesAsReadableMiB
        val maxVram =
            (glGetInteger(NVXGpuMemoryInfo.GL_GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX).toLong() * 1024).bytesAsReadableMiB
        return super.getGPUString()?.plus(listOf("Dedicated VRAM: $dedicatedVram", "Maximum VRAM: $maxVram"))
    }
}

// https://www.khronos.org/registry/OpenGL/extensions/ATI/ATI_meminfo.txt
private class ATIGPUInfo : GPUInfo() {
    override fun getFreeVRAM(): Long = glGetInteger(ATIMeminfo.GL_TEXTURE_FREE_MEMORY_ATI) * 1024L
}

// Intel's integrated GPUS share system RAM instead of having dedicated VRAM
private class IntelGPUInfo : GPUInfo() {
    override fun getFreeVRAM(): Long = Runtime.getRuntime().freeMemory()
}

// Unknown GPU vendor - assume it's absolute crap that uses system RAM
private class GenericGPUInfo : GPUInfo() {
    override fun getFreeVRAM(): Long = Runtime.getRuntime().freeMemory()
}