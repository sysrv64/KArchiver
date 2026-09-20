package com.kerneldroid.karchiver

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.svg.SvgDecoder
import coil3.video.VideoFrameDecoder
import com.kerneldroid.karchiver.data.RustBridge
import com.kerneldroid.karchiver.data.elevation.ShizukuEngine
import com.kerneldroid.karchiver.data.log.KLog
import com.kerneldroid.karchiver.data.log.LogReport

class KArchiverApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        ShizukuEngine.init(this)
        try {
            System.loadLibrary("karchiver_rs")
        } catch (_: UnsatisfiedLinkError) {
        }
        runCatching { RustBridge.setLogFile(LogReport.rustLogFile(this).absolutePath) }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            KLog.e("Crash", "Uncaught exception in ${thread.name}", throwable)
            previous?.uncaughtException(thread, throwable)
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(SvgDecoder.Factory())
                add(VideoFrameDecoder.Factory())
            }
            .build()
}
