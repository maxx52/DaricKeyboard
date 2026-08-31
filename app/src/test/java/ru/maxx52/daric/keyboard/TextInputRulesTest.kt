package ru.maxx52.daric.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextInputRulesTest {

    @Test
    fun capitalizesAtBeginningOfMessage() {
        assertTrue(TextInputRules.shouldCapitalize(""))
        assertTrue(TextInputRules.shouldCapitalize("   "))
    }

    @Test
    fun capitalizesAfterSentenceEnding() {
        assertTrue(TextInputRules.shouldCapitalize("Привет. "))
        assertTrue(TextInputRules.shouldCapitalize("Привет! \""))
        assertTrue(TextInputRules.shouldCapitalize("До встречи… "))
        assertTrue(TextInputRules.shouldCapitalize("Новая строка\n  "))
    }

    @Test
    fun doesNotCapitalizeAfterComma() {
        assertFalse(TextInputRules.shouldCapitalize("Привет, "))
    }

    @Test
    fun removesSingleSpaceBeforePunctuation() {
        assertEquals(1, TextInputRules.spacesToDeleteBeforePunctuation("Привет ", ','))
        assertEquals(1, TextInputRules.spacesToDeleteBeforePunctuation("Привет ", '.'))
    }

    @Test
    fun keepsOneSpaceWhenUserTypesTwoSpaces() {
        assertEquals(1, TextInputRules.spacesToDeleteBeforePunctuation("Привет  ", '!'))
    }

    @Test
    fun doesNotChangeSpacingBeforeDash() {
        assertEquals(0, TextInputRules.spacesToDeleteBeforePunctuation("Привет ", '—'))
    }
}
