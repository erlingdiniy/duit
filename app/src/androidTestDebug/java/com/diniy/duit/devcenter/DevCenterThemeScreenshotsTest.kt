package com.diniy.duit.devcenter

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.platform.app.InstrumentationRegistry
import com.diniy.duit.config.RemoteAppConfigPolicy
import com.diniy.duit.core.ui.components.LocalMotionPreferences
import com.diniy.duit.core.ui.components.MotionPreferences
import com.diniy.duit.core.ui.state.LocalPrivacySettings
import com.diniy.duit.core.ui.state.PrivacySettings
import com.diniy.duit.core.ui.theme.DuitTheme
import com.diniy.duit.devcenter.ui.DevCenterAuthScreen
import com.diniy.duit.devcenter.ui.DevEnrollmentScreen
import com.diniy.duit.devcenter.ui.DeveloperCenterShell
import com.diniy.duit.domain.model.AnimationIntensity
import com.diniy.duit.domain.model.GlassIntensity
import com.diniy.duit.domain.model.ThemeMode
import com.diniy.duit.domain.model.VisualTheme
import com.diniy.duit.security.BiometricAuthGateway
import com.diniy.duit.security.BiometricAuthResult
import com.diniy.duit.security.BiometricAvailability
import com.diniy.duit.security.PinHashEngine
import com.diniy.duit.security.SecretProtector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class DevCenterThemeScreenshotsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class TestStorage(initial: DevCenterVaultState) : DevCenterStorage {
        private val flow = MutableStateFlow(initial)
        override fun observe(): Flow<DevCenterVaultState> = flow
        override suspend fun read(): DevCenterVaultState = flow.value
        override suspend fun update(transform: (DevCenterVaultState) -> DevCenterVaultState) {
            flow.value = transform(flow.value)
        }
    }

    private class TestProtector : SecretProtector {
        override fun seal(plaintext: String): String = "test_sealed::$plaintext"
        override fun unseal(ciphertext: String): String? =
            if (ciphertext.startsWith("test_sealed::")) ciphertext.removePrefix("test_sealed::") else null
    }

    private class TestBiometric : BiometricAuthGateway {
        override fun checkAvailability(): BiometricAvailability = BiometricAvailability.AVAILABLE
        override fun authenticate(
            activity: FragmentActivity,
            title: String,
            subtitle: String,
            negativeButtonText: String,
            onResult: (BiometricAuthResult) -> Unit
        ) = Unit
    }

    private class TestKeyManager : DeviceSigningKeyManager {
        override fun hasKey(): Boolean = true
        override fun ensureKeyPair(): DeviceKeyMetadata = DeviceKeyMetadata(
            alias = "duit_dev_mc_ecdsa_p256_v1",
            publicKeySpkiBase64 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEQA==",
            hardwareBacked = true,
            attestationSummary = "StrongBox ECDSA P-256 (QA)"
        )
        override fun signChallengePayload(payload: String): String = "mock_sig_base64"
        override fun deleteKey() = Unit
    }

    private class SanitizedMockTransport : DevHttpTransport {
        var offlineMode: Boolean = false

        override suspend fun request(
            method: String,
            url: String,
            headers: Map<String, String>,
            body: String?
        ): DevHttpResponse = when {
            offlineMode -> throw java.io.IOException("Simulated offline snapshot")
            url.contains("/v1/admin/overview") -> DevHttpResponse(200, OVERVIEW_JSON)
            url.contains("/v1/admin/analytics") -> DevHttpResponse(200, """{"available":true,"snapshot":$GA4_WRAPPER_JSON}""")
            url.contains("/v1/admin/feedback") -> DevHttpResponse(200, FEEDBACK_JSON)
            url.contains("/v1/admin/notifications/state") -> DevHttpResponse(200, NOTIF_STATE_JSON)
            url.contains("/v1/admin/remote-config") -> DevHttpResponse(200, """{"snapshot":$RC_WRAPPER_JSON}""")
            url.contains("/v1/admin/github/overview") -> DevHttpResponse(200, GITHUB_OVERVIEW_JSON)
            url.contains("/v1/admin/infra") -> DevHttpResponse(200, INFRA_JSON)
            url.contains("/v1/admin/audit-logs") -> DevHttpResponse(200, """{"items":$AUDIT_ITEMS_JSON}""")
            url.contains("/v1/admin/devices") -> DevHttpResponse(200, DEVICES_JSON)
            url.contains("/v1/admin/ota/latest") -> DevHttpResponse(200, "{}")
            else -> DevHttpResponse(404, "{}")
        }

        private companion object {
            const val NOW_MS = 1795860900000L

            val GA4_WRAPPER_JSON = """
                {
                  "status": "ok",
                  "syncedAt": $NOW_MS,
                  "payload": {
                    "activeUsers": 184,
                    "newUsers": 42,
                    "returningUsers": 142,
                    "dau": 96,
                    "wau": 154,
                    "mau": 184,
                    "sessions": 612,
                    "retentionRatePercent": 77.2,
                    "avgEngagementSeconds": 195,
                    "quotaRemainingTokensPerHour": 39920,
                    "quotaRemainingTokensPerDay": 199640,
                    "growthSeries": [
                      {"date":"2026-11-22","activeUsers":118,"newUsers":12},
                      {"date":"2026-11-23","activeUsers":132,"newUsers":15},
                      {"date":"2026-11-24","activeUsers":129,"newUsers":9},
                      {"date":"2026-11-25","activeUsers":148,"newUsers":18},
                      {"date":"2026-11-26","activeUsers":162,"newUsers":14},
                      {"date":"2026-11-27","activeUsers":171,"newUsers":16},
                      {"date":"2026-11-28","activeUsers":184,"newUsers":21}
                    ],
                    "features": [
                      {"label":"Quick Add Transaction","count":820,"users":152},
                      {"label":"Budget Insights","count":495,"users":118},
                      {"label":"Bills Calendar","count":312,"users":94}
                    ],
                    "themes": [
                      {"label":"Pure Liquid Glass","count":118,"users":118},
                      {"label":"Candy Bubble 2.0","count":66,"users":66}
                    ],
                    "widgets": [{"label":"Spend Summary","count":268,"users":88}],
                    "appVersions": [
                      {"label":"11.0.0","count":142,"users":142},
                      {"label":"1.3.0","count":42,"users":42}
                    ],
                    "channels": [
                      {"label":"stable","count":142,"users":142},
                      {"label":"beta","count":42,"users":42}
                    ],
                    "countries": [
                      {"label":"Malaysia","count":148,"users":148},
                      {"label":"Singapore","count":24,"users":24}
                    ],
                    "androidVersions": [
                      {"label":"15","count":110,"users":110},
                      {"label":"14","count":54,"users":54}
                    ],
                    "deviceModels": [
                      {"label":"Pixel 9 Pro","count":46,"users":46},
                      {"label":"Xiaomi 15","count":38,"users":38}
                    ],
                    "exceptions": {
                      "crashFreeObservedPercent": 99.46,
                      "affectedUsers": 1,
                      "exceptionEvents": 2,
                      "breakdown": [
                        {"appVersion":"11.0.0","osVersion":"15","exceptionCount":2,"affectedUsers":1}
                      ]
                    }
                  }
                }
            """.trimIndent()

            val RC_WRAPPER_JSON = """
                {
                  "status": "ok",
                  "syncedAt": $NOW_MS,
                  "payload": {
                    "etag": "etag-rc-v12-qa-01",
                    "versionNumber": "12",
                    "parameters": [
                      {"key":"${RemoteAppConfigPolicy.KEY_MAINTENANCE_BANNER_ENABLED}","valueType":"BOOLEAN","defaultValue":"false"},
                      {"key":"${RemoteAppConfigPolicy.KEY_MAINTENANCE_BANNER_TITLE}","valueType":"STRING","defaultValue":"Service Notice"},
                      {"key":"${RemoteAppConfigPolicy.KEY_MAINTENANCE_BANNER_MESSAGE}","valueType":"STRING","defaultValue":"Offline budgets & backups remain available on-device."},
                      {"key":"${RemoteAppConfigPolicy.KEY_EXPERIMENTAL_INSIGHTS_TIPS}","valueType":"BOOLEAN","defaultValue":"true"},
                      {"key":"${RemoteAppConfigPolicy.KEY_GRADUAL_ROLLOUT_NOTICE_ENABLED}","valueType":"BOOLEAN","defaultValue":"true"},
                      {"key":"${RemoteAppConfigPolicy.KEY_GRADUAL_ROLLOUT_PERCENT}","valueType":"NUMBER","defaultValue":"100"},
                      {"key":"${RemoteAppConfigPolicy.KEY_MIN_RECOMMENDED_VERSION_CODE}","valueType":"NUMBER","defaultValue":"11000000"}
                    ],
                    "versions": [
                      {"versionNumber":"12","updateTime":"2026-11-28T09:15:00Z","updateOrigin":"REST_API","description":"Unified Edition rollout target"},
                      {"versionNumber":"11","updateTime":"2026-11-25T08:00:00Z","updateOrigin":"CONSOLE","description":"Initial v1.2.0 config"}
                    ]
                  }
                }
            """.trimIndent()

            val AUDIT_ITEMS_JSON = """
                [
                  {"id":"aud-1","timestampIso":"2026-11-28T10:12:00Z","actor":"owner:dev_emul","action":"sync.completed","target":"ga4_30d","status":"ok","summary":"WIF telemetry snapshot synced"},
                  {"id":"aud-2","timestampIso":"2026-11-28T09:30:00Z","actor":"github-actions","action":"broadcast.release","target":"v11.0.0","status":"ok","summary":"Release notification sent cleanly"},
                  {"id":"aud-3","timestampIso":"2026-11-28T08:45:00Z","actor":"owner:dev_emul","action":"remote_config.publish","target":"v12","status":"ok","summary":"Updated rollout target to 100%"}
                ]
            """.trimIndent()

            val OVERVIEW_JSON = """
                {
                  "syncedAtMs": $NOW_MS,
                  "killSwitches": {"customBroadcastsEnabled": true, "releaseNotificationsEnabled": true},
                  "releases": {
                    "rateRemaining": 4950,
                    "releases": [
                      {
                        "tag": "v11.0.0",
                        "name": "duit. Unified Edition v11.0.0",
                        "channel": "stable",
                        "isPrerelease": false,
                        "publishedAt": "2026-11-28T09:24:20Z",
                        "htmlUrl": "https://github.com/erlingdiniy/duit-beta/releases/tag/v11.0.0",
                        "apkName": "duit-11.0.0.apk",
                        "apkSizeBytes": 10098114,
                        "downloadCount": 24,
                        "apkSha256": "3395d07bb7ae35a7c98dba669285c9ba653f94d9e81d8c9edd21f02b8ffe36e1",
                        "ledgerStatus": "sent",
                        "notes": "- Unified Public & Owner Edition APK.\n- Zero-Config Mission Control via Keystore ECDSA."
                      },
                      {
                        "tag": "v11.0.0-beta.2",
                        "name": "duit. 11.0.0-beta.2",
                        "channel": "beta",
                        "isPrerelease": true,
                        "publishedAt": "2026-11-23T12:32:30Z",
                        "htmlUrl": "https://github.com/erlingdiniy/duit-beta/releases/tag/v11.0.0-beta.2",
                        "apkName": "duit-11.0.0-beta.2.apk",
                        "apkSizeBytes": 9940120,
                        "downloadCount": 9,
                        "ledgerStatus": "preseeded_skip",
                        "notes": "- Public Beta validating FCM routing & updater."
                      }
                    ]
                  },
                  "ga4": $GA4_WRAPPER_JSON,
                  "remoteConfig": $RC_WRAPPER_JSON,
                  "recentAudit": $AUDIT_ITEMS_JSON,
                  "wifHealth": {"syncedAt": $NOW_MS}
                }
            """.trimIndent()

            val FEEDBACK_JSON = """
                {
                  "items": [
                    {
                      "id": "fb-1",
                      "createdAt": 1795858800000,
                      "updatedAt": 1795858800000,
                      "category": "Bug Report",
                      "message": "Receipt camera preview crops bottom margin on narrow screens.",
                      "contactEmail": "qa@example.invalid",
                      "appVersion": "11.0.0",
                      "status": "pending",
                      "priority": "high",
                      "isRead": false
                    },
                    {
                      "id": "fb-2",
                      "createdAt": 1795853700000,
                      "updatedAt": 1795853700000,
                      "category": "Feature Request",
                      "message": "Add weekly savings goal widget size for 2x2 launchers.",
                      "appVersion": "11.0.0",
                      "status": "pending",
                      "priority": "normal",
                      "isRead": false
                    },
                    {
                      "id": "fb-3",
                      "createdAt": 1795803600000,
                      "updatedAt": 1795803600000,
                      "category": "General Feedback",
                      "message": "Love Pure Liquid Glass navigation transitions!",
                      "appVersion": "11.0.0",
                      "status": "resolved",
                      "priority": "normal",
                      "isRead": true
                    }
                  ],
                  "summary": {"openFeatures": 1, "spam": 0}
                }
            """.trimIndent()

            val NOTIF_STATE_JSON = """
                {
                  "killSwitches": {"customBroadcastsEnabled": true, "releaseNotificationsEnabled": true},
                  "drafts": [
                    {"id":"dr-1","category":"announcement","audience":"beta","route":"home","title":"v11.0.1 Release Candidate","body":"Refined 5-tab Developer Center UI."}
                  ],
                  "templates": [
                    {"id":"tpl-1","name":"Maintenance","category":"maintenance","audience":"both","route":"home","title":"Scheduled Maintenance","body":"Local wallet and receipts stay offline-ready."}
                  ],
                  "autoScheduled": [],
                  "history": [
                    {
                      "id": "h-1",
                      "kind": "custom",
                      "type": "general",
                      "broadcastId": "duit-custom-20261128-1005",
                      "title": "Unified Edition v11.0.0 Live",
                      "body": "Verified mobile relay dry-run and release notification delivery.",
                      "audience": "beta",
                      "route": "insights",
                      "status": "validate_only",
                      "createdAt": $NOW_MS,
                      "workflowUrl": "https://github.com/erlingdiniy/duit-beta/actions/runs/46132517964"
                    }
                  ],
                  "workflows": []
                }
            """.trimIndent()

            val GITHUB_OVERVIEW_JSON = """
                {
                  "repository": {
                    "full_name": "erlingdiniy/duit-beta",
                    "private": false,
                    "visibility": "public",
                    "stargazers_count": 42,
                    "forks_count": 4,
                    "watchers_count": 42,
                    "open_issues_count": 1,
                    "default_branch": "main",
                    "pushed_at": "2026-11-28T09:30:00Z",
                    "html_url": "https://github.com/erlingdiniy/duit-beta"
                  },
                  "issues": [
                    {
                      "number": 14,
                      "title": "Polish 5-tab Developer Center navigation & typography",
                      "body": "Refine overview KPI cards, bottom capsule contrast and settings URL wrapping.",
                      "state": "open",
                      "user": {"login":"erlingdiniy"},
                      "labels": [{"name":"ui/ux"}],
                      "comments": 2,
                      "updated_at": "2026-11-28T10:00:00Z",
                      "html_url": "https://github.com/erlingdiniy/duit-beta/issues/14"
                    }
                  ],
                  "pullRequests": [],
                  "commits": [
                    {
                      "sha": "b88c8ee12345",
                      "commit": {"message":"chore(unified): wire Worker feedback and live URL","author":{"name":"erlingdiniy","date":"2026-11-28T09:00:00Z"}},
                      "html_url": "https://github.com/erlingdiniy/duit/commit/b88c8ee"
                    }
                  ],
                  "workflows": [
                    {
                      "id": 46132517964,
                      "name": "Automated Release Notifications",
                      "display_title": "v11.0.0 release notify",
                      "status": "completed",
                      "conclusion": "success",
                      "created_at": "2026-11-28T09:30:00Z",
                      "html_url": "https://github.com/erlingdiniy/duit-beta/actions/runs/46132517964",
                      "head_branch": "main"
                    }
                  ],
                  "rateRemaining": 4950
                }
            """.trimIndent()

            val INFRA_JSON = """
                {
                  "checkedAtMs": $NOW_MS,
                  "d1Database": {"activeDevices": 1, "feedbackRows": 3},
                  "githubApi": {"authMode": "github_app", "status": "verified_remote", "remaining": 4950, "limit": 5000},
                  "ga4Sync": {"status": "cached_sync", "syncedAtMs": $NOW_MS},
                  "remoteConfigSync": {"status": "cached_sync", "syncedAtMs": $NOW_MS},
                  "crashlyticsNote": "Free Spark tier active via Firebase Console."
                }
            """.trimIndent()

            val DEVICES_JSON = """
                {
                  "currentDeviceId": "dev_emulator_owner",
                  "devices": [
                    {
                      "device_id": "dev_emulator_owner",
                      "label": "Pixel Owner",
                      "key_algorithm": "ECDSA_P256_SHA256",
                      "hardware_backed": 1,
                      "attestation_summary": "StrongBox ECDSA P-256",
                      "created_at": $NOW_MS,
                      "last_seen_at": $NOW_MS,
                      "revoked_at": null
                    }
                  ]
                }
            """.trimIndent()
        }
    }

    @Test
    fun captureAllDeveloperCenterThemeScreenshots() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver

        val protector = TestProtector()
        val storage = TestStorage(
            DevCenterVaultState(
                isPinConfigured = true,
                pinEnvelope = "dummy_env",
                enrolledDeviceId = "dev_emulator_owner",
                enrolledDeviceLabel = "Pixel Owner",
                hardwareKeyAlias = "duit_dev_mc_ecdsa_p256_v1",
                hardwareKeyBacked = true,
                encryptedBackendAccessToken = "test_sealed::eyJ.mock.jwt",
                backendAccessExpiresAtMs = System.currentTimeMillis() + 900_000L,
                encryptedBackendRefreshToken = "test_sealed::drc_mock_refresh_token",
                backendRefreshExpiresAtMs = System.currentTimeMillis() + 86_400_000L,
                backendUrlOverride = "https://duit-mission-control.erlingdiniy.workers.dev",
                lastFullSyncIso = "2026-11-28T10:15:00Z"
            )
        )
        val vault = DevCenterVault(
            storage = storage,
            tokenProtector = protector,
            pinHashEngine = PinHashEngine(protector, iterations = 10_000)
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val auth = DevCenterAuthManager(vault = vault, biometricGateway = TestBiometric(), scope = scope)
        val transport = SanitizedMockTransport()
        val githubClient = DevCenterGitHubClient(transport)
        val backendClient = DevBackendClient(vault = vault, keyManager = TestKeyManager(), transport = transport)
        val vm = DevCenterViewModel(
            vault = vault,
            githubClient = githubClient,
            feedbackClient = DevCenterFeedbackClient(transport),
            analyticsClient = DevCenterAnalyticsClient(transport),
            analyticsOAuth = DevCenterAnalyticsOAuth(transport),
            authManager = auth,
            scope = scope,
            remoteConfigClient = DevCenterRemoteConfigClient(transport),
            githubManager = DevCenterGitHubManager(transport, githubClient),
            singleDeviceFcmClient = DevCenterSingleDeviceFcmClient(transport),
            infraMonitor = DevCenterInfraMonitor(transport),
            feedbackUrlProvider = { "https://example.invalid/feedback" },
            backendClient = backendClient
        )

        runBlocking {
            vm.updateComposerCategory(BroadcastCategory.GENERAL)
            vm.updateComposerAudience(BroadcastAudience.BETA)
            vm.updateComposerRoute(BroadcastDestination.INSIGHTS)
            vm.updateComposerTitle("Weekend Maintenance Window")
            vm.updateComposerBody("Offline budgeting and encrypted backups remain available on-device.")
            vm.refreshAll()
            vm.clearBanners()
        }

        var screenMode by mutableStateOf("enroll")
        var themeMode by mutableStateOf(ThemeMode.LIGHT)
        var visualTheme by mutableStateOf(VisualTheme.LIQUID_GLASS)
        var glassIntensity by mutableStateOf(GlassIntensity.BALANCED)
        var fontScaleMultiplier by mutableStateOf(1.0f)

        composeRule.setContent {
            val uiState by vm.uiState.collectAsStateWithLifecycle()
            val baseDensity = LocalDensity.current
            DuitTheme(
                themeMode = themeMode,
                visualTheme = visualTheme,
                glassIntensity = glassIntensity
            ) {
                CompositionLocalProvider(
                    LocalDensity provides Density(baseDensity.density, baseDensity.fontScale * fontScaleMultiplier),
                    LocalPrivacySettings provides PrivacySettings(),
                    LocalMotionPreferences provides MotionPreferences(
                        reduceMotion = true,
                        animationIntensity = AnimationIntensity.BALANCED,
                        hapticFeedbackEnabled = false
                    )
                ) {
                    when (screenMode) {
                        "enroll" -> DevEnrollmentScreen(
                            uiState = uiState,
                            onEnroll = { _, _, _ -> },
                            onUseManualMode = {},
                            onBack = {},
                            onLockNow = {}
                        )
                        "auth" -> DevCenterAuthScreen(
                            authState = DevAuthUiState(
                                stage = DevLockStage.LOCKED,
                                isBiometricEnabled = true,
                                biometricAvailability = BiometricAvailability.AVAILABLE
                            ),
                            onBack = {},
                            onSetupPin = { _, _ -> },
                            onSubmitPin = {},
                            onTriggerBiometric = {},
                            onDismissError = {}
                        )
                        else -> DeveloperCenterShell(
                            uiState = uiState,
                            authState = DevAuthUiState(
                                stage = DevLockStage.UNLOCKED,
                                isBiometricEnabled = true,
                                biometricAvailability = BiometricAvailability.AVAILABLE
                            ),
                            viewModel = vm,
                            onBack = {},
                            onLockNow = {}
                        )
                    }
                }
            }
        }

        fun snap(fileName: String) {
            composeRule.waitForIdle()
            val bmp = composeRule.onRoot().captureToImage().asAndroidBitmap()
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/v11_0_1_after")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Failed to create MediaStore entry for $fileName")
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    check(bmp.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                        "Bitmap compress failed for $fileName"
                    }
                } ?: error("Null OutputStream for $fileName")
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (t: Throwable) {
                resolver.delete(uri, null, null)
                throw t
            }
        }

        // 0-1. Enrollment & Auth screens
        screenMode = "enroll"
        visualTheme = VisualTheme.LIQUID_GLASS
        themeMode = ThemeMode.LIGHT
        snap("00_enroll_liquid_light.png")

        visualTheme = VisualTheme.CANDY_BUBBLE
        themeMode = ThemeMode.DARK
        snap("00b_enroll_candy_dark.png")

        screenMode = "auth"
        visualTheme = VisualTheme.LIQUID_GLASS
        themeMode = ThemeMode.LIGHT
        snap("01_auth_liquid_light.png")

        // 2-5. Overview across all 4 theme combinations (populated live snapshot)
        screenMode = "shell"
        composeRule.waitUntil(5_000L) {
            vm.uiState.value.analyticsState is AnalyticsSourceState.Ready &&
                vm.uiState.value.dashboard.releases.isNotEmpty()
        }
        vm.selectSection(DevCenterSection.OVERVIEW)
        vm.clearBanners()
        visualTheme = VisualTheme.LIQUID_GLASS
        themeMode = ThemeMode.LIGHT
        snap("02_overview_liquid_light.png")

        themeMode = ThemeMode.DARK
        snap("03_overview_liquid_dark.png")

        visualTheme = VisualTheme.CANDY_BUBBLE
        themeMode = ThemeMode.LIGHT
        snap("04_overview_candy_light.png")

        themeMode = ThemeMode.DARK
        snap("05_overview_candy_dark.png")

        // 6-8. Primary destinations: Analytics, Inbox, Notify
        vm.selectSection(DevCenterSection.ANALYTICS)
        visualTheme = VisualTheme.LIQUID_GLASS
        themeMode = ThemeMode.LIGHT
        snap("06_analytics_liquid_light.png")

        vm.selectSection(DevCenterSection.FEEDBACK)
        visualTheme = VisualTheme.LIQUID_GLASS
        themeMode = ThemeMode.DARK
        snap("07_inbox_liquid_dark.png")

        vm.selectSection(DevCenterSection.NOTIFICATIONS)
        visualTheme = VisualTheme.LIQUID_GLASS
        themeMode = ThemeMode.DARK
        snap("08_notify_liquid_dark.png")

        // 9. More Hub (click "More" in bottom bar to verify real navigation)
        composeRule.onNodeWithText("More").performClick()
        visualTheme = VisualTheme.LIQUID_GLASS
        themeMode = ThemeMode.LIGHT
        snap("09_more_hub_liquid_light.png")

        visualTheme = VisualTheme.CANDY_BUBBLE
        themeMode = ThemeMode.DARK
        snap("09b_more_hub_candy_dark.png")

        // 10-16. All 7 More sub-modules (populated)
        vm.selectSection(DevCenterSection.RELEASES)
        visualTheme = VisualTheme.LIQUID_GLASS
        themeMode = ThemeMode.LIGHT
        snap("10_releases_liquid_light.png")

        vm.selectSection(DevCenterSection.CRASH_REPORTS)
        visualTheme = VisualTheme.LIQUID_GLASS
        themeMode = ThemeMode.DARK
        snap("11_crash_reports_liquid_dark.png")

        vm.selectSection(DevCenterSection.REMOTE_CONFIG)
        visualTheme = VisualTheme.CANDY_BUBBLE
        themeMode = ThemeMode.LIGHT
        snap("12_remote_config_candy_light.png")

        vm.selectSection(DevCenterSection.GITHUB)
        visualTheme = VisualTheme.LIQUID_GLASS
        themeMode = ThemeMode.LIGHT
        snap("13_github_liquid_light.png")

        vm.selectSection(DevCenterSection.INFRASTRUCTURE)
        visualTheme = VisualTheme.CANDY_BUBBLE
        themeMode = ThemeMode.DARK
        snap("14_infrastructure_candy_dark.png")

        vm.selectSection(DevCenterSection.AUDIT_LOGS)
        visualTheme = VisualTheme.CANDY_BUBBLE
        themeMode = ThemeMode.LIGHT
        snap("15_audit_logs_candy_light.png")

        vm.selectSection(DevCenterSection.SETTINGS)
        visualTheme = VisualTheme.CANDY_BUBBLE
        themeMode = ThemeMode.DARK
        snap("16_settings_candy_dark.png")

        // 17. Liquid Glass RICH intensity on Overview Dark
        vm.selectSection(DevCenterSection.OVERVIEW)
        visualTheme = VisualTheme.LIQUID_GLASS
        themeMode = ThemeMode.DARK
        glassIntensity = GlassIntensity.RICH
        snap("17_overview_liquid_rich_dark.png")

        // 18. Offline / Error state on Overview Light
        transport.offlineMode = true
        vm.refreshAll()
        composeRule.waitUntil(5_000L) { !vm.uiState.value.isRefreshing }
        glassIntensity = GlassIntensity.BALANCED
        themeMode = ThemeMode.LIGHT
        snap("18_overview_offline_liquid_light.png")

        // 19-20. Large Font Scaling (1.3x) on Overview & More Hub
        vm.clearBanners()
        fontScaleMultiplier = 1.3f
        visualTheme = VisualTheme.CANDY_BUBBLE
        themeMode = ThemeMode.LIGHT
        snap("19_overview_font_scale_130.png")

        composeRule.onNodeWithText("More").performClick()
        visualTheme = VisualTheme.LIQUID_GLASS
        themeMode = ThemeMode.DARK
        snap("20_more_hub_font_scale_130.png")
    }
}
