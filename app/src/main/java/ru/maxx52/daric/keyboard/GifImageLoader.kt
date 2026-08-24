package ru.maxx52.daric.keyboard

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.util.LruCache
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URI
import java.nio.ByteBuffer
import java.util.concurrent.Executors

internal object GifImageLoader {
    private val executor = Executors.newFixedThreadPool(4)
    private val memoryCache = object : LruCache<String, ByteArray>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }

    fun load(imageView: ImageView, url: String) {
        imageView.tag = url
        val cached = memoryCache.get(url)
        if (cached != null) {
            show(imageView, url, cached)
            return
        }

        executor.execute {
            val bytes = runCatching {
                val connection = URI(url).toURL().openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 20_000
                    connection.readTimeout = 20_000
                    connection.inputStream.use { it.readBytes() }
                } finally {
                    connection.disconnect()
                }
            }.getOrNull() ?: return@execute

            memoryCache.put(url, bytes)
            imageView.post { show(imageView, url, bytes) }
        }
    }

    private fun show(imageView: ImageView, url: String, bytes: ByteArray) {
        if (imageView.tag != url) return
        val drawable = runCatching {
            ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(bytes)))
        }.getOrNull() ?: return
        imageView.setImageDrawable(drawable)
        (drawable as? AnimatedImageDrawable)?.apply {
            repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
            start()
        }
    }
}
