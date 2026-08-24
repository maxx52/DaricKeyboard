package ru.maxx52.daric.keyboard

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal data class KlipyGif(
    val slug: String,
    val title: String,
    val previewUrl: String,
    val contentUrl: String
)

internal class KlipyGifClient(
    private val apiKey: String,
    private val customerId: String
) {
    private val executor: ExecutorService = Executors.newFixedThreadPool(3)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun trending(callback: (Result<List<KlipyGif>>) -> Unit) {
        load("trending", emptyMap(), callback)
    }

    fun search(query: String, callback: (Result<List<KlipyGif>>) -> Unit) {
        load("search", mapOf("q" to query), callback)
    }

    fun download(
        gif: KlipyGif,
        destinationDirectory: File,
        callback: (Result<File>) -> Unit
    ) {
        executor.execute {
            val result = runCatching {
                if (!destinationDirectory.exists() && !destinationDirectory.mkdirs()) {
                    error("Не удалось создать папку для GIF")
                }
                val safeName = gif.slug.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val destination = File(destinationDirectory, "$safeName.gif")
                openConnection(gif.contentUrl).useConnection { connection ->
                    connection.inputStream.use { input ->
                        destination.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                destination
            }
            mainHandler.post { callback(result) }
        }
    }

    fun reportShare(slug: String) {
        executor.execute {
            runCatching {
                val url = endpoint("share/${encodePath(slug)}", emptyMap())
                val body = JSONObject().put("customer_id", customerId).toString()
                openConnection(url, method = "POST").useConnection { connection ->
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.outputStream.use {
                        it.write(body.toByteArray(StandardCharsets.UTF_8))
                    }
                    connection.inputStream.use { it.readBytes() }
                }
            }
        }
    }

    fun shutdown() {
        executor.shutdownNow()
    }

    private fun load(
        action: String,
        parameters: Map<String, String>,
        callback: (Result<List<KlipyGif>>) -> Unit
    ) {
        if (apiKey.isBlank()) {
            callback(Result.failure(IllegalStateException("KLIPY_API_KEY не задан")))
            return
        }

        executor.execute {
            val result = runCatching {
                val allParameters = parameters + mapOf(
                    "page" to "1",
                    "per_page" to "30",
                    "customer_id" to customerId,
                    "locale" to Locale.getDefault().language
                )
                val url = endpoint(action, allParameters)
                val response = openConnection(url).useConnection { connection ->
                    connection.inputStream.bufferedReader().use { it.readText() }
                }
                parseResponse(response)
            }
            mainHandler.post { callback(result) }
        }
    }

    private fun parseResponse(json: String): List<KlipyGif> {
        val root = JSONObject(json)
        if (!root.optBoolean("result", false)) error("KLIPY вернул ошибку")
        val items = root.optJSONObject("data")
            ?.optJSONArray("data")
            ?: return emptyList()

        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                if (item.optString("type") == "ad") continue
                val file = item.optJSONObject("file") ?: continue
                val preview = findGifUrl(file, listOf("sm", "xs", "md", "hd")) ?: continue
                val content = findGifUrl(file, listOf("md", "hd", "sm", "xs")) ?: preview
                val slug = item.optString("slug").ifBlank { "gif_$index" }
                add(
                    KlipyGif(
                        slug = slug,
                        title = item.optString("title", "GIF"),
                        previewUrl = preview,
                        contentUrl = content
                    )
                )
            }
        }
    }

    private fun findGifUrl(file: JSONObject, sizes: List<String>): String? {
        return sizes.firstNotNullOfOrNull { size ->
            file.optJSONObject(size)
                ?.optJSONObject("gif")
                ?.optString("url")
                ?.takeIf(String::isNotBlank)
        }
    }

    private fun endpoint(action: String, parameters: Map<String, String>): String {
        val query = parameters.entries.joinToString("&") { (key, value) ->
            "${encodeQuery(key)}=${encodeQuery(value)}"
        }
        return buildString {
            append(BASE_URL)
            append(encodePath(apiKey))
            append("/gifs/")
            append(action)
            if (query.isNotEmpty()) append('?').append(query)
        }
    }

    private fun openConnection(url: String, method: String = "GET"): HttpURLConnection {
        return URI(url).toURL().openConnection().let { it as HttpURLConnection }.apply {
            requestMethod = method
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("User-Agent", "DaricKeyboard/1.0 Android")
        }
    }

    private inline fun <T> HttpURLConnection.useConnection(block: (HttpURLConnection) -> T): T {
        return try {
            val result = block(this)
            if (responseCode !in 200..299) {
                error("HTTP $responseCode: ${errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()}")
            }
            result
        } finally {
            disconnect()
        }
    }

    private fun encodePath(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString()).replace("+", "%20")

    private fun encodeQuery(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    private companion object {
        const val BASE_URL = "https://api.klipy.com/api/v1/"
        const val TIMEOUT_MS = 30_000
    }
}
