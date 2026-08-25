package ru.maxx52.daric.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RussianSuggestionEngineTest {

    @Test
    fun parserSeparatesCurrentWordFromContext() {
        val context = SuggestionContextParser.parse("спасибо за пом")

        assertEquals("пом", context.currentWord)
        assertEquals(listOf("спасибо", "за"), context.previousWords)
    }

    @Test
    fun suggestsNextWordAfterSpace() {
        val suggestions = RussianSuggestionEngine.suggest(
            context = SuggestionContextParser.parse("доброе "),
            limit = 3
        )

        assertEquals("утро", suggestions.first())
    }

    @Test
    fun usesTwoPreviousWordsForPhrasePrediction() {
        val suggestions = RussianSuggestionEngine.suggest(
            context = SuggestionContextParser.parse("спасибо за "),
            limit = 3
        )

        assertEquals(listOf("помощь", "поддержку", "поздравление"), suggestions)
    }

    @Test
    fun contextCandidateWinsWhileCompletingAWord() {
        val suggestions = RussianSuggestionEngine.suggest(
            context = SuggestionContextParser.parse("доброе у"),
            limit = 3
        )

        assertEquals("утро", suggestions.first())
    }

    @Test
    fun dictionaryStillCompletesWordsWithoutContext() {
        val suggestions = RussianSuggestionEngine.suggest(
            context = SuggestionContextParser.parse("прив"),
            limit = 3
        )

        assertTrue("привет" in suggestions)
    }

    @Test
    fun neuralCandidatesAreRankedBeforeRuleFallback() {
        val suggestions = RussianSuggestionEngine.suggest(
            context = SuggestionContextParser.parse("давай "),
            limit = 3,
            neuralCandidates = listOf("добавим", "проверим")
        )

        assertEquals(listOf("добавим", "проверим", "сделаем"), suggestions)
    }

    @Test
    fun personalizedCandidatesAreRankedBeforeNeuralCandidates() {
        val suggestions = RussianSuggestionEngine.suggest(
            context = SuggestionContextParser.parse("давай "),
            limit = 3,
            neuralCandidates = listOf("проверим"),
            personalizedCandidates = listOf("продолжим")
        )

        assertEquals(listOf("продолжим", "проверим", "сделаем"), suggestions)
    }

    @Test
    fun detectsBeginningOfANewSentence() {
        assertTrue(SuggestionContextParser.parse("Всё хорошо. ").startsNewSentence)
    }
}
