package ru.maxx52.daric.keyboard

internal object TextInputRules {

    fun removesLeadingSpace(punctuation: Char): Boolean =
        punctuation in NO_LEADING_SPACE_PUNCTUATION

    fun spacesToDeleteBeforePunctuation(
        textBeforeCursor: String,
        punctuation: Char
    ): Int {
        if (!removesLeadingSpace(punctuation)) return 0

        val trailingSpaces = textBeforeCursor
            .takeLastWhile { it == ' ' }
            .length
        if (trailingSpaces == 0) return 0

        val characterBeforeSpaces = textBeforeCursor
            .dropLast(trailingSpaces)
            .lastOrNull()
            ?: return 0

        if (characterBeforeSpaces.isWhitespace()) return 0

        // Один пробел считается обычным и удаляется автоматически. Два и более
        // пробела означают явное желание пользователя оставить один пробел.
        return if (trailingSpaces == 1) 1 else trailingSpaces - 1
    }

    fun shouldCapitalize(textBeforeCursor: String): Boolean {
        var index = textBeforeCursor.lastIndex
        while (
            index >= 0 &&
            (textBeforeCursor[index] in IGNORED_TRAILING_WHITESPACE ||
                textBeforeCursor[index] in CLOSING_PUNCTUATION)
        ) {
            index--
        }

        return index < 0 ||
            textBeforeCursor[index] == '\n' ||
            textBeforeCursor[index] in SENTENCE_END_PUNCTUATION
    }

    private const val NO_LEADING_SPACE_PUNCTUATION = ".,!?;:%…)]}»"
    private const val SENTENCE_END_PUNCTUATION = ".!?…"
    private const val CLOSING_PUNCTUATION = "\"'»)]}"
    private const val IGNORED_TRAILING_WHITESPACE = " \t\r"
}
