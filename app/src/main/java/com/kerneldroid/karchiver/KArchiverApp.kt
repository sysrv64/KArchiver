package com.kerneldroid.karchiver

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.svg.SvgDecoder
import coil3.video.VideoFrameDecoder
import com.kerneldroid.karchiver.data.elevation.ShizukuEngine

class KArchiverApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        ShizukuEngine.init(this)
        try {
            System.loadLibrary("karchiver_rs")
        } catch (_: UnsatisfiedLinkError) {
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
