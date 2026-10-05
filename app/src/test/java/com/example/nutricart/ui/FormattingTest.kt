package com.example.nutricart.ui

import androidx.compose.ui.text.AnnotatedString
import com.example.nutricart.navigation.Routes
import com.example.nutricart.navigation.ShellTabs
import com.example.nutricart.ui.components.BottomNavTab
import com.example.nutricart.ui.components.ThousandsVisualTransformation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormattingTest {

    @Test
    fun initials_useUpToTwoWords() {
        assertEquals("AR", initialsOf("Arpita Roy"))
        assertEquals("A", initialsOf("arpita"))
        assertEquals("AR", initialsOf("  Arpita   Roy  Chowdhury "))
        assertEquals("", initialsOf("   "))
    }

    @Test
    fun firstName_isTheFirstWord() {
        assertEquals("Arpita", firstNameOf("Arpita Roy"))
        assertEquals("Arpita", firstNameOf("  Arpita "))
        assertEquals("", firstNameOf(""))
    }

    @Test
    fun dayPeriod_followsTheClock() {
        // Night runs from 9 PM through 4:59 AM
        (0..4).forEach { assertEquals("hour $it", DayPeriod.Night, DayPeriod.forHour(it)) }
        (5..11).forEach { assertEquals("hour $it", DayPeriod.Morning, DayPeriod.forHour(it)) }
        (12..16).forEach { assertEquals("hour $it", DayPeriod.Afternoon, DayPeriod.forHour(it)) }
        (17..20).forEach { assertEquals("hour $it", DayPeriod.Evening, DayPeriod.forHour(it)) }
        (21..23).forEach { assertEquals("hour $it", DayPeriod.Night, DayPeriod.forHour(it)) }
    }

    @Test
    fun dayPeriod_changesExactlyAtTheBoundaryHours() {
        assertEquals(DayPeriod.Night, DayPeriod.forHour(4))
        assertEquals(DayPeriod.Morning, DayPeriod.forHour(5))
        assertEquals(DayPeriod.Morning, DayPeriod.forHour(11))
        assertEquals(DayPeriod.Afternoon, DayPeriod.forHour(12))
        assertEquals(DayPeriod.Afternoon, DayPeriod.forHour(16))
        assertEquals(DayPeriod.Evening, DayPeriod.forHour(17))
        assertEquals(DayPeriod.Evening, DayPeriod.forHour(20))
        assertEquals(DayPeriod.Night, DayPeriod.forHour(21))
    }

    @Test
    fun dayPeriod_nowUsesTheDevicesLocalHour() {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val now = DayPeriod.now()
        // Either the hour read here or, if the hour just turned, the next one
        assertTrue(now == DayPeriod.forHour(hour) || now == DayPeriod.forHour((hour + 1) % 24))
    }

    @Test
    fun dayPeriod_eachHasItsOwnGreeting() {
        assertEquals(4, DayPeriod.entries.map { it.greetingRes }.toSet().size)
    }

    @Test
    fun thousands_groupsDigitsInThrees() {
        assertEquals("", ThousandsVisualTransformation.format(""))
        assertEquals("500", ThousandsVisualTransformation.format("500"))
        assertEquals("12,000", ThousandsVisualTransformation.format("12000"))
        assertEquals("999,999", ThousandsVisualTransformation.format("999999"))
        assertEquals("1,234,567", ThousandsVisualTransformation.format("1234567"))
    }

    @Test
    fun thousands_cursorPositionsStayInsideTheText() {
        listOf("", "5", "500", "12000", "999999", "1234567").forEach { digits ->
            val transformed = ThousandsVisualTransformation.filter(AnnotatedString(digits))
            val shown = transformed.text.text
            for (offset in 0..digits.length) {
                val mapped = transformed.offsetMapping.originalToTransformed(offset)
                assert(mapped in 0..shown.length) { "'$digits' offset $offset mapped to $mapped" }
            }
            assertEquals(shown.length, transformed.offsetMapping.originalToTransformed(digits.length))
            for (offset in 0..shown.length) {
                val mapped = transformed.offsetMapping.transformedToOriginal(offset)
                assert(mapped in 0..digits.length) { "'$shown' offset $offset mapped to $mapped" }
            }
            assertEquals(digits.length, transformed.offsetMapping.transformedToOriginal(shown.length))
        }
    }

    // Bottom-navigation shell

    @Test
    fun everyTab_hasItsOwnRoute() {
        assertEquals(Routes.HOME, ShellTabs.routeFor(BottomNavTab.Home))
        assertEquals(Routes.LIST, ShellTabs.routeFor(BottomNavTab.GroceryList))
        assertEquals(Routes.NUTRITION, ShellTabs.routeFor(BottomNavTab.Nutrition))
        assertEquals(Routes.PROFILE, ShellTabs.routeFor(BottomNavTab.Profile))
        assertEquals(4, BottomNavTab.entries.map(ShellTabs::routeFor).toSet().size)
    }

    @Test
    fun bottomBar_isShownOnlyOnTabRoots() {
        BottomNavTab.entries.forEach { assertEquals(it, ShellTabs.tabFor(ShellTabs.routeFor(it))) }

        listOf(Routes.SPLASH, Routes.WELCOME, Routes.LOGIN, Routes.REGISTER, Routes.PROFILE_SETUP, null)
            .forEach { assertNull("no bar on $it", ShellTabs.tabFor(it)) }
    }

    @Test
    fun profileSetup_routeCarriesTheEditMode() {
        assertEquals("profile/setup", Routes.profileSetup())
        assertEquals("profile/setup?mode=edit", Routes.profileSetup(edit = true))
    }
}
