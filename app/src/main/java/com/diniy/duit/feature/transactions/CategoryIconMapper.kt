package com.diniy.duit.feature.transactions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.WorkOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.diniy.duit.core.ui.theme.LocalPrismaticGlass
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.TransactionType

object CategoryIconMapper {

    val selectableIcons: List<Pair<String, String>> = listOf(
        "food" to "Dining",
        "groceries" to "Groceries",
        "transport" to "Transport",
        "bills" to "Bills",
        "shopping" to "Shopping",
        "health" to "Health",
        "salary" to "Salary",
        "commission" to "Commission",
        "freelance" to "Freelance",
        "bonus" to "Bonus",
        "refund" to "Refund",
        "custom" to "General"
    )

    fun iconFor(iconKey: String?, type: TransactionType = TransactionType.EXPENSE): ImageVector {
        if (type == TransactionType.TRANSFER) return Icons.Rounded.SwapHoriz
        return when (iconKey) {
            "food" -> Icons.Rounded.Restaurant
            "groceries" -> Icons.Rounded.ShoppingCart
            "transport" -> Icons.Rounded.DirectionsCar
            "bills" -> Icons.AutoMirrored.Rounded.ReceiptLong
            "shopping" -> Icons.Rounded.ShoppingBag
            "health" -> Icons.Rounded.MedicalServices
            "salary" -> Icons.Rounded.Payments
            "commission" -> Icons.AutoMirrored.Rounded.TrendingUp
            "freelance" -> Icons.Rounded.WorkOutline
            "bonus" -> Icons.Rounded.CardGiftcard
            "refund" -> Icons.Rounded.Replay
            "wallet" -> Icons.Rounded.AccountBalanceWallet
            else -> Icons.Rounded.Category
        }
    }

    fun inferIconKeyFromName(categoryName: String, type: TransactionType): String {
        if (type == TransactionType.TRANSFER) return "transfer"
        val lower = categoryName.lowercase()
        return when {
            "food" in lower || "dining" in lower -> "food"
            "grocer" in lower -> "groceries"
            "transport" in lower || "fuel" in lower || "grab" in lower -> "transport"
            "bill" in lower || "util" in lower || "rent" in lower -> "bills"
            "shop" in lower -> "shopping"
            "health" in lower || "med" in lower -> "health"
            "salary" in lower -> "salary"
            "commiss" in lower -> "commission"
            "freelance" in lower -> "freelance"
            "bonus" in lower -> "bonus"
            "refund" in lower -> "refund"
            else -> "custom"
        }
    }

    @Composable
    fun accentContainerColors(token: PrismaticAccentToken): Pair<Color, Color> {
        val glass = LocalPrismaticGlass.current
        return when (token) {
            PrismaticAccentToken.AQUA -> glass.aquaContainer to glass.onAquaContainer
            PrismaticAccentToken.LAVENDER -> glass.lavenderContainer to glass.onLavenderContainer
            PrismaticAccentToken.SKY_BLUE -> glass.skyBlueContainer to glass.onSkyBlueContainer
            PrismaticAccentToken.PEACH -> glass.peachContainer to glass.onPeachContainer
            PrismaticAccentToken.SLATE -> glass.glassSurfaceMuted to glass.onLavenderContainer
        }
    }
}
