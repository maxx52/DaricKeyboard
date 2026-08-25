package ru.maxx52.daric.keyboard

import android.graphics.Movie
import android.util.LruCache
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import kotlin.math.max

@Composable
internal fun AnimatedGif(
    url: String,
    description: String,
    modifier: Modifier = Modifier
) {
    var movie by remember(url) { mutableStateOf<Movie?>(null) }

    LaunchedEffect(url) {
        movie = withContext(Dispatchers.IO) {
            GifByteCache.load(url)?.let { bytes ->
                Movie.decodeByteArray(bytes, 0, bytes.size)
            }
        }
    }

    val currentMovie = movie
    if (currentMovie == null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(strokeWidth = 2.dp)
        }
        return
    }

    val duration = currentMovie.duration().takeIf { it > 0 } ?: 1_000
    val transition = rememberInfiniteTransition(label = "gif-animation")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = duration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gif-progress"
    )

    Canvas(
        modifier = modifier.semantics { contentDescription = description }
    ) {
        val movieWidth = currentMovie.width().coerceAtLeast(1).toFloat()
        val movieHeight = currentMovie.height().coerceAtLeast(1).toFloat()
        val scale = max(size.width / movieWidth, size.height / movieHeight)
        val offsetX = (size.width / scale - movieWidth) / 2f
        val offsetY = (size.height / scale - movieHeight) / 2f

        currentMovie.setTime((progress * duration).toInt())
        drawIntoCanvas { canvas ->
            val nativeCanvas = canvas.nativeCanvas
            val checkpoint = nativeCanvas.save()
            nativeCanvas.scale(scale, scale)
            currentMovie.draw(nativeCanvas, offsetX, offsetY)
            nativeCanvas.restoreToCount(checkpoint)
        }
    }
}

private object GifByteCache {
    private val cache = object : LruCache<String, ByteArray>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }

    fun load(url: String): ByteArray? {
        cache.get(url)?.let { return it }
        return runCatching {
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 20_000
                connection.readTimeout = 20_000
                connection.setRequestProperty("User-Agent", "DaricKeyboard/1.0 Android")
                connection.inputStream.use { it.readBytes() }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()?.also { cache.put(url, it) }
    }
}
