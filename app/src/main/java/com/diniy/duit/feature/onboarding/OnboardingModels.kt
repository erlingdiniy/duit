package com.diniy.duit.feature.onboarding

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.ui.graphics.vector.ImageVector
import com.diniy.duit.core.ui.components.CandyTone

data class OnboardingSlideItem(
    val title: String,
    val description: String,
    val icon: ImageVector
)

data class OnboardingSlide(
    val id: Int,
    val title: String,
    val subtitle: String,
    val heroIcon: ImageVector,
    val heroTone: CandyTone,
    val items: List<OnboardingSlideItem>
)

object OnboardingContent {
    val slides = listOf(
        OnboardingSlide(
            id = 1,
            title = "Welcome to duit.",
            subtitle = "Your money, made simple.",
            heroIcon = Icons.Rounded.AccountBalanceWallet,
            heroTone = CandyTone.Mint,
            items = listOf(
                OnboardingSlideItem(
                    title = "Friendly & Local-First",
                    description = "A cute, privacy-respecting financial companion that runs entirely on your device.",
                    icon = Icons.Rounded.Shield
                ),
                OnboardingSlideItem(
                    title = "No Cloud Tracking",
                    description = "Your income, balances and budgets stay in your hands. No account to create, no cloud sync.",
                    icon = Icons.Rounded.Lock
                ),
                OnboardingSlideItem(
                    title = "Built for Malaysia (MYR)",
                    description = "Designed around Ringgit Malaysia, local salary cycles, commissions, and everyday spending.",
                    icon = Icons.Rounded.Payments
                )
            )
        ),
        OnboardingSlide(
            id = 2,
            title = "Know where it goes",
            subtitle = "Track your money across all accounts",
            heroIcon = Icons.Rounded.ReceiptLong,
            heroTone = CandyTone.Coral,
            items = listOf(
                OnboardingSlideItem(
                    title = "Wallets & Accounts",
                    description = "Organize cash, bank accounts, and e-wallets with instant balance updates.",
                    icon = Icons.Rounded.AccountBalanceWallet
                ),
                OnboardingSlideItem(
                    title = "Income & Expenses",
                    description = "Log everyday spending with custom categories in just a few taps.",
                    icon = Icons.Rounded.Payments
                ),
                OnboardingSlideItem(
                    title = "Transfers & Quick Add",
                    description = "Move funds seamlessly between accounts without inflating spending totals.",
                    icon = Icons.Rounded.SwapHoriz
                ),
                OnboardingSlideItem(
                    title = "Commission Tracking",
                    description = "Track your sales, gross profit and commission payouts in one place.",
                    icon = Icons.Rounded.Calculate
                )
            )
        ),
        OnboardingSlide(
            id = 3,
            title = "Make room for your goals",
            subtitle = "Plan ahead with clarity and confidence",
            heroIcon = Icons.Rounded.Savings,
            heroTone = CandyTone.Sunny,
            items = listOf(
                OnboardingSlideItem(
                    title = "Safe to Spend",
                    description = "See the exact money you can spend freely after reserving for bills and savings.",
                    icon = Icons.Rounded.Dashboard
                ),
                OnboardingSlideItem(
                    title = "Protected Savings & Goals",
                    description = "Ring-fence money for emergency funds or big dreams with visual progress rings.",
                    icon = Icons.Rounded.Savings
                ),
                OnboardingSlideItem(
                    title = "Monthly Budgets",
                    description = "Set spending limits and get warned as you get close to them.",
                    icon = Icons.Rounded.Category
                ),
                OnboardingSlideItem(
                    title = "Bills, PayLater & Debts",
                    description = "Keep upcoming commitments, installments, and friend debts in view.",
                    icon = Icons.Rounded.Handshake
                )
            )
        ),
        OnboardingSlide(
            id = 4,
            title = "See the bigger picture",
            subtitle = "Understand your money through rich insights",
            heroIcon = Icons.Rounded.Insights,
            heroTone = CandyTone.Mint,
            items = listOf(
                OnboardingSlideItem(
                    title = "Interactive Doughnut Breakdown",
                    description = "See where your spending goes by category, and tap one to see its transactions.",
                    icon = Icons.Rounded.PieChart
                ),
                OnboardingSlideItem(
                    title = "Spending Trends & History",
                    description = "Compare income and expenses month by month.",
                    icon = Icons.Rounded.Insights
                ),
                OnboardingSlideItem(
                    title = "Commitment Horizons",
                    description = "Track what's due next so you're never caught off-guard before payday.",
                    icon = Icons.Rounded.CreditCard
                )
            )
        ),
        OnboardingSlide(
            id = 5,
            title = "Your money, your control",
            subtitle = "Privacy, protection and peace of mind",
            heroIcon = Icons.Rounded.Shield,
            heroTone = CandyTone.Sky,
            items = listOf(
                OnboardingSlideItem(
                    title = "Offline-First Storage",
                    description = "Your database never leaves your phone. Full functionality even without internet.",
                    icon = Icons.Rounded.Shield
                ),
                OnboardingSlideItem(
                    title = "App Lock & Biometrics",
                    description = "Lock the app with a 6-digit PIN, and use your fingerprint where your phone supports it.",
                    icon = Icons.Rounded.Lock
                ),
                OnboardingSlideItem(
                    title = "Encrypted Backup & CSV Export",
                    description = "Create password-protected backups, or export your records as CSV. CSV files are not encrypted.",
                    icon = Icons.Rounded.FileDownload
                ),
                OnboardingSlideItem(
                    title = "Smart Reminders",
                    description = "Gentle local notifications before bills are due or when budgets near capacity.",
                    icon = Icons.Rounded.Notifications
                )
            )
        )
    )
}

