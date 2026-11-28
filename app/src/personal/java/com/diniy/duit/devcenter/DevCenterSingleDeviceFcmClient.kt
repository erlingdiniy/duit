package com.diniy.duit.devcenter

import org.json.JSONObject

/**
 * Isolated single-device FCM HTTP v1 test dispatcher.
 * Never persists tokens, never sets topic or condition targets, and redacts both tokens from all outputs.
 */
class DevCenterSingleDeviceFcmClient(
    private val transport: DevHttpTransport
) {
    private val projectRegex = Regex("""^[a-z0-9-]{4,64}$""")
    private val fcmTokenRegex = Regex("""^[A-Za-z0-9:_-]{60,512}$""")

    suspend fun sendSingleDeviceTest(
        projectId: String,
        deviceToken: String,
        ephemeralAccessToken: String,
        title: String,
        body: String,
        category: BroadcastCategory,
        route: BroadcastDestination,
        broadcastId: String,
        validateOnly: Boolean = false
    ): Result<String> {
        val cleanProject = projectId.trim()
        val cleanDevice = deviceToken.trim()
        val cleanBearer = ephemeralAccessToken.trim()
        if (!projectRegex.matches(cleanProject)) {
            return Result.failure(IllegalArgumentException("Invalid Firebase Project ID."))
        }
        if (!fcmTokenRegex.matches(cleanDevice)) {
            return Result.failure(IllegalArgumentException("Enter a valid single-device FCM registration token."))
        }
        if (cleanBearer.length < 20) {
            return Result.failure(IllegalArgumentException("Supply an ephemeral Firebase access token at send time."))
        }
        val cleanTitle = DevCenterValidators.cleanInputText(title, 100)
        val cleanBody = DevCenterValidators.cleanInputText(body, 220)
        if (cleanTitle.length !in 3..100 || cleanBody.length !in 8..220) {
            return Result.failure(IllegalArgumentException("Title (3-100 chars) and message (8-220 chars) are required."))
        }

        val payload = buildSingleDevicePayload(
            deviceToken = cleanDevice,
            title = cleanTitle,
            body = cleanBody,
            category = category,
            route = route,
            broadcastId = broadcastId.trim().ifBlank { DevCenterValidators.generateBroadcastId() },
            validateOnly = validateOnly
        )
        val url = "https://fcm.googleapis.com/v1/projects/$cleanProject/messages:send"
        val secrets = listOf(cleanDevice, cleanBearer)

        return runCatching {
            val res = transport.request(
                method = "POST",
                url = url,
                headers = mapOf(
                    "Authorization" to "Bearer $cleanBearer",
                    "Content-Type" to "application/json"
                ),
                body = payload
            )
            if (!res.isSuccess) {
                val msg = runCatching {
                    JSONObject(res.body).optJSONObject("error")?.optString("message")
                }.getOrNull().orEmpty().ifBlank { "FCM HTTP v1 rejected request (HTTP ${res.statusCode})." }
                throw IllegalStateException(DevCenterRedactor.redact(msg, secrets))
            }
            val msgName = runCatching { JSONObject(res.body).optString("name") }.getOrNull().orEmpty()
            val modeLabel = if (validateOnly) "Validated (dry-run)" else "Accepted by FCM"
            DevCenterRedactor.redact("$modeLabel for single device ($msgName). Tokens discarded.", secrets)
        }.recoverCatching { err ->
            throw IllegalStateException(DevCenterRedactor.redact(err.message ?: "Single-device push failed.", secrets))
        }
    }

    internal fun buildSingleDevicePayload(
        deviceToken: String,
        title: String,
        body: String,
        category: BroadcastCategory,
        route: BroadcastDestination,
        broadcastId: String,
        validateOnly: Boolean
    ): String {
        val messageObj = JSONObject().apply {
            put("token", deviceToken)
            put("notification", JSONObject().apply {
                put("title", title)
                put("body", body)
            })
            put("data", JSONObject().apply {
                put("title", title)
                put("body", body)
                put("route", route.apiValue)
                put("broadcast_id", broadcastId)
                put("broadcast_type", category.apiValue)
                put("audience", "single_device")
            })
            put("android", JSONObject().apply {
                put("ttl", "3600s")
                put("collapse_key", broadcastId)
                put("notification", JSONObject().apply {
                    put("channel_id", ANDROID_BROADCAST_CHANNEL_ID)
                    put("tag", broadcastId)
                    put("visibility", "PRIVATE")
                })
            })
        }
        return JSONObject().apply {
            put("validate_only", validateOnly)
            put("message", messageObj)
        }.toString()
    }
}
