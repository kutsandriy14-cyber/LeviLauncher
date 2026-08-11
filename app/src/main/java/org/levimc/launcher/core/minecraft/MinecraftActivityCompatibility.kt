package org.levimc.launcher.core.minecraft

import org.levimc.launcher.core.versions.GameVersion

/**
 * Minecraft 1.21.61.01 and earlier package their main screen as Android
 * NativeActivity. The launcher source currently targets newer GameActivity
 * builds, so these releases must use the NativeActivity bridge instead.
 */
object MinecraftActivityCompatibility {
    private const val GAME_ACTIVITY_MINOR = 21
    private const val GAME_ACTIVITY_PATCH = 80

    fun usesLegacyNativeActivity(version: GameVersion?): Boolean {
        val value = version?.versionCode.orEmpty()
        val parts = value.replace(Regex("[^0-9.]"), "")
            .split('.')
            .mapNotNull { it.toIntOrNull() }
        if (parts.size < 2) return false
        val major = parts[0]
        val minor = parts[1]
        val patch = parts.getOrElse(2) { 0 }
        if (major != 1) return false
        return minor < GAME_ACTIVITY_MINOR ||
                (minor == GAME_ACTIVITY_MINOR && patch < GAME_ACTIVITY_PATCH)
    }
}
