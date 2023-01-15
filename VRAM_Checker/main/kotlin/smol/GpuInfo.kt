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

package smol

/**
 * File adapted from <https://github.com/LazyWizard/console-commands/blob/ebfaeb5b0f6425745ffd039bdf0f98ffe7502734/src/main/kotlin/org/lazywizard/console/ext/SystemInfoExtGPU.kt>
 */

//import org.lwjgl.opengl.NVXGpuMemoryInfo
//import org.lwjgl.opengl.GL11.*
import oshi.SystemInfo

private fun trimVendorString(vendor: String) = vendor.split("/".toRegex(), 2)[0]
    .replace(""" ?(series|\(r\)|\(tm\)|opengl engine)""", "")

internal fun getGPUInfo(): GPUInfo? {
//    Display.getAvailableDisplayModes().forEach {
//        println("${it.width} x ${it.height}")
//    }
    return runCatching {
//        println("Checking VRAM using LWJGL.")
//        glfwWindowHint(GLFW_RESIZABLE, GL_TRUE);
//        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
//        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
//        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
//        window = glfwCreateWindow(WINDOW_WIDTH, WINDOW_HEIGHT, "VRAM Counter: Checking VRAM using LWJGL...", 0, 0);
//        Display.getAvailableDisplayModes().minByOrNull { it.height }?.let { Display.setDisplayMode(it) }
//        Display.setTitle("VRAM Counter: Checking VRAM using LWJGL...")
//        Display.create()

//        val vendor = glGetString(GL_VENDOR)?.lowercase(Locale.ROOT) ?: return null
//        when {
//            vendor.startsWith("nvidia") -> NvidiaGPUInfo()
//            vendor.startsWith("ati") || vendor.startsWith("amd") -> ATIGPUInfo()
//            vendor.startsWith("intel") -> IntelGPUInfo()
//            else -> GenericGPUInfo()
//        }
//    }
//        .onFailure { println("Unable to get GPU info using LWJGL.") }
//        .recover {
        println("Checking VRAM using Oshi.")
        OshiGPUInfo().also { println(it) }
    }
        .getOrThrow()
}

internal abstract class GPUInfo {
    abstract val freeVRAM: Long
    abstract val gpuString: List<String>?
    override fun toString(): String {
        return "${this::class.simpleName}(freeVRAM=$freeVRAM, gpuString=$gpuString)"
    }
}

//internal abstract class LwjglGpuInfo : GPUInfo() {
//    override val gpuString: List<String>? =
//        listOf(
//            "GPU Model: ${trimVendorString(glGetString(GL_RENDERER) ?: "")}",
//            "Vendor: ${glGetString(GL_VENDOR)}",
//            "Driver version: ${glGetString(GL_VERSION)}",
//            "Available VRAM: ${freeVRAM.bytesAsReadableMB}"
//        )
//}

// https://www.khronos.org/registry/OpenGL/extensions/NVX/NVX_gpu_memory_info.txt
//private class NvidiaGPUInfo : LwjglGpuInfo() {
//    override fun getFreeVRAM(): Long =
//        glGetInteger(NVXGpuMemoryInfo.GL_GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX) * 1024L
//
//    override fun getGPUString(): List<String>? {
//        val dedicatedVram =
//            (glGetInteger(NVXGpuMemoryInfo.GL_GPU_MEMORY_INFO_DEDICATED_VIDMEM_NVX).toLong() * 1024).bytesAsReadableMB
//        val maxVram =
//            (glGetInteger(NVXGpuMemoryInfo.GL_GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX).toLong() * 1024).bytesAsReadableMB
//        return super.getGPUString()?.plus(listOf("Dedicated VRAM: $dedicatedVram", "Maximum VRAM: $maxVram"))
//    }
//}

// https://www.khronos.org/registry/OpenGL/extensions/ATI/ATI_meminfo.txt
//private class ATIGPUInfo : LwjglGpuInfo() {
//    override fun getFreeVRAM(): Long = glGetInteger(ATIMeminfo.GL_TEXTURE_FREE_MEMORY_ATI) * 1024L
//}
//
//// Intel's integrated GPUS share system RAM instead of having dedicated VRAM
//private class IntelGPUInfo : LwjglGpuInfo() {
//    override fun getFreeVRAM(): Long = Runtime.getRuntime().freeMemory()
//}
//
//// Unknown GPU vendor - assume it's absolute crap that uses system RAM
//private class GenericGPUInfo : LwjglGpuInfo() {
//    override fun getFreeVRAM(): Long = Runtime.getRuntime().freeMemory()
//}

// Use OSHI to get VRAM
private class OshiGPUInfo : GPUInfo() {
    override val freeVRAM: Long =
        SystemInfo().hardware.graphicsCards.maxOfOrNull { it.vRam } ?: 0L

    override val gpuString: List<String>?
        get() {
            val gpu = SystemInfo().hardware.graphicsCards.maxByOrNull { it.vRam } ?: return null
            return listOf(
                "GPU Model: ${gpu.name}",
                "Vendor: ${gpu.vendor}",
                "Driver version: ${gpu.versionInfo}",
                "Detected VRAM: ${gpu.vRam.bytesAsReadableMB}"
            )
        }
}