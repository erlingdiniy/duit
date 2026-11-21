package com.diniy.duit.feature.savings.challenges

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.diniy.duit.R
import com.diniy.duit.core.currency.MyrFormatter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import com.diniy.duit.core.ui.components.CandyBubbleCard
import com.diniy.duit.core.ui.components.CandyIconBlob
import com.diniy.duit.core.ui.components.CandyDialog
import com.diniy.duit.core.ui.components.CandyFullScreenForm
import com.diniy.duit.core.ui.components.CandyTone
import com.diniy.duit.core.ui.components.PrismaticEmptyStateCard
import com.diniy.duit.core.ui.components.PrismaticTextField
import com.diniy.duit.core.ui.components.SpatialButtonVariant
import com.diniy.duit.core.ui.components.SpatialGlassButton
import com.diniy.duit.core.ui.components.SpatialGlassPill
import com.diniy.duit.core.ui.components.prismaticTween
import com.diniy.duit.core.ui.components.rememberReducedMotionEnabled
import com.diniy.duit.core.ui.state.LocalPrivacySettings
import com.diniy.duit.core.ui.state.PrivacyMask
import com.diniy.duit.core.ui.theme.SpatialTheme
import com.diniy.duit.core.util.DateTimeFormatters
import com.diniy.duit.domain.challenge.ChallengeCalculator
import com.diniy.duit.domain.challenge.ChallengeType
import com.diniy.duit.domain.challenge.GardenStage
import com.diniy.duit.domain.challenge.SavingsChallenge
import com.diniy.duit.domain.model.SavingsGoal

internal fun ChallengeType.label() = when (this) {
    ChallengeType.WEEK_52 -> "52-Week"
    ChallengeType.DAILY -> "Daily"
    ChallengeType.CUSTOM -> "Custom"
    ChallengeType.RANDOM -> "Random"
}

private fun ChallengeType.roundName() = when (this) {
    ChallengeType.WEEK_52 -> "Week"
    ChallengeType.DAILY -> "Day"
    else -> "Round"
}

/** One consistent vector icon per challenge type. */
internal fun ChallengeType.icon(): ImageVector = when (this) {
    ChallengeType.WEEK_52 -> Icons.Rounded.CalendarMonth
    ChallengeType.DAILY -> Icons.Rounded.Today
    ChallengeType.CUSTOM -> Icons.Rounded.Tune
    ChallengeType.RANDOM -> Icons.Rounded.Shuffle
}

internal fun GardenStage.label() = when (this) {
    GardenStage.SEED -> "Seed"
    GardenStage.SPROUT -> "Sprout · 25%"
    GardenStage.BUD -> "Bud · 50%"
    GardenStage.BLOOM -> "Bloom · 75%"
    GardenStage.HARVEST -> "Harvest · 100%"
}

private val STICKER_STAGES = GardenStage.entries - GardenStage.SEED

@Composable
private fun money(sen: Long) = PrivacyMask.formatAmount(sen, LocalPrivacySettings.current)

/** The Challenges view inside Planning → Savings. Progress comes from each goal's real savings. */
@Composable
fun SavingsChallengesSection(
    state: ChallengesUiState,
    isPreview: Boolean,
    onCreate: () -> Unit,
    onOpen: (Long) -> Unit,
    onRetry: () -> Unit
) {
    val colors = SpatialTheme.colors
    val type = SpatialTheme.typography
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            state.isLoading -> Text("Loading your challenges…", style = type.supportingInfo, color = colors.secondaryText)
            state.errorMessage != null -> PrismaticEmptyStateCard(
                iconRes = R.drawable.ic_solar_planning, title = "Challenges didn't load", message = state.errorMessage,
                actionLabel = "Retry", onActionClick = onRetry
            )
            state.items.isEmpty() -> PrismaticEmptyStateCard(
                iconRes = R.drawable.ic_solar_planning,
                title = "Grow a savings garden",
                message = if (state.activeGoals.isEmpty()) "Create a savings goal first, then turn it into a fun challenge."
                else "Pick a goal and save a little at a time. Every real deposit helps your garden grow.",
                actionLabel = if (!isPreview && state.activeGoals.isNotEmpty()) "Start a Challenge" else null,
                onActionClick = if (!isPreview && state.activeGoals.isNotEmpty()) onCreate else null
            )
            else -> {
                state.items.forEach { item -> ChallengeCard(item, onClick = { onOpen(item.challenge.id) }) }
                if (!isPreview && state.activeGoals.isNotEmpty()) {
                    SpatialGlassButton(text = "Start a Challenge", onClick = onCreate, variant = SpatialButtonVariant.SecondaryGlass,
                        modifier = Modifier.fillMaxWidth())
                }
            }
        }
        state.actionMessage?.let { Text(it, style = type.caption, color = colors.error) }
    }
}

