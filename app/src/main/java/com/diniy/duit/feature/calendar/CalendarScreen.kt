package com.diniy.duit.feature.calendar

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.diniy.duit.R
import com.diniy.duit.core.ui.components.CandyBubbleCard
import com.diniy.duit.core.ui.components.CandyMintIconButton
import com.diniy.duit.core.ui.components.CandyTone
import com.diniy.duit.core.ui.components.PrismaticBackground
import com.diniy.duit.core.ui.components.PrismaticEmptyStateCard
import com.diniy.duit.core.ui.components.prismaticTween
import com.diniy.duit.core.ui.components.rememberDuitHaptic
import com.diniy.duit.core.ui.components.rememberReducedMotionEnabled
import com.diniy.duit.core.ui.components.rememberSpatialScreenPadding
import com.diniy.duit.core.ui.state.LocalPrivacySettings
import com.diniy.duit.core.ui.state.PrivacyMask
import com.diniy.duit.core.ui.theme.SpatialTheme
import com.diniy.duit.domain.calendar.CalendarEvent
import com.diniy.duit.domain.calendar.CalendarEventStatus
import com.diniy.duit.domain.calendar.CalendarEventType
import com.diniy.duit.domain.model.CommitmentFrequency
import com.diniy.duit.domain.model.MoneyReminder
import com.diniy.duit.domain.model.TransactionType
import androidx.compose.material.icons.rounded.Add
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** The six filterable layers. SUMMARY controls the selected-day totals; the rest are event dots. */
internal enum class CalendarLayer(val label: String, val type: CalendarEventType?) {
    INCOME("Income Dates", CalendarEventType.INCOME_DATE),
    BILLS("Bills", CalendarEventType.BILL),
    TRANSACTIONS("Transactions", CalendarEventType.TRANSACTION),
    SUMMARY("Daily Summary", null),
    GOALS("Goal Deadlines", CalendarEventType.GOAL_DEADLINE),
    REMINDERS("Reminders", CalendarEventType.REMINDER)
}

/** "reminder:<id>:<day>" -> id. */
internal fun reminderIdOf(event: CalendarEvent): Long? =
    if (event.type == CalendarEventType.REMINDER) event.key.split(':').getOrNull(1)?.toLongOrNull() else null

@Composable
private fun CalendarEventType.dotColor(): Color = with(SpatialTheme.colors) {
    when (this@dotColor) {
        CalendarEventType.INCOME_DATE -> income
        CalendarEventType.BILL -> coral
        CalendarEventType.TRANSACTION -> skyBlue
        CalendarEventType.GOAL_DEADLINE -> lavender
        CalendarEventType.REMINDER -> sunny
    }
}

private val MONTH_TITLE = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
private val DAY_TITLE = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)

