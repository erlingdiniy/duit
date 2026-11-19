package com.diniy.duit.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.EventNote
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.ui.components.CandyBubbleCard
import com.diniy.duit.core.ui.components.CandyIconBlob
import com.diniy.duit.core.ui.components.CandyTone
import com.diniy.duit.core.ui.components.PrismaticButton
import com.diniy.duit.core.ui.components.PrismaticButtonVariant
import com.diniy.duit.core.ui.theme.SpatialTheme

/** One short guide. [link] opens a Settings destination; [opensFeedback] opens the feedback form. */
internal data class HelpTopic(
    val title: String,
    val icon: ImageVector,
    val tone: CandyTone,
    val answer: String,
    val link: Pair<String, SettingsHubRoute>? = null,
    val opensFeedback: Boolean = false
)

/** Every answer describes behaviour that exists in this build (see the matching feature code and tests). */
internal val HelpTopics = listOf(
    HelpTopic(
        "Getting Started", Icons.Rounded.AccountBalanceWallet, CandyTone.Mint,
        "The first time you open duit., a short tour shows the basics, then you add your first wallet. " +
            "You can tap \"Set up later\" if you'd rather restore a backup first. Add more wallets anytime from Home → Wallets → Add wallet."
    ),
    HelpTopic(
        "Transactions", Icons.Rounded.SwapHoriz, CandyTone.Coral,
        "Tap the coral + button and choose Expense, Income or Transfer. Pick the wallet, amount and category, then save. " +
            "Transfers move money between your own wallets and don't count as income or spending. " +
            "Open a transaction in Activity to edit or delete it; balances update automatically."
    ),
    HelpTopic(
        "Safe to Spend", Icons.Rounded.Calculate, CandyTone.Sunny,
        "Safe to Spend starts with the money in your everyday wallets. It leaves out savings wallets and wallets you've excluded, " +
            "sets aside money allocated to savings goals, and sets aside the bills, instalments and debt repayments due in your current salary cycle " +
            "(or this calendar month if you haven't set a cycle). Commission that hasn't been paid to you yet is never counted."
    ),
    HelpTopic(
        "Savings Goals", Icons.Rounded.Savings, CandyTone.Mint,
        "Go to Planning → Goals and tap + New Goal to set a target and an optional date. Tap Add Funds to set money aside from a wallet. " +
            "The money stays in that wallet, but it's protected so Safe to Spend won't count it."
    ),
    HelpTopic(
        "Budgets & Commitments", Icons.Rounded.EventNote, CandyTone.Peach,
        "Planning → Budgets: set a limit for everything or one category, per month or salary cycle. You'll be warned at 80% and 100%. " +
            "Planning → Bills: add bills with due dates and repeats, plus PayLater plans and debts. Record a payment when you pay; a repeating bill then moves to its next date."
    ),
    HelpTopic(
        "Backup & Restore", Icons.Rounded.Backup, CandyTone.Sky,
        "Create a backup file protected by a password you choose (encrypted with AES-256) and save it wherever you like. " +
            "To restore, pick the file and enter its password. If anything goes wrong, your current data stays as it was. " +
            "duit. can't recover a forgotten backup password, so keep it safe.",
        link = "Open Backup & Restore" to SettingsHubRoute.BACKUP_RESTORE
    ),
    HelpTopic(
        "Security & Privacy", Icons.Rounded.Shield, CandyTone.Coral,
        "Set a 6-digit PIN in Settings → Security and add your fingerprint if your phone supports it. Privacy Mode hides amounts on screen. " +
            "Your records are stored on this phone, with no account or cloud sync.",
        link = "Privacy & Your Data" to SettingsHubRoute.PRIVACY_DATA
    ),
    HelpTopic(
        "Optional Features", Icons.Rounded.Tune, CandyTone.Sunny,
        "Commission Tracking is for people paid by commission. Turn it on in Settings → Optional Features → Commission Tracking. " +
            "It's off by default for new users. Turning it off only hides the commission tools; your records are kept."
    ),
    HelpTopic(
        "Updates", Icons.Rounded.SystemUpdate, CandyTone.Mint,
        "duit. checks GitHub for new versions when you open it, or tap Settings → About → Check for updates. " +
            "You'll see what's new first. Each download is checked before Android asks you to confirm the install, and your data is kept.",
        link = "Open About" to SettingsHubRoute.ABOUT
    ),
    HelpTopic(
        "Help & Feedback", Icons.Rounded.Forum, CandyTone.Peach,
        "Choose Report a Bug, Suggest a Feature or General Feedback, write at least 10 characters, and add your email only if you'd like a reply. " +
            "Please don't include PINs, passwords or financial details.",
        opensFeedback = true
    )
)

/** Settings > Help Centre (public edition): short, collapsible guides. */
@Composable
internal fun HelpCentreScreen(onBack: () -> Unit, onOpenRoute: (SettingsHubRoute) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf<Int?>(0) }
    var showFeedback by rememberSaveable { mutableStateOf(false) }
    if (showFeedback) com.diniy.duit.feedback.HelpFeedbackScreen(onClose = { showFeedback = false })
    SettingsPageScaffold(
        title = "Help Centre",
        subtitle = "Quick answers about using duit.",
        onBack = onBack
    ) {
        HelpTopics.forEachIndexed { index, topic ->
            item(key = "help_$index") {
                HelpTopicCard(
                    topic = topic,
                    expanded = expanded == index,
                    onToggle = { expanded = if (expanded == index) null else index },
                    onOpenRoute = onOpenRoute,
                    onOpenFeedback = { showFeedback = true }
                )
            }
        }
    }
}

@Composable
private fun HelpTopicCard(
    topic: HelpTopic,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenRoute: (SettingsHubRoute) -> Unit,
    onOpenFeedback: () -> Unit
) {
    val colors = SpatialTheme.colors
    val type = SpatialTheme.typography
    CandyBubbleCard(tone = topic.tone, contentPadding = PaddingValues(0.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = if (expanded) "Collapse" else "Expand", onClick = onToggle)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CandyIconBlob(icon = topic.icon, tone = topic.tone, size = 40.dp)
                Text(topic.title, style = type.cardTitle, color = colors.primaryText, modifier = Modifier.weight(1f).semantics { heading() })
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, tint = colors.primaryText)
            }
            AnimatedVisibility(visible = expanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Column(Modifier.padding(top = 12.dp)) {
                    Text(topic.answer, style = type.supportingInfo, color = colors.primaryText)
                    val label = topic.link?.first ?: if (topic.opensFeedback && com.diniy.duit.BuildConfig.FEEDBACK_URL.isNotEmpty()) "Send feedback" else null
                    if (label != null) {
                        Spacer(Modifier.height(10.dp))
                        PrismaticButton(
                            text = label,
                            onClick = { topic.link?.let { onOpenRoute(it.second) } ?: onOpenFeedback() },
                            variant = PrismaticButtonVariant.Secondary,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
