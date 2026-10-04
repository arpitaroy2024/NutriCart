package com.example.nutricart.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetRulesTest {

    @Test
    fun sanitize_keepsDigitsOnlyAndCapsTheLength() {
        assertEquals("12000", BudgetRules.sanitize("12,000"))
        assertEquals("12", BudgetRules.sanitize("Tk 1a2-"))
        assertEquals("1234567", BudgetRules.sanitize("123456789"))
        assertEquals("", BudgetRules.sanitize("abc"))
    }

    @Test
    fun validate_acceptsTheLimitsThemselves() {
        assertNull(BudgetRules.validate("500"))
        assertNull(BudgetRules.validate("12000"))
        assertNull(BudgetRules.validate("999999"))
    }

    @Test
    fun validate_rejectsAmountsOutsideTheLimits() {
        assertEquals(BudgetError.BelowMinimum, BudgetRules.validate("499"))
        assertEquals(BudgetError.BelowMinimum, BudgetRules.validate("0"))
        assertEquals(BudgetError.AboveMaximum, BudgetRules.validate("1000000"))
    }

    @Test
    fun blank_isNeitherAnErrorNorValid() {
        assertNull(BudgetRules.validate(""))
        assertFalse(BudgetRules.isValid(""))
        assertTrue(BudgetRules.isValid("500"))
        assertFalse(BudgetRules.isValid("499"))
    }
}
