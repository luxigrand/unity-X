package com.nexusneuro.app.domain

import java.security.MessageDigest

enum class UserRole(val label: String) {
    ADMIN("Administrator"),
    PERSONNEL("Personel"),
}

data class UserAccount(
    val nationalId: String,
    val passwordHash: String,
    val role: UserRole,
    val displayName: String,
)

object Auth {
    private fun hashPassword(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(password.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private val users: Map<String, UserAccount> = mapOf(
        "57019027696" to UserAccount(
            nationalId = "57019027696",
            passwordHash = hashPassword("15041212.k"),
            role = UserRole.ADMIN,
            displayName = "Administrator",
        ),
        "5433307329" to UserAccount(
            nationalId = "5433307329",
            passwordHash = hashPassword("1599511324"),
            role = UserRole.PERSONNEL,
            displayName = "Personel",
        ),
    )

    fun authenticate(nationalId: String, password: String): UserAccount? {
        val user = users[nationalId.trim()] ?: return null
        if (user.passwordHash != hashPassword(password)) return null
        return user
    }

    fun allowedModes(role: UserRole): List<ControlMode> =
        if (role == UserRole.ADMIN) listOf(ControlMode.MANUAL)
        else listOf(ControlMode.AUTO, ControlMode.COPILOT)

    fun defaultMode(role: UserRole): ControlMode = allowedModes(role).first()

    fun canAccessManualControls(role: UserRole): Boolean = role == UserRole.ADMIN
}
