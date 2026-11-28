package com.diniy.duit.devcenter

import com.diniy.duit.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Compile-time isolation guarantee for public builds (beta / stable):
 * verifies that Developer Center is disabled and all Personal Edition implementation classes
 * do not exist in the beta classpath.
 */
class DeveloperCenterIsolationBetaTest {

    @Test
    fun publicEdition_excludesDeveloperCenterEntryPointAndClasses() {
        assertTrue(BuildConfig.PUBLIC_EDITION)
        assertFalse(BuildConfig.DEVELOPER_CENTER_ENABLED)
        assertTrue(BuildConfig.FCM_ENABLED)
        assertTrue(BuildConfig.ANALYTICS_ENABLED)
        assertFalse(BuildConfig.ANALYTICS_QA_TEST)
        assertEquals("", BuildConfig.DEV_ANALYTICS_OAUTH_CLIENT_ID)
        assertEquals("", BuildConfig.DEV_BACKEND_URL)
        assertFalse(BuildConfig.PRIVATE_DEV_EDITION)
        assertTrue(com.diniy.duit.analytics.UsageAnalytics.isSupported)
        assertFalse(DeveloperCenterEntryPoint.isAvailable)
        assertTrue(
            "Public beta build must retain BetaUpdates updater",
            runCatching { Class.forName("com.diniy.duit.update.BetaUpdates") }.isSuccess
        )

        val excludedClasses = listOf(
            "com.diniy.duit.devcenter.DevCenterVault",
            "com.diniy.duit.devcenter.DevCenterAuthManager",
            "com.diniy.duit.devcenter.DevCenterGitHubClient",
            "com.diniy.duit.devcenter.DevCenterFeedbackClient",
            "com.diniy.duit.devcenter.DevCenterAnalyticsClient",
            "com.diniy.duit.devcenter.DevCenterAnalyticsOAuth",
            "com.diniy.duit.devcenter.DevAnalyticsOAuthRedirectActivity",
            "com.diniy.duit.devcenter.DevCenterRemoteConfigClient",
            "com.diniy.duit.devcenter.DevCenterGitHubManager",
            "com.diniy.duit.devcenter.DevFeedbackTriageEngine",
            "com.diniy.duit.devcenter.DevCenterSingleDeviceFcmClient",
            "com.diniy.duit.devcenter.DevCenterInfraMonitor",
            "com.diniy.duit.devcenter.DevApkIntegrityChecker",
            "com.diniy.duit.devcenter.DevCenterViewModel",
            "com.diniy.duit.devcenter.DevHttpTransport",
            "com.diniy.duit.devcenter.DevCenterRedactor",
            "com.diniy.duit.devcenter.DevCenterValidators",
            "com.diniy.duit.devcenter.DevCenterModelsKt",
            "com.diniy.duit.devcenter.ui.DeveloperCenterShellKt",
            "com.diniy.duit.devcenter.ui.DevCenterThemeTokensKt",
            "com.diniy.duit.devcenter.ui.DevOverviewScreenKt",
            "com.diniy.duit.devcenter.ui.DevAnalyticsScreenKt",
            "com.diniy.duit.devcenter.ui.DevNotificationsScreenKt",
            "com.diniy.duit.devcenter.ui.DevCrashReportsScreenKt",
            "com.diniy.duit.devcenter.ui.DevReleasesScreenKt",
            "com.diniy.duit.devcenter.ui.DevRemoteConfigScreenKt",
            "com.diniy.duit.devcenter.ui.DevGitHubScreenKt",
            "com.diniy.duit.devcenter.ui.DevInfrastructureScreenKt",
            "com.diniy.duit.devcenter.ui.DevAuditLogsScreenKt",
            "com.diniy.duit.devcenter.ui.DevFeedbackScreenKt",
            "com.diniy.duit.devcenter.ui.DevSettingsScreenKt",
            "com.diniy.duit.devcenter.ui.DevCenterAuthScreenKt",
            "com.diniy.duit.devcenter.DevDeviceKeyManager",
            "com.diniy.duit.devcenter.DevBackendClient",
            "com.diniy.duit.devcenter.ui.DevEnrollmentScreenKt"
        )
        for (className in excludedClasses) {
            val missing = runCatching { Class.forName(className) }.isFailure
            assertTrue("Public beta build must exclude $className", missing)
        }
    }
}
