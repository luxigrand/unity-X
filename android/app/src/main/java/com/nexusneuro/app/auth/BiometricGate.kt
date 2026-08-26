package com.nexusneuro.app.auth

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * System BiometricPrompt helpers.
 *
 * Note: Android does not always let apps force fingerprint-only vs face-only.
 * Dual flow runs two sequential prompts titled for finger then face so the
 * user completes both checks when both modalities are enrolled.
 */
object BiometricGate {
    private const val WITH_PIN = BIOMETRIC_STRONG or DEVICE_CREDENTIAL
    private const val BIO_ONLY = BIOMETRIC_STRONG

    fun canAuthenticate(activity: FragmentActivity, allowPin: Boolean = true): Boolean {
        val allowed = if (allowPin) WITH_PIN else BIO_ONLY
        return BiometricManager.from(activity).canAuthenticate(allowed) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    fun unavailableReason(activity: FragmentActivity, allowPin: Boolean = true): String {
        val allowed = if (allowPin) WITH_PIN else BIO_ONLY
        return when (BiometricManager.from(activity).canAuthenticate(allowed)) {
            BiometricManager.BIOMETRIC_SUCCESS -> ""
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                "Cihazda biyometrik kayıt yok. Ayarlardan parmak izi veya yüz ekleyin."
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                "Cihazda biyometrik donanım yok veya kullanılamıyor."
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                "Biyometri için güvenlik güncellemesi gerekli."
            else ->
                "Biyometrik doğrulama şu an kullanılamıyor."
        }
    }

    /** Parmak izi veya yüz (tek sefer). */
    fun authenticateSingle(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        prompt(
            activity = activity,
            title = "unity-X",
            subtitle = "Parmak izi veya yüz ile giriş",
            allowPin = true,
            onSuccess = onSuccess,
            onError = onError,
        )
    }

    /**
     * İki ayrı doğrulama: 1) parmak izi, 2) yüz.
     * İkisi de başarılı olmalı.
     */
    fun authenticateDual(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        if (!canAuthenticate(activity, allowPin = false) && !canAuthenticate(activity, allowPin = true)) {
            onError(unavailableReason(activity, allowPin = true))
            return
        }
        prompt(
            activity = activity,
            title = "unity-X — 1/2",
            subtitle = "Parmak izi ile doğrulayın",
            allowPin = false,
            negativeText = "İptal",
            onSuccess = {
                prompt(
                    activity = activity,
                    title = "unity-X — 2/2",
                    subtitle = "Yüz ile doğrulayın",
                    allowPin = false,
                    negativeText = "İptal",
                    onSuccess = onSuccess,
                    onError = onError,
                )
            },
            onError = onError,
        )
    }

    /** İlk kurulum / şifre sonrası (PIN yedekli). */
    fun authenticate(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) = authenticateSingle(activity, onSuccess, onError)

    private fun prompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        allowPin: Boolean,
        negativeText: String? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val allowed = if (allowPin) WITH_PIN else BIO_ONLY
        if (BiometricManager.from(activity).canAuthenticate(allowed) != BiometricManager.BIOMETRIC_SUCCESS) {
            // Dual step may fail with BIO_ONLY if only weak sensors — fall back to WITH_PIN once.
            if (!allowPin &&
                BiometricManager.from(activity).canAuthenticate(WITH_PIN) == BiometricManager.BIOMETRIC_SUCCESS
            ) {
                prompt(activity, title, subtitle, allowPin = true, onSuccess = onSuccess, onError = onError)
                return
            }
            onError(unavailableReason(activity, allowPin))
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val bioPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    ) {
                        onError("Biyolojik doğrulama iptal edildi.")
                    } else {
                        onError(errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    // Keep prompt open.
                }
            },
        )

        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(allowed)

        // Negative button only when DEVICE_CREDENTIAL is not included.
        if (!allowPin) {
            builder.setNegativeButtonText(negativeText ?: "İptal")
        }

        bioPrompt.authenticate(builder.build())
    }
}