@Composable
fun CalendarScreen(
    uiState: CalendarUiState,
    onBack: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToday: () -> Unit,
    onSelectDay: (Long) -> Unit,
    onRetry: () -> Unit,
    onSaveReminder: (MoneyReminder?, String, Long, CommitmentFrequency, String, () -> Unit) -> String? = { _, _, _, _, _, _ -> null },
    onDeleteReminder: (Long, () -> Unit) -> Unit = { _, _ -> }
) {
    BackHandler(onBack = onBack)
    val colors = SpatialTheme.colors
    val type = SpatialTheme.typography
    val haptic = rememberDuitHaptic()
    val reducedMotion = rememberReducedMotionEnabled()
    var hidden by rememberSaveable { mutableStateOf(emptyList<String>()) }
    // null = closed, 0 = new reminder, otherwise the id being edited.
    var editingReminderId by rememberSaveable { mutableStateOf<Long?>(null) }
    val visible = CalendarLayer.entries.filter { it.name !in hidden }.toSet()
    val visibleTypes = visible.mapNotNull { it.type }.toSet()
    fun tick(action: () -> Unit) = { haptic(HapticFeedbackType.TextHandleMove); action() }

    PrismaticBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = rememberSpatialScreenPadding(hasFloatingBottomBar = false),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CandyMintIconButton(icon = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", onClick = onBack)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Money Calendar", style = type.screenHeading, color = colors.primaryText, modifier = Modifier.semantics { heading() })
                        Text("Your money days at a glance", style = type.supportingInfo, color = colors.secondaryText)
                    }
                }
            }

            item(key = "month_bar") {
                // Month title leads; prev/next sit together with Today so navigation reads as one control group.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AnimatedContent(
                        targetState = uiState.month,
                        transitionSpec = { fadeIn(prismaticTween(reducedMotion, 180)) togetherWith fadeOut(prismaticTween(reducedMotion, 120)) },
                        label = "calendar_month",
                        modifier = Modifier.weight(1f).padding(start = 4.dp)
                    ) { month ->
                        Text(month.format(MONTH_TITLE), style = type.sectionHeading, color = colors.primaryText, maxLines = 1)
                    }
                    CandyMintIconButton(icon = Icons.Rounded.ChevronLeft, contentDescription = "Previous month", onClick = tick(onPreviousMonth))
                    CandyMintIconButton(icon = Icons.Rounded.ChevronRight, contentDescription = "Next month", onClick = tick(onNextMonth))
                    CalendarChip(label = "Today", selected = false, dotColor = null, onClick = tick(onToday))
                }
            }

            item(key = "filters") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CalendarLayer.entries.forEach { layer ->
                        val on = layer in visible
                        CalendarChip(
                            label = layer.label,
                            selected = on,
                            dotColor = layer.type?.dotColor() ?: colors.sunny,
                            onClick = tick { hidden = if (on) hidden + layer.name else hidden - layer.name }
                        )
                    }
                }
            }

            item(key = "grid") {
                CandyBubbleCard(tone = CandyTone.Neutral, contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                    MonthGrid(uiState, visibleTypes) { day -> haptic(HapticFeedbackType.TextHandleMove); onSelectDay(day) }
                }
            }

            when {
                uiState.errorMessage != null -> item(key = "error") {
                    PrismaticEmptyStateCard(
                        iconRes = R.drawable.ic_candy_art_calendar,
                        title = "Calendar didn't load",
                        message = uiState.errorMessage,
                        actionLabel = "Retry",
                        onActionClick = tick(onRetry)
                    )
                }
                uiState.isLoading -> item(key = "loading") {
                    Text("Loading your month…", style = type.supportingInfo, color = colors.secondaryText, modifier = Modifier.padding(horizontal = 4.dp))
                }
                else -> {
                    if (uiState.isEmpty) item(key = "empty") {
                        PrismaticEmptyStateCard(
                            iconRes = R.drawable.ic_candy_art_calendar,
                            title = "A quiet month",
                            message = "No transactions, salary dates, bills, goal deadlines or reminders in ${uiState.month.format(MONTH_TITLE)}."
                        )
                    }
                    item(key = "day_panel") {
                        DayPanel(
                            uiState, visible, visibleTypes,
                            onAddReminder = tick { editingReminderId = 0L },
                            onOpenReminder = { id -> haptic(HapticFeedbackType.TextHandleMove); editingReminderId = id }
                        )
                    }
                }
            }
        }
    }

    editingReminderId?.let { id ->
        ReminderEditorHost(uiState, id, onClose = { editingReminderId = null }, onSave = onSaveReminder, onDelete = onDeleteReminder)
    }
}

@Composable
private fun ReminderEditorHost(
    uiState: CalendarUiState,
    editingId: Long,
    onClose: () -> Unit,
    onSave: (MoneyReminder?, String, Long, CommitmentFrequency, String, () -> Unit) -> String?,
    onDelete: (Long, () -> Unit) -> Unit
) {
    val existing = uiState.reminders.firstOrNull { it.id == editingId }
    ReminderEditor(existing, defaultDay = uiState.selectedEpochDay, onClose = onClose, onSave = onSave, onDelete = onDelete)
}

