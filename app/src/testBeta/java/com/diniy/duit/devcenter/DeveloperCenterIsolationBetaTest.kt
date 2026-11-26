package com.diniy.duit.devcenter

import com.diniy.duit.BuildConfig
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
        assertFalse(DeveloperCenterEntryPoint.isAvailable)

        val excludedClasses = listOf(
            "com.diniy.duit.devcenter.DevCenterVault",
            "com.diniy.duit.devcenter.DevCenterAuthManager",
            "com.diniy.duit.devcenter.DevCenterGitHubClient",
            "com.diniy.duit.devcenter.DevCenterFeedbackClient",
            "com.diniy.duit.devcenter.DevCenterViewModel",
            "com.diniy.duit.devcenter.DevHttpTransport",
            "com.diniy.duit.devcenter.DevCenterRedactor",
            "com.diniy.duit.devcenter.DevCenterValidators",
            "com.diniy.duit.devcenter.DevCenterModelsKt",
            "com.diniy.duit.devcenter.ui.DeveloperCenterShellKt",
            "com.diniy.duit.devcenter.ui.DevOverviewScreenKt",
            "com.diniy.duit.devcenter.ui.DevNotificationsScreenKt",
            "com.diniy.duit.devcenter.ui.DevHistoryScreenKt",
            "com.diniy.duit.devcenter.ui.DevReleasesScreenKt",
            "com.diniy.duit.devcenter.ui.DevFeedbackScreenKt",
            "com.diniy.duit.devcenter.ui.DevSettingsScreenKt",
            "com.diniy.duit.devcenter.ui.DevCenterAuthScreenKt"
        )
        for (className in excludedClasses) {
            val missing = runCatching { Class.forName(className) }.isFailure
            assertTrue("Public beta build must exclude $className", missing)
        }
    }
}
