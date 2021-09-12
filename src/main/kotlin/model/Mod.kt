package model

import java.io.File
import kotlin.random.Random

data class Mod(
    val modInfo: ModInfo,
    val isEnabled: Boolean = Random.nextBoolean(),
    val folder: File
)