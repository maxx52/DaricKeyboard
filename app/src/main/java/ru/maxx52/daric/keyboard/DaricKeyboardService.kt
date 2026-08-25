package ru.maxx52.daric.keyboard

import android.content.ClipDescription
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
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

    private var gifClient: KlipyGifClient? = null
    private var gifRequestGeneration = 0

    override fun onCreate() {
        super.onCreate()
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        window?.window?.decorView?.let(::installViewTreeOwners)
    }

    private fun installViewTreeOwners(view: View) {
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
    }

    override fun onCreateInputView(): View {
        return ComposeView(this).apply {
            installViewTreeOwners(this)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                DaricTheme(darkTheme = false) {
                    KeyboardScreen(
                        state = uiState,
                        onKey = ::handleKey,
                        onSuggestion = ::applySuggestion,
                        onOpenGif = ::openGifPanel,
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
        stopBackspaceRepeat()
        gifClient?.shutdown()
        gifClient = null
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
        super.onDestroy()
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
            "пробел", "space" -> inputConnection.commitText(" ", 1)
            "↵" -> handleEnter()
            else -> {
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

        val prefix = currentWord()
        if (prefix.isBlank()) {
            uiState = uiState.copy(
                suggestionsVisible = true,
                suggestions = emptySuggestions
            )
            return
        }

        val completions = when (uiState.language) {
            KeyboardLanguage.RUSSIAN -> RussianSuggestionEngine
                .suggest(prefix.lowercase(russianLocale), limit = 2)
            KeyboardLanguage.ENGLISH -> emptyList()
        }.map { applyPrefixCase(prefix, it) }

        uiState = uiState.copy(
            suggestionsVisible = true,
            suggestions = listOf(
                completions.getOrNull(0).orEmpty(),
                prefix,
                completions.getOrNull(1).orEmpty()
            )
        )
    }

    private fun currentWord(): String {
        return currentInputConnection
            ?.getTextBeforeCursor(MAX_CONTEXT_LENGTH, 0)
            ?.toString()
            .orEmpty()
            .takeLastWhile { it.isLetter() || it == '-' }
    }

    private fun applySuggestion(suggestion: String) {
        if (suggestion.isBlank()) return
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
        updateSuggestions()
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
        const val MAX_CONTEXT_LENGTH = 64
        const val DELETE_REPEAT_START_DELAY_MS = 350L
        const val DELETE_REPEAT_INTERVAL_MS = 55L
        const val GIF_MIME_TYPE = "image/gif"
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
