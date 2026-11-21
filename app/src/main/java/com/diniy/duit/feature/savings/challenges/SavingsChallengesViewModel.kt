package com.diniy.duit.feature.savings.challenges

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.diniy.duit.data.repository.SavingsRepository
import com.diniy.duit.domain.challenge.ChallengeCalculator
import com.diniy.duit.domain.challenge.ChallengeProgress
import com.diniy.duit.domain.challenge.ChallengeType
import com.diniy.duit.domain.challenge.EarnedSticker
import com.diniy.duit.domain.challenge.GardenStage
import com.diniy.duit.domain.challenge.SavingsChallenge
import com.diniy.duit.domain.model.SavingsGoal
import com.diniy.duit.domain.model.SavingsGoalStatus
import com.diniy.duit.domain.model.SavingsMovement
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChallengeItem(
    val challenge: SavingsChallenge,
    val goal: SavingsGoal,
    val progress: ChallengeProgress,
    val skipped: Set<Int>,
    val stickers: Set<GardenStage>
)

data class ChallengesUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val items: List<ChallengeItem> = emptyList(),
    /** Goals a new challenge can be linked to. */
    val activeGoals: List<SavingsGoal> = emptyList(),
    val actionMessage: String? = null
)

/** What Planning needs to show Challenges (public edition only). */
data class SavingsChallengesBinding(
    val state: ChallengesUiState,
    val safeToSpendSen: Long,
    val onSave: (SavingsChallenge?, Long?, ChallengeType, Long?, Int?, Int?, () -> Unit) -> String?,
    val onSetPaused: (SavingsChallenge, Boolean) -> Unit,
    val onSetSkipped: (Long, Int, Boolean) -> Unit,
    val onDelete: (Long, () -> Unit) -> Unit,
    val onRetry: () -> Unit
)

/** Validation for the challenge form. Amounts are sen; counts and intervals follow [ChallengeCalculator]. */
object ChallengeRules {
    fun error(goalId: Long?, baseAmountSen: Long?, type: ChallengeType, slotCount: Int?, intervalDays: Int?): String? = when {
        goalId == null -> "Pick a savings goal for this challenge."
        baseAmountSen == null || baseAmountSen <= 0L -> "Enter an amount more than RM0."
        type != ChallengeType.WEEK_52 && (slotCount == null || slotCount !in 1..ChallengeCalculator.MAX_SLOTS) ->
            "Choose between 1 and ${ChallengeCalculator.MAX_SLOTS} rounds."
        (type == ChallengeType.CUSTOM || type == ChallengeType.RANDOM) && (intervalDays == null || intervalDays !in 1..365) ->
            "Choose every 1 to 365 days."
        else -> null
    }
}

/**
 * Savings challenges are plans on existing goals. Progress always comes from the goal's real savings
 * movements, so withdrawals lower it and nothing is counted twice. This ViewModel never moves money.
 */