data class FeatureGuideSection(
    val id: Int,
    val title: String,
    val icon: ImageVector,
    val tone: CandyTone,
    val whatItDoes: String,
    val whyItIsUseful: String,
    val steps: List<String>
)

object FeatureGuideContent {
    val sections = listOf(
        FeatureGuideSection(
            id = 1,
            title = "Home & Safe to Spend",
            icon = Icons.Rounded.Dashboard,
            tone = CandyTone.Coral,
            whatItDoes = "Shows your total balance, Safe to Spend, your wallets, upcoming bills and recent activity.",
            whyItIsUseful = "Safe to Spend leaves out protected savings and the bills, instalments and debt repayments due in your current salary cycle, so you can see what is free to use.",
            steps = listOf(
                "Check the Safe to Spend hero card at the top of the Home tab.",
                "Review upcoming bills due in the current salary cycle.",
                "Tap Hide on the Safe to Spend card to mask amounts when others can see your screen."
            )
        ),
        FeatureGuideSection(
            id = 2,
            title = "Wallets & Accounts",
            icon = Icons.Rounded.AccountBalanceWallet,
            tone = CandyTone.Mint,
            whatItDoes = "Tracks individual accounts including physical cash, bank accounts, e-wallets, and dedicated savings vaults.",
            whyItIsUseful = "Shows where your money sits. Savings vault wallets are kept out of Safe to Spend.",
            steps = listOf(
                "On Home, find Wallets and tap Add wallet.",
                "Choose account type (Cash, Bank, e-Wallet, or Savings) and initial balance.",
                "Tap any wallet to view its transaction history or edit account details."
            )
        ),
        FeatureGuideSection(
            id = 3,
            title = "Income, Expenses & Transfers",
            icon = Icons.Rounded.ReceiptLong,
            tone = CandyTone.Sunny,
            whatItDoes = "Records money going out, coming in and moving between your wallets, with a category and an optional note.",
            whyItIsUseful = "Keeps your ledger accurate. Transfers shift money between accounts without altering net income or expense totals.",
            steps = listOf(
                "Tap the floating coral + button to open Quick Add.",
                "Pick Expense, Income, or Transfer, then type the RM amount.",
                "Select the category and wallet, then tap Save."
            )
        ),
        FeatureGuideSection(
            id = 4,
            title = "Commission Tracking",
            icon = Icons.Rounded.Calculate,
            tone = CandyTone.Mint,
            whatItDoes = "Tracks your commission: sales, gross profit and what has been paid out.",
            whyItIsUseful = "Useful if part of your income is commission. Pending commission is not counted in Safe to Spend until it is received.",
            steps = listOf(
                "Tap + then Commission, or open Planning → Bills → Salary & commission.",
                "Add a commission with its source, and log daily sales if you track them.",
                "When a payout arrives, record the payment to credit the wallet you choose."
            )
        ),
        FeatureGuideSection(
            id = 5,
            title = "Savings Goals & Protected Savings",
            icon = Icons.Rounded.Savings,
            tone = CandyTone.Coral,
            whatItDoes = "Lets you save towards a target amount, with an optional target date and a progress ring.",
            whyItIsUseful = "Money set aside for goals and savings vaults is kept out of Safe to Spend, so you don't spend it by accident.",
            steps = listOf(
                "Open the Planning tab, then Goals.",
                "Tap + New Goal and set the target amount.",
                "Tap Add Funds to move money from a wallet towards the goal."
            )
        ),
        FeatureGuideSection(
            id = 6,
            title = "Budgets",
            icon = Icons.Rounded.Category,
            tone = CandyTone.Sunny,
            whatItDoes = "Sets a spending limit for a period, overall or for one category, and shows how much is used.",
            whyItIsUseful = "Warns you at 80% and 100% of a limit so you can slow down before overspending.",
            steps = listOf(
                "Open the Planning tab, then Budgets.",
                "Select a category (e.g. Food, Transport, Entertainment) and set a monthly limit.",
                "Watch the bar fill as you record expenses."
            )
        ),
        FeatureGuideSection(
            id = 7,
            title = "Bills & Commitments",
            icon = Icons.Rounded.Payments,
            tone = CandyTone.Sky,
            whatItDoes = "Keeps track of bills like rent, utilities, insurance and subscriptions, with due dates and optional repeats.",
            whyItIsUseful = "Bills due in your current salary cycle are set aside from Safe to Spend. Later bills are shown but not deducted yet.",
            steps = listOf(
                "Open the Planning tab, then Bills.",
                "Tap + Add and enter the amount, due date and how often it repeats.",
                "Record a payment when you pay; a repeating bill then moves to its next due date."
            )
        ),
        FeatureGuideSection(
            id = 8,
            title = "PayLater & Personal Debts",
            icon = Icons.Rounded.Handshake,
            tone = CandyTone.Coral,
            whatItDoes = "Tracks Buy-Now-Pay-Later (BNPL) installment schedules and personal money lent to or borrowed from others.",
            whyItIsUseful = "Keeps installment counts and repayment balances organized in one clear place without mental math.",
            steps = listOf(
                "In Planning → Bills, choose PayLater or Debts.",
                "Add a PayLater plan with its provider, total and number of instalments, or a debt with who it's with.",
                "Record payments as you make them to update what is left. Only the instalment or repayment due this cycle is set aside from Safe to Spend."
            )
        ),
        FeatureGuideSection(
            id = 9,
            title = "Insights & Reports",
            icon = Icons.Rounded.Insights,
            tone = CandyTone.Mint,
            whatItDoes = "Shows your income, spending, categories, trends and commitments for the period you choose.",
            whyItIsUseful = "Helps you discover spending patterns, identify top expense categories, and see your net cashflow across cycles.",
            steps = listOf(
                "Tap the Insights tab in the bottom navigation.",
                "Use the arrows to change month, or the filter button at the top right to choose Salary Cycle, Calendar Month, Previous Period or Custom Range.",
                "In Spending, tap a category to see its transactions."
            )
        ),
        FeatureGuideSection(
            id = 10,
            title = "Notifications & Reminders",
            icon = Icons.Rounded.Notifications,
            tone = CandyTone.Sunny,
            whatItDoes = "Sends timely local reminder notifications for bills due today, overdue items, and budget threshold warnings.",
            whyItIsUseful = "Reminds you before bills are due, even when the app is closed. Lock-screen amounts can be hidden.",
            steps = listOf(
                "Open Settings -> Notifications.",
                "Choose your reminder time, how many days before a due date to be reminded, and what to be reminded about.",
                "Turn on Hide amounts on lock screen if you want reminders without Ringgit figures."
            )
        ),
        FeatureGuideSection(
            id = 11,
            title = "Security & Privacy",
            icon = Icons.Rounded.Shield,
            tone = CandyTone.Sky,
            whatItDoes = "Protects your financial data with PIN access, biometric fingerprint unlock, and privacy masking modes.",
            whyItIsUseful = "Prevents unauthorized access and protects your financial privacy when friends or colleagues look at your screen.",
            steps = listOf(
                "Open Settings -> Security.",
                "Set a 6-digit PIN, and optionally turn on fingerprint unlock.",
                "Choose when the app locks again, and whether Privacy Mode hides amounts."
            )
        ),
        FeatureGuideSection(
            id = 12,
            title = "Backup, Restore & CSV Export",
            icon = Icons.Rounded.FileDownload,
            tone = CandyTone.Mint,
            whatItDoes = "Creates a password-protected backup file of your records, restores from one, and exports CSV.",
            whyItIsUseful = "Lets you move to a new phone or keep a safe copy. Keep your backup password safe: it can't be recovered. CSV files are not encrypted.",
            steps = listOf(
                "Open Settings -> Backup & Restore.",
                "Tap Create Encrypted Backup and choose a secure password.",
                "To export a spreadsheet, use the CSV export on the same screen."
            )
        )
    )
}
