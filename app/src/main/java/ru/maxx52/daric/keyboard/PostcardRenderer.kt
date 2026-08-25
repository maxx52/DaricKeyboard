package ru.maxx52.daric.keyboard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import java.io.File
import java.io.FileOutputStream

internal object PostcardRenderer {

    private const val IMAGE_SIZE = 1080
    private const val MAX_CACHED_POSTCARDS = 12

    fun renderToFile(postcard: Postcard, directory: File): File {
        check(directory.exists() || directory.mkdirs()) {
            "Unable to create postcard cache directory"
        }

        val bitmap = Bitmap.createBitmap(
            IMAGE_SIZE,
            IMAGE_SIZE,
            Bitmap.Config.ARGB_8888
        )
        try {
            val canvas = Canvas(bitmap)
            drawBackground(canvas, postcard)
            drawDecoration(canvas, postcard)
            drawMessage(canvas, postcard)

            val file = File(
                directory,
                postcard.id + "-" + System.currentTimeMillis() + ".png"
            )
            FileOutputStream(file).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "Unable to encode postcard"
                }
            }
            pruneCache(directory)
            return file
        } finally {
            bitmap.recycle()
        }
    }

    private fun drawBackground(canvas: Canvas, postcard: Postcard) {
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                IMAGE_SIZE.toFloat(),
                IMAGE_SIZE.toFloat(),
                postcard.startColor,
                postcard.endColor,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(
            0f,
            0f,
            IMAGE_SIZE.toFloat(),
            IMAGE_SIZE.toFloat(),
            background
        )

        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(34, 255, 255, 255)
        }
        canvas.drawCircle(120f, 170f, 230f, glow)
        canvas.drawCircle(970f, 910f, 290f, glow)
        canvas.drawCircle(900f, 80f, 130f, glow)

        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        canvas.drawRoundRect(38f, 38f, 1042f, 1042f, 48f, 48f, frame)
    }

    private fun drawDecoration(canvas: Canvas, postcard: Postcard) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = 150f
            typeface = Typeface.DEFAULT
        }
        canvas.drawText(postcard.decoration, 835f, 245f, paint)
    }

    private fun drawMessage(canvas: Canvas, postcard: Postcard) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = postcard.textColor
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setShadowLayer(4f, 0f, 2f, Color.argb(60, 0, 0, 0))
        }

        paint.textSize = 82f
        val headlineBottom = drawCenteredMultiline(
            canvas = canvas,
            text = postcard.title,
            firstBaseline = 500f,
            maxWidth = 850f,
            lineHeight = 96f,
            paint = paint
        )

        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        paint.textSize = 48f
        drawCenteredMultiline(
            canvas = canvas,
            text = postcard.message,
            firstBaseline = headlineBottom + 92f,
            maxWidth = 820f,
            lineHeight = 64f,
            paint = paint
        )
    }

    private fun drawCenteredMultiline(
        canvas: Canvas,
        text: String,
        firstBaseline: Float,
        maxWidth: Float,
        lineHeight: Float,
        paint: Paint
    ): Float {
        val lines = wrapText(text, paint, maxWidth)
        var baseline = firstBaseline
        lines.forEach { line ->
            canvas.drawText(line, IMAGE_SIZE / 2f, baseline, paint)
            baseline += lineHeight
        }
        return baseline - lineHeight
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        val words = text.trim().split(Regex("\\s+"))
        var current = ""

        words.forEach { word ->
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth || current.isEmpty()) {
                current = candidate
            } else {
                lines += current
                current = word
            }
        }
        if (current.isNotEmpty()) lines += current
        return lines.ifEmpty { listOf("") }
    }

    private fun pruneCache(directory: File) {
        directory.listFiles()
            ?.filter { it.isFile && it.extension.equals("png", ignoreCase = true) }
            ?.sortedByDescending(File::lastModified)
            ?.drop(MAX_CACHED_POSTCARDS)
            ?.forEach { it.delete() }
    }
}
