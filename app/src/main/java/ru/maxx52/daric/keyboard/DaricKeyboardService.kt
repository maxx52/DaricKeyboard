package ru.maxx52.daric.keyboard

import android.content.ClipDescription
import android.content.Intent
import android.media.AudioManager
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import ru.maxx52.daric.BuildConfig
import ru.maxx52.daric.settings.KeyboardSettings
import ru.maxx52.daric.settings.KeyboardSettingsStore
import ru.maxx52.daric.ui.theme.DaricTheme
import java.io.File
import java.util.Locale

class DaricKeyboardService : InputMethodService(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore = ViewModelStore()
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

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
    private var editorInfo: EditorInfo? = null
    private var uiState by mutableStateOf(KeyboardUiState())
    private var neuralSuggestionModel: LiteRtNextWordModel? = null
    private lateinit var personalSuggestionStore: PersonalSuggestionStore
    private lateinit var settingsStore: KeyboardSettingsStore
    private var keyboardSettings = KeyboardSettings()
    private var inputComposeView: View? = null
    @Volatile private var serviceDestroyed = false

    private var gifClient: KlipyGifClient? = null
    private var gifRequestGeneration = 0

    override fun onCreate() {
        super.onCreate()
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        window?.window?.decorView?.let(::installViewTreeOwners)
        serviceDestroyed = false
        personalSuggestionStore = PersonalSuggestionStore(applicationContext)
        settingsStore = KeyboardSettingsStore(applicationContext)
        refreshKeyboardSettings()
        loadNeuralSuggestionModel()
    }

    private fun installViewTreeOwners(view: View) {
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
    }

    private fun loadNeuralSuggestionModel() {
        Thread({
            val result = runCatching {
                LiteRtNextWordModel.create(applicationContext)
            }
            deleteRepeatHandler.post {
                if (serviceDestroyed) {
                    result.getOrNull()?.close()
                    return@post
                }
                result.onSuccess { model ->
                    neuralSuggestionModel?.close()
                    neuralSuggestionModel = model
                    if (uiState.panel == KeyboardPanel.KEYS) updateSuggestions()
                }.onFailure { error ->
                    Log.w(LOG_TAG, "LiteRT suggestion model is unavailable", error)
                }
            }
        }, "daric-neural-model-loader").start()
    }

    override fun onCreateInputView(): View {
        return ComposeView(this).apply {
            inputComposeView = this
            installViewTreeOwners(this)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                DaricTheme(darkTheme = uiState.darkTheme) {
                    KeyboardScreen(
                        state = uiState,
                        onKey = ::handleKey,
                        onSuggestion = ::applySuggestion,
                        onOpenGif = ::openGifPanel,
                        onOpenPostcards = ::openPostcards,
                        onCloseGif = ::closeGifPanel,
                        onOpenGifSearch = ::openGifSearch,
                        onCloseGifSearch = ::closeGifSearch,
                        onClearGifSearch = {
                            uiState = uiState.copy(gifQuery = "")
                        },
                        onRunGifSearch = {
                            val query = uiState.gifQuery.trim()
                            if (query.isNotBlank()) loadGifs(query)
                        },
                        onRetryGif = {
                            loadGifs(uiState.gifQuery.takeIf(String::isNotBlank))
                        },
                        onGifSelected = ::sendGif,
                        onClosePostcards = ::closePostcards,
                        onPostcardSelected = ::sendPostcard,
                        onBackspacePressStart = ::startBackspaceRepeat,
                        onBackspacePressEnd = ::finishBackspaceRepeat
                    )
                }
            }
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        editorInfo = info
        refreshKeyboardSettings()
        uiState = uiState.copy(
            mode = when (info?.inputType?.and(InputType.TYPE_MASK_CLASS)) {
                InputType.TYPE_CLASS_NUMBER,
                InputType.TYPE_CLASS_PHONE,
                InputType.TYPE_CLASS_DATETIME -> KeyboardMode.SYMBOLS
                else -> KeyboardMode.LETTERS
            },
            panel = KeyboardPanel.KEYS,
            uppercase = false,
            suggestionsVisible = suggestionsAllowed(),
            suggestions = emptySuggestions
        )
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        updateSuggestions()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onFinishInputView(finishingInput: Boolean) {
        stopBackspaceRepeat()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
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
        if (uiState.panel == KeyboardPanel.KEYS) updateSuggestions()
    }

    override fun onDestroy() {
        serviceDestroyed = true
        stopBackspaceRepeat()
        neuralSuggestionModel?.close()
        neuralSuggestionModel = null
        inputComposeView = null
        gifClient?.shutdown()
        gifClient = null
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
        super.onDestroy()
    }

    private fun openPostcards() {
        uiState = uiState.copy(panel = KeyboardPanel.POSTCARDS)
    }

    private fun closePostcards() {
        uiState = uiState.copy(panel = KeyboardPanel.KEYS)
        updateSuggestions()
    }

    private fun openGifPanel() {
        uiState = uiState.copy(panel = KeyboardPanel.GIFS)
        if (uiState.gifItems.isEmpty()) loadGifs()
    }

    private fun closeGifPanel() {
        uiState = uiState.copy(panel = KeyboardPanel.KEYS)
        updateSuggestions()
    }

    private fun openGifSearch() {
        uiState = uiState.copy(
            panel = KeyboardPanel.GIF_SEARCH,
            mode = KeyboardMode.LETTERS,
            uppercase = false
        )
    }

    private fun closeGifSearch() {
        uiState = uiState.copy(panel = KeyboardPanel.GIFS)
    }

    private fun loadGifs(query: String? = null) {
        val requestGeneration = ++gifRequestGeneration
        uiState = uiState.copy(gifLoading = true, gifError = null)

        val callback: (Result<List<KlipyGif>>) -> Unit = callback@{ result ->
            if (requestGeneration != gifRequestGeneration) return@callback
            result.onSuccess { items ->
                uiState = uiState.copy(
                    gifItems = items,
                    gifLoading = false,
                    gifError = null
                )
            }.onFailure {
                uiState = uiState.copy(
                    gifItems = emptyList(),
                    gifLoading = false,
                    gifError = if (BuildConfig.KLIPY_API_KEY.isBlank()) {
                        "Добавьте KLIPY_API_KEY в local.properties и пересоберите приложение"
                    } else {
                        "Не удалось загрузить GIF. Проверьте интернет и API-ключ"
                    }
                )
            }
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
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
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


    private fun sendPostcard(postcard: Postcard) {
        if (currentInputConnection == null) return
        Toast.makeText(this, "Готовлю открытку…", Toast.LENGTH_SHORT).show()

        Thread({
            val result = runCatching {
                PostcardRenderer.renderToFile(
                    postcard = postcard,
                    directory = File(cacheDir, "shared_postcards")
                )
            }
            deleteRepeatHandler.post {
                result.onSuccess { file -> commitPostcard(postcard, file) }
                    .onFailure {
                        Toast.makeText(
                            this,
                            "Не удалось создать открытку",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
        }, "postcard-renderer").start()
    }

    private fun commitPostcard(postcard: Postcard, file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val inputConnection = currentInputConnection
        val contentInfo = InputContentInfo(
            uri,
            ClipDescription(postcard.title, arrayOf(PNG_MIME_TYPE)),
            null
        )
        val committed = inputConnection != null && runCatching {
            inputConnection.commitContent(
                contentInfo,
                InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,
                null
            )
        }.getOrDefault(false)

        if (committed) {
            Toast.makeText(this, "Открытка отправлена", Toast.LENGTH_SHORT).show()
            return
        }

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = PNG_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(sendIntent, "Отправить открытку").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { startActivity(chooser) }
            .onFailure {
                Toast.makeText(
                    this,
                    "Это приложение не принимает изображения",
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

    private fun handleKey(key: String) {
        performKeyFeedback()
        if (uiState.panel == KeyboardPanel.GIF_SEARCH) {
            handleGifSearchKey(key)
            return
        }
        val inputConnection = currentInputConnection ?: return

        when (key) {
            "⇧" -> uiState = uiState.copy(uppercase = !uiState.uppercase)
            "?123" -> uiState = uiState.copy(mode = KeyboardMode.SYMBOLS)
            "АБВ", "ABC" -> uiState = uiState.copy(mode = KeyboardMode.LETTERS)
            "🌐" -> uiState = uiState.copy(
                language = if (uiState.language == KeyboardLanguage.RUSSIAN) {
                    KeyboardLanguage.ENGLISH
                } else KeyboardLanguage.RUSSIAN,
                uppercase = false
            )
            "⌫" -> deleteOneCharacter()
            "пробел", "space" -> {
                learnCurrentWord()
                inputConnection.commitText(" ", 1)
            }
            "↵" -> {
                learnCurrentWord()
                handleEnter()
            }
            else -> {
                if (key.length == 1 && key.first() in WORD_TERMINATORS) {
                    learnCurrentWord()
                }
                inputConnection.commitText(uiState.displayText(key), 1)
                if (uiState.uppercase && key.length == 1 && key.first().isLetter()) {
                    uiState = uiState.copy(uppercase = false)
                }
            }
        }
        updateSuggestions()
    }

    private fun handleGifSearchKey(key: String) {
        when (key) {
            "⇧" -> uiState = uiState.copy(uppercase = !uiState.uppercase)
            "?123" -> uiState = uiState.copy(mode = KeyboardMode.SYMBOLS)
            "АБВ", "ABC" -> uiState = uiState.copy(mode = KeyboardMode.LETTERS)
            "🌐" -> uiState = uiState.copy(
                language = if (uiState.language == KeyboardLanguage.RUSSIAN) {
                    KeyboardLanguage.ENGLISH
                } else KeyboardLanguage.RUSSIAN,
                mode = KeyboardMode.LETTERS,
                uppercase = false
            )
            "⌫" -> deleteGifQueryCharacter()
            "пробел", "space" -> uiState = uiState.copy(gifQuery = uiState.gifQuery + " ")
            "↵" -> {
                val query = uiState.gifQuery.trim()
                if (query.isNotBlank()) {
                    uiState = uiState.copy(panel = KeyboardPanel.GIFS)
                    loadGifs(query)
                }
            }
            else -> {
                uiState = uiState.copy(
                    gifQuery = uiState.gifQuery + uiState.displayText(key),
                    uppercase = if (key.length == 1 && key.first().isLetter()) {
                        false
                    } else uiState.uppercase
                )
            }
        }
    }

    private fun startBackspaceRepeat() {
        performKeyFeedback()
        deleteRepeated = false
        deleteRepeatHandler.removeCallbacks(deleteRepeatAction)
        deleteRepeatHandler.postDelayed(deleteRepeatAction, DELETE_REPEAT_START_DELAY_MS)
    }

    private fun finishBackspaceRepeat(released: Boolean) {
        stopBackspaceRepeat()
        if (released && !deleteRepeated) deleteOneCharacter()
        deleteRepeated = false
    }

    private fun stopBackspaceRepeat() {
        deleteRepeatHandler.removeCallbacks(deleteRepeatAction)
    }

    private fun deleteOneCharacter() {
        if (uiState.panel == KeyboardPanel.GIF_SEARCH) {
            deleteGifQueryCharacter()
        } else {
            currentInputConnection?.deleteSurroundingTextInCodePoints(1, 0)
            updateSuggestions()
        }
    }

    private fun deleteGifQueryCharacter() {
        val query = uiState.gifQuery
        if (query.isEmpty()) return
        val start = query.offsetByCodePoints(query.length, -1)
        uiState = uiState.copy(gifQuery = query.substring(0, start))
    }

    private fun updateSuggestions() {
        val allowed = suggestionsAllowed()
        if (!allowed || uiState.panel != KeyboardPanel.KEYS) {
            uiState = uiState.copy(
                suggestionsVisible = false,
                suggestions = emptySuggestions
            )
            return
        }

        val context = suggestionContext()
        val candidates = when (uiState.language) {
            KeyboardLanguage.RUSSIAN -> RussianSuggestionEngine.suggest(
                context = context,
                limit = SUGGESTION_SLOT_COUNT,
                neuralCandidates = neuralSuggestionModel
                    ?.predictNext(context.previousWords, NEURAL_CANDIDATE_LIMIT)
                    .orEmpty(),
                personalizedCandidates = if (personalizedLearningAllowed()) {
                    personalSuggestionStore.predictNext(
                        context.previousWords,
                        PERSONAL_CANDIDATE_LIMIT
                    )
                } else {
                    emptyList()
                }
            )
            KeyboardLanguage.ENGLISH -> emptyList()
        }.map { suggestion -> applySuggestionCase(context, suggestion) }

        val suggestions = if (context.currentWord.isNotBlank()) {
            listOf(
                candidates.getOrNull(0).orEmpty(),
                context.currentWord,
                candidates.getOrNull(1).orEmpty()
            )
        } else {
            List(SUGGESTION_SLOT_COUNT) { index -> candidates.getOrNull(index).orEmpty() }
        }

        uiState = uiState.copy(
            suggestionsVisible = true,
            suggestions = suggestions
        )
    }

    private fun suggestionContext(): SuggestionContext {
        val textBeforeCursor = currentInputConnection
            ?.getTextBeforeCursor(MAX_CONTEXT_LENGTH, 0)
            ?.toString()
            .orEmpty()
        return SuggestionContextParser.parse(textBeforeCursor)
    }

    private fun applySuggestion(suggestion: String) {
        if (suggestion.isBlank()) return
        val inputConnection = currentInputConnection ?: return
        val context = suggestionContext()
        if (personalizedLearningAllowed()) {
            personalSuggestionStore.learn(context.previousWords, suggestion)
        }

        inputConnection.beginBatchEdit()
        try {
            if (context.currentWord.isNotBlank()) {
                inputConnection.deleteSurroundingTextInCodePoints(
                    context.currentWord.codePointCount(0, context.currentWord.length),
                    0
                )
            }

            val leadingSpace = if (
                context.currentWord.isBlank() &&
                needsSpaceBeforeSuggestion(context.textBeforeCursor)
            ) {
                " "
            } else {
                ""
            }
            inputConnection.commitText("$leadingSpace$suggestion ", 1)
        } finally {
            inputConnection.endBatchEdit()
        }
        updateSuggestions()
    }

    private fun needsSpaceBeforeSuggestion(textBeforeCursor: String): Boolean {
        val lastCharacter = textBeforeCursor.lastOrNull() ?: return false
        return !lastCharacter.isWhitespace() && lastCharacter !in OPENING_PUNCTUATION
    }

    private fun applySuggestionCase(
        context: SuggestionContext,
        suggestion: String
    ): String {
        if (context.currentWord.isNotBlank()) {
            return applyPrefixCase(context.currentWord, suggestion)
        }
        return if (context.startsNewSentence) {
            suggestion.take(1).uppercase(currentLocale()) + suggestion.drop(1)
        } else {
            suggestion
        }
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

    private fun currentLocale(): Locale = when (uiState.language) {
        KeyboardLanguage.RUSSIAN -> russianLocale
        KeyboardLanguage.ENGLISH -> englishLocale
    }

    private fun suggestionsAllowed(): Boolean {
        val inputType = editorInfo?.inputType ?: return false
        if ((inputType and InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) return false
        if ((inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0) return false
        return (inputType and InputType.TYPE_MASK_VARIATION) !in blockedSuggestionVariations
    }

    private fun personalizedLearningAllowed(): Boolean {
        if (!keyboardSettings.personalizedLearning) return false
        if (uiState.language != KeyboardLanguage.RUSSIAN || !suggestionsAllowed()) return false
        val imeOptions = editorInfo?.imeOptions ?: return false
        return (imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) == 0
    }

    private fun learnCurrentWord() {
        if (!personalizedLearningAllowed()) return
        val context = suggestionContext()
        if (context.currentWord.isNotBlank()) {
            personalSuggestionStore.learn(context.previousWords, context.currentWord)
        }
    }

    private fun refreshKeyboardSettings() {
        keyboardSettings = settingsStore.load()
        uiState = uiState.copy(
            showNumberRow = keyboardSettings.showNumberRow,
            keyHeightDp = keyboardSettings.keyHeightDp,
            darkTheme = settingsStore.isDarkTheme(keyboardSettings)
        )
    }

    private fun performKeyFeedback() {
        if (keyboardSettings.vibrationEnabled) {
            inputComposeView?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
        if (keyboardSettings.soundEnabled) {
            getSystemService(AudioManager::class.java)
                ?.playSoundEffect(AudioManager.FX_KEY_CLICK, SOUND_EFFECT_VOLUME)
        }
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

    private companion object {
        const val MAX_CONTEXT_LENGTH = 256
        const val SUGGESTION_SLOT_COUNT = 3
        const val NEURAL_CANDIDATE_LIMIT = 6
        const val PERSONAL_CANDIDATE_LIMIT = 6
        val WORD_TERMINATORS = setOf('.', ',', '!', '?', ';', ':', '…')
        const val LOG_TAG = "DaricKeyboard"
        const val SOUND_EFFECT_VOLUME = 0.25f
        const val OPENING_PUNCTUATION = "([{'\"«"
        const val DELETE_REPEAT_START_DELAY_MS = 350L
        const val DELETE_REPEAT_INTERVAL_MS = 55L
        const val GIF_MIME_TYPE = "image/gif"
        const val PNG_MIME_TYPE = "image/png"
        val emptySuggestions = listOf("", "", "")

        val blockedSuggestionVariations = setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI
        )
    }
}
