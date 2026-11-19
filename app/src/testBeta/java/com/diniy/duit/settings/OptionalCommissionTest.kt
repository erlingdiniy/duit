package com.diniy.duit.settings

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.diniy.duit.BuildConfig
import com.diniy.duit.data.local.DuitDatabase
import com.diniy.duit.data.preferences.UserPreferencesDataStore
import com.diniy.duit.data.repository.OfflineFirstFinanceRepository
import com.diniy.duit.domain.calculator.FinanceSummaryCalculator
import com.diniy.duit.domain.model.FinanceTransaction
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.TransactionType
import com.diniy.duit.domain.model.Wallet
import com.diniy.duit.domain.model.WalletType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Public edition (beta build type): Commission Tracking is optional. Isolated in-memory database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OptionalCommissionTest {
    private lateinit var context: Context
    private lateinit var database: DuitDatabase
    private lateinit var repository: OfflineFirstFinanceRepository
    private lateinit var store: UserPreferencesDataStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, DuitDatabase::class.java).allowMainThreadQueries().build()
        repository = OfflineFirstFinanceRepository(database)
        store = UserPreferencesDataStore(context)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun publicBuild_isOptional_andOffUntilChosen() = runTest {
        assertTrue(BuildConfig.OPTIONAL_COMMISSION)
        if (!store.hasCommissionFeatureChoice()) assertFalse(store.appSettings.first().commissionFeatureEnabled)
        store.setCommissionFeatureEnabled(true)
        assertTrue(store.hasCommissionFeatureChoice())
        assertTrue(store.appSettings.first().commissionFeatureEnabled)
    }

    @Test
    fun offOnOff_neverTouchesRecordsBalancesOrSummaries() = runTest {
        val now = System.currentTimeMillis()
        val wallet = repository.upsertWallet(Wallet(0L, "Bank", WalletType.BANK, 100_000L, false, PrismaticAccentToken.AQUA, now, now))
        repository.recordTransaction(FinanceTransaction(0L, wallet, null, TransactionType.INCOME, 50_000L, "Salary", "Pay", "", null, now, now, submissionId = "s1"))
        repository.createCommissionSource("Store A", PrismaticAccentToken.AQUA)

        suspend fun snapshot(): List<Any> {
            val summary = FinanceSummaryCalculator.calculate(
                wallets = repository.observeWallets().first(),
                transactions = repository.observeTransactions().first(),
                commitments = emptyList(),
                commissions = repository.observeCommissions().first(),
                savingsAllocations = emptyList(),
                savingsGoals = emptyList(),
                payLaterPlans = emptyList(),
                debts = emptyList(),
                activeCycle = null
            )
            return listOf(
                summary.totalBalanceSen, summary.safeToSpendSen, summary.pendingCommissionsSen,
                repository.observeCommissionSources().first().size, repository.observeTransactions().first().size
            )
        }

        val before = snapshot()
        for (enabled in listOf(false, true, false)) {
            store.setCommissionFeatureEnabled(enabled)
            assertEquals(enabled, store.appSettings.first().commissionFeatureEnabled)
            assertEquals(before, snapshot())
        }
        assertEquals(150_000L, before[0])
        assertEquals(1, before[3])
    }
}
