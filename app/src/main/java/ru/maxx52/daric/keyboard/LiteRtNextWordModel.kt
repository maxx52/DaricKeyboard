package ru.maxx52.daric.keyboard

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.exp

/**
 * Компактная двухслойная нейросеть для ранжирования следующего русского слова.
 * Модель и словари загружаются из APK; набранный текст никуда не передаётся.
 */
internal class LiteRtNextWordModel private constructor(
    private val interpreter: Interpreter,
    contextWords: List<String>,
    private val outputWords: List<String>
) : ContextLanguageModel, Closeable {

    private val russianLocale = Locale("ru", "RU")
    private val contextWordIndex = contextWords
        .withIndex()
        .associate { indexedValue -> indexedValue.value to indexedValue.index }
    private val contextVocabularySize = contextWords.size
    private val inputSize = MODEL_CONTEXT_WORD_COUNT * contextVocabularySize
    private var closed = false

    @Synchronized
    override fun predictNext(previousWords: List<String>, limit: Int): List<String> {
        if (closed || previousWords.isEmpty() || limit <= 0) return emptyList()

        val inputValues = FloatArray(inputSize)
        val context = previousWords.takeLast(MODEL_CONTEXT_WORD_COUNT)
        val firstSlot = MODEL_CONTEXT_WORD_COUNT - context.size
        var recognizedWordCount = 0

        context.forEachIndexed { relativePosition, word ->
            val wordIndex = contextWordIndex[word.lowercase(russianLocale)]
                ?: return@forEachIndexed
            val position = (firstSlot + relativePosition) * contextVocabularySize + wordIndex
            inputValues[position] = 1f
            recognizedWordCount++
        }
        if (recognizedWordCount == 0) return emptyList()

        val outputValues = FloatArray(outputWords.size)
        interpreter.run(arrayOf(inputValues), arrayOf(outputValues))

        val maxLogit = outputValues.maxOrNull() ?: return emptyList()
        val exponentials = DoubleArray(outputValues.size) { index ->
            exp((outputValues[index] - maxLogit).toDouble())
        }
        val exponentialSum = exponentials.sum()
        if (!exponentialSum.isFinite() || exponentialSum <= 0.0) return emptyList()

        return outputValues.indices
            .sortedByDescending { index -> outputValues[index] }
            .mapNotNull { index ->
                val confidence = exponentials[index] / exponentialSum
                outputWords[index].takeIf { confidence >= MIN_CONFIDENCE }
            }
            .take(limit)
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        interpreter.close()
    }

    internal companion object {

        fun create(context: Context): LiteRtNextWordModel {
            val contextWords = readVocabulary(context, CONTEXT_VOCABULARY_ASSET)
            val outputWords = readVocabulary(context, OUTPUT_VOCABULARY_ASSET)
            val modelBuffer = loadModelBuffer(context)

            val interpreter = Interpreter(
                modelBuffer,
                Interpreter.Options().setNumThreads(INFERENCE_THREAD_COUNT)
            )
            try {
                val inputShape = interpreter.getInputTensor(0).shape()
                val outputShape = interpreter.getOutputTensor(0).shape()
                require(inputShape.contentEquals(intArrayOf(1, MODEL_CONTEXT_WORD_COUNT * contextWords.size))) {
                    "Unexpected neural model input shape: " + inputShape.contentToString()
                }
                require(outputShape.contentEquals(intArrayOf(1, outputWords.size))) {
                    "Unexpected neural model output shape: " + outputShape.contentToString()
                }
                return LiteRtNextWordModel(interpreter, contextWords, outputWords)
            } catch (error: Throwable) {
                interpreter.close()
                throw error
            }
        }

        private fun loadModelBuffer(context: Context): ByteBuffer {
            val modelBytes = context.assets.open(MODEL_ASSET).use { stream ->
                stream.readBytes()
            }
            return ByteBuffer.allocateDirect(modelBytes.size)
                .order(ByteOrder.nativeOrder())
                .apply {
                    put(modelBytes)
                    rewind()
                }
        }

        private fun readVocabulary(context: Context, assetName: String): List<String> {
            return context.assets.open(assetName).bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.map(String::trim)
                    .filter(String::isNotBlank)
                    .distinct()
                    .toList()
            }.also { words ->
                require(words.isNotEmpty()) { "Empty neural model vocabulary: " + assetName }
            }
        }

        private const val MODEL_ASSET = "daric_next_word.tflite"
        private const val CONTEXT_VOCABULARY_ASSET = "daric_context_vocab.txt"
        private const val OUTPUT_VOCABULARY_ASSET = "daric_output_vocab.txt"
        private const val MODEL_CONTEXT_WORD_COUNT = 3
        private const val INFERENCE_THREAD_COUNT = 1
        private const val MIN_CONFIDENCE = 0.08
    }
}