@Composable
private fun MonthGrid(uiState: CalendarUiState, visibleTypes: Set<CalendarEventType>, onSelectDay: (Long) -> Unit) {
    val colors = SpatialTheme.colors
    val type = SpatialTheme.typography
    val month: YearMonth = uiState.month
    val leading = month.atDay(1).dayOfWeek.value - DayOfWeek.MONDAY.value // Monday-first weeks
    val cells = List(leading) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    val today = LocalDate.now()

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 2.dp)) {
            DayOfWeek.entries.forEach {
                Text(it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase(Locale.ENGLISH),
                    style = type.caption.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
                    color = colors.secondaryText, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f))
            }
        }
        cells.chunked(7).forEach { week ->
            // Fixed 7 columns with equal square-ish cells, padded weeks keep the grid rectangular.
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (week + List(7 - week.size) { null }).forEach { date ->
                    Box(Modifier.weight(1f).aspectRatio(0.86f), contentAlignment = Alignment.Center) {
                        if (date != null) {
                            val epochDay = date.toEpochDay()
                            val types = uiState.calendar?.eventsByDay?.get(epochDay).orEmpty()
                                .map { it.type }.filter { it in visibleTypes }.distinct().sorted()
                            DayCell(date, types, isSelected = epochDay == uiState.selectedEpochDay, isToday = date == today) { onSelectDay(epochDay) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, types: List<CalendarEventType>, isSelected: Boolean, isToday: Boolean, onClick: () -> Unit) {
    val colors = SpatialTheme.colors
    val isLiquidGlass = com.diniy.duit.core.ui.theme.LocalVisualTheme.current ==
        com.diniy.duit.domain.model.VisualTheme.LIQUID_GLASS
    val selectedFill = if (isLiquidGlass) colors.primaryAction else colors.coral
    val selectedInk = if (isLiquidGlass) colors.onPrimaryAction else colors.onCoral
    val fill by animateColorAsState(
        if (isSelected) selectedFill else Color.Transparent,
        prismaticTween(rememberReducedMotionEnabled(), 160),
        label = "day_fill"
    )
    val label = buildString {
        append(date.format(DAY_TITLE))
        if (isToday) append(", today")
        if (types.isNotEmpty()) append(", ").append(types.size).append(if (types.size == 1) " kind of event" else " kinds of events")
    }
    // Selected = solid accent; today = soft accent tint + accent number; other days stay quiet.
    val todayTint = selectedFill.copy(alpha = if (colors.isDark) 0.22f else 0.13f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isToday && !isSelected) todayTint else fill)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label; selected = isSelected }
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = SpatialTheme.typography.supportingInfo.copy(fontWeight = if (isSelected || isToday) FontWeight.ExtraBold else FontWeight.Medium),
            color = when {
                isSelected -> selectedInk
                isToday -> selectedFill
                else -> colors.primaryText
            }
        )
        Spacer(Modifier.height(3.dp))
        // Up to 3 event dots keep every cell the same visual weight; the label carries the full count.
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(5.dp)) {
            types.take(3).forEach { t ->
                Box(Modifier.size(5.dp).clip(CircleShape).background(if (isSelected) selectedInk else t.dotColor()))
            }
        }
    }
}

