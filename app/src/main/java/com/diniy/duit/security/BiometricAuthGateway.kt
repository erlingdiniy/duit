package com.diniy.duit.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

enum class BiometricAvailability {
    AVAILABLE,
    NONE_ENROLLED,
    UNAVAILABLE,
    CHANGED_OR_INVALIDATED
}

sealed class BiometricAuthResult {
    data object Succeeded : BiometricAuthResult()
    data object Cancelled : BiometricAuthResult()
    data class Error(val code: Int, val message: String) : BiometricAuthResult()
}

interface BiometricAuthGateway {
    fun checkAvailability(): BiometricAvailability
    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negativeButtonText: String,
        onResult: (BiometricAuthResult) -> Unit
    )
}

class AndroidXBiometricAuthGateway(
    context: Context
) : BiometricAuthGateway {

    private val appContext = context.applicationContext

    override fun checkAvailability(): BiometricAvailability {
        val manager = BiometricManager.from(appContext)
        return when (manager.canAuthenticate(ALLOWED_AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NONE_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> BiometricAvailability.CHANGED_OR_INVALIDATED
            else -> BiometricAvailability.UNAVAILABLE
        }
    }

    override fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negativeButtonText: String,
        onResult: (BiometricAuthResult) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onResult(BiometricAuthResult.Succeeded)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                val cancelledCodes = setOf(
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_CANCELED
                )
                if (errorCode in cancelledCodes) {
                    onResult(BiometricAuthResult.Cancelled)
                } else {
                    onResult(BiometricAuthResult.Error(errorCode, errString.toString()))
                }
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
            .setConfirmationRequired(false)
            .build()

        prompt.authenticate(promptInfo)
    }

    companion object {
        private const val ALLOWED_AUTHENTICATORS =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
    }
}
