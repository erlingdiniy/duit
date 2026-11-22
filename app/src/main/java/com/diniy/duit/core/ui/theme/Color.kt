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

/**
 * Distinct Liquid Glass optical palette derived from `zakisheriff/Liquid-Glass` (`base.css`).
 * Cool neutral translucent glass, crisp hairline specular rims, optical iOS Blue accent,
 * and clean Apple semantic Green/Red — zero Candy Bubble cream or coral forced into glass.
 */
object LiquidGlassPalette {
    // Light mode (base.css:3-51)
    val LightCanvas = Color(0xFFF2F4F8)
    val LightCanvasElevated = Color(0xFFFFFFFF)
    val LightSurfaceSubtle = Color(0xFFE6EAF2)
    val LightGlassBase = Color(0xFFFFFFFF)
    val LightGlassSubtle = Color(0xFFF7F9FC)
    val LightSheetSurface = Color(0xFFFAFBFD)
    val LightInk = Color(0xFF111215)
    val LightInkMuted = Color(0xFF52555E)
    val LightInkDisabled = Color(0xFF989CA6)

    // Optical Blue & semantics (AA >= 4.5:1 on white/light glass)
    val LightAccentFill = Color(0xFF007AFF)
    val LightAccentText = Color(0xFF0062CC)
    val LightAccentContainer = Color(0xFFE1F0FF)
    val LightOnAccentContainer = Color(0xFF003B7A)

    val LightIncome = Color(0xFF1A7F37)
    val LightIncomeContainer = Color(0xFFE2F6E9)
    val LightOnIncomeContainer = Color(0xFF0E5322)

    val LightExpense = Color(0xFFC62828)
    val LightExpenseContainer = Color(0xFFFFE5E7)
    val LightOnExpenseContainer = Color(0xFF7A1416)

    val LightSavings = Color(0xFF0B5FA5)
    val LightSavingsContainer = Color(0xFFE3F2FD)
    val LightOnSavingsContainer = Color(0xFF073B66)

    val LightWarning = Color(0xFF9A5B00)
    val LightWarningContainer = Color(0xFFFFF2DB)
    val LightOnWarningContainer = Color(0xFF5C3500)

    val LightError = Color(0xFFC62828)
    val LightErrorContainer = Color(0xFFFFE5E7)
    val LightOnErrorContainer = Color(0xFF7A1416)

    // Dark mode (base.css:54-93)
    val DarkCanvas = Color(0xFF0B0C0F)
    val DarkCanvasElevated = Color(0xFF191A1F)
    val DarkSurfaceSubtle = Color(0xFF24262D)
    val DarkGlassBase = Color(0xFF1C1D22)
    val DarkGlassElevated = Color(0xFF282A31)
    val DarkGlassSubtle = Color(0xFF141519)
    val DarkSheetSurface = Color(0xFF1A1B20)
    val DarkInk = Color(0xFFF5F6FA)
    val DarkInkMuted = Color(0xFFA2A6B3)
    val DarkInkDisabled = Color(0xFF636773)

    val DarkAccentFill = Color(0xFF0A84FF)
    val DarkAccentText = Color(0xFF4DA8FF)
    val DarkAccentContainer = Color(0xFF153252)
    val DarkOnAccentContainer = Color(0xFFD6EAFF)

    val DarkIncome = Color(0xFF30D158)
    val DarkIncomeContainer = Color(0xFF143520)
    val DarkOnIncomeContainer = Color(0xFFC9F6D5)

    val DarkExpense = Color(0xFFFF5E57)
    val DarkExpenseContainer = Color(0xFF44191B)
    val DarkOnExpenseContainer = Color(0xFFFFD8D6)

    val DarkSavings = Color(0xFF64D2FF)
    val DarkSavingsContainer = Color(0xFF133345)
    val DarkOnSavingsContainer = Color(0xFFD6F3FF)

    val DarkWarning = Color(0xFFFFB340)
    val DarkWarningContainer = Color(0xFF402C10)
    val DarkOnWarningContainer = Color(0xFFFFE5BE)

    val DarkError = Color(0xFFFF6961)
    val DarkErrorContainer = Color(0xFF4A1818)
    val DarkOnErrorContainer = Color(0xFFFFDAD6)
}
