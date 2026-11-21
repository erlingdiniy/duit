package com.diniy.duit.feature.savings.challenges

import com.diniy.duit.domain.challenge.ChallengeType
import com.diniy.duit.domain.challenge.EarnedSticker
import com.diniy.duit.domain.challenge.GardenStage
import com.diniy.duit.domain.challenge.SavingsChallenge
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.SavingsGoal
import com.diniy.duit.domain.model.SavingsGoalStatus
import com.diniy.duit.domain.model.SavingsMovement
import com.diniy.duit.domain.model.SavingsMovementType
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SavingsChallengesViewModelTest {
    private val kl = ZoneId.of("Asia/Kuala_Lumpur")
    private val today = LocalDate.of(2026, 11, 21)
    private val goal = SavingsGoal(1, "Japan Trip", 500_000, null, "", "star", PrismaticAccentToken.AQUA, SavingsGoalStatus.ACTIVE, 1, 1)

    private val challenges = MutableStateFlow<List<SavingsChallenge>>(emptyList())
    private val skipped = MutableStateFlow<Map<Long, Set<Int>>>(emptyMap())
    private val stickers = MutableStateFlow<List<EarnedSticker>>(emptyList())
    private val goals = MutableStateFlow(listOf(goal))
    private val movements = MutableStateFlow<List<SavingsMovement>>(emptyList())
    private val saved = mutableListOf<SavingsChallenge>()
    private val skipCalls = mutableListOf<Triple<Long, Int, Boolean>>()
    private val stickerCalls = mutableListOf<Set<GardenStage>>()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun move(type: SavingsMovementType, sen: Long, id: Long) =
        SavingsMovement(id, 1, 1, 1, null, type, sen, null, "", today.atTime(12, 0).atZone(kl).toInstant().toEpochMilli())

    private fun TestScope.vm(failing: Boolean = false) = SavingsChallengesViewModel(
        challenges = if (failing) flow { throw IllegalStateException("db") } else challenges,
        skippedSlots = skipped, stickers = stickers, goals = goals, movements = movements,
        saveChallenge = { c -> saved += c; challenges.value = challenges.value.filterNot { it.id == c.id } + c.copy(id = if (c.id == 0L) 1 else c.id); 1L },
        deleteChallengeById = { id -> challenges.value = challenges.value.filterNot { it.id == id } },
        setSlotSkipped = { id, idx, s -> skipCalls += Triple(id, idx, s); skipped.value = if (s) mapOf(id to setOf(idx)) else emptyMap() },
        recordStickers = { id, set -> stickerCalls += set; stickers.value = stickers.value + set.map { EarnedSticker(id, it, 0) } },
        zone = kl, today = { today }, newSeed = { 42L }
    ).also { m -> backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { m.uiState.collect {} } }

    @Test
    fun validation_andCreateLinksToGoal_withFixedWeek52Shape() = runTest {
        val m = vm()
        assertEquals("Pick a savings goal for this challenge.", m.save(null, null, ChallengeType.DAILY, 100, 30, null))
        assertEquals("Enter an amount more than RM0.", m.save(null, 1, ChallengeType.DAILY, 0, 30, null))
        assertTrue(m.save(null, 1, ChallengeType.DAILY, 100, 0, null) != null)
        assertTrue(m.save(null, 1, ChallengeType.CUSTOM, 100, 10, 0) != null)
        assertTrue(saved.isEmpty())

        assertNull(m.save(null, 1, ChallengeType.WEEK_52, 100, 5, 3))
        val c = saved.single()
        assertEquals(52, c.slotCount)
        assertEquals(7, c.intervalDays)
        assertEquals(today.toEpochDay(), c.startEpochDay)
        assertEquals("Japan Trip", m.uiState.value.items.single().goal.name)
    }

    @Test
    fun progress_comesFromRealDeposits_andWithdrawalsLowerIt() = runTest {
        val m = vm()
        m.save(null, 1, ChallengeType.DAILY, 1_000, 4, null)
        movements.value = listOf(move(SavingsMovementType.ALLOCATE, 3_000, 1))
        assertEquals(75, m.uiState.value.items.single().progress.percent)
        movements.value = movements.value + move(SavingsMovementType.WITHDRAW_TO_WALLET, 2_000, 2)
        val item = m.uiState.value.items.single()
        assertEquals(1_000L, item.progress.savedSen)
        assertEquals(25, item.progress.percent)
        // Stickers earned at 75% stay after the drop, recorded once each.
        assertEquals(setOf(GardenStage.SPROUT, GardenStage.BUD, GardenStage.BLOOM), item.stickers)
        assertEquals(1, stickerCalls.size)
    }

    @Test
    fun skipPauseResume_andEditKeepsSeedAndStart() = runTest {
        val m = vm()
        m.save(null, 1, ChallengeType.RANDOM, 100, 10, 7)
        val c = m.uiState.value.items.single().challenge
        m.setSkipped(c.id, 1, true)
        assertEquals(Triple(c.id, 1, true), skipCalls.single())
        assertEquals(2, m.uiState.value.items.single().progress.nextSlot!!.index)

        m.setPaused(c, true)
        assertNull(m.uiState.value.items.single().progress.nextSlot)
        m.setPaused(m.uiState.value.items.single().challenge, false)
        assertTrue(m.uiState.value.items.single().progress.nextSlot != null)

        m.save(m.uiState.value.items.single().challenge, 1, ChallengeType.RANDOM, 200, 10, 7)
        assertEquals(42L, saved.last().randomSeed)
        assertEquals(c.startEpochDay, saved.last().startEpochDay)
    }

    @Test
    fun deletedGoal_hidesItsChallenge_andDeleteWorks() = runTest {
        val m = vm()
        m.save(null, 1, ChallengeType.DAILY, 100, 5, null)
        goals.value = emptyList()
        assertTrue(m.uiState.value.items.isEmpty())
        goals.value = listOf(goal)
        var closed = false
        m.delete(1) { closed = true }
        assertTrue(closed && m.uiState.value.items.isEmpty())
    }

    @Test
    fun loadFailure_showsError() = runTest {
        val m = vm(failing = true)
        assertTrue(m.uiState.value.errorMessage != null)
    }
}
