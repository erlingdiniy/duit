package com.diniy.duit.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Candy Pop palette (Phase 6C): 70% Candy Bubble + 30% Sticker Pop.
 * Legacy accent names (Aqua, Lavender, SkyBlue, Peach, Slate) are kept because wallets,
 * goals and widgets persist them as [com.diniy.duit.domain.model.PrismaticAccentToken];
 * only their colours change.
 */
object PrismaticPalette {
    // Candy Pop brand
    val Grape = Color(0xFF7B3CFF)
    val Bubblegum = Color(0xFFFF5C8A)
    val Sunny = Color(0xFFFFD84D)
    val Mint = Color(0xFF5EE0C1)
    val GrapeInk = Color(0xFF2A1650)

    // Legacy accent tokens remapped to Candy Pop
    val Aqua = Mint
    val Lavender = Color(0xFF9B6BFF)
    val SkyBlue = Color(0xFF62B6FF)
    val Peach = Bubblegum
    val Slate = Sunny // 5th candy accent choice (was dark slate; a dark tint turned cards grey)

    // Light canvas & surfaces (lavender world, white candy cards)
    val LightCanvas = Color(0xFFF3ECFF)
    val LightCanvasElevated = Color(0xFFFFFFFF)
    val LightSurfaceSubtle = Color(0xFFECE2FF)
    val LightGlassBase = Color(0xFFFFFFFF)
    val LightGlassSubtle = Color(0xFFF8F3FF)
    val LightSheetSurface = Color(0xFFFBF8FF)

    // Light ink
    val LightInk = Color(0xFF2A1650)
    val LightInkMuted = Color(0xFF5E4C85)
    val LightInkDisabled = Color(0xFF9A8BBB)

    // Light semantic colours (text tones are darker than the candy fills for AA contrast)
    val LightPrimary = Grape
    val LightOnPrimary = Color(0xFFFFFFFF)
    val LightPrimaryContainer = Color(0xFFECE2FF)
    val LightOnPrimaryContainer = GrapeInk

    val LightSecondary = Color(0xFF0B6B5C)
    val LightIncome = Color(0xFF0B6B5C)
    val LightIncomeContainer = Color(0xFFD6F7EF)
    val LightOnIncomeContainer = Color(0xFF064A3F)

    val LightExpense = Color(0xFFC2185B)
    val LightExpenseContainer = Color(0xFFFFE1EA)
    val LightOnExpenseContainer = Color(0xFF6E0F33)

    val LightTertiary = Color(0xFF7A5200)
    val LightSavings = Color(0xFF7A5200)
    val LightSavingsContainer = Color(0xFFFFF3C4)
    val LightOnSavingsContainer = Color(0xFF4D3300)

    val LightCautionInk = Color(0xFF9A4700)
    val LightWarning = Color(0xFF9A4700)
    val LightWarningContainer = Color(0xFFFFE6CC)
    val LightOnWarningContainer = Color(0xFF5A2600)

    val LightError = Color(0xFFB3261E)
    val LightErrorContainer = Color(0xFFFFDAD6)
    val LightOnErrorContainer = Color(0xFF5F1410)

    val LightSuccess = LightIncome
    val LightSuccessContainer = LightIncomeContainer
    val LightOnSuccessContainer = LightOnIncomeContainer

    // Dark canvas & surfaces (night grape, not a naive inversion)
    val DarkCanvas = Color(0xFF160C2B)
    val DarkCanvasElevated = Color(0xFF22143F)
    val DarkSurfaceSubtle = Color(0xFF2B1A4F)
    val DarkGlassBase = Color(0xFF22143F)
    val DarkGlassElevated = Color(0xFF2E1C55)
    val DarkGlassSubtle = Color(0xFF1C1036)
    val DarkSheetSurface = Color(0xFF22143F)

    // Dark ink
    val DarkInk = Color(0xFFF4EEFF)
    val DarkInkMuted = Color(0xFFBBA9E0)
    val DarkInkDisabled = Color(0xFF7C6AA3)

    // Dark semantic colours
    val DarkPrimary = Color(0xFFB18CFF)
    val DarkPrimaryInk = Color(0xFF1D0B3D)
    val DarkPrimaryContainer = Color(0xFF3A2470)
    val DarkOnPrimaryContainer = Color(0xFFEADDFF)

    val DarkSecondaryInk = Color(0xFF053A30)
    val DarkIncome = Color(0xFF6FEBCD)
    val DarkIncomeContainer = Color(0xFF0F3B33)
    val DarkOnIncomeContainer = Color(0xFFBDF7E8)

    val DarkExpense = Color(0xFFFF7FA6)
    val DarkExpenseContainer = Color(0xFF4A1530)
    val DarkOnExpenseContainer = Color(0xFFFFD6E3)

    val DarkTertiaryInk = Color(0xFF3D2A00)
    val DarkSavings = Color(0xFFFFE07A)
    val DarkSavingsContainer = Color(0xFF3D3010)
    val DarkOnSavingsContainer = Color(0xFFFFF0B8)

    val DarkCautionInk = Color(0xFF3A1E00)
    val DarkWarning = Color(0xFFFFB870)
    val DarkWarningContainer = Color(0xFF4A2A08)
    val DarkOnWarningContainer = Color(0xFFFFDDB8)

    val DarkError = Color(0xFFFF8A80)
    val DarkErrorContainer = Color(0xFF5C1A15)
    val DarkOnErrorContainer = Color(0xFFFFDAD6)

    val DarkSuccess = DarkIncome
    val DarkSuccessContainer = DarkIncomeContainer
    val DarkOnSuccessContainer = DarkOnIncomeContainer

    // Sticker Pop outline / offset-shadow ink
    val LightStickerInk = GrapeInk
    val DarkStickerInk = Color(0xFF07030F)
}
