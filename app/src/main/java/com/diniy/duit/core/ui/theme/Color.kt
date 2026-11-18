package com.diniy.duit.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * UI Experiment 01 palette: Cream, Coral, Sunny, Mint and Deep Teal.
 * - Bright Coral is a fill (hero, quick action); text on it is Deep Teal (white fails contrast).
 * - [CoralDeep] is the text-safe coral used for actions, links and selected labels.
 * Legacy accent names (Aqua, Lavender, SkyBlue, Peach, Slate) are kept because wallets,
 * goals and widgets persist them as [com.diniy.duit.domain.model.PrismaticAccentToken].
 */
object PrismaticPalette {
    // Experiment 01 brand
    val Cream = Color(0xFFFFF4E9)
    val Coral = Color(0xFFFF836B)
    val CoralDeep = Color(0xFFB23F27)
    val Sunny = Color(0xFFFFB84D)
    val Mint = Color(0xFF79DCC6)
    val DeepTeal = Color(0xFF263B43)

    // Legacy accent tokens remapped (no purple identity)
    val Aqua = Mint
    val Lavender = Coral
    val SkyBlue = Color(0xFF6EB8E8)
    val Peach = Color(0xFFFFB199)
    val Slate = Sunny

    // Light canvas & surfaces (cream world, white cards, peach tints)
    val LightCanvas = Cream
    val LightCanvasElevated = Color(0xFFFFFFFF)
    val LightSurfaceSubtle = Color(0xFFFFE6D3)
    val LightGlassBase = Color(0xFFFFFFFF)
    val LightGlassSubtle = Color(0xFFFFF9F3)
    val LightSheetSurface = Color(0xFFFFFAF5)

    // Light ink
    val LightInk = DeepTeal
    val LightInkMuted = Color(0xFF4F6670)
    val LightInkDisabled = Color(0xFF93A3A8)

    // Light semantic colours (text tones are darker than the fills for AA contrast)
    val LightPrimary = CoralDeep
    val LightOnPrimary = Color(0xFFFFFFFF)
    val LightPrimaryContainer = Color(0xFFFFE1D8)
    val LightOnPrimaryContainer = Color(0xFF5C1E10)

    val LightSecondary = Color(0xFF0B6B5C)
    val LightIncome = Color(0xFF0B6B5C)
    val LightIncomeContainer = Color(0xFFD9F5EE)
    val LightOnIncomeContainer = Color(0xFF064A3F)

    val LightExpense = Color(0xFFB4235A)
    val LightExpenseContainer = Color(0xFFFFE2E8)
    val LightOnExpenseContainer = Color(0xFF660F31)

    val LightTertiary = Color(0xFF7E4E00)
    val LightSavings = Color(0xFF7E4E00)
    val LightSavingsContainer = Color(0xFFFFEBC8)
    val LightOnSavingsContainer = Color(0xFF4A2E00)

    val LightCautionInk = Color(0xFF9A4100)
    val LightWarning = Color(0xFF9A4100)
    val LightWarningContainer = Color(0xFFFFE2CC)
    val LightOnWarningContainer = Color(0xFF552300)

    val LightError = Color(0xFFB3261E)
    val LightErrorContainer = Color(0xFFFFDAD6)
    val LightOnErrorContainer = Color(0xFF5F1410)

    val LightSuccess = LightIncome
    val LightSuccessContainer = LightIncomeContainer
    val LightOnSuccessContainer = LightOnIncomeContainer

    // Dark canvas & surfaces (deep teal night, independently tuned)
    val DarkCanvas = Color(0xFF15242A)
    val DarkCanvasElevated = Color(0xFF1E3138)
    val DarkSurfaceSubtle = Color(0xFF2B434C)
    val DarkGlassBase = Color(0xFF1E3138)
    val DarkGlassElevated = DeepTeal
    val DarkGlassSubtle = Color(0xFF192B31)
    val DarkSheetSurface = Color(0xFF1E3138)

    // Dark ink
    val DarkInk = Cream
    val DarkInkMuted = Color(0xFFB9C9CC)
    val DarkInkDisabled = Color(0xFF6F8489)

    // Dark semantic colours (bright coral is text-safe on the dark canvas)
    val DarkPrimary = Coral
    val DarkPrimaryInk = Color(0xFF1A2A30)
    val DarkPrimaryContainer = Color(0xFF4A3833)
    val DarkOnPrimaryContainer = Color(0xFFFFDAD0)

    val DarkSecondaryInk = Color(0xFF06332B)
    val DarkIncome = Mint
    val DarkIncomeContainer = Color(0xFF18403A)
    val DarkOnIncomeContainer = Color(0xFFC8F3E8)

    val DarkExpense = Color(0xFFFF8FA3)
    val DarkExpenseContainer = Color(0xFF4D2230)
    val DarkOnExpenseContainer = Color(0xFFFFD9E0)

    val DarkTertiaryInk = Color(0xFF3D2A00)
    val DarkSavings = Color(0xFFFFC86B)
    val DarkSavingsContainer = Color(0xFF4A3812)
    val DarkOnSavingsContainer = Color(0xFFFFE9C2)

    val DarkCautionInk = Color(0xFF3A1E00)
    val DarkWarning = Color(0xFFFFAA66)
    val DarkWarningContainer = Color(0xFF4A2A0E)
    val DarkOnWarningContainer = Color(0xFFFFDDBE)

    val DarkError = Color(0xFFFF8A80)
    val DarkErrorContainer = Color(0xFF5C1A15)
    val DarkOnErrorContainer = Color(0xFFFFDAD6)

    val DarkSuccess = DarkIncome
    val DarkSuccessContainer = DarkIncomeContainer
    val DarkOnSuccessContainer = DarkOnIncomeContainer

    // Sticker outline / offset-shadow ink
    val LightStickerInk = DeepTeal
    val DarkStickerInk = Color(0xFF0A1418)
}