@Composable
private fun DayPanel(
    uiState: CalendarUiState,
    visible: Set<CalendarLayer>,
    visibleTypes: Set<CalendarEventType>,
    onAddReminder: () -> Unit,
    onOpenReminder: (Long) -> Unit
) {
    val colors = SpatialTheme.colors
    val type = SpatialTheme.typography
    val privacy = LocalPrivacySettings.current
    val events = uiState.selectedEvents.filter { it.type in visibleTypes }.sortedBy { it.type }
    val summary = uiState.selectedSummary.takeIf { CalendarLayer.SUMMARY in visible }

    CandyBubbleCard(tone = CandyTone.Peach) {
        Text(LocalDate.ofEpochDay(uiState.selectedEpochDay).format(DAY_TITLE), style = type.cardTitle, color = colors.primaryText,
            modifier = Modifier.semantics { heading() })
        if (uiState.isSamplePreview) Text("Sample data", style = type.caption, color = colors.secondaryText)
        Spacer(Modifier.height(10.dp))
        if (summary != null) {
            // Recorded money only: expected salary, upcoming bills and transfers never count here.
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SummaryFigure("Received", PrivacyMask.formatAmount(summary.incomeSen, privacy), colors.income)
                SummaryFigure("Spent", PrivacyMask.formatAmount(summary.expenseSen, privacy), colors.expense)
                SummaryFigure("Net", PrivacyMask.formatAmount(summary.netSen, privacy, includePlusSign = true), colors.primaryText)
            }
            Spacer(Modifier.height(12.dp))
        }
        if (events.isEmpty() && summary == null) {
            Text("Nothing on this day.", style = type.supportingInfo, color = colors.secondaryText)
        }
        events.forEach { event -> EventRow(event, onClick = reminderIdOf(event)?.let { id -> { onOpenReminder(id) } }) }
        uiState.reminderMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = type.caption, color = colors.error)
        }
        if (!uiState.isSamplePreview) {
            Spacer(Modifier.height(12.dp))
            com.diniy.duit.core.ui.components.SpatialGlassButton(
                text = "Add Reminder",
                onClick = onAddReminder,
                variant = com.diniy.duit.core.ui.components.SpatialButtonVariant.SecondaryGlass,
                leadingIcon = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Add, contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SummaryFigure(label: String, value: String, color: Color) {
    Column {
        Text(label, style = SpatialTheme.typography.caption, color = SpatialTheme.colors.secondaryText)
        Text(value, style = SpatialTheme.typography.transactionAmount, color = color)
    }
}

@Composable
private fun EventRow(event: CalendarEvent, onClick: (() -> Unit)? = null) {
    val colors = SpatialTheme.colors
    val type = SpatialTheme.typography
    val privacy = LocalPrivacySettings.current
    val status = when {
        event.transactionType == TransactionType.TRANSFER -> "Transfer · not counted"
        event.transactionType == TransactionType.INCOME -> "Income"
        event.transactionType == TransactionType.EXPENSE -> "Expense"
        else -> when (event.status) {
            CalendarEventStatus.EXPECTED -> "Expected salary · not received yet"
            CalendarEventStatus.RECEIVED -> "Salary received"
            CalendarEventStatus.UPCOMING -> "Upcoming bill · not paid yet"
            CalendarEventStatus.PAID -> "Bill paid"
            CalendarEventStatus.DUE -> "Goal deadline"
            CalendarEventStatus.COMPLETED -> "Goal completed"
            CalendarEventStatus.RECORDED -> ""
            CalendarEventStatus.REMINDER -> "Reminder"
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = "Edit reminder", role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = if (onClick != null) 48.dp else 0.dp)
            .padding(vertical = 6.dp)
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(event.type.dotColor()))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(event.title, style = type.transactionDescription, color = colors.primaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (status.isNotEmpty()) Text(status, style = type.caption, color = colors.secondaryText)
        }
        event.amountSen?.let {
            Text(PrivacyMask.formatAmount(it, privacy), style = type.transactionAmount, color = colors.primaryText)
        }
    }
}

/** Compact filter/action chip for the calendar: 32dp visual pill inside a 48dp accessible target. */
@Composable
private fun CalendarChip(label: String, selected: Boolean, dotColor: Color?, onClick: () -> Unit) {
    val colors = SpatialTheme.colors
    val accent = dotColor ?: colors.primaryText
    val shape = RoundedCornerShape(50)
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .semantics(mergeDescendants = true) { this.selected = selected }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .heightIn(min = 32.dp)
                .clip(shape)
                .background(if (selected) accent.copy(alpha = if (colors.isDark) 0.24f else 0.14f) else colors.baseSurface)
                .border(1.dp, if (selected) accent.copy(alpha = 0.55f) else colors.divider, shape)
                .indication(interactionSource, LocalIndication.current)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            if (dotColor != null) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(if (selected) dotColor else dotColor.copy(alpha = 0.45f)))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                label,
                style = SpatialTheme.typography.caption.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
                color = if (selected) colors.primaryText else colors.secondaryText,
                maxLines = 1
            )
        }
    }
}
