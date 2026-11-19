package com.diniy.duit.data.preferences

import kotlinx.coroutines.flow.first
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.diniy.duit.domain.model.AnimationIntensity
import com.diniy.duit.domain.model.AppSettingsSnapshot
import com.diniy.duit.domain.model.BudgetPeriodType
import com.diniy.duit.domain.model.CommissionFormulaConfig
import com.diniy.duit.domain.model.DashboardSectionKey
import com.diniy.duit.domain.model.GlassIntensity
import com.diniy.duit.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.duitDataStore: DataStore<Preferences> by preferencesDataStore(name = "duit_preferences")

class UserPreferencesDataStore(context: Context) {

    private val dataStore = context.applicationContext.duitDataStore

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val GLASS_INTENSITY = stringPreferencesKey("glass_intensity")
        val HAPTIC_FEEDBACK_ENABLED = booleanPreferencesKey("haptic_feedback_enabled")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val ANIMATION_INTENSITY = stringPreferencesKey("animation_intensity")
        val HIDDEN_DASHBOARD_SECTIONS = stringPreferencesKey("hidden_dashboard_sections")
        val DASHBOARD_SECTION_ORDER = stringPreferencesKey("dashboard_section_order")
        val DEFAULT_WALLET_ID = longPreferencesKey("default_wallet_id")
        val DEFAULT_BUDGET_PERIOD_TYPE = stringPreferencesKey("default_budget_period_type")
        val AUTO_ADVANCE_RECURRING = booleanPreferencesKey("auto_advance_recurring_commitments")
        val COMMISSION_TRACKING_ENABLED = booleanPreferencesKey("commission_tracking_enabled")
        val COMMISSION_FEATURE_ENABLED = booleanPreferencesKey("commission_feature_enabled")
        val COMMISSION_FORMULA_NAME = stringPreferencesKey("commission_formula_name")
        val COMMISSION_CONFIGURED = booleanPreferencesKey("commission_configured")
        val COMMISSION_ENABLED = booleanPreferencesKey("commission_enabled")
        val COMMISSION_THRESHOLD_SEN = longPreferencesKey("commission_threshold_sen")
        val COMMISSION_RATE_BPS = intPreferencesKey("commission_rate_bps")
        val COMMISSION_FLAT_BONUS_SEN = longPreferencesKey("commission_flat_bonus_sen")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

    val appSettings: Flow<AppSettingsSnapshot> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { prefs ->
            val theme = ThemeMode.entries.firstOrNull { it.name == prefs[Keys.THEME_MODE] } ?: ThemeMode.SYSTEM
            val glass = GlassIntensity.entries.firstOrNull { it.name == prefs[Keys.GLASS_INTENSITY] } ?: GlassIntensity.BALANCED
            val anim = AnimationIntensity.entries.firstOrNull { it.name == prefs[Keys.ANIMATION_INTENSITY] } ?: AnimationIntensity.BALANCED
            val hidden = prefs[Keys.HIDDEN_DASHBOARD_SECTIONS]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.mapNotNull { key -> DashboardSectionKey.entries.firstOrNull { it.name == key } }
                ?.toSet()
                ?: emptySet()

            val parsedOrder = prefs[Keys.DASHBOARD_SECTION_ORDER]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.mapNotNull { key -> DashboardSectionKey.entries.firstOrNull { it.name == key } }
                ?.distinct()
                ?: emptyList()
            val fullOrder = (parsedOrder + DashboardSectionKey.entries.filterNot { it in parsedOrder })

            val budgetPeriod = BudgetPeriodType.entries.firstOrNull { it.name == prefs[Keys.DEFAULT_BUDGET_PERIOD_TYPE] }
                ?: BudgetPeriodType.MONTHLY
            val defaultWalletId = prefs[Keys.DEFAULT_WALLET_ID]?.takeIf { it > 0L }

            AppSettingsSnapshot(
                themeMode = theme,
                glassIntensity = glass,
                hapticFeedbackEnabled = prefs[Keys.HAPTIC_FEEDBACK_ENABLED] ?: true,
                reduceMotion = prefs[Keys.REDUCE_MOTION] ?: false,
                animationIntensity = anim,
                hiddenDashboardSections = hidden,
                dashboardSectionOrder = fullOrder,
                defaultWalletId = defaultWalletId,
                defaultBudgetPeriodType = budgetPeriod,
                autoAdvanceRecurringCommitments = prefs[Keys.AUTO_ADVANCE_RECURRING] ?: true,
                commissionTrackingEnabled = prefs[Keys.COMMISSION_TRACKING_ENABLED] ?: true,
                // Personal edition: always on. Public edition: off until chosen (startup turns it on for existing records).
                commissionFeatureEnabled = !com.diniy.duit.BuildConfig.OPTIONAL_COMMISSION ||
                    (prefs[Keys.COMMISSION_FEATURE_ENABLED] ?: false),
                commissionFormulaConfig = CommissionFormulaConfig(
                    formulaName = prefs[Keys.COMMISSION_FORMULA_NAME] ?: "",
                    isConfigured = prefs[Keys.COMMISSION_CONFIGURED] ?: false,
                    isEnabled = prefs[Keys.COMMISSION_ENABLED] ?: false,
                    minimumSalesThresholdSen = prefs[Keys.COMMISSION_THRESHOLD_SEN] ?: 0L,
                    rateBps = prefs[Keys.COMMISSION_RATE_BPS] ?: 0,
                    flatBonusSen = prefs[Keys.COMMISSION_FLAT_BONUS_SEN] ?: 0L
                ),
                hasCompletedOnboarding = prefs[Keys.ONBOARDING_COMPLETED] ?: false
            )
        }

