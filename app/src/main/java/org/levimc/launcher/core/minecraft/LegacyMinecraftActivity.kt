package org.levimc.launcher.core.minecraft

import android.app.NativeActivity
import android.content.Intent
import android.content.res.AssetManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import org.levimc.launcher.core.crash.CrashReporter
import org.levimc.launcher.core.mods.ModManager
import org.levimc.launcher.preloader.PreloaderInput
import java.io.File

/**
 * Minecraft builds before the GameActivity migration (including 1.21.61.01)
 * ship an Android NativeActivity entrypoint. Launching those packages through
 * the newer GameActivity superclass fails before Minecraft can start.
 */
class LegacyMinecraftActivity : NativeActivity() {
    private lateinit var gameManager: GamePackageManager
    private lateinit var trace: LaunchTrace
    private var runtimeStarted = false
    private var restartScheduled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        trace = LaunchTrace.ensure(intent)
        trace.mark("LegacyMinecraftActivity onCreate entered")
        try {
            val preparedRuntime = MinecraftLaunchSession.getPreparedRuntime()
                ?: MinecraftRuntimePreparer.prepare(applicationContext, intent)
            gameManager = preparedRuntime.gameManager
            trace.mark("Legacy NativeActivity runtime consumed")
        } catch (error: Throwable) {
            trace.error("Legacy activity preparation failed", error.message ?: error.javaClass.simpleName)
            finish()
            return
        }

        // NativeActivity invokes the preloader ANativeActivity_onCreate bridge.
        // That bridge forwards to the already-loaded libminecraftpe.so entrypoint.
        super.onCreate(savedInstanceState)
        runtimeStarted = true
        ModManager.enableLoadedMods()
        PreloaderInput.setActivity(this)
        MinecraftActivityState.onCreated(this)
        trace.mark("LegacyMinecraftActivity onCreate finished")
    }

    override fun onResume() {
        super.onResume()
        MinecraftActivityState.onResumed(this)
    }

    override fun onPause() {
        MinecraftActivityState.onPaused(this)
        super.onPause()
    }

    override fun onDestroy() {
        ModManager.disableAndUnloadLoadedMods()
        PreloaderInput.clearActivity()
        MinecraftActivityState.onDestroyed(this)
        MinecraftLaunchSession.clear()
        super.onDestroy()
        if (runtimeStarted && isFinishing && !CrashReporter.isHandlingCrash() && !restartScheduled) {
            restartScheduled = true
            MinecraftProcessRestarter.restartLauncherAfterMinecraftExit(this)
        }
    }

    override fun getAssets(): AssetManager {
        return if (::gameManager.isInitialized) gameManager.getAssets() else super.getAssets()
    }

    override fun getFilesDir(): File =
        resolveStorageDir(MinecraftLauncher.EXTRA_STORAGE_FILES_DIR, super.getFilesDir())

    override fun getDataDir(): File =
        resolveStorageDir(MinecraftLauncher.EXTRA_STORAGE_DATA_DIR, super.getDataDir())

    override fun getExternalFilesDir(type: String?): File? {
        val base = resolveStorageDir(
            MinecraftLauncher.EXTRA_STORAGE_EXTERNAL_FILES_DIR,
            super.getExternalFilesDir(null) ?: super.getFilesDir()
        )
        return if (type.isNullOrEmpty()) base else File(base, type).also { it.mkdirs() }
    }

    /** JNI callbacks used by legacy libminecraftpe.so; NativeActivity does not provide them itself. */
    fun getInternalStoragePath(): String = getFilesDir().absolutePath

    /** JNI callbacks used by legacy libminecraftpe.so; NativeActivity does not provide them itself. */
    fun getExternalStoragePath(): String = (getExternalFilesDir(null) ?: getFilesDir()).absolutePath

    override fun getDatabasePath(name: String): File {
        val directory = File(getDataDir(), "databases")
        if (!directory.exists()) directory.mkdirs()
        return File(directory, name)
    }

    override fun getCacheDir(): File =
        resolveStorageDir(MinecraftLauncher.EXTRA_STORAGE_CACHE_DIR, super.getCacheDir())

    override fun onNewIntent(intent: Intent) {
        setIntent(intent)
        super.onNewIntent(intent)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val handled = when (event.action) {
            KeyEvent.ACTION_DOWN -> PreloaderInput.onKeyEvent(event.keyCode, event.unicodeChar, true)
            KeyEvent.ACTION_UP -> PreloaderInput.onKeyEvent(event.keyCode, event.unicodeChar, false)
            else -> false
        }
        return handled || super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val index = event.actionIndex
        if (PreloaderInput.onTouch(
                event.actionMasked,
                event.getPointerId(index),
                event.getX(index),
                event.getY(index)
            )
        ) return true
        return super.dispatchTouchEvent(event)
    }

    private fun resolveStorageDir(extra: String, fallback: File): File {
        val path = intent.getStringExtra(extra)
        if (path.isNullOrBlank()) return fallback
        return File(path).also { it.mkdirs() }
    }
}
