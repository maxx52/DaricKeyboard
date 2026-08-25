package ru.maxx52.daric.keyboard

import android.content.Context
import org.json.JSONObject
import java.util.Locale

/**
 * Локальная ограниченная история частот. Хранятся только отдельные слова
 * и короткие связи между ними, а не введённые сообщения целиком.
 */
internal class PersonalSuggestionStore(context: Context) : ContextLanguageModel {

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )
    private val russianLocale = Locale("ru", "RU")
    private val counts = mutableMapOf<String, MutableMap<String, Int>>()
    private var loadedClearGeneration = preferences.getInt(CLEAR_GENERATION_KEY, 0)

    init {
        load()
    }

    @Synchronized
    fun learn(previousWords: List<String>, completedWord: String) {
        synchronizeClearGeneration()
        val word = normalize(completedWord) ?: return
        val normalizedContext = previousWords.mapNotNull(::normalize).takeLast(MAX_CONTEXT_WORDS)
        val contextKeys = buildList {
            add(GLOBAL_CONTEXT)
            if (normalizedContext.isNotEmpty()) add(normalizedContext.takeLast(1).joinToString(" "))
            if (normalizedContext.size >= 2) add(normalizedContext.takeLast(2).joinToString(" "))
        }

        contextKeys.forEach { key ->
            val candidates = counts.getOrPut(key) { mutableMapOf() }
            candidates[word] = ((candidates[word] ?: 0) + 1).coerceAtMost(MAX_COUNT)
            while (candidates.size > MAX_CANDIDATES_PER_CONTEXT) {
                candidates.minByOrNull { it.value }?.key?.let(candidates::remove)
            }
        }
        while (counts.size > MAX_CONTEXTS) {
            counts.entries
                .filter { it.key != GLOBAL_CONTEXT }
                .minByOrNull { entry -> entry.value.values.sum() }
                ?.key
                ?.let(counts::remove)
                ?: break
        }
        persist()
    }

    @Synchronized
    override fun predictNext(previousWords: List<String>, limit: Int): List<String> {
        synchronizeClearGeneration()
        if (limit <= 0) return emptyList()
        val normalizedContext = previousWords.mapNotNull(::normalize).takeLast(MAX_CONTEXT_WORDS)
        val keys = buildList {
            if (normalizedContext.size >= 2) add(normalizedContext.takeLast(2).joinToString(" "))
            if (normalizedContext.isNotEmpty()) add(normalizedContext.takeLast(1).joinToString(" "))
            add(GLOBAL_CONTEXT)
        }

        return buildList {
            keys.forEach { key ->
                counts[key]
                    ?.entries
                    ?.sortedByDescending { it.value }
                    ?.forEach { entry -> add(entry.key) }
            }
        }.distinct().take(limit)
    }

    private fun normalize(value: String): String? {
        val word = value.lowercase(russianLocale).trim('-')
        if (word.length !in MIN_WORD_LENGTH..MAX_WORD_LENGTH) return null
        if (word.any { character ->
                character != '-' && character != 'ё' && character !in 'а'..'я'
            }
        ) return null
        return word
    }

    private fun load() {
        val raw = preferences.getString(DATA_KEY, null) ?: return
        runCatching {
            val root = JSONObject(raw)
            root.keys().forEach { contextKey ->
                val source = root.optJSONObject(contextKey) ?: return@forEach
                val candidates = mutableMapOf<String, Int>()
                source.keys().forEach { word ->
                    val count = source.optInt(word, 0)
                    if (count > 0 && normalize(word) != null) candidates[word] = count
                }
                if (candidates.isNotEmpty()) counts[contextKey] = candidates
            }
        }.onFailure {
            counts.clear()
            preferences.edit().remove(DATA_KEY).apply()
        }
    }

    private fun synchronizeClearGeneration() {
        val currentGeneration = preferences.getInt(CLEAR_GENERATION_KEY, 0)
        if (currentGeneration != loadedClearGeneration) {
            counts.clear()
            loadedClearGeneration = currentGeneration
        }
    }

    private fun persist() {
        val root = JSONObject()
        counts.forEach { (contextKey, candidates) ->
            val values = JSONObject()
            candidates.forEach { (word, count) -> values.put(word, count) }
            root.put(contextKey, values)
        }
        preferences.edit().putString(DATA_KEY, root.toString()).apply()
    }

    internal companion object {
        fun clearAll(context: Context) {
            val preferences = context.applicationContext.getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE
            )
            val nextGeneration = preferences.getInt(CLEAR_GENERATION_KEY, 0) + 1
            preferences.edit()
                .remove(DATA_KEY)
                .putInt(CLEAR_GENERATION_KEY, nextGeneration)
                .apply()
        }

        private const val PREFERENCES_NAME = "daric_personal_suggestions"
        private const val DATA_KEY = "transition_counts"
        private const val CLEAR_GENERATION_KEY = "clear_generation"
        private const val GLOBAL_CONTEXT = "*"
        private const val MAX_CONTEXT_WORDS = 2
        private const val MAX_CONTEXTS = 250
        private const val MAX_CANDIDATES_PER_CONTEXT = 8
        private const val MAX_COUNT = 1_000
        private const val MIN_WORD_LENGTH = 2
        private const val MAX_WORD_LENGTH = 32
    }
}
