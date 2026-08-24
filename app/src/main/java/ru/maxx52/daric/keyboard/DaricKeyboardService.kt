package ru.maxx52.daric.keyboard

import android.content.ClipDescription
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import ru.maxx52.daric.BuildConfig
import java.io.File
import java.util.Locale

class DaricKeyboardService : InputMethodService() {

    private enum class KeyboardMode { LETTERS, SYMBOLS }
    private enum class KeyboardLanguage { RUSSIAN, ENGLISH }
    private enum class KeyboardPanel { KEYS, GIFS, GIF_SEARCH }

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
    private val englishLocale = Locale.ENGLISH
    private var mode = KeyboardMode.LETTERS
    private var language = KeyboardLanguage.RUSSIAN
    private var panel = KeyboardPanel.KEYS
    private var uppercase = false
    private var editorInfo: EditorInfo? = null

    private var gifClient: KlipyGifClient? = null
    private var gifItems: List<KlipyGif> = emptyList()
    private var gifQuery = ""
    private var gifSearchLabel: TextView? = null
    private var gifLoading = false
    private var gifError: String? = null
    private var gifRequestGeneration = 0

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
        panel = KeyboardPanel.KEYS
        uppercase = false
        if (::keyboardRoot.isInitialized) renderKeyboard()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onFinishInputView(finishingInput: Boolean) {
        stopBackspaceRepeat()
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        stopBackspaceRepeat()
        gifClient?.shutdown()
        gifClient = null
        super.onDestroy()
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
        if (::keyboardRoot.isInitialized && panel == KeyboardPanel.KEYS) {
            keyboardRoot.post { updateSuggestions() }
        }
    }

    private fun renderKeyboard() {
        keyboardRoot.removeAllViews()
        suggestionButtons.clear()
        gifSearchLabel = null

        when (panel) {
            KeyboardPanel.KEYS -> renderKeysPanel()
            KeyboardPanel.GIFS -> renderGifPanel()
            KeyboardPanel.GIF_SEARCH -> renderGifSearchPanel()
        }
    }

    private fun renderKeysPanel() {
        addMediaToolbar()
        if (suggestionsAllowed()) addSuggestionBar()
        addRow(numberRow, isNumberPanel = true)
        currentRows().forEach(::addRow)
        keyboardRoot.post { updateSuggestions() }
    }

    private fun currentRows(): List<List<String>> = when (mode) {
        KeyboardMode.LETTERS -> when (language) {
            KeyboardLanguage.RUSSIAN -> russianLetterRows
            KeyboardLanguage.ENGLISH -> englishLetterRows
        }
        KeyboardMode.SYMBOLS -> when (language) {
            KeyboardLanguage.RUSSIAN -> russianSymbolRows
            KeyboardLanguage.ENGLISH -> englishSymbolRows
        }
    }