@Composable
private fun ChallengeCard(item: ChallengeItem, onClick: () -> Unit) {
    val colors = SpatialTheme.colors
    val type = SpatialTheme.typography
    val p = item.progress
    CandyBubbleCard(tone = CandyTone.Mint, onClick = onClick, onClickLabel = "Open challenge") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CandyIconBlob(icon = item.challenge.type.icon(), tone = CandyTone.Mint, size = 44.dp,
                modifier = Modifier.semantics { contentDescription = "${item.challenge.type.label()} challenge" })
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${item.challenge.type.label()} Challenge", style = type.cardTitle, color = colors.primaryText)
                Text("For ${item.goal.name}", style = type.supportingInfo, color = colors.secondaryText)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GardenIllustration(p.stage, 44.dp)
                Text("${p.percent}%", style = type.transactionAmount, color = colors.primaryText)
            }
        }
        Spacer(Modifier.height(10.dp))
        GardenBar(p.percent)
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                item.challenge.isPaused -> "Paused · take your time"
                p.nextSlot == null -> "Garden complete!"
                else -> "Next: ${item.challenge.type.roundName()} ${p.nextSlot.index} · ${money(p.nextSlot.amountSen)}"
            },
            style = type.caption, color = colors.secondaryText
        )
        if (item.stickers.isNotEmpty()) {
            Text("${item.stickers.size} of ${STICKER_STAGES.size} stickers collected", style = type.caption, color = colors.secondaryText)
        }
    }
}

@Composable
private fun GardenBar(percent: Int) {
    val colors = SpatialTheme.colors
    val fraction by animateFloatAsState(percent.coerceIn(0, 100) / 100f, prismaticTween(rememberReducedMotionEnabled(), 400), label = "garden")
    Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(colors.glassSurfaceSubtle)) {
        Box(Modifier.fillMaxWidth(fraction).height(10.dp).clip(RoundedCornerShape(5.dp)).background(colors.income))
    }
}

