package com.diniy.duit.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Centralized palette for Prismatic Spatial Glass.
 * Preserves all existing [PrismaticPalette] tokens while providing independently tuned
 * Light and Dark spatial glass, border, highlight, text, and semantic financial tokens.
 */
object PrismaticPalette {
    // Official Brand Palette (Phase 6A Section 4)
    val Aqua = Color(0xFF42CDBB)
    val Lavender = Color(0xFF9A83E8)
    val SkyBlue = Color(0xFF75B9ED)
    val Peach = Color(0xFFF3B78B)
    val Slate = Color(0xFF263A55)

    // Light Mode Canvas & Surfaces (Airy spatial daylight with cool pearl undertones)
    val LightCanvas = Color(0xFFF3F6FC)
    val LightCanvasElevated = Color(0xFFFFFFFF)
    val LightSurfaceSubtle = Color(0xFFE9EFF8)
    val LightGlassBase = Color(0xFFFFFFFF)
    val LightGlassSubtle = Color(0xFFF1F5FB)
    val LightSheetSurface = Color(0xFFF7F9FE)

    // Light Mode Ink & Contrast-Verified Text (WCAG AA >= 4.5:1 against LightCanvas & Glass)
    val LightInk = Color(0xFF121E2F)
    val LightInkMuted = Color(0xFF44566E)
    val LightInkDisabled = Color(0xFF7B8CA3)

    // Light Mode Semantic Financial & Action Colours
    val LightPrimary = Color(0xFF5842B5)
    val LightOnPrimary = Color(0xFFFFFFFF)
    val LightPrimaryContainer = Color(0xFFE8E2FC)
    val LightOnPrimaryContainer = Color(0xFF25165E)

    val LightSecondary = Color(0xFF0F7B6C)
    val LightIncome = Color(0xFF0E7A6B)
    val LightIncomeContainer = Color(0xFFD5F5EE)
    val LightOnIncomeContainer = Color(0xFF073E36)

    val LightExpense = Color(0xFFB93848)
    val LightExpenseContainer = Color(0xFFFCE2E5)
    val LightOnExpenseContainer = Color(0xFF66121E)

    val LightTertiary = Color(0xFF216499)
    val LightSavings = Color(0xFF1F6396)
    val LightSavingsContainer = Color(0xFFD9EEFC)
    val LightOnSavingsContainer = Color(0xFF0C3554)

    val LightCautionInk = Color(0xFF87481A)
    val LightWarning = Color(0xFF87481A)
    val LightWarningContainer = Color(0xFFFDE8D7)
    val LightOnWarningContainer = Color(0xFF4D2507)

    val LightError = Color(0xFFC43B4B)
    val LightErrorContainer = Color(0xFFFCE2E5)
    val LightOnErrorContainer = Color(0xFF6B1420)

    val LightSuccess = Color(0xFF0F7B6C)
    val LightSuccessContainer = Color(0xFFD5F5EE)
    val LightOnSuccessContainer = Color(0xFF073E36)

    // Dark Mode Canvas & Spatial Surfaces (Deep midnight slate with luminous frost panels)
    val DarkCanvas = Color(0xFF0B111C)
    val DarkCanvasElevated = Color(0xFF141F31)
    val DarkSurfaceSubtle = Color(0xFF1B2940)
    val DarkGlassBase = Color(0xFF19263B)
    val DarkGlassElevated = Color(0xFF21324C)
    val DarkGlassSubtle = Color(0xFF131E30)
    val DarkSheetSurface = Color(0xFF152033)

    // Dark Mode Ink & Contrast-Verified Text (WCAG AA >= 4.5:1 against DarkCanvas & Glass)
    val DarkInk = Color(0xFFF3F7FD)
    val DarkInkMuted = Color(0xFFB2C1D6)
    val DarkInkDisabled = Color(0xFF6E8099)

    // Dark Mode Semantic Financial & Action Colours
    val DarkPrimaryInk = Color(0xFF140F2A)
    val DarkPrimaryContainer = Color(0xFF33266B)
    val DarkOnPrimaryContainer = Color(0xFFE7E1FF)

    val DarkSecondaryInk = Color(0xFF052823)
    val DarkIncome = Color(0xFF55DEC9)
    val DarkIncomeContainer = Color(0xFF104A43)
    val DarkOnIncomeContainer = Color(0xFFB5F7EE)

    val DarkExpense = Color(0xFFFF8590)
    val DarkExpenseContainer = Color(0xFF561922)
    val DarkOnExpenseContainer = Color(0xFFFFDADE)

    val DarkTertiaryInk = Color(0xFF072136)
    val DarkSavings = Color(0xFF84C4F4)
    val DarkSavingsContainer = Color(0xFF164266)
    val DarkOnSavingsContainer = Color(0xFFD2ECFD)

    val DarkCautionInk = Color(0xFF361D09)
    val DarkWarning = Color(0xFFF6C29B)
    val DarkWarningContainer = Color(0xFF4F2D13)
    val DarkOnWarningContainer = Color(0xFFFCD9BE)

    val DarkError = Color(0xFFFF7A85)
    val DarkErrorContainer = Color(0xFF5D1821)
    val DarkOnErrorContainer = Color(0xFFFFDADE)

    val DarkSuccess = Color(0xFF55DEC9)
    val DarkSuccessContainer = Color(0xFF104A43)
    val DarkOnSuccessContainer = Color(0xFFB5F7EE)
}
