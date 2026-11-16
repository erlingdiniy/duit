package com.diniy.duit.domain.model

/**
 * User-selectable appearance mode for duit.
 */
enum class ThemeMode(val label: String, val description: String) {
    SYSTEM("System", "Matches your device appearance"),
    LIGHT("Light", "Bright frosted alabaster canvas"),
    DARK("Dark", "Deep nocturnal slate glass")
}