class SavingsChallengesViewModel(
    challenges: Flow<List<SavingsChallenge>>,
    skippedSlots: Flow<Map<Long, Set<Int>>>,
    stickers: Flow<List<EarnedSticker>>,
    goals: Flow<List<SavingsGoal>>,
    movements: Flow<List<SavingsMovement>>,
    private val saveChallenge: suspend (SavingsChallenge) -> Long,
    private val deleteChallengeById: suspend (Long) -> Unit,
    private val setSlotSkipped: suspend (Long, Int, Boolean) -> Unit,
    private val recordStickers: suspend (Long, Set<GardenStage>) -> Unit,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val today: () -> LocalDate = { LocalDate.now(zone) },
    private val newSeed: () -> Long = { System.nanoTime() }
) : ViewModel() {

    private val actionMessage = MutableStateFlow<String?>(null)
    private val reloads = MutableStateFlow(0)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ChallengesUiState> = reloads.flatMapLatest { combine(
        combine(challenges, skippedSlots, stickers) { c, s, st -> Triple(c, s, st) },
        goals,
        movements,
        actionMessage
    ) { (list, skipped, earned), goalList, moves, message ->
        val goalsById = goalList.associateBy { it.id }
        val earnedById = earned.groupBy({ it.challengeId }, { it.stage }).mapValues { it.value.toSet() }
        val items = list.mapNotNull { c ->
            val goal = goalsById[c.goalId] ?: return@mapNotNull null
            val skips = skipped[c.id].orEmpty()
            ChallengeItem(c, goal, ChallengeCalculator.progress(c, moves, skips, zone), skips, earnedById[c.id].orEmpty())
        }
        ChallengesUiState(
            isLoading = false,
            items = items,
            activeGoals = goalList.filter { it.status == SavingsGoalStatus.ACTIVE },
            actionMessage = message
        )
    }
        .catch { emit(ChallengesUiState(isLoading = false, errorMessage = "Couldn't load your challenges. Please try again.")) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChallengesUiState())

    init {
        // Stickers are history: record each newly reached stage once; later drops never remove them.
        viewModelScope.launch {
            uiState.collect { state ->
                state.items.forEach { item ->
                    val fresh = ChallengeCalculator.newlyEarnedStickers(item.progress.stage, item.stickers)
                    if (fresh.isNotEmpty()) runCatching { recordStickers(item.challenge.id, fresh) }
                }
            }
        }
    }

    /** Returns a validation error, or null when the save was started. */
    fun save(
        existing: SavingsChallenge?,
        goalId: Long?,
        type: ChallengeType,
        baseAmountSen: Long?,
        slotCount: Int?,
        intervalDays: Int?,
        onSaved: () -> Unit = {}
    ): String? {
        ChallengeRules.error(goalId, baseAmountSen, type, slotCount, intervalDays)?.let { return it }
        val challenge = SavingsChallenge(
            id = existing?.id ?: 0L,
            goalId = goalId!!,
            type = type,
            baseAmountSen = baseAmountSen!!,
            startEpochDay = existing?.startEpochDay ?: today().toEpochDay(),
            slotCount = if (type == ChallengeType.WEEK_52) 52 else slotCount!!,
            intervalDays = when (type) {
                ChallengeType.WEEK_52 -> 7
                ChallengeType.DAILY -> 1
                else -> intervalDays!!
            },
            randomSeed = existing?.randomSeed?.takeIf { existing.type == type } ?: newSeed(),
            isPaused = existing?.isPaused ?: false
        )
        launchAction("Couldn't save the challenge. Please try again.") { saveChallenge(challenge); onSaved() }
        return null
    }

    fun setPaused(challenge: SavingsChallenge, paused: Boolean) =
        launchAction("Couldn't update the challenge. Please try again.") { saveChallenge(challenge.copy(isPaused = paused)) }

    /** Skipping removes that round from the target: nothing to catch up on later. */
    fun setSkipped(challengeId: Long, slotIndex: Int, skipped: Boolean) =
        launchAction("Couldn't update the challenge. Please try again.") { setSlotSkipped(challengeId, slotIndex, skipped) }

    fun delete(challengeId: Long, onDeleted: () -> Unit = {}) =
        launchAction("Couldn't delete the challenge. Please try again.") { deleteChallengeById(challengeId); onDeleted() }

    fun retry() {
        reloads.value++
    }

    fun dismissMessage() {
        actionMessage.value = null
    }

    private fun launchAction(failure: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { actionMessage.value = null }
                .onFailure { actionMessage.value = failure }
        }
    }

    companion object {
        fun factory(savingsRepository: SavingsRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SavingsChallengesViewModel(
                challenges = savingsRepository.observeSavingsChallenges(),
                skippedSlots = savingsRepository.observeChallengeSkippedSlots(),
                stickers = savingsRepository.observeChallengeStickers(),
                goals = savingsRepository.observeSavingsGoals(),
                movements = savingsRepository.observeSavingsMovements(),
                saveChallenge = savingsRepository::saveSavingsChallenge,
                deleteChallengeById = savingsRepository::deleteSavingsChallenge,
                setSlotSkipped = savingsRepository::setChallengeSlotSkipped,
                recordStickers = savingsRepository::recordEarnedStickers
            ) as T
        }
    }
}
