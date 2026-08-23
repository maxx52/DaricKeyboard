package ru.maxx52.daric.keyboard

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

class DaricKeyboardService : InputMethodService() {

    private enum class KeyboardMode { LETTERS, SYMBOLS }

    private lateinit var keyboardRoot: LinearLayout
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

    private fun renderKeyboard() {
        keyboardRoot.removeAllViews()
        val rows = when (mode) {
            KeyboardMode.LETTERS -> letterRows
            KeyboardMode.SYMBOLS -> symbolRows
        }
        rows.forEach(::addRow)
    }

    private fun addRow(keys: List<String>) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        keys.forEach { key ->
            val button = Button(this).apply {
                text = displayText(key)
                gravity = Gravity.CENTER
                textSize = if (key == "пробел") 13f else 18f
                isAllCaps = false
                minWidth = 0
                minimumWidth = 0
                minHeight = 0
                minimumHeight = 0
                setPadding(0, 0, 0, 0)
                setTextColor(Color.parseColor("#241F2E"))
                background = keyBackground(key)
                setOnClickListener { handleKey(key) }
            }

            row.addView(
                button,
                LinearLayout.LayoutParams(0, dp(52), keyWeight(key)).apply {
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

    private fun displayText(key: String): String =
        if (uppercase && key.length == 1 && key.first().isLetter()) {
            key.uppercase(Locale("ru", "RU"))
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
            "⌫" -> inputConnection.deleteSurroundingTextInCodePoints(1, 0)
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

    private fun keyBackground(key: String): GradientDrawable {
        val special = key in setOf("⇧", "⌫", "?123", "АБВ", "🌐", "↵")
        return GradientDrawable().apply {
            cornerRadius = dp(9).toFloat()
            setColor(Color.parseColor(if (special) "#D8D1E8" else "#FFFFFF"))
            setStroke(dp(1), Color.parseColor("#C8C1D2"))
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private companion object {
        val letterRows = listOf(
            listOf("й", "ц", "у", "к", "е", "н", "г", "ш", "щ", "з", "х", "ъ"),
            listOf("ф", "ы", "в", "а", "п", "р", "о", "л", "д", "ж", "э"),
            listOf("⇧", "я", "ч", "с", "м", "и", "т", "ь", "б", "ю", "⌫"),
            listOf("?123", "🌐", ",", "пробел", ".", "↵")
        )

        val symbolRows = listOf(
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            listOf("@", "#", "₽", "_", "&", "-", "+", "(", ")", "/"),
            listOf("*", """, "'", ":", ";", "!", "?", "⌫"),
            listOf("АБВ", "🌐", ",", "пробел", ".", "↵")
        )
    }
}
