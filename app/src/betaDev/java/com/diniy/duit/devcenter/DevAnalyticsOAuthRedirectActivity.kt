package com.diniy.duit.devcenter

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.diniy.duit.MainActivity

/**
 * Private Developer build-only OAuth 2.0 PKCE redirect receiver.
 * Captures the authorization callback URI into [DevAnalyticsOAuthBus] and finishes immediately
 * without exposing any analytics UI directly to external intents.
 */
class DevAnalyticsOAuthRedirectActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uriString = intent?.data?.toString()
        if (!uriString.isNullOrBlank()) {
            DevAnalyticsOAuthBus.publishCallbackUri(uriString)
        }
        val resumeIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(resumeIntent)
        finish()
    }
}
