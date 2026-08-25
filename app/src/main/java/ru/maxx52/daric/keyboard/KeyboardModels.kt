package ru.maxx52.daric.keyboard

internal enum class KeyboardMode { LETTERS, SYMBOLS }
internal enum class KeyboardLanguage { RUSSIAN, ENGLISH }
internal enum class KeyboardPanel { KEYS, GIFS, GIF_SEARCH, POSTCARDS }

internal data class KeyboardUiState(
    val mode: KeyboardMode = KeyboardMode.LETTERS,
    val language: KeyboardLanguage = KeyboardLanguage.RUSSIAN,
    val panel: KeyboardPanel = KeyboardPanel.KEYS,
    val uppercase: Boolean = false,
    val suggestionsVisible: Boolean = false,
    val suggestions: List<String> = listOf("", "", ""),
    val gifItems: List<KlipyGif> = emptyList(),
    val gifQuery: String = "",
    val gifLoading: Boolean = false,
    val gifError: String? = null
) {
    val rows: List<List<String>>
        get() = when (mode) {
            KeyboardMode.LETTERS -> when (language) {
                KeyboardLanguage.RUSSIAN -> russianLetterRows
                KeyboardLanguage.ENGLISH -> englishLetterRows
            }
            KeyboardMode.SYMBOLS -> when (language) {
                KeyboardLanguage.RUSSIAN -> russianSymbolRows
                KeyboardLanguage.ENGLISH -> englishSymbolRows
            }
        }

    fun displayText(key: String): String =
        if (uppercase && key.length == 1 && key.first().isLetter()) {
            key.uppercase(
                if (language == KeyboardLanguage.RUSSIAN) java.util.Locale("ru", "RU")
                else java.util.Locale.ENGLISH
            )
        } else key
}

internal val numberRow = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")

private val russianLetterRows = listOf(
    listOf("й", "ц", "у", "к", "е", "н", "г", "ш", "щ", "з", "х", "ъ"),
    listOf("ф", "ы", "в", "а", "п", "р", "о", "л", "д", "ж", "э"),
    listOf("⇧", "я", "ч", "с", "м", "и", "т", "ь", "б", "ю", "⌫"),
    listOf("?123", "🌐", ",", "пробел", ".", "↵")
)

private val englishLetterRows = listOf(
    listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
    listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
    listOf("⇧", "z", "x", "c", "v", "b", "n", "m", "⌫"),
    listOf("?123", "🌐", ",", "space", ".", "↵")
)

private val russianSymbolRows = listOf(
    listOf("@", "#", "₽", "_", "&", "-", "+", "(", ")", "/"),
    listOf("*", "\"", "'", ":", ";", "!", "?", "⌫"),
    listOf("АБВ", "🌐", ",", "пробел", ".", "↵")
)

private val englishSymbolRows = listOf(
    listOf("@", "#", "\$", "_", "&", "-", "+", "(", ")", "/"),
    listOf("*", "\"", "'", ":", ";", "!", "?", "⌫"),
    listOf("ABC", "🌐", ",", "space", ".", "↵")
)
