package com.example.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

object ThemeState {
    var isDarkTheme by mutableStateOf(true) // Starts in beautiful high-contrast dark mode!
    var selectedTheme by mutableStateOf("SLATE") // "SLATE", "CYBERPUNK", "EMERALD", "AMBER", "AMETHYST"
}

// Professional Polish Palette (M3 Dynamic Switchable Theme)
val SlateDark: Color
    get() = when (ThemeState.selectedTheme) {
        "CYBERPUNK" -> if (ThemeState.isDarkTheme) Color(0xFF030308) else Color(0xFFF9F5F7)
        "EMERALD" -> if (ThemeState.isDarkTheme) Color(0xFF070B08) else Color(0xFFF2F6F3)
        "AMBER" -> if (ThemeState.isDarkTheme) Color(0xFF0E0A07) else Color(0xFFFAF6F2)
        "AMETHYST" -> if (ThemeState.isDarkTheme) Color(0xFF0B0912) else Color(0xFFF5F2FA)
        else -> if (ThemeState.isDarkTheme) Color(0xFF0C0E14) else Color(0xFFF4F6F9) // "SLATE"
    }

val CardDark: Color
    get() = when (ThemeState.selectedTheme) {
        "CYBERPUNK" -> if (ThemeState.isDarkTheme) Color(0xFF0D0C15) else Color(0xFFFFFFFF)
        "EMERALD" -> if (ThemeState.isDarkTheme) Color(0xFF101913) else Color(0xFFFFFFFF)
        "AMBER" -> if (ThemeState.isDarkTheme) Color(0xFF17110C) else Color(0xFFFFFFFF)
        "AMETHYST" -> if (ThemeState.isDarkTheme) Color(0xFF13101E) else Color(0xFFFFFFFF)
        else -> if (ThemeState.isDarkTheme) Color(0xFF131722) else Color(0xFFFFFFFF) // "SLATE"
    }

val PrimaryCyan: Color
    get() = when (ThemeState.selectedTheme) {
        "CYBERPUNK" -> if (ThemeState.isDarkTheme) Color(0xFFFF007F) else Color(0xFFD81B60) // Neon Pink
        "EMERALD" -> if (ThemeState.isDarkTheme) Color(0xFF00E676) else Color(0xFF2E7D32)  // Mint Green
        "AMBER" -> if (ThemeState.isDarkTheme) Color(0xFFFF9100) else Color(0xFFE65100)    // Safety Orange
        "AMETHYST" -> if (ThemeState.isDarkTheme) Color(0xFFD0BCFF) else Color(0xFF7E57C2) // Cosmic Lavender
        else -> if (ThemeState.isDarkTheme) Color(0xFF00E5FF) else Color(0xFF0097A7)       // "SLATE" Cyan
    }

val AccentBlue: Color
    get() = when (ThemeState.selectedTheme) {
        "CYBERPUNK" -> if (ThemeState.isDarkTheme) Color(0xFF00FFCC) else Color(0xFF00BFA5) // Cyan Neon
        "EMERALD" -> if (ThemeState.isDarkTheme) Color(0xFF00E5FF) else Color(0xFF0097A7)   // Teal Accent
        "AMBER" -> if (ThemeState.isDarkTheme) Color(0xFFFFD700) else Color(0xFFF57C00)     // Amber / Gold Accent
        "AMETHYST" -> if (ThemeState.isDarkTheme) Color(0xFFE040FB) else Color(0xFF8E24AA)  // Magenta Accent
        else -> if (ThemeState.isDarkTheme) Color(0xFF2979FF) else Color(0xFF1565C0)        // Classic Blue
    }

val SpindleGold: Color
    get() = when (ThemeState.selectedTheme) {
        "CYBERPUNK" -> Color(0xFF00FFCC)
        "EMERALD" -> Color(0xFF00B0FF)
        "AMBER" -> Color(0xFFFFD700)
        "AMETHYST" -> Color(0xFFE040FB)
        else -> Color(0xFFFFB300) // Spindle Gold
    }

val LaserCrimson: Color
    get() = if (ThemeState.isDarkTheme) Color(0xFFFF1744) else Color(0xFFC62828)

val RouterGreen: Color
    get() = if (ThemeState.isDarkTheme) Color(0xFF00E676) else Color(0xFF2E7D32)

val UnselectedGrey: Color
    get() = if (ThemeState.isDarkTheme) Color(0xFF78909C) else Color(0xFF546E7A)

val TextLight: Color
    get() = if (ThemeState.isDarkTheme) Color(0xFFECEFF1) else Color(0xFF212121)

val TravelGray: Color
    get() = if (ThemeState.isDarkTheme) Color(0xFF37474F) else Color(0xFFB0BEC5)

val BorderCyan: Color
    get() = when (ThemeState.selectedTheme) {
        "CYBERPUNK" -> if (ThemeState.isDarkTheme) Color(0xFF2A0C1D) else Color(0xFFF5CCE2)
        "EMERALD" -> if (ThemeState.isDarkTheme) Color(0xFF152A1C) else Color(0xFFD2E4D6)
        "AMBER" -> if (ThemeState.isDarkTheme) Color(0xFF2E1C11) else Color(0xFFEEDFD2)
        "AMETHYST" -> if (ThemeState.isDarkTheme) Color(0xFF221731) else Color(0xFFE2DBF0)
        else -> if (ThemeState.isDarkTheme) Color(0xFF1C2431) else Color(0xFFE0E0E0)
    }

// Classic Material 3 styling values
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6750A4)
val PurpleGrey40 = Color(0xFF625B71)
val Pink40 = Color(0xFF7D5260)