    private fun addMediaToolbar() {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }
        val gifButton = smallButton("GIF").apply {
            contentDescription = "Открыть GIF"
            setOnClickListener { openGifPanel() }
        }
        row.addView(gifButton, LinearLayout.LayoutParams(dp(68), dp(36)))
        keyboardRoot.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(2) }
        )
    }

    private fun openGifPanel() {
        panel = KeyboardPanel.GIFS
        renderKeyboard()
        if (gifItems.isEmpty()) loadGifs()
    }

    private fun renderGifPanel() {
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        toolbar.addView(
            smallButton("⌨").apply {
                contentDescription = "Вернуться к клавиатуре"
                setOnClickListener {
                    panel = KeyboardPanel.KEYS
                    renderKeyboard()
                }
            },
            LinearLayout.LayoutParams(dp(48), dp(44))
        )

        val search = TextView(this).apply {
            text = gifQuery.ifBlank { "Поиск GIF" }
            textSize = 16f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(Color.parseColor(if (gifQuery.isBlank()) "#776B84" else "#241F2E"))
            setPadding(dp(14), 0, dp(10), 0)
            background = roundedBackground(Color.WHITE, Color.rgb(200, 193, 210), 10)
            setOnClickListener {
                panel = KeyboardPanel.GIF_SEARCH
                mode = KeyboardMode.LETTERS
                uppercase = false
                renderKeyboard()
            }
        }
        toolbar.addView(
            search,
            LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                marginStart = dp(5)
                marginEnd = dp(5)
            }
        )
        toolbar.addView(
            smallButton("⌕").apply {
                contentDescription = "Искать GIF"
                setOnClickListener {
                    if (gifQuery.isBlank()) {
                        panel = KeyboardPanel.GIF_SEARCH
                        renderKeyboard()
                    } else {
                        loadGifs(gifQuery)
                    }
                }
            },
            LinearLayout.LayoutParams(dp(48), dp(44))
        )
        keyboardRoot.addView(
            toolbar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        keyboardRoot.addView(
            TextView(this).apply {
                text = "Powered by KLIPY"
                textSize = 11f
                gravity = Gravity.END
                setTextColor(Color.parseColor("#776B84"))
                setPadding(0, dp(2), dp(5), dp(3))
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        when {
            gifLoading -> addGifLoading()
            gifError != null -> addGifError(gifError.orEmpty())
            gifItems.isEmpty() -> addGifError("GIF не найдены")
            else -> addGifGrid()
        }
    }

    private fun renderGifSearchPanel() {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            smallButton("←").apply {
                contentDescription = "Назад к GIF"
                setOnClickListener {
                    panel = KeyboardPanel.GIFS
                    renderKeyboard()
                }
            },
            LinearLayout.LayoutParams(dp(48), dp(44))
        )
        gifSearchLabel = TextView(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            textSize = 17f
            setTextColor(Color.parseColor("#241F2E"))
            setPadding(dp(14), 0, dp(10), 0)
            background = roundedBackground(Color.WHITE, Color.rgb(200, 193, 210), 10)
        }
        updateGifSearchLabel()
        header.addView(
            gifSearchLabel,
            LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                marginStart = dp(5)
                marginEnd = dp(5)
            }
        )
        header.addView(
            smallButton("×").apply {
                contentDescription = "Очистить поиск"
                setOnClickListener {
                    gifQuery = ""
                    updateGifSearchLabel()
                }
            },
            LinearLayout.LayoutParams(dp(48), dp(44))
        )
        keyboardRoot.addView(
            header,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(3) }
        )

        addRow(numberRow, isNumberPanel = true)
        currentRows().forEach(::addRow)
    }

    private fun addGifLoading() {
        val container = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            addView(ProgressBar(this@DaricKeyboardService))
        }
        keyboardRoot.addView(
            container,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(GIF_GRID_HEIGHT_DP)
            )
        )
    }

    private fun addGifError(message: String) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(TextView(this@DaricKeyboardService).apply {
                text = message
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#5A4E67"))
                setPadding(dp(12), dp(8), dp(12), dp(8))
            })
            addView(smallButton("Повторить").apply {
                setOnClickListener { loadGifs(gifQuery.takeIf(String::isNotBlank)) }
            })
        }
        keyboardRoot.addView(
            container,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(GIF_GRID_HEIGHT_DP)
            )
        )
    }

    private fun addGifGrid() {
        val grid = GridLayout(this).apply {
            columnCount = GIF_COLUMN_COUNT
            setPadding(dp(3), 0, dp(3), dp(4))
        }
        val itemWidth = (resources.displayMetrics.widthPixels - dp(18)) / GIF_COLUMN_COUNT

        gifItems.forEach { gif ->
            val preview = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = gif.title
                background = roundedBackground(
                    Color.rgb(225, 219, 235),
                    Color.rgb(200, 193, 210),
                    10
                )
                clipToOutline = true
                setOnClickListener { sendGif(gif) }
            }
            grid.addView(
                preview,
                GridLayout.LayoutParams().apply {
                    width = itemWidth
                    height = dp(112)
                    setMargins(dp(2), dp(2), dp(2), dp(2))
                }
            )
            GifImageLoader.load(preview, gif.previewUrl)
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(
                grid,
                ScrollView.LayoutParams(
                    ScrollView.LayoutParams.MATCH_PARENT,
                    ScrollView.LayoutParams.WRAP_CONTENT
                )
            )
        }
        keyboardRoot.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(GIF_GRID_HEIGHT_DP)
            )
        )
    }

    private fun loadGifs(query: String? = null) {
        val requestGeneration = ++gifRequestGeneration
        gifLoading = true
        gifError = null
        if (::keyboardRoot.isInitialized && panel == KeyboardPanel.GIFS) renderKeyboard()

        val callback: (Result<List<KlipyGif>>) -> Unit = callback@{ result ->
            if (requestGeneration != gifRequestGeneration) return@callback
            gifLoading = false
            result.onSuccess { gifItems = it }
                .onFailure {
                    gifItems = emptyList()
                    gifError = when {
                        BuildConfig.KLIPY_API_KEY.isBlank() ->
                            "Добавьте KLIPY_API_KEY в local.properties и пересоберите приложение"
                        else -> "Не удалось загрузить GIF. Проверьте интернет и API-ключ"
                    }
                }
            if (::keyboardRoot.isInitialized && panel == KeyboardPanel.GIFS) renderKeyboard()
        }

        if (query.isNullOrBlank()) gifClient().trending(callback)
        else gifClient().search(query.trim(), callback)
    }

    private fun sendGif(gif: KlipyGif) {
        Toast.makeText(this, "Загружаю GIF…", Toast.LENGTH_SHORT).show()
        gifClient().download(gif, File(cacheDir, "shared_gifs")) { result ->
            result.onSuccess { file -> commitGif(gif, file) }
                .onFailure {
                    Toast.makeText(this, "Не удалось загрузить GIF", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun commitGif(gif: KlipyGif, file: File) {
        val inputConnection = currentInputConnection ?: return
        val uri = FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            file
        )
        val contentInfo = InputContentInfo(
            uri,
            ClipDescription(gif.title, arrayOf(GIF_MIME_TYPE)),
            null
        )

        val committed = runCatching {
            inputConnection.commitContent(
                contentInfo,
                InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,
                null
            )
        }.getOrDefault(false)

        if (committed) {
            gifClient().reportShare(gif.slug)
            Toast.makeText(this, "GIF отправлен", Toast.LENGTH_SHORT).show()
        } else {
            inputConnection.commitText(gif.contentUrl, 1)
            Toast.makeText(
                this,
                "Это приложение не принимает GIF — вставлена ссылка",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun gifClient(): KlipyGifClient {
        return gifClient ?: KlipyGifClient(
            apiKey = BuildConfig.KLIPY_API_KEY,
            customerId = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ANDROID_ID
            ).orEmpty().ifBlank { packageName }
        ).also { gifClient = it }
    }

    private fun updateGifSearchLabel() {
        gifSearchLabel?.apply {
            text = gifQuery.ifBlank { "Введите запрос" }
            setTextColor(Color.parseColor(if (gifQuery.isBlank()) "#776B84" else "#241F2E"))
        }
    }

    private fun handleGifSearchKey(key: String) {
        when (key) {
            "⇧" -> {
                uppercase = !uppercase
                renderKeyboard()
            }
            "?123" -> {
                mode = KeyboardMode.SYMBOLS
                renderKeyboard()
            }
            "АБВ", "ABC" -> {
                mode = KeyboardMode.LETTERS
                renderKeyboard()
            }
            "🌐" -> {
                language = when (language) {
                    KeyboardLanguage.RUSSIAN -> KeyboardLanguage.ENGLISH
                    KeyboardLanguage.ENGLISH -> KeyboardLanguage.RUSSIAN
                }
                mode = KeyboardMode.LETTERS
                uppercase = false
                renderKeyboard()
            }
            "⌫" -> deleteGifQueryCharacter()
            "пробел", "space" -> {
                gifQuery += " "
                updateGifSearchLabel()
            }
            "↵" -> {
                if (gifQuery.isNotBlank()) {
                    panel = KeyboardPanel.GIFS
                    renderKeyboard()
                    loadGifs(gifQuery)
                }
            }
            else -> {
                gifQuery += displayText(key)
                if (uppercase && key.length == 1 && key.first().isLetter()) {
                    uppercase = false
                    renderKeyboard()
                } else {
                    updateGifSearchLabel()
                }
            }
        }
    }

    private fun deleteGifQueryCharacter() {
        if (gifQuery.isNotEmpty()) {
            val lastCodePointStart = gifQuery.offsetByCodePoints(gifQuery.length, -1)
            gifQuery = gifQuery.substring(0, lastCodePointStart)
            updateGifSearchLabel()
        }
    }

    private fun smallButton(label: String): Button = Button(this).apply {
        text = label
        gravity = Gravity.CENTER
        textSize = 14f
        isAllCaps = false
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        setPadding(dp(5), 0, dp(5), 0)
        setTextColor(Color.parseColor("#241F2E"))
        background = roundedBackground(
            Color.rgb(216, 209, 232),
            Color.rgb(185, 175, 203),
            9
        )
    }

    private fun roundedBackground(fillColor: Int, strokeColor: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(radius).toFloat()
            setColor(fillColor)
            setStroke(dp(1), strokeColor)
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
                background = GradientDrawable().apply { setColor(Color.TRANSPARENT) }
            }
            suggestionButtons += button
            row.addView(button, LinearLayout.LayoutParams(0, dp(42), 1f))
        }

        keyboardRoot.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(5) }
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

        val completions = when (language) {
            KeyboardLanguage.RUSSIAN -> RussianSuggestionEngine
                .suggest(prefix.lowercase(russianLocale), limit = 2)
            KeyboardLanguage.ENGLISH -> emptyList()
        }.map { applyPrefixCase(prefix, it) }

        val candidates = listOf(completions.getOrNull(0), prefix, completions.getOrNull(1))
        suggestionButtons.forEachIndexed { index, button ->
            val candidate = candidates[index]
            button.text = candidate.orEmpty()
            button.isEnabled = candidate != null
            button.setOnClickListener(
                candidate?.let { word -> View.OnClickListener { applySuggestion(word) } }
            )
        }
    }

    private fun currentWord(): String {
        val beforeCursor = currentInputConnection
            ?.getTextBeforeCursor(MAX_CONTEXT_LENGTH, 0)
            ?.toString()
            .orEmpty()
        return beforeCursor.takeLastWhile { it.isLetter() || it == '-' }
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

    private fun applyPrefixCase(prefix: String, suggestion: String): String {
        val locale = currentLocale()
        return when {
            prefix.all(Char::isUpperCase) -> suggestion.uppercase(locale)
            prefix.firstOrNull()?.isUpperCase() == true ->
                suggestion.take(1).uppercase(locale) + suggestion.drop(1)
            else -> suggestion
        }
    }

    private fun currentLocale(): Locale = when (language) {
        KeyboardLanguage.RUSSIAN -> russianLocale
        KeyboardLanguage.ENGLISH -> englishLocale
    }

    private fun suggestionsAllowed(): Boolean {
        if (panel != KeyboardPanel.KEYS) return false
        val inputType = editorInfo?.inputType ?: return false
        if ((inputType and InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) return false
        if ((inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0) return false
        return (inputType and InputType.TYPE_MASK_VARIATION) !in blockedSuggestionVariations
    }

    private fun addRow(keys: List<String>, isNumberPanel: Boolean = false) {
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
                    key == "пробел" || key == "space" -> 13f
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
                if (key == "⌫") configureBackspace(this)
                else setOnClickListener { handleKey(key) }
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
        button.setOnClickListener { if (!deleteRepeated) deleteOneCharacter() }
        button.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    deleteRepeated = false
                    view.isPressed = true
                    deleteRepeatHandler.removeCallbacks(deleteRepeatAction)
                    deleteRepeatHandler.postDelayed(deleteRepeatAction, DELETE_REPEAT_START_DELAY_MS)
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
        if (panel == KeyboardPanel.GIF_SEARCH) {
            deleteGifQueryCharacter()
            return
        }
        currentInputConnection?.deleteSurroundingTextInCodePoints(1, 0)
        if (::keyboardRoot.isInitialized) keyboardRoot.post { updateSuggestions() }
    }

    private fun displayText(key: String): String =
        if (uppercase && key.length == 1 && key.first().isLetter()) {
            key.uppercase(currentLocale())
        } else key

    private fun handleKey(key: String) {
        if (panel == KeyboardPanel.GIF_SEARCH) {
            handleGifSearchKey(key)
            return
        }
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
            "АБВ", "ABC" -> {
                mode = KeyboardMode.LETTERS
                renderKeyboard()
            }
            "🌐" -> {
                language = when (language) {
                    KeyboardLanguage.RUSSIAN -> KeyboardLanguage.ENGLISH
                    KeyboardLanguage.ENGLISH -> KeyboardLanguage.RUSSIAN
                }
                uppercase = false
                renderKeyboard()
            }
            "⌫" -> deleteOneCharacter()
            "пробел", "space" -> inputConnection.commitText(" ", 1)
            "↵" -> handleEnter()
            else -> {
                inputConnection.commitText(displayText(key), 1)
                if (uppercase && key.length == 1 && key.first().isLetter()) {
                    uppercase = false
                    renderKeyboard()
                }
            }
        }
        if (::keyboardRoot.isInitialized) keyboardRoot.post { updateSuggestions() }
    }

    private fun handleEnter() {
        val action = editorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
            ?: EditorInfo.IME_ACTION_NONE
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            currentInputConnection?.performEditorAction(action)
        } else {
            currentInputConnection?.commitText("\n", 1)
        }
    }

    private fun keyWeight(key: String): Float = when (key) {
        "пробел", "space" -> 4f
        "⇧", "⌫", "?123", "АБВ", "ABC" -> 1.45f
        else -> 1f
    }

    private fun keyBackground(key: String, isNumberPanel: Boolean): GradientDrawable {
        val special = key in setOf("⇧", "⌫", "?123", "АБВ", "ABC", "🌐", "↵")
        val fillColor = when {
            isNumberPanel -> Color.rgb(248, 246, 252)
            special -> Color.rgb(216, 209, 232)
            else -> Color.WHITE
        }
        val strokeColor = if (isNumberPanel) Color.rgb(185, 175, 203)
        else Color.rgb(200, 193, 210)
        return roundedBackground(fillColor, strokeColor, 9)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val SUGGESTION_COUNT = 3
        const val MAX_CONTEXT_LENGTH = 64
        const val DELETE_REPEAT_START_DELAY_MS = 350L
        const val DELETE_REPEAT_INTERVAL_MS = 55L
        const val GIF_GRID_HEIGHT_DP = 286
        const val GIF_COLUMN_COUNT = 2
        const val GIF_MIME_TYPE = "image/gif"

        val blockedSuggestionVariations = setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI
        )

        val numberRow = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")

        val russianLetterRows = listOf(
            listOf("й", "ц", "у", "к", "е", "н", "г", "ш", "щ", "з", "х", "ъ"),
            listOf("ф", "ы", "в", "а", "п", "р", "о", "л", "д", "ж", "э"),
            listOf("⇧", "я", "ч", "с", "м", "и", "т", "ь", "б", "ю", "⌫"),
            listOf("?123", "🌐", ",", "пробел", ".", "↵")
        )

        val englishLetterRows = listOf(
            listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
            listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
            listOf("⇧", "z", "x", "c", "v", "b", "n", "m", "⌫"),
            listOf("?123", "🌐", ",", "space", ".", "↵")
        )

        val russianSymbolRows = listOf(
            listOf("@", "#", "₽", "_", "&", "-", "+", "(", ")", "/"),
            listOf("*", "\"", "'", ":", ";", "!", "?", "⌫"),
            listOf("АБВ", "🌐", ",", "пробел", ".", "↵")
        )

        val englishSymbolRows = listOf(
            listOf("@", "#", "\$", "_", "&", "-", "+", "(", ")", "/"),
            listOf("*", "\"", "'", ":", ";", "!", "?", "⌫"),
            listOf("ABC", "🌐", ",", "space", ".", "↵")
        )
    }
}
