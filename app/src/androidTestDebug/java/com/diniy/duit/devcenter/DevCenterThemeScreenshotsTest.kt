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
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.platform.app.InstrumentationRegistry
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

    private class SanitizedMockTransport : DevHttpTransport {
        override suspend fun request(
            method: String,
            url: String,
            headers: Map<String, String>,
            body: String?
        ): DevHttpResponse = when {
            url.endsWith("/user") -> DevHttpResponse(200, """{"login":"erlingdiniy"}""", mapOf("x-ratelimit-remaining" to "4950"))
            url.contains("/releases") -> DevHttpResponse(
                200,
                """
                [
                  {
                    "tag_name": "v1.2.0",
                    "name": "duit. v1.2.0",
                    "prerelease": false,
                    "published_at": "2026-11-25T09:24:20Z",
                    "html_url": "https://github.com/erlingdiniy/duit-beta/releases/tag/v1.2.0",
                    "body": "- Pure Liquid Glass Theme with Light & Dark modes.\n- Private on-device Receipt Vault.",
                    "assets": [{
                      "name": "duit-1.2.0.apk",
                      "size": 8450511,
                      "download_count": 16,
                      "browser_download_url": "https://github.com/erlingdiniy/duit-beta/releases/download/v1.2.0/duit-1.2.0.apk",
                      "digest": "sha256:e883b223504d684a0deac38acf903be1"
                    }]
                  },
                  {
                    "tag_name": "v1.2.0-beta.2",
                    "name": "duit. 1.2.0-beta.2 — Public Beta",
                    "prerelease": true,
                    "published_at": "2026-11-23T12:32:30Z",
                    "html_url": "https://github.com/erlingdiniy/duit-beta/releases/tag/v1.2.0-beta.2",
                    "body": "- Public Beta validating FCM routing & updater.",
                    "assets": [{
                      "name": "duit-1.2.0-beta.2.apk",
                      "size": 8440120,
                      "download_count": 7,
                      "browser_download_url": "https://github.com/erlingdiniy/duit-beta/releases/download/v1.2.0-beta.2/duit-1.2.0-beta.2.apk"
                    }]
                  }
                ]
                """.trimIndent()
            )
            url.contains("release-notifications.json") -> DevHttpResponse(
                200,
                """
                {
                  "minExclusiveVersionCode": 1002000,
                  "releases": {
                    "v1.2.0": {
                      "tag": "v1.2.0",
                      "versionCode": 1002000,
                      "channel": "stable",
                      "status": "preseeded_skip",
                      "updatedAt": "2026-11-25T10:05:00Z",
                      "note": "Initial Stable baseline seeded during Phase 18A setup."
                    }
                  }
                }
                """.trimIndent()
            )
            url.contains("/actions/runs") -> DevHttpResponse(
                200,
                """
                {
                  "workflow_runs": [
                    {
                      "id": 45759172199,
                      "name": "Custom Developer Broadcast (Keyless WIF Relay)",
                      "display_title": "Dry run validate_only",
                      "status": "completed",
                      "conclusion": "success",
                      "created_at": "2026-11-26T05:04:20Z",
                      "html_url": "https://github.com/erlingdiniy/duit-developer-console/actions/runs/45759172199",
                      "head_branch": "main"
                    },
                    {
                      "id": 45751803660,
                      "name": "Automated Release Notifications",
                      "display_title": "Phase 18A validation",
                      "status": "completed",
                      "conclusion": "success",
                      "created_at": "2026-11-26T03:54:00Z",
                      "html_url": "https://github.com/erlingdiniy/duit-beta/actions/runs/45751803660",
                      "head_branch": "main"
                    }
                  ]
                }
                """.trimIndent()
            )
            url.endsWith("/ENABLE_LIVE_FCM_DISPATCH") ->
                DevHttpResponse(200, """{"name":"ENABLE_LIVE_FCM_DISPATCH","value":"true"}""")
            url.endsWith("/ENABLE_CUSTOM_FCM_DISPATCH") ->
                DevHttpResponse(200, """{"name":"ENABLE_CUSTOM_FCM_DISPATCH","value":"true"}""")
            else -> DevHttpResponse(404, "{}")
        }
    }

    @Test
    fun captureAllDeveloperCenterThemeScreenshots() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver

        val protector = TestProtector()
        val sampleHistory = listOf(
            BroadcastHistoryEntry(
                id = "custom-sample-1",
                kind = "custom",
                category = "general",
                broadcastId = "duit-custom-20261126-0504",
                title = "Phase 19 General Dry-Run Check",
                body = "Verified mobile custom relay with validate_only=true and zero public sends.",
                audience = "beta",
                topics = listOf("duit_beta_broadcasts"),
                route = "insights",
                status = "validate_only",
                statusLabel = "Dry-Run Dispatched",
                timestampIso = "2026-11-26T05:04:20Z",
                note = "Workflow custom-broadcast.yml HTTP 200 validateOnly=true.",
                workflowUrl = "https://github.com/erlingdiniy/duit-developer-console/actions/runs/45759172199"
            )
        )
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
                backendUrlOverride = "https://duit-mission-control.workers.dev",
                customHistory = sampleHistory
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
            feedbackUrlProvider = { "https://example.invalid/feedback" }
        )

        runBlocking {
            vm.updateComposerCategory(BroadcastCategory.GENERAL)
            vm.updateComposerAudience(BroadcastAudience.BETA)
            vm.updateComposerRoute(BroadcastDestination.INSIGHTS)
            vm.updateComposerTitle("Weekend Maintenance Window")
            vm.updateComposerBody("Offline budgeting and encrypted backups remain available on-device.")
            vm.refreshAll()
        }

        var screenMode by mutableStateOf("enroll")
        var themeMode by mutableStateOf(ThemeMode.LIGHT)
        var visualTheme by mutableStateOf(VisualTheme.LIQUID_GLASS)

        composeRule.setContent {
            val uiState by vm.uiState.collectAsStateWithLifecycle()
            DuitTheme(
                themeMode = themeMode,
                visualTheme = visualTheme,
                glassIntensity = GlassIntensity.BALANCED
            ) {
                CompositionLocalProvider(
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
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/p19_shots")
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

        // 0. Enrollment screen
        screenMode = "enroll"
        visualTheme = VisualTheme.LIQUID_GLASS
        themeMode = ThemeMode.LIGHT
        snap("00_enroll_liquid_light.png")

        visualTheme = VisualTheme.CANDY_BUBBLE
        themeMode = ThemeMode.DARK
        snap("00b_enroll_candy_dark.png")

        // 1. Auth screen in Pure Liquid Glass Light
        screenMode = "auth"
        visualTheme = VisualTheme.LIQUID_GLASS
        themeMode = ThemeMode.LIGHT
        snap("01_auth_liquid_light.png")

        // 2-5. Overview across all 4 theme combinations
        screenMode = "shell"
        composeRule.waitUntil(5_000L) { vm.uiState.value.dashboard.releases.isNotEmpty() }
        vm.selectSection(DevCenterSection.OVERVIEW)
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

        // 6-12. Remaining Developer Center sections
        vm.selectSection(DevCenterSection.ANALYTICS)
        visualTheme = VisualTheme.LIQUID_GLASS
        themeMode = ThemeMode.LIGHT
        snap("06_analytics_liquid_light.png")

        vm.selectSection(DevCenterSection.CRASH_REPORTS)
        visualTheme = VisualTheme.LIQUID_GLASS
        themeMode = ThemeMode.DARK
        snap("07_crash_reports_liquid_dark.png")

        vm.selectSection(DevCenterSection.NOTIFICATIONS)
        visualTheme = VisualTheme.LIQUID_GLASS
        themeMode = ThemeMode.DARK
        snap("08_notifications_liquid_dark.png")

        vm.selectSection(DevCenterSection.REMOTE_CONFIG)
        visualTheme = VisualTheme.CANDY_BUBBLE
        themeMode = ThemeMode.LIGHT
        snap("09_remote_config_candy_light.png")

        vm.selectSection(DevCenterSection.GITHUB)
        visualTheme = VisualTheme.LIQUID_GLASS
        themeMode = ThemeMode.LIGHT
        snap("10_github_liquid_light.png")

        vm.selectSection(DevCenterSection.INFRASTRUCTURE)
        visualTheme = VisualTheme.CANDY_BUBBLE
        themeMode = ThemeMode.DARK
        snap("11_infrastructure_candy_dark.png")

        vm.selectSection(DevCenterSection.AUDIT_LOGS)
        visualTheme = VisualTheme.CANDY_BUBBLE
        themeMode = ThemeMode.LIGHT
        snap("12_audit_logs_candy_light.png")

        vm.selectSection(DevCenterSection.RELEASES)
        visualTheme = VisualTheme.LIQUID_GLASS
        themeMode = ThemeMode.LIGHT
        snap("13_releases_liquid_light.png")

        vm.selectSection(DevCenterSection.FEEDBACK)
        visualTheme = VisualTheme.LIQUID_GLASS
        themeMode = ThemeMode.DARK
        snap("14_feedback_liquid_dark.png")

        vm.selectSection(DevCenterSection.SETTINGS)
        visualTheme = VisualTheme.CANDY_BUBBLE
        themeMode = ThemeMode.DARK
        snap("15_settings_candy_dark.png")
    }
}
