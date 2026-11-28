package com.diniy.duit.devcenter

import com.diniy.duit.config.RemoteAppConfigPolicy
import org.json.JSONArray
import org.json.JSONObject

/**
 * Spark-plan Firebase Remote Config REST API v1 client.
 * Reads live templates + version history, enforces strict financial-safety key allowlisting,
 * publishes changes using ETag optimistic concurrency (`If-Match`), and supports rollback.
 */
class DevCenterRemoteConfigClient(
    private val transport: DevHttpTransport
) {
    private val projectIdRegex = Regex("""^[a-z0-9-]{4,64}$""")
    private val versionRegex = Regex("""^\d{1,12}$""")

    suspend fun fetchTemplateAndVersions(
        projectId: String,
        syncedAtIso: String,
        canSignInWithGoogle: Boolean,
        tokenResolver: suspend (forceRefresh: Boolean) -> String?
    ): RemoteConfigSourceState {
        val cleanProject = projectId.trim()
        if (!projectIdRegex.matches(cleanProject)) {
            return RemoteConfigSourceState.Error("Invalid Firebase Project ID.", syncedAtIso)
        }
        var token = tokenResolver(false)?.trim().orEmpty()
        if (token.isEmpty()) {
            return RemoteConfigSourceState.Unavailable(
                reason = "Connect Google OAuth with Remote Config access in Developer Settings to manage live flags.",
                canSignInWithGoogle = canSignInWithGoogle
            )
        }

        val tplUrl = "https://firebaseremoteconfig.googleapis.com/v1/projects/$cleanProject/remoteConfig"
        val verUrl = "$tplUrl:listVersions?pageSize=20"
        return try {
            val (tplRes, usedToken) = executeWithRefresh(
                method = "GET",
                url = tplUrl,
                extraHeaders = emptyMap(),
                body = null,
                initialToken = token,
                tokenResolver = tokenResolver
            )
            token = usedToken
            if (!tplRes.isSuccess) {
                return handleHttpFailure(tplRes.statusCode, tplRes.body, token, syncedAtIso, canSignInWithGoogle)
            }
            val etag = tplRes.headers["etag"].orEmpty()
            val (params, conditions, currentVersion) = parseTemplate(tplRes.body)

            val versionsRes = runCatching {
                transport.request(
                    method = "GET",
                    url = verUrl,
                    headers = mapOf(
                        "Authorization" to "Bearer $token",
                        "Accept" to "application/json"
                    )
                )
            }.getOrNull()
            val versions = if (versionsRes?.isSuccess == true) parseVersions(versionsRes.body) else emptyList()

            RemoteConfigSourceState.Ready(
                etag = etag,
                versionNumber = currentVersion,
                parameters = params,
                conditions = conditions,
                versions = versions,
                syncedAtIso = syncedAtIso
            )
        } catch (err: Exception) {
            RemoteConfigSourceState.Error(
                message = DevCenterRedactor.redact(err.message ?: "Remote Config request failed.", listOf(token)),
                syncedAtIso = syncedAtIso
            )
        }
    }

    suspend fun publishTemplate(
        projectId: String,
        etag: String,
        parameters: List<RemoteConfigParam>,
        conditions: List<RemoteConfigCondition>,
        description: String,
        syncedAtIso: String,
        tokenResolver: suspend (forceRefresh: Boolean) -> String?
    ): Result<RemoteConfigSourceState.Ready> {
        val cleanProject = projectId.trim()
        require(projectIdRegex.matches(cleanProject)) { "Invalid Firebase Project ID." }
        for (p in parameters) {
            if (!RemoteAppConfigPolicy.isSafeParameterKey(p.key)) {
                throw SecurityException("Blocked unsafe Remote Config key '${p.key}': financial/backup keys cannot be managed remotely.")
            }
        }
        val token = tokenResolver(false)?.trim().orEmpty()
        if (token.isEmpty()) {
            return Result.failure(IllegalStateException("Google OAuth access token required."))
        }

        val payloadJson = buildTemplatePayload(parameters, conditions, description)
        val url = "https://firebaseremoteconfig.googleapis.com/v1/projects/$cleanProject/remoteConfig"
        val cleanEtag = etag.trim().ifEmpty { "*" }

        return runCatching {
            val (res, usedToken) = executeWithRefresh(
                method = "PUT",
                url = url,
                extraHeaders = mapOf(
                    "If-Match" to cleanEtag,
                    "Content-Type" to "application/json; charset=UTF-8"
                ),
                body = payloadJson,
                initialToken = token,
                tokenResolver = tokenResolver
            )
            if (!res.isSuccess) {
                val msg = extractGoogleError(res.body) ?: "Remote Config publish failed (HTTP ${res.statusCode})."
                throw IllegalStateException(DevCenterRedactor.redact(msg, listOf(usedToken)))
            }
            val newEtag = res.headers["etag"].orEmpty().ifBlank { cleanEtag }
            val (parsedParams, parsedConds, newVer) = parseTemplate(res.body)
            val versionsRes = runCatching {
                transport.request(
                    "GET",
                    "$url:listVersions?pageSize=20",
                    mapOf("Authorization" to "Bearer $usedToken", "Accept" to "application/json")
                )
            }.getOrNull()
            val versions = if (versionsRes?.isSuccess == true) parseVersions(versionsRes.body) else emptyList()
            RemoteConfigSourceState.Ready(
                etag = newEtag,
                versionNumber = newVer,
                parameters = parsedParams,
                conditions = parsedConds,
                versions = versions,
                syncedAtIso = syncedAtIso
            )
        }
    }

    suspend fun rollbackToVersion(
        projectId: String,
        versionNumber: String,
        etag: String,
        syncedAtIso: String,
        tokenResolver: suspend (forceRefresh: Boolean) -> String?
    ): Result<RemoteConfigSourceState.Ready> {
        val cleanProject = projectId.trim()
        val cleanVer = versionNumber.trim()
        require(projectIdRegex.matches(cleanProject)) { "Invalid Firebase Project ID." }
        require(versionRegex.matches(cleanVer)) { "Invalid Remote Config version number." }

        val token = tokenResolver(false)?.trim().orEmpty()
        if (token.isEmpty()) {
            return Result.failure(IllegalStateException("Google OAuth access token required."))
        }
        val url = "https://firebaseremoteconfig.googleapis.com/v1/projects/$cleanProject/remoteConfig:rollback"
        val body = JSONObject().put("versionNumber", cleanVer).toString()
        val extra = buildMap {
            put("Content-Type", "application/json")
            if (etag.isNotBlank()) put("If-Match", etag)
        }

        return runCatching {
            val (res, usedToken) = executeWithRefresh(
                method = "POST",
                url = url,
                extraHeaders = extra,
                body = body,
                initialToken = token,
                tokenResolver = tokenResolver
            )
            if (!res.isSuccess) {
                val msg = extractGoogleError(res.body) ?: "Rollback failed (HTTP ${res.statusCode})."
                throw IllegalStateException(DevCenterRedactor.redact(msg, listOf(usedToken)))
            }
            val newEtag = res.headers["etag"].orEmpty().ifBlank { etag }
            val (parsedParams, parsedConds, newVer) = parseTemplate(res.body)
            val versionsRes = runCatching {
                transport.request(
                    "GET",
                    "https://firebaseremoteconfig.googleapis.com/v1/projects/$cleanProject/remoteConfig:listVersions?pageSize=20",
                    mapOf("Authorization" to "Bearer $usedToken", "Accept" to "application/json")
                )
            }.getOrNull()
            val versions = if (versionsRes?.isSuccess == true) parseVersions(versionsRes.body) else emptyList()
            RemoteConfigSourceState.Ready(
                etag = newEtag,
                versionNumber = newVer,
                parameters = parsedParams,
                conditions = parsedConds,
                versions = versions,
                syncedAtIso = syncedAtIso
            )
        }
    }

    fun buildEmergencyDisableParameters(current: List<RemoteConfigParam>): List<RemoteConfigParam> {
        val byKey = current.associateBy { it.key }.toMutableMap()
        byKey[RemoteAppConfigPolicy.KEY_MAINTENANCE_BANNER_ENABLED] = RemoteConfigParam(
            key = RemoteAppConfigPolicy.KEY_MAINTENANCE_BANNER_ENABLED,
            valueType = "BOOLEAN",
            defaultValue = "false",
            description = "Emergency disable maintenance notice",
            isSafeKey = true
        )
        byKey[RemoteAppConfigPolicy.KEY_EXPERIMENTAL_INSIGHTS_TIPS] = RemoteConfigParam(
            key = RemoteAppConfigPolicy.KEY_EXPERIMENTAL_INSIGHTS_TIPS,
            valueType = "BOOLEAN",
            defaultValue = "false",
            description = "Emergency disable experimental insights tips",
            isSafeKey = true
        )
        byKey[RemoteAppConfigPolicy.KEY_GRADUAL_ROLLOUT_NOTICE_ENABLED] = RemoteConfigParam(
            key = RemoteAppConfigPolicy.KEY_GRADUAL_ROLLOUT_NOTICE_ENABLED,
            valueType = "BOOLEAN",
            defaultValue = "false",
            description = "Emergency pause gradual rollout notice",
            isSafeKey = true
        )
        byKey[RemoteAppConfigPolicy.KEY_GRADUAL_ROLLOUT_PERCENT] = RemoteConfigParam(
            key = RemoteAppConfigPolicy.KEY_GRADUAL_ROLLOUT_PERCENT,
            valueType = "NUMBER",
            defaultValue = "0",
            description = "Gradual rollout percentage set to 0",
            isSafeKey = true
        )
        return byKey.values.sortedBy { it.key }
    }

    internal fun buildTemplatePayload(
        parameters: List<RemoteConfigParam>,
        conditions: List<RemoteConfigCondition>,
        description: String
    ): String {
        val paramsJson = JSONObject()
        for (p in parameters) {
            if (!RemoteAppConfigPolicy.isSafeParameterKey(p.key)) continue
            val sanitizedValue = when (p.key) {
                RemoteAppConfigPolicy.KEY_MAINTENANCE_BANNER_TITLE ->
                    RemoteAppConfigPolicy.sanitizeText(p.defaultValue, 80)
                RemoteAppConfigPolicy.KEY_MAINTENANCE_BANNER_MESSAGE ->
                    RemoteAppConfigPolicy.sanitizeText(p.defaultValue, 240)
                RemoteAppConfigPolicy.KEY_GRADUAL_ROLLOUT_PERCENT ->
                    (p.defaultValue.toIntOrNull() ?: 0).coerceIn(0, 100).toString()
                RemoteAppConfigPolicy.KEY_MIN_RECOMMENDED_VERSION_CODE ->
                    (p.defaultValue.toLongOrNull() ?: 0L).coerceAtLeast(0L).toString()
                else -> if (p.defaultValue.equals("true", ignoreCase = true)) "true" else "false"
            }
            paramsJson.put(
                p.key,
                JSONObject().apply {
                    put("defaultValue", JSONObject().put("value", sanitizedValue))
                    put("valueType", p.valueType)
                    if (p.description.isNotBlank()) put("description", p.description.take(200))
                }
            )
        }
        val condsArray = JSONArray().apply {
            conditions.forEach { c ->
                put(
                    JSONObject().apply {
                        put("name", c.name)
                        put("expression", c.expression)
                        if (c.tagColor.isNotBlank()) put("tagColor", c.tagColor)
                    }
                )
            }
        }
        return JSONObject().apply {
            put("parameters", paramsJson)
            if (condsArray.length() > 0) put("conditions", condsArray)
            put("version", JSONObject().put("description", description.trim().take(120)))
        }.toString()
    }

    internal fun parseTemplate(rawJson: String): Triple<List<RemoteConfigParam>, List<RemoteConfigCondition>, String> {
        val root = runCatching { JSONObject(rawJson) }.getOrNull()
            ?: return Triple(emptyList(), emptyList(), "")
        val paramsObj = root.optJSONObject("parameters") ?: JSONObject()
        val params = buildList {
            val keys = paramsObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val item = paramsObj.optJSONObject(key) ?: continue
                add(
                    RemoteConfigParam(
                        key = key,
                        valueType = item.optString("valueType", "STRING"),
                        defaultValue = item.optJSONObject("defaultValue")?.optString("value").orEmpty(),
                        description = item.optString("description"),
                        isSafeKey = RemoteAppConfigPolicy.isSafeParameterKey(key)
                    )
                )
            }
        }.sortedBy { it.key }

        val condArr = root.optJSONArray("conditions") ?: JSONArray()
        val conds = buildList {
            for (i in 0 until condArr.length()) {
                val c = condArr.optJSONObject(i) ?: continue
                add(
                    RemoteConfigCondition(
                        name = c.optString("name"),
                        expression = c.optString("expression"),
                        tagColor = c.optString("tagColor")
                    )
                )
            }
        }
        val versionNum = root.optJSONObject("version")?.optString("versionNumber").orEmpty()
        return Triple(params, conds, versionNum)
    }

    internal fun parseVersions(rawJson: String): List<RemoteConfigVersionInfo> {
        val arr = runCatching { JSONObject(rawJson).optJSONArray("versions") }.getOrNull() ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val v = arr.optJSONObject(i) ?: continue
                add(
                    RemoteConfigVersionInfo(
                        versionNumber = v.optString("versionNumber"),
                        updateTimeIso = v.optString("updateTime"),
                        updateUserEmail = v.optJSONObject("updateUser")?.optString("email").orEmpty(),
                        updateOrigin = v.optString("updateOrigin"),
                        updateType = v.optString("updateType"),
                        description = v.optString("description")
                    )
                )
            }
        }
    }

    private suspend fun executeWithRefresh(
        method: String,
        url: String,
        extraHeaders: Map<String, String>,
        body: String?,
        initialToken: String,
        tokenResolver: suspend (forceRefresh: Boolean) -> String?
    ): Pair<DevHttpResponse, String> {
        var active = initialToken
        var res = transport.request(
            method = method,
            url = url,
            headers = mapOf("Authorization" to "Bearer $active", "Accept" to "application/json") + extraHeaders,
            body = body
        )
        if (res.statusCode == 401) {
            val refreshed = tokenResolver(true)?.trim().orEmpty()
            if (refreshed.isNotEmpty() && refreshed != active) {
                active = refreshed
                res = transport.request(
                    method = method,
                    url = url,
                    headers = mapOf("Authorization" to "Bearer $active", "Accept" to "application/json") + extraHeaders,
                    body = body
                )
            }
        }
        return res to active
    }

    private fun handleHttpFailure(
        code: Int,
        body: String,
        token: String,
        syncedAtIso: String,
        canSignInWithGoogle: Boolean
    ): RemoteConfigSourceState {
        return when (code) {
            401, 403 -> RemoteConfigSourceState.Unavailable(
                reason = "Google account lacks Remote Config permission (HTTP $code). Sign in with Remote Config scope enabled.",
                canSignInWithGoogle = canSignInWithGoogle
            )
            else -> RemoteConfigSourceState.Error(
                message = DevCenterRedactor.redact(
                    extractGoogleError(body) ?: "Remote Config API returned HTTP $code.",
                    listOf(token)
                ),
                syncedAtIso = syncedAtIso
            )
        }
    }

    private fun extractGoogleError(rawBody: String): String? =
        runCatching { JSONObject(rawBody).optJSONObject("error")?.optString("message") }
            .getOrNull()?.takeIf { it.isNotBlank() }
}
