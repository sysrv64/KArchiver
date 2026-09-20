package com.kerneldroid.karchiver.presentation.browser

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.video.videoFramePercent
import com.kerneldroid.karchiver.data.FileItem
import com.kerneldroid.karchiver.data.FormatCategory

internal val FileItem.canShowThumbnail: Boolean
    get() = !isDirectory && when (format.category) {
        FormatCategory.IMAGE, FormatCategory.VIDEO -> true
        else -> false
    }

@Composable
internal fun FileLeadingContent(
    item: FileItem,
    tint: Color,
    iconModifier: Modifier,
    enableThumbnails: Boolean
) {
    Icon(
        if (item.isDirectory) Icons.Filled.Folder else item.format.icon,
        null,
        tint = tint,
        modifier = iconModifier
    )
    if (enableThumbnails && item.canShowThumbnail) {
        val context = LocalContext.current
        val request = remember(item.file, item.format.category) {
            val builder = ImageRequest.Builder(context).data(item.file)
            if (item.format.category == FormatCategory.VIDEO) builder.videoFramePercent(0.5)
            builder.build()
        }
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            error = ColorPainter(Color.Transparent),
            fallback = ColorPainter(Color.Transparent),
            modifier = Modifier.fillMaxSize()
        )
    }
}
