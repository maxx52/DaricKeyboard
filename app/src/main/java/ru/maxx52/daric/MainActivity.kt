package ru.maxx52.daric

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.maxx52.daric.keyboard.PersonalSuggestionStore
import ru.maxx52.daric.settings.KeyboardBackgroundMode
import ru.maxx52.daric.settings.KeyboardColorTheme
import ru.maxx52.daric.settings.KeyboardSettings
import ru.maxx52.daric.settings.KeyboardSettingsStore
import ru.maxx52.daric.settings.KeyboardThemeMode
import ru.maxx52.daric.ui.theme.DaricTheme
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val store = remember { KeyboardSettingsStore(applicationContext) }
            var settings by remember { mutableStateOf(store.load()) }
            DaricTheme(darkTheme = store.isDarkTheme(settings)) {
                SetupScreen(
                    settings = settings,
                    onSettingsChange = { settings = it; store.save(it) },
                    onClearHistory = { PersonalSuggestionStore.clearAll(applicationContext) },
                    onEnableKeyboard = {
                        startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                    },
                    onChooseKeyboard = {
                        getSystemService(InputMethodManager::class.java).showInputMethodPicker()
                    },
                    onOpenPrivacy = {
                        startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL))
                        )
                    }
                )
            }
        }
    }

    private companion object {
        const val PRIVACY_POLICY_URL =
            "https://github.com/maxx52/DaricKeyboard/blob/master/PRIVACY.md"
    }
}

@Composable
private fun SetupScreen(
    settings: KeyboardSettings,
    onSettingsChange: (KeyboardSettings) -> Unit,
    onClearHistory: () -> Unit,
    onEnableKeyboard: () -> Unit,
    onChooseKeyboard: () -> Unit,
    onOpenPrivacy: () -> Unit
) {
    var testText by rememberSaveable { mutableStateOf("") }
    var historyCleared by rememberSaveable { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Дарик",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Клавиатура с открытками, GIF и умными подсказками",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onEnableKeyboard,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("1. Включить клавиатуру")
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onChooseKeyboard,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("2. Выбрать «Дарик»")
            }
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = testText,
                onValueChange = { testText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("3. Проверить клавиатуру") },
                minLines = 3
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "Настройки клавиатуры",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    SettingSwitch(
                        "Персональное обучение",
                        "Поднимать ваши частые слова выше остальных",
                        settings.personalizedLearning
                    ) { onSettingsChange(settings.copy(personalizedLearning = it)) }
                    SettingSwitch(
                        "Цифровой ряд",
                        "Показывать цифры над буквами",
                        settings.showNumberRow
                    ) { onSettingsChange(settings.copy(showNumberRow = it)) }
                    SettingSwitch("Вибрация клавиш", checked = settings.vibrationEnabled) {
                        onSettingsChange(settings.copy(vibrationEnabled = it))
                    }
                    SettingSwitch("Звук клавиш", checked = settings.soundEnabled) {
                        onSettingsChange(settings.copy(soundEnabled = it))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Тема", fontWeight = FontWeight.SemiBold)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeChip("Система", KeyboardThemeMode.SYSTEM, settings, onSettingsChange)
                        ThemeChip("Светлая", KeyboardThemeMode.LIGHT, settings, onSettingsChange)
                        ThemeChip("Тёмная", KeyboardThemeMode.DARK, settings, onSettingsChange)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Цветовая тема", fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ColorThemeChip(
                            "Лаванда",
                            KeyboardColorTheme.LAVENDER,
                            settings,
                            onSettingsChange
                        )
                        ColorThemeChip(
                            "Океан",
                            KeyboardColorTheme.OCEAN,
                            settings,
                            onSettingsChange
                        )
                        ColorThemeChip(
                            "Роза",
                            KeyboardColorTheme.ROSE,
                            settings,
                            onSettingsChange
                        )
                        ColorThemeChip(
                            "Лес",
                            KeyboardColorTheme.FOREST,
                            settings,
                            onSettingsChange
                        )
                        ColorThemeChip(
                            "Закат",
                            KeyboardColorTheme.SUNSET,
                            settings,
                            onSettingsChange
                        )
                        ColorThemeChip(
                            "Графит",
                            KeyboardColorTheme.GRAPHITE,
                            settings,
                            onSettingsChange
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Фон клавиатуры", fontWeight = FontWeight.SemiBold)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BackgroundModeChip(
                            "Однотонный",
                            KeyboardBackgroundMode.SOLID,
                            settings,
                            onSettingsChange
                        )
                        BackgroundModeChip(
                            "Градиент",
                            KeyboardBackgroundMode.GRADIENT,
                            settings,
                            onSettingsChange
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Высота клавиш: " + settings.keyHeightDp + " dp",
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = settings.keyHeightDp.toFloat(),
                        onValueChange = {
                            onSettingsChange(
                                settings.copy(keyHeightDp = (it / 4f).roundToInt() * 4)
                            )
                        },
                        valueRange = 44f..64f,
                        steps = 4
                    )
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            onClearHistory()
                            historyCleared = true
                        }
                    ) {
                        Text("Очистить историю подсказок")
                    }
                    if (historyCleared) {
                        Text(
                            "Персональная история очищена",
                            Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onOpenPrivacy) {
                Text("Политика конфиденциальности")
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Настройки применятся при следующем открытии клавиатуры. " +
                    "Введённый текст не передаётся в интернет; сеть используется только для GIF.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked, onCheckedChange)
    }
}

@Composable
private fun ThemeChip(
    label: String,
    mode: KeyboardThemeMode,
    settings: KeyboardSettings,
    onSettingsChange: (KeyboardSettings) -> Unit
) {
    FilterChip(
        selected = settings.themeMode == mode,
        onClick = { onSettingsChange(settings.copy(themeMode = mode)) },
        label = { Text(label) }
    )
}

@Composable
private fun ColorThemeChip(
    label: String,
    colorTheme: KeyboardColorTheme,
    settings: KeyboardSettings,
    onSettingsChange: (KeyboardSettings) -> Unit
) {
    FilterChip(
        selected = settings.colorTheme == colorTheme,
        onClick = { onSettingsChange(settings.copy(colorTheme = colorTheme)) },
        label = { Text(label) }
    )
}

@Composable
private fun BackgroundModeChip(
    label: String,
    backgroundMode: KeyboardBackgroundMode,
    settings: KeyboardSettings,
    onSettingsChange: (KeyboardSettings) -> Unit
) {
    FilterChip(
        selected = settings.backgroundMode == backgroundMode,
        onClick = { onSettingsChange(settings.copy(backgroundMode = backgroundMode)) },
        label = { Text(label) }
    )
}
