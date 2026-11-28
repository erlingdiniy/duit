package com.diniy.duit.config

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Spark-plan Firebase Remote Config wrapper for beta & privateDev builds.
 * Never blocks startup and falls back safely to hardcoded defaults when offline.
 */
object RemoteAppConfig {
    const val isSupported: Boolean = true

    private val _snapshot = MutableStateFlow(RemoteAppConfigSnapshot())
    val snapshot: StateFlow<RemoteAppConfigSnapshot> = _snapshot.asStateFlow()

    fun initialize(context: Context) {
        val ctx = context.applicationContext
        runCatching {
            if (FirebaseApp.getApps(ctx).isEmpty()) {
                FirebaseApp.initializeApp(ctx) ?: return
            }
            val remoteConfig = FirebaseRemoteConfig.getInstance()
            val settings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(3600L)
                .setFetchTimeoutInSeconds(10L)
                .build()
            remoteConfig.setConfigSettingsAsync(settings)
            remoteConfig.setDefaultsAsync(RemoteAppConfigPolicy.defaultStringValues())
            publishFrom(remoteConfig, source = "cached_or_default")
            remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
                val src = if (task.isSuccessful) "remote_activated" else "cached_fallback"
                runCatching { publishFrom(remoteConfig, source = src) }
            }
        }
    }

    private fun publishFrom(remoteConfig: FirebaseRemoteConfig, source: String) {
        _snapshot.value = RemoteAppConfigPolicy.sanitizeSnapshot(
            maintenanceBannerEnabled = remoteConfig.getBoolean(RemoteAppConfigPolicy.KEY_MAINTENANCE_BANNER_ENABLED),
            maintenanceBannerTitle = remoteConfig.getString(RemoteAppConfigPolicy.KEY_MAINTENANCE_BANNER_TITLE),
            maintenanceBannerMessage = remoteConfig.getString(RemoteAppConfigPolicy.KEY_MAINTENANCE_BANNER_MESSAGE),
            experimentalInsightsTipsEnabled = remoteConfig.getBoolean(RemoteAppConfigPolicy.KEY_EXPERIMENTAL_INSIGHTS_TIPS),
            gradualRolloutNoticeEnabled = remoteConfig.getBoolean(RemoteAppConfigPolicy.KEY_GRADUAL_ROLLOUT_NOTICE_ENABLED),
            gradualRolloutPercent = remoteConfig.getLong(RemoteAppConfigPolicy.KEY_GRADUAL_ROLLOUT_PERCENT).toInt(),
            minRecommendedVersionCode = remoteConfig.getLong(RemoteAppConfigPolicy.KEY_MIN_RECOMMENDED_VERSION_CODE),
            lastFetchEpochMs = System.currentTimeMillis(),
            source = source
        )
    }
}
