package ru.maxx52.daric.keyboard

internal data class SuggestionContext(
    val textBeforeCursor: String,
    val currentWord: String,
    val previousWords: List<String>
) {
    val startsNewSentence: Boolean
        get() {
            val textBeforeCurrentWord = textBeforeCursor.dropLast(currentWord.length)
            return TextInputRules.shouldCapitalize(textBeforeCurrentWord)
        }
}

/**
 * Общий контракт движка подсказок. Позже вместо словарной реализации
 * сюда можно подключить локальную LiteRT-модель, не меняя код клавиатуры.
 */
internal fun interface SuggestionEngine {
    fun suggest(context: SuggestionContext, limit: Int): List<String>
}

internal fun interface ContextLanguageModel {
    fun predictNext(previousWords: List<String>, limit: Int): List<String>
}

internal object SuggestionContextParser {

    private val wordPattern = Regex("[\\p{L}]+(?:-[\\p{L}]+)*")

    fun parse(textBeforeCursor: String): SuggestionContext {
        val currentWord = textBeforeCursor.takeLastWhile { it.isLetter() || it == '-' }
        val completedText = textBeforeCursor.dropLast(currentWord.length)
        val previousWords = wordPattern
            .findAll(completedText)
            .map { match -> match.value.lowercase() }
            .toList()
            .takeLast(MAX_PREVIOUS_WORDS)

        return SuggestionContext(
            textBeforeCursor = textBeforeCursor,
            currentWord = currentWord,
            previousWords = previousWords
        )
    }

    private const val MAX_PREVIOUS_WORDS = 4
}
