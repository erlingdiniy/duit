package com.diniy.duit.feature.savings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LaptopMac
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.diniy.duit.core.ui.theme.LocalPrismaticGlass
import com.diniy.duit.domain.model.PrismaticAccentToken

object SavingsIconMapper {

    val selectableIcons: List<Pair<String, String>> = listOf(
        "savings" to "Savings",
        "emergency" to "Emergency",
        "travel" to "Travel",
        "home" to "Home",
        "car" to "Vehicle",
        "education" to "Education",
        "gadget" to "Gadget",
        "investment" to "Invest"
    )

    fun iconFor(iconKey: String?): ImageVector {
        return when (iconKey) {
            "emergency" -> Icons.Rounded.Shield
            "travel" -> Icons.Rounded.Flight
            "home" -> Icons.Rounded.Home
            "car" -> Icons.Rounded.DirectionsCar
            "education" -> Icons.Rounded.School
            "gadget" -> Icons.Rounded.LaptopMac
            "investment" -> Icons.AutoMirrored.Rounded.TrendingUp
            else -> Icons.Rounded.Savings
        }
    }

    @Composable
    fun accentColors(token: PrismaticAccentToken): Triple<Color, Color, Color> {
        val glass = LocalPrismaticGlass.current
        return when (token) {
            PrismaticAccentToken.AQUA -> Triple(glass.aquaAccent, glass.aquaContainer, glass.onAquaContainer)
            PrismaticAccentToken.LAVENDER -> Triple(glass.lavenderAccent, glass.lavenderContainer, glass.onLavenderContainer)
            PrismaticAccentToken.SKY_BLUE -> Triple(glass.skyBlueAccent, glass.skyBlueContainer, glass.onSkyBlueContainer)
            PrismaticAccentToken.PEACH -> Triple(glass.peachAccent, glass.peachContainer, glass.onPeachContainer)
            PrismaticAccentToken.SLATE -> Triple(glass.slateAccent, glass.slateContainer, glass.onSlateContainer)
        }
    }
}