/** Challenge detail. "Save" opens the existing goal deposit flow, so only real money counts. */
@Composable
fun ChallengeDetail(
    item: ChallengeItem,
    safeToSpendSen: Long,
    isPreview: Boolean,
    onClose: () -> Unit,
    onSaveToGoal: (Long) -> Unit,
    onSkip: (Int) -> Unit,
    onUnskip: (Int) -> Unit,
    onSetPaused: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = SpatialTheme.colors
    val type = SpatialTheme.typography
    val p = item.progress
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    CandyFullScreenForm(title = "${item.challenge.type.label()} Challenge", subtitle = "For ${item.goal.name}", onDismissRequest = onClose) {
        CandyBubbleCard(tone = CandyTone.Mint) {
            GardenIllustration(p.stage, 120.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text(p.stage.label(), style = type.cardTitle, color = colors.primaryText, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(10.dp))
            GardenBar(p.percent)
            Spacer(Modifier.height(6.dp))
            Text("${money(p.savedSen)} of ${money(p.targetSen)} saved · ${p.percent}%", style = type.supportingInfo, color = colors.secondaryText)
            Text("From real deposits into ${item.goal.name} since ${DateTimeFormatters.formatEpochDay(item.challenge.startEpochDay)}.",
                style = type.caption, color = colors.secondaryText)
        }
        Spacer(Modifier.height(16.dp))
        Text("Sticker Collection", style = type.sectionHeading, color = colors.primaryText, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            STICKER_STAGES.forEach { stage ->
                val earned = stage in item.stickers
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(if (earned) colors.sunny.copy(alpha = 0.35f) else colors.glassSurfaceSubtle),
                    contentAlignment = Alignment.Center
                ) {
                    GardenIllustration(stage, 40.dp, muted = !earned,
                        description = "${stage.label()} sticker, ${if (earned) "earned" else "not earned yet"}")
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        val next = p.nextSlot
        when {
            item.challenge.isPaused -> Text("Paused. Your garden waits for you, no rush.", style = type.supportingInfo, color = colors.secondaryText)
            next == null -> Text("Every round is done. Lovely work!", style = type.supportingInfo, color = colors.secondaryText)
            else -> {
                Text("Next: ${item.challenge.type.roundName()} ${next.index} · ${money(next.amountSen)}", style = type.cardTitle, color = colors.primaryText)
                Text("Planned for ${DateTimeFormatters.formatEpochDay(next.dueEpochDay)}", style = type.caption, color = colors.secondaryText)
                if (!ChallengeCalculator.isComfortable(next.amountSen, safeToSpendSen)) {
                    Spacer(Modifier.height(6.dp))
                    Text("Money looks a bit tight right now. It's okay to skip this one or save a smaller amount.",
                        style = type.supportingInfo, color = colors.secondaryText)
                }
                if (!isPreview) {
                    Spacer(Modifier.height(10.dp))
                    SpatialGlassButton(text = "Save to ${item.goal.name}", onClick = { onSaveToGoal(item.goal.id) }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    SpatialGlassButton(text = "Skip This Round", onClick = { onSkip(next.index) }, variant = SpatialButtonVariant.Ghost,
                        modifier = Modifier.fillMaxWidth())
                }
            }
        }
        if (item.skipped.isNotEmpty() && !isPreview) {
            Spacer(Modifier.height(12.dp))
            Text("Skipped rounds", style = type.inputLabel, color = colors.secondaryText)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item.skipped.sorted().forEach { idx ->
                    SpatialGlassPill(label = "${item.challenge.type.roundName()} $idx · Undo", onClick = { onUnskip(idx) })
                }
            }
        }
        if (!isPreview) {
            Spacer(Modifier.height(20.dp))
            SpatialGlassButton(
                text = if (item.challenge.isPaused) "Resume Challenge" else "Pause Challenge",
                onClick = { onSetPaused(!item.challenge.isPaused) },
                variant = SpatialButtonVariant.SecondaryGlass, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            SpatialGlassButton(text = "Edit Challenge", onClick = onEdit, variant = SpatialButtonVariant.SecondaryGlass, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            SpatialGlassButton(text = "Delete Challenge", onClick = { confirmDelete = true }, variant = SpatialButtonVariant.Destructive,
                modifier = Modifier.fillMaxWidth())
        }
    }
    if (confirmDelete) {
        CandyDialog(onDismiss = { confirmDelete = false }) {
            Text("Delete this challenge?", style = type.sectionHeading, color = colors.primaryText, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(8.dp))
            Text("Your goal and the money in it stay exactly as they are. Earned stickers stay in your collection.",
                style = type.supportingInfo, color = colors.secondaryText)
            Spacer(Modifier.height(20.dp))
            SpatialGlassButton(text = "Delete Challenge", onClick = { confirmDelete = false; onDelete() },
                variant = SpatialButtonVariant.Destructive, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            SpatialGlassButton(text = "Keep It", onClick = { confirmDelete = false }, variant = SpatialButtonVariant.Ghost, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Create or edit a challenge linked to an existing active goal. */
@Composable
fun ChallengeEditor(
    existing: SavingsChallenge?,
    activeGoals: List<SavingsGoal>,
    onClose: () -> Unit,
    onSave: (SavingsChallenge?, Long?, ChallengeType, Long?, Int?, Int?, () -> Unit) -> String?
) {
    val colors = SpatialTheme.colors
    val type = SpatialTheme.typography
    var goalId by rememberSaveable { mutableStateOf(existing?.goalId ?: activeGoals.singleOrNull()?.id) }
    var kind by rememberSaveable { mutableStateOf(existing?.type ?: ChallengeType.WEEK_52) }
    var amount by rememberSaveable { mutableStateOf(existing?.let { MyrFormatter.formatInputValue(it.baseAmountSen) } ?: "") }
    var rounds by rememberSaveable { mutableStateOf(existing?.slotCount?.toString() ?: "30") }
    var every by rememberSaveable { mutableStateOf(existing?.intervalDays?.toString() ?: "7") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    CandyFullScreenForm(
        title = if (existing == null) "New Challenge" else "Edit Challenge",
        subtitle = "A fun plan for a goal. Only real deposits count.",
        onDismissRequest = onClose
    ) {
        Text("Savings goal", style = type.inputLabel, color = colors.secondaryText)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            activeGoals.forEach { g -> SpatialGlassPill(label = g.name, selected = g.id == goalId, onClick = { goalId = g.id; error = null }) }
        }
        Spacer(Modifier.height(16.dp))
        Text("Challenge type", style = type.inputLabel, color = colors.secondaryText)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChallengeType.entries.forEach { t -> SpatialGlassPill(label = t.label(), selected = t == kind, onClick = { kind = t; error = null }) }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            when (kind) {
                ChallengeType.WEEK_52 -> "Week 1 saves your amount, week 2 saves double, up to 52 weeks."
                ChallengeType.DAILY -> "Save the same small amount every day."
                ChallengeType.CUSTOM -> "Save the same amount on your own schedule."
                ChallengeType.RANDOM -> "Surprise amounts in a shuffled order, on your schedule."
            },
            style = type.caption, color = colors.secondaryText
        )
        Spacer(Modifier.height(16.dp))
        PrismaticTextField(
            value = amount, onValueChange = { amount = it; error = null },
            label = if (kind == ChallengeType.WEEK_52 || kind == ChallengeType.RANDOM) "Starting amount" else "Amount each round",
            prefixText = "RM ", placeholder = "1.00",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        if (kind != ChallengeType.WEEK_52) {
            Spacer(Modifier.height(12.dp))
            PrismaticTextField(value = rounds, onValueChange = { rounds = it.filter(Char::isDigit); error = null },
                label = "Number of rounds", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
        if (kind == ChallengeType.CUSTOM || kind == ChallengeType.RANDOM) {
            Spacer(Modifier.height(12.dp))
            PrismaticTextField(value = every, onValueChange = { every = it.filter(Char::isDigit); error = null },
                label = "Every how many days", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
        error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = type.caption, color = colors.error)
        }
        Spacer(Modifier.height(24.dp))
        SpatialGlassButton(
            text = if (existing == null) "Start Challenge" else "Save Changes",
            onClick = {
                error = onSave(existing, goalId, kind, MyrFormatter.parseToSen(amount), rounds.toIntOrNull(), every.toIntOrNull(), onClose)
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
