package com.diniy.duit.widgets

import com.diniy.duit.data.repository.BudgetRepository
import com.diniy.duit.data.repository.CategoryRepository
import com.diniy.duit.data.repository.CommissionRepository
import com.diniy.duit.data.repository.ObligationRepository
import com.diniy.duit.data.repository.PlanningRepository
import com.diniy.duit.data.repository.SavingsRepository
import com.diniy.duit.data.repository.TransactionRepository
import com.diniy.duit.data.repository.UserPreferencesRepository
import com.diniy.duit.data.repository.WalletRepository
import com.diniy.duit.domain.model.CommissionRecord
import com.diniy.duit.domain.model.Commitment
import com.diniy.duit.domain.model.DebtRecord
import com.diniy.duit.domain.model.FinanceTransaction
import com.diniy.duit.domain.model.PayLaterPlan
import com.diniy.duit.domain.model.SalaryCycle
import com.diniy.duit.domain.model.SavingsAllocation
import com.diniy.duit.domain.model.SavingsGoal
import com.diniy.duit.domain.model.Wallet
import com.diniy.duit.security.SecurityRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn

class WidgetDataProvider(
    private val walletRepository: WalletRepository,
    private val transactionRepository: TransactionRepository,
    private val planningRepository: PlanningRepository,
    private val commissionRepository: CommissionRepository,
    private val savingsRepository: SavingsRepository,
    private val obligationRepository: ObligationRepository,
    private val securityRepository: SecurityRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val budgetRepository: BudgetRepository? = null,
    private val categoryRepository: CategoryRepository? = null
) {
    private val coreFlow = combine(
        walletRepository.observeWallets(),
        transactionRepository.observeTransactions(),
        planningRepository.observeCommitments(),
        commissionRepository.observeCommissions(),
        planningRepository.observeActiveCycle()
    ) { wallets, transactions, commitments, commissions, activeCycle ->
        CoreBundle(wallets, transactions, commitments, commissions, activeCycle)
    }

    private val obligationAndSavingsFlow = combine(
        savingsRepository.observeSavingsAllocations(),
        savingsRepository.observeSavingsGoals(),
        obligationRepository.observePayLaterPlans(),
        obligationRepository.observeDebts()
    ) { allocs, goals, plans, debts ->
        ObligationSavingsBundle(allocs, goals, plans, debts)
    }

    private val budgetFlow = combine(
        budgetRepository?.observeBudgets() ?: flowOf(emptyList()),
        categoryRepository?.observeCategories() ?: flowOf(emptyList())
    ) { budgets, categories -> budgets to categories }

    fun observeSnapshot(): Flow<DuitWidgetsSnapshot> = combine(
        coreFlow,
        obligationAndSavingsFlow,
        securityRepository.observeSecurityConfig(),
        userPreferencesRepository.observeThemeMode(),
        budgetFlow
    ) { core, obl, security, themeMode, budgetBundle ->
        WidgetSnapshotCalculator.build(
            wallets = core.wallets,
            transactions = core.transactions,
            commitments = core.commitments,
            commissions = core.commissions,
            savingsAllocations = obl.allocs,
            savingsGoals = obl.goals,
            payLaterPlans = obl.plans,
            debts = obl.debts,
            activeCycle = core.activeCycle,
            security = security,
            themeMode = themeMode,
            budgets = budgetBundle.first,
            categories = budgetBundle.second
        )
    }.flowOn(Dispatchers.IO)

    suspend fun loadSnapshot(): DuitWidgetsSnapshot = observeSnapshot().first()

    private data class CoreBundle(
        val wallets: List<Wallet>,
        val transactions: List<FinanceTransaction>,
        val commitments: List<Commitment>,
        val commissions: List<CommissionRecord>,
        val activeCycle: SalaryCycle?
    )

    private data class ObligationSavingsBundle(
        val allocs: List<SavingsAllocation>,
        val goals: List<SavingsGoal>,
        val plans: List<PayLaterPlan>,
        val debts: List<DebtRecord>
    )
}
