package com.nexusneuro.app.auth

import android.content.Context

enum class UnlockChallenge {
    /** Kimlik + şifre (ilk kurulum veya her 20. giriş). */
    FullCredentials,
    /** Parmak izi veya yüz (tek BiometricPrompt). */
    SingleBiometric,
    /** Önce parmak izi, sonra yüz — iki ayrı doğrulama (her 10. giriş). */
    DualBiometric,
}

/**
 * Remembers last account and successful unlock count for stepped auth.
 *
 * Rules for upcoming unlock N = count + 1:
 * - N % 20 == 0 → FullCredentials
 * - N % 10 == 0 → DualBiometric
 * - else → SingleBiometric
 */
class LoginSessionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var savedNationalId: String?
        get() = prefs.getString(KEY_NATIONAL_ID, null)
        private set(value) = prefs.edit().putString(KEY_NATIONAL_ID, value).apply()

    var savedDisplayName: String?
        get() = prefs.getString(KEY_DISPLAY_NAME, null)
        private set(value) = prefs.edit().putString(KEY_DISPLAY_NAME, value).apply()

    /** Number of successful dashboard unlocks so far. */
    var unlockCount: Int
        get() = prefs.getInt(KEY_UNLOCK_COUNT, 0)
        private set(value) = prefs.edit().putInt(KEY_UNLOCK_COUNT, value).apply()

    val hasSavedAccount: Boolean
        get() = !savedNationalId.isNullOrBlank()

    fun challengeForNextUnlock(): UnlockChallenge {
        if (!hasSavedAccount) return UnlockChallenge.FullCredentials
        val n = unlockCount + 1
        return when {
            n % 20 == 0 -> UnlockChallenge.FullCredentials
            n % 10 == 0 -> UnlockChallenge.DualBiometric
            else -> UnlockChallenge.SingleBiometric
        }
    }

    fun rememberSuccessfulUnlock(nationalId: String, displayName: String) {
        savedNationalId = nationalId
        savedDisplayName = displayName
        unlockCount = unlockCount + 1
    }

    fun clearAccount() {
        prefs.edit()
            .remove(KEY_NATIONAL_ID)
            .remove(KEY_DISPLAY_NAME)
            .remove(KEY_UNLOCK_COUNT)
            .apply()
    }

    companion object {
        private const val PREFS = "nexus_login_session"
        private const val KEY_NATIONAL_ID = "national_id"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_UNLOCK_COUNT = "unlock_count"
    }
}
