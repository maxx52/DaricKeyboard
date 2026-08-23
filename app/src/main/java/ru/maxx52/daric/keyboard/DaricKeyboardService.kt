package ru.maxx52.daric.keyboard

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import java.util.Locale

class DaricKeyboardService : InputMethodService() {

    private enum class KeyboardMode { LETTERS, SYMBOLS }

    private lateinit var keyboardRoot: LinearLayout
    private val suggestionButtons = mutableListOf<Button>()
    private val deleteRepeatHandler = Handler(Looper.getMainLooper())
    private var deleteRepeated = false
    private val deleteRepeatAction = object : Runnable {
        override fun run() {
            deleteRepeated = true
            deleteOneCharacter()
            deleteRepeatHandler.postDelayed(this, DELETE_REPEAT_INTERVAL_MS)
        }
    }
    private val russianLocale = Locale("ru", "RU")
    private var mode = KeyboardMode.LETTERS
    private var uppercase = false
    private var editorInfo: EditorInfo? = null

    override fun onCreateInputView(): View {
        keyboardRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(3), dp(5), dp(3), dp(5))
            setBackgroundColor(Color.parseColor("#ECE9F4"))
        }
        renderKeyboard()
        return keyboardRoot
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        editorInfo = info
        mode = when (info?.inputType?.and(InputType.TYPE_MASK_CLASS)) {
            InputType.TYPE_CLASS_NUMBER,
            InputType.TYPE_CLASS_PHONE,
            InputType.TYPE_CLASS_DATETIME -> KeyboardMode.SYMBOLS
            else -> KeyboardMode.LETTERS
        }
        uppercase = false
        if (::keyboardRoot.isInitialized) renderKeyboard()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onFinishInputView(finishingInput: Boolean) {
        stopBackspaceRepeat()
        super.onFinishInputView(finishingInput)
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(
            oldSelStart,
            oldSelEnd,
            newSelStart,
            newSelEnd,
            candidatesStart,
            candidatesEnd
        )
        if (::keyboardRoot.isInitialized) {
            keyboardRoot.post { updateSuggestions() }
        }
    }

    private fun renderKeyboard() {
        keyboardRoot.removeAllViews()
        suggestionButtons.clear()

        if (suggestionsAllowed()) {
            addSuggestionBar()
        }
        addRow(numberRow, isNumberPanel = true)

        val rows = when (mode) {
            KeyboardMode.LETTERS -> letterRows
            KeyboardMode.SYMBOLS -> symbolRows
        }
        rows.forEach(::addRow)
        keyboardRoot.post { updateSuggestions() }
    }

    private fun addSuggestionBar() {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(3), dp(2), dp(3), dp(2))
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(Color.rgb(245, 241, 250))
            }
        }

        repeat(SUGGESTION_COUNT) {
            val button = Button(this).apply {
                gravity = Gravity.CENTER
                textSize = 16f
                isAllCaps = false
                minWidth = 0
                minimumWidth = 0
                minHeight = 0
                minimumHeight = 0
                setPadding(dp(4), 0, dp(4), 0)
                setTextColor(Color.rgb(57, 45, 72))
                background = GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                }
            }
            suggestionButtons += button
            row.addView(
                button,
                LinearLayout.LayoutParams(0, dp(42), 1f)
            )
        }

        keyboardRoot.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(5)
            }
        )
    }

    private fun updateSuggestions() {
        if (suggestionButtons.isEmpty() || !suggestionsAllowed()) return

        val prefix = currentWord()
        if (prefix.isBlank()) {
            suggestionButtons.forEach {
                it.text = ""
                it.isEnabled = false
                it.setOnClickListener(null)
            }
            return
        }

        val completions = RussianSuggestionEngine
            .suggest(prefix.lowercase(russianLocale), limit = 2)
            .map { applyPrefixCase(prefix, it) }

        val candidates = listOf(
            completions.getOrNull(0),
            prefix,
            completions.getOrNull(1)
        )

        suggestionButtons.forEachIndexed { index, button ->
            val candidate = candidates[index]
            button.text = candidate.orEmpty()
            button.isEnabled = candidate != null
            button.setOnClickListener(
                candidate?.let { word ->
                    View.OnClickListener { applySuggestion(word) }
                }
            )
        }
    }

    private fun currentWord(): String {
        val beforeCursor = currentInputConnection
            ?.getTextBeforeCursor(MAX_CONTEXT_LENGTH, 0)
            ?.toString()
            .orEmpty()

        return beforeCursor.takeLastWhile { character ->
            character.isLetter() || character == '-'
        }
    }

    private fun applySuggestion(suggestion: String) {
        val inputConnection = currentInputConnection ?: return
        val currentWord = currentWord()
        if (currentWord.isBlank()) return

        inputConnection.beginBatchEdit()
        inputConnection.deleteSurroundingTextInCodePoints(
            currentWord.codePointCount(0, currentWord.length),
            0
        )
        inputConnection.commitText("$suggestion ", 1)
        inputConnection.endBatchEdit()
        keyboardRoot.post { updateSuggestions() }
    }

    private fun applyPrefixCase(prefix: String, suggestion: String): String = when {
        prefix.all(Char::isUpperCase) -> suggestion.uppercase(russianLocale)
        prefix.firstOrNull()?.isUpperCase() == true ->
            suggestion.take(1).uppercase(russianLocale) + suggestion.drop(1)
        else -> suggestion
    }

    private fun suggestionsAllowed(): Boolean {
        val inputType = editorInfo?.inputType ?: return false
        if ((inputType and InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) {
            return false
        }
        if ((inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0) {
            return false
        }

        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return variation !in blockedSuggestionVariations
    }

    private fun addRow(
        keys: List<String>,
        isNumberPanel: Boolean = false
    ) {
        val keyHeight = if (isNumberPanel) 44 else 52

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            if (isNumberPanel) {
                setPadding(dp(2), dp(2), dp(2), dp(2))
                background = GradientDrawable().apply {
                    cornerRadius = dp(10).toFloat()
                    setColor(Color.rgb(216, 209, 232))
                }
            }
        }

        keys.forEach { key ->
            val button = Button(this).apply {
                text = displayText(key)
                gravity = Gravity.CENTER
                textSize = when {
                    key == "пробел" -> 13f
                    isNumberPanel -> 16f
                    else -> 18f
                }
                isAllCaps = false
                minWidth = 0
                minimumWidth = 0
                minHeight = 0
                minimumHeight = 0
                setPadding(0, 0, 0, 0)
                setTextColor(Color.parseColor("#241F2E"))
                background = keyBackground(key, isNumberPanel)
                if (key == "⌫") {
                    configureBackspace(this)
                } else {
                    setOnClickListener { handleKey(key) }
                }
            }

            row.addView(
                button,
                LinearLayout.LayoutParams(0, dp(keyHeight), keyWeight(key)).apply {
                    setMargins(dp(2), dp(2), dp(2), dp(2))
                }
            )
        }

        keyboardRoot.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun configureBackspace(button: Button) {
        button.setOnClickListener {
            if (!deleteRepeated) deleteOneCharacter()
        }
        button.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    deleteRepeated = false
                    view.isPressed = true
                    deleteRepeatHandler.removeCallbacks(deleteRepeatAction)
                    deleteRepeatHandler.postDelayed(
                        deleteRepeatAction,
                        DELETE_REPEAT_START_DELAY_MS
                    )
                    true
                }
                MotionEvent.ACTION_UP -> {
                    view.isPressed = false
                    stopBackspaceRepeat()
                    view.performClick()
                    view.post { deleteRepeated = false }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false
                    stopBackspaceRepeat()
                    deleteRepeated = false
                    true
                }
                else -> true
            }
        }
    }

    private fun stopBackspaceRepeat() {
        deleteRepeatHandler.removeCallbacks(deleteRepeatAction)
    }

    private fun deleteOneCharacter() {
        currentInputConnection?.deleteSurroundingTextInCodePoints(1, 0)
        if (::keyboardRoot.isInitialized) {
            keyboardRoot.post { updateSuggestions() }
        }
    }

    private fun displayText(key: String): String =
        if (uppercase && key.length == 1 && key.first().isLetter()) {
            key.uppercase(russianLocale)
        } else {
            key
        }

    private fun handleKey(key: String) {
        val inputConnection = currentInputConnection ?: return

        when (key) {
            "⇧" -> {
                uppercase = !uppercase
                renderKeyboard()
            }
            "?123" -> {
                mode = KeyboardMode.SYMBOLS
                renderKeyboard()
            }
            "АБВ" -> {
                mode = KeyboardMode.LETTERS
                renderKeyboard()
            }
            "🌐" -> switchToNextInputMethod(false)
            "⌫" -> deleteOneCharacter()
            "пробел" -> inputConnection.commitText(" ", 1)
            "↵" -> handleEnter()
            else -> {
                inputConnection.commitText(displayText(key), 1)
                if (uppercase && key.length == 1 && key.first().isLetter()) {
                    uppercase = false
                    renderKeyboard()
                }
            }
        }

        if (::keyboardRoot.isInitialized) {
            keyboardRoot.post { updateSuggestions() }
        }
    }

    private fun handleEnter() {
        val action = editorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
            ?: EditorInfo.IME_ACTION_NONE

        if (action != EditorInfo.IME_ACTION_NONE &&
            action != EditorInfo.IME_ACTION_UNSPECIFIED
        ) {
            currentInputConnection?.performEditorAction(action)
        } else {
            currentInputConnection?.commitText("\n", 1)
        }
    }

    private fun keyWeight(key: String): Float = when (key) {
        "пробел" -> 4f
        "⇧", "⌫", "?123", "АБВ" -> 1.45f
        else -> 1f
    }

    private fun keyBackground(
        key: String,
        isNumberPanel: Boolean
    ): GradientDrawable {
        val special = key in setOf("⇧", "⌫", "?123", "АБВ", "🌐", "↵")
        val fillColor: Int = when {
            isNumberPanel -> Color.rgb(248, 246, 252)
            special -> Color.rgb(216, 209, 232)
            else -> Color.WHITE
        }
        val strokeColor: Int =
            if (isNumberPanel) Color.rgb(185, 175, 203)
            else Color.rgb(200, 193, 210)

        return GradientDrawable().apply {
            cornerRadius = dp(9).toFloat()
            setColor(fillColor)
            setStroke(dp(1), strokeColor)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val SUGGESTION_COUNT = 3
        const val MAX_CONTEXT_LENGTH = 64
        const val DELETE_REPEAT_START_DELAY_MS = 350L
        const val DELETE_REPEAT_INTERVAL_MS = 55L

        val blockedSuggestionVariations = setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI
        )

        val numberRow = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")

        val letterRows = listOf(
            listOf("й", "ц", "у", "к", "е", "н", "г", "ш", "щ", "з", "х", "ъ"),
            listOf("ф", "ы", "в", "а", "п", "р", "о", "л", "д", "ж", "э"),
            listOf("⇧", "я", "ч", "с", "м", "и", "т", "ь", "б", "ю", "⌫"),
            listOf("?123", "🌐", ",", "пробел", ".", "↵")
        )

        val symbolRows = listOf(
            listOf("@", "#", "₽", "_", "&", "-", "+", "(", ")", "/"),
            listOf("*", "\"", "'", ":", ";", "!", "?", "⌫"),
            listOf("АБВ", "🌐", ",", "пробел", ".", "↵")
        )
    }
}
