package ru.maxx52.daric.settings

import android.content.Context
import android.content.res.Configuration

enum class KeyboardThemeMode { SYSTEM, LIGHT, DARK }
enum class KeyboardColorTheme { LAVENDER, OCEAN, ROSE, FOREST, SUNSET, GRAPHITE }
enum class KeyboardBackgroundMode { SOLID, GRADIENT }

data class KeyboardSettings(
    val personalizedLearning: Boolean = true,
    val showNumberRow: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val soundEnabled: Boolean = false,
    val themeMode: KeyboardThemeMode = KeyboardThemeMode.SYSTEM,
    val colorTheme: KeyboardColorTheme = KeyboardColorTheme.LAVENDER,
    val backgroundMode: KeyboardBackgroundMode = KeyboardBackgroundMode.GRADIENT,
    val keyHeightDp: Int = 52
)

class KeyboardSettingsStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun load(): KeyboardSettings = KeyboardSettings(
        personalizedLearning = preferences.getBoolean(KEY_LEARNING, true),
        showNumberRow = preferences.getBoolean(KEY_NUMBER_ROW, true),
        vibrationEnabled = preferences.getBoolean(KEY_VIBRATION, true),
        soundEnabled = preferences.getBoolean(KEY_SOUND, false),
        themeMode = runCatching {
            KeyboardThemeMode.valueOf(
                preferences.getString(KEY_THEME, KeyboardThemeMode.SYSTEM.name)
                    ?: KeyboardThemeMode.SYSTEM.name
            )
        }.getOrDefault(KeyboardThemeMode.SYSTEM),
        colorTheme = runCatching {
            KeyboardColorTheme.valueOf(
                preferences.getString(KEY_COLOR_THEME, KeyboardColorTheme.LAVENDER.name)
                    ?: KeyboardColorTheme.LAVENDER.name
            )
        }.getOrDefault(KeyboardColorTheme.LAVENDER),
        backgroundMode = runCatching {
            KeyboardBackgroundMode.valueOf(
                preferences.getString(
                    KEY_BACKGROUND_MODE,
                    KeyboardBackgroundMode.GRADIENT.name
                ) ?: KeyboardBackgroundMode.GRADIENT.name
            )
        }.getOrDefault(KeyboardBackgroundMode.GRADIENT),
        keyHeightDp = preferences.getInt(KEY_HEIGHT, 52).coerceIn(44, 64)
    )

    fun save(settings: KeyboardSettings) {
        preferences.edit()
            .putBoolean(KEY_LEARNING, settings.personalizedLearning)
            .putBoolean(KEY_NUMBER_ROW, settings.showNumberRow)
            .putBoolean(KEY_VIBRATION, settings.vibrationEnabled)
            .putBoolean(KEY_SOUND, settings.soundEnabled)
            .putString(KEY_THEME, settings.themeMode.name)
            .putString(KEY_COLOR_THEME, settings.colorTheme.name)
            .putString(KEY_BACKGROUND_MODE, settings.backgroundMode.name)
            .putInt(KEY_HEIGHT, settings.keyHeightDp.coerceIn(44, 64))
            .apply()
    }

    fun isDarkTheme(settings: KeyboardSettings): Boolean = when (settings.themeMode) {
        KeyboardThemeMode.LIGHT -> false
        KeyboardThemeMode.DARK -> true
        KeyboardThemeMode.SYSTEM ->
            (appContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }

    private companion object {
        const val PREFERENCES_NAME = "daric_keyboard_settings"
        const val KEY_LEARNING = "personalized_learning"
        const val KEY_NUMBER_ROW = "show_number_row"
        const val KEY_VIBRATION = "vibration"
        const val KEY_SOUND = "sound"
        const val KEY_THEME = "theme"
        const val KEY_COLOR_THEME = "keyboard_color_theme"
        const val KEY_BACKGROUND_MODE = "keyboard_background_mode"
        const val KEY_HEIGHT = "key_height"
    }
}