    val onboardingCompletedFlow: Flow<Boolean?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { prefs -> prefs[Keys.ONBOARDING_COMPLETED] }

    /** True if anything other than the onboarding flag was ever stored (user settings or a restore). */
    suspend fun hasStoredPreferences(): Boolean =
        dataStore.data
            .catch { exception -> if (exception is IOException) emit(emptyPreferences()) else throw exception }
            .first()
            .asMap().keys.any { it != Keys.ONBOARDING_COMPLETED }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    val themeMode: Flow<ThemeMode> = appSettings.map { it.themeMode }

    val commissionFormulaConfig: Flow<CommissionFormulaConfig> = appSettings.map { it.commissionFormulaConfig }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
        }
    }

    suspend fun setGlassIntensity(intensity: GlassIntensity) {
        dataStore.edit { it[Keys.GLASS_INTENSITY] = intensity.name }
    }

    suspend fun setHapticFeedbackEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.HAPTIC_FEEDBACK_ENABLED] = enabled }
    }

    suspend fun setReduceMotion(enabled: Boolean) {
        dataStore.edit { it[Keys.REDUCE_MOTION] = enabled }
    }

    suspend fun setAnimationIntensity(intensity: AnimationIntensity) {
        dataStore.edit { it[Keys.ANIMATION_INTENSITY] = intensity.name }
    }

    suspend fun setDashboardSectionVisible(section: DashboardSectionKey, visible: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.HIDDEN_DASHBOARD_SECTIONS]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.mapNotNull { key -> DashboardSectionKey.entries.firstOrNull { it.name == key } }
                ?.toMutableSet()
                ?: mutableSetOf()
            if (visible) current.remove(section) else current.add(section)
            prefs[Keys.HIDDEN_DASHBOARD_SECTIONS] = current.joinToString(",") { it.name }
        }
    }

    suspend fun setDashboardSectionOrder(order: List<DashboardSectionKey>) {
        val normalized = (order.distinct() + DashboardSectionKey.entries.filterNot { it in order })
        dataStore.edit { prefs ->
            prefs[Keys.DASHBOARD_SECTION_ORDER] = normalized.joinToString(",") { it.name }
        }
    }

    suspend fun restoreDefaultDashboardLayout() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.HIDDEN_DASHBOARD_SECTIONS)
            prefs.remove(Keys.DASHBOARD_SECTION_ORDER)
        }
    }

    suspend fun setDefaultWalletId(walletId: Long?) {
        dataStore.edit { prefs ->
            if (walletId != null && walletId > 0L) prefs[Keys.DEFAULT_WALLET_ID] = walletId
            else prefs.remove(Keys.DEFAULT_WALLET_ID)
        }
    }

    suspend fun setDefaultBudgetPeriodType(periodType: BudgetPeriodType) {
        dataStore.edit { it[Keys.DEFAULT_BUDGET_PERIOD_TYPE] = periodType.name }
    }

    suspend fun setAutoAdvanceRecurringCommitments(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_ADVANCE_RECURRING] = enabled }
    }

    suspend fun setCommissionFeatureEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.COMMISSION_FEATURE_ENABLED] = enabled }
    }

    /** True once the public-edition commission choice was stored (by the user, startup or a restore). */
    suspend fun hasCommissionFeatureChoice(): Boolean = dataStore.data
        .catch { exception -> if (exception is IOException) emit(emptyPreferences()) else throw exception }
        .first()
        .contains(Keys.COMMISSION_FEATURE_ENABLED)

    suspend fun setCommissionTrackingEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.COMMISSION_TRACKING_ENABLED] = enabled }
    }

    suspend fun saveCommissionFormulaConfig(config: CommissionFormulaConfig) {
        dataStore.edit { prefs ->
            prefs[Keys.COMMISSION_FORMULA_NAME] = config.formulaName
            prefs[Keys.COMMISSION_CONFIGURED] = config.isConfigured
            prefs[Keys.COMMISSION_ENABLED] = config.isEnabled
            prefs[Keys.COMMISSION_THRESHOLD_SEN] = config.minimumSalesThresholdSen
            prefs[Keys.COMMISSION_RATE_BPS] = config.rateBps
            prefs[Keys.COMMISSION_FLAT_BONUS_SEN] = config.flatBonusSen
        }
    }

    suspend fun resetCommissionFormulaConfig() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.COMMISSION_FORMULA_NAME)
            prefs.remove(Keys.COMMISSION_CONFIGURED)
            prefs.remove(Keys.COMMISSION_ENABLED)
            prefs.remove(Keys.COMMISSION_THRESHOLD_SEN)
            prefs.remove(Keys.COMMISSION_RATE_BPS)
            prefs.remove(Keys.COMMISSION_FLAT_BONUS_SEN)
        }
    }

    suspend fun applyPortableSettings(snapshot: AppSettingsSnapshot) {
        dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = snapshot.themeMode.name
            prefs[Keys.GLASS_INTENSITY] = snapshot.glassIntensity.name
            prefs[Keys.HAPTIC_FEEDBACK_ENABLED] = snapshot.hapticFeedbackEnabled
            prefs[Keys.REDUCE_MOTION] = snapshot.reduceMotion
            prefs[Keys.ANIMATION_INTENSITY] = snapshot.animationIntensity.name
            prefs[Keys.HIDDEN_DASHBOARD_SECTIONS] = snapshot.hiddenDashboardSections.joinToString(",") { it.name }
            prefs[Keys.DASHBOARD_SECTION_ORDER] = snapshot.dashboardSectionOrder.joinToString(",") { it.name }
            if (snapshot.defaultWalletId != null && snapshot.defaultWalletId > 0L) {
                prefs[Keys.DEFAULT_WALLET_ID] = snapshot.defaultWalletId
            } else {
                prefs.remove(Keys.DEFAULT_WALLET_ID)
            }
            prefs[Keys.DEFAULT_BUDGET_PERIOD_TYPE] = snapshot.defaultBudgetPeriodType.name
            prefs[Keys.AUTO_ADVANCE_RECURRING] = snapshot.autoAdvanceRecurringCommitments
            prefs[Keys.COMMISSION_TRACKING_ENABLED] = snapshot.commissionTrackingEnabled
            prefs[Keys.COMMISSION_FEATURE_ENABLED] = snapshot.commissionFeatureEnabled
            prefs[Keys.COMMISSION_FORMULA_NAME] = snapshot.commissionFormulaConfig.formulaName
            prefs[Keys.COMMISSION_CONFIGURED] = snapshot.commissionFormulaConfig.isConfigured
            prefs[Keys.COMMISSION_ENABLED] = snapshot.commissionFormulaConfig.isEnabled
            prefs[Keys.COMMISSION_THRESHOLD_SEN] = snapshot.commissionFormulaConfig.minimumSalesThresholdSen
            prefs[Keys.COMMISSION_RATE_BPS] = snapshot.commissionFormulaConfig.rateBps
            prefs[Keys.COMMISSION_FLAT_BONUS_SEN] = snapshot.commissionFormulaConfig.flatBonusSen
            prefs[Keys.ONBOARDING_COMPLETED] = true
        }
    }

    suspend fun restoreDefaultPreferences() {
        dataStore.edit { it.clear() }
    }
}
