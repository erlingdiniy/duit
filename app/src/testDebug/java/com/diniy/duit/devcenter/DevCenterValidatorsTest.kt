package com.diniy.duit.devcenter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevCenterValidatorsTest {

    @Test
    fun broadcastDraft_acceptsAllFourCategoriesAndAudiencesInDryRun() {
        val categories = listOf(
            BroadcastCategory.ANNOUNCEMENT,
            BroadcastCategory.MAINTENANCE,
            BroadcastCategory.UPDATE,
            BroadcastCategory.GENERAL
        )
        for (cat in categories) {
            for (aud in BroadcastAudience.entries) {
                val res = DevCenterValidators.validateBroadcastDraft(
                    category = cat,
                    audience = aud,
                    route = BroadcastDestination.INSIGHTS,
                    rawTitle = "Phase 19 Dry Run Check",
                    rawBody = "Verifying category ${cat.apiValue} for audience ${aud.apiValue}.",
                    rawBroadcastId = "duit-custom-${cat.apiValue}-${aud.apiValue}-01",
                    validateOnly = true,
                    confirmationPhrase = "",
                    rawReason = "",
                    usedBroadcastIds = emptySet(),
                    lastLiveDispatchMs = 0L,
                    customKillSwitchEnabled = false,
                    nowMs = 100_000L
                )
                assertTrue("Expected valid dry-run for ${cat.apiValue}/${aud.apiValue}", res is ValidationResult.Valid)
            }
        }
    }

    @Test
    fun liveBroadcast_enforcesKillSwitchConfirmationReasonDeduplicationAndCooldown() {
        val baseTime = 500_000L

        val blockedKill = DevCenterValidators.validateBroadcastDraft(
            category = BroadcastCategory.GENERAL,
            audience = BroadcastAudience.STABLE,
            route = BroadcastDestination.HOME,
            rawTitle = "Live Check Title",
            rawBody = "Live message body exceeding minimum length.",
            rawBroadcastId = "duit-custom-live-01",
            validateOnly = false,
            confirmationPhrase = "SEND STABLE",
            rawReason = "Operator check",
            usedBroadcastIds = emptySet(),
            lastLiveDispatchMs = 0L,
            customKillSwitchEnabled = false,
            nowMs = baseTime
        )
        assertTrue(blockedKill is ValidationResult.Invalid)
        assertTrue((blockedKill as ValidationResult.Invalid).errors.any { it.contains("kill switch") })

        val badPhrase = DevCenterValidators.validateBroadcastDraft(
            category = BroadcastCategory.ANNOUNCEMENT,
            audience = BroadcastAudience.BOTH,
            route = BroadcastDestination.CALENDAR,
            rawTitle = "Live Check Title",
            rawBody = "Live message body exceeding minimum length.",
            rawBroadcastId = "duit-custom-live-02",
            validateOnly = false,
            confirmationPhrase = "SEND BETA",
            rawReason = "Operator check",
            usedBroadcastIds = emptySet(),
            lastLiveDispatchMs = 0L,
            customKillSwitchEnabled = true,
            nowMs = baseTime
        )
        assertTrue(badPhrase is ValidationResult.Invalid)
        assertTrue((badPhrase as ValidationResult.Invalid).errors.any { it.contains("SEND ALL") })

        val cooldownBlocked = DevCenterValidators.validateBroadcastDraft(
            category = BroadcastCategory.UPDATE,
            audience = BroadcastAudience.BETA,
            route = BroadcastDestination.BUDGETS,
            rawTitle = "Live Check Title",
            rawBody = "Live message body exceeding minimum length.",
            rawBroadcastId = "duit-custom-live-03",
            validateOnly = false,
            confirmationPhrase = "SEND BETA",
            rawReason = "Operator check",
            usedBroadcastIds = emptySet(),
            lastLiveDispatchMs = baseTime - 10_000L,
            customKillSwitchEnabled = true,
            nowMs = baseTime
        )
        assertTrue(cooldownBlocked is ValidationResult.Invalid)
        assertTrue((cooldownBlocked as ValidationResult.Invalid).errors.any { it.contains("Cooldown") })

        val dupBlocked = DevCenterValidators.validateBroadcastDraft(
            category = BroadcastCategory.GENERAL,
            audience = BroadcastAudience.BETA,
            route = BroadcastDestination.HOME,
            rawTitle = "Live Check Title",
            rawBody = "Live message body exceeding minimum length.",
            rawBroadcastId = "duit-custom-dup-01",
            validateOnly = true,
            confirmationPhrase = "",
            rawReason = "",
            usedBroadcastIds = setOf("duit-custom-dup-01"),
            lastLiveDispatchMs = 0L,
            customKillSwitchEnabled = true,
            nowMs = baseTime
        )
        assertTrue(dupBlocked is ValidationResult.Invalid)
        assertTrue((dupBlocked as ValidationResult.Invalid).errors.any { it.contains("already used") })

        val validLive = DevCenterValidators.validateBroadcastDraft(
            category = BroadcastCategory.GENERAL,
            audience = BroadcastAudience.BOTH,
            route = BroadcastDestination.PLANNING,
            rawTitle = "Live Check Title",
            rawBody = "Live message body exceeding minimum length.",
            rawBroadcastId = "duit-custom-live-04",
            validateOnly = false,
            confirmationPhrase = "SEND ALL",
            rawReason = "Verified release announcement",
            usedBroadcastIds = emptySet(),
            lastLiveDispatchMs = baseTime - 31_000L,
            customKillSwitchEnabled = true,
            nowMs = baseTime
        )
        assertTrue(validLive is ValidationResult.Valid)
        val req = (validLive as ValidationResult.Valid).value
        assertFalse(req.validateOnly)
        assertEquals("SEND ALL", req.confirmationPhrase)
        assertEquals(BroadcastCategory.GENERAL, req.category)
    }

    @Test
    fun killSwitchConfirmation_validatesExactPhrases() {
        val bad = DevCenterValidators.validateKillSwitchConfirmation(
            target = KillSwitchTarget.CUSTOM_BROADCAST,
            enable = false,
            confirmationPhrase = "DISABLE RELEASE AUTOMATION",
            rawReason = "Emergency stop"
        )
        assertTrue(bad is ValidationResult.Invalid)

        val good = DevCenterValidators.validateKillSwitchConfirmation(
            target = KillSwitchTarget.RELEASE_AUTOMATION,
            enable = true,
            confirmationPhrase = "ENABLE RELEASE AUTOMATION",
            rawReason = "Resuming automated release notifications"
        )
        assertTrue(good is ValidationResult.Valid)
    }
}
