package com.diniy.duit.devcenter

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DevCenterSingleDeviceFcmClientTest {

    @Test
    fun sendsSingleDeviceTokenPayloadWithoutTopicsAndRedactsSecrets() = runTest {
        val secretDevice = "cHzu1aQPQ_CIMbCVDmDlm-:APA91bH-XbVprVPJhkZWHFygSC2HB2IKs_2wPcdtfdJOduL7marKo_JE5Zy1Mz"
        val secretBearer = "ya29.ephemeralFcmBearerSecret123456"
        var sentBody = ""

        val client = DevCenterSingleDeviceFcmClient(object : DevHttpTransport {
            override suspend fun request(method: String, url: String, headers: Map<String, String>, body: String?): DevHttpResponse {
                sentBody = body.orEmpty()
                return DevHttpResponse(200, """{"name":"projects/duit-beta/messages/123"}""")
            }
        })

        val res = client.sendSingleDeviceTest(
            projectId = "duit-beta",
            deviceToken = secretDevice,
            ephemeralAccessToken = secretBearer,
            title = "Single Device Check",
            body = "Verifying isolated single device push delivery.",
            category = BroadcastCategory.GENERAL,
            route = BroadcastDestination.HOME,
            broadcastId = "duit-custom-test-99",
            validateOnly = true
        )
        assertTrue(res.isSuccess)
        assertTrue(sentBody.contains(""""token":"$secretDevice""""))
        assertFalse(sentBody.contains(""""topic":"""))
        assertFalse(sentBody.contains(""""condition":"""))
        assertFalse(res.getOrThrow().contains(secretDevice))
        assertFalse(res.getOrThrow().contains(secretBearer))
    }
}
