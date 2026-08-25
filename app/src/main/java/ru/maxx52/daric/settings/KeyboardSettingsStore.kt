package ru.maxx52.daric.settings

import android.content.Context
import android.content.res.Configuration

enum class KeyboardThemeMode { SYSTEM, LIGHT, DARK }

data class KeyboardSettings(
    val personalizedLearning: Boolean = true,
    val showNumberRow: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val soundEnabled: Boolean = false,
    val themeMode: KeyboardThemeMode = KeyboardThemeMode.SYSTEM,
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
        keyHeightDp = preferences.getInt(KEY_HEIGHT, 52).coerceIn(44, 64)
    )

    fun save(settings: KeyboardSettings) {
        preferences.edit()
            .putBoolean(KEY_LEARNING, settings.personalizedLearning)
            .putBoolean(KEY_NUMBER_ROW, settings.showNumberRow)
            .putBoolean(KEY_VIBRATION, settings.vibrationEnabled)
            .putBoolean(KEY_SOUND, settings.soundEnabled)
            .putString(KEY_THEME, settings.themeMode.name)
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
        const val KEY_HEIGHT = "key_height"
    }
}
