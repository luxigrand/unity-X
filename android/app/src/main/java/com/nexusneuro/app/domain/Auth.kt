package com.nexusneuro.app.domain

import java.security.MessageDigest

enum class UserRole(val label: String) {
    ADMIN("Administrator"),
    PERSONNEL("Personel"),
    PRESENTER("Sunum"),
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
        "159951" to UserAccount(
            nationalId = "159951",
            passwordHash = hashPassword("1324"),
            role = UserRole.PRESENTER,
            displayName = "Sunum",
        ),
    )

    fun authenticate(nationalId: String, password: String): UserAccount? {
        val user = users[nationalId.trim()] ?: return null
        if (user.passwordHash != hashPassword(password)) return null
        return user
    }

    fun findByNationalId(nationalId: String): UserAccount? =
        users[nationalId.trim()]

    fun allowedModes(role: UserRole): List<ControlMode> =
        when (role) {
            UserRole.ADMIN,
            UserRole.PRESENTER,
            -> listOf(ControlMode.MANUAL, ControlMode.AUTO, ControlMode.COPILOT)
            UserRole.PERSONNEL -> listOf(ControlMode.AUTO, ControlMode.COPILOT)
        }

    fun defaultMode(role: UserRole): ControlMode =
        when (role) {
            UserRole.ADMIN -> ControlMode.MANUAL
            UserRole.PRESENTER -> ControlMode.COPILOT
            UserRole.PERSONNEL -> ControlMode.AUTO
        }

    fun canAccessManualControls(role: UserRole): Boolean =
        role == UserRole.ADMIN || role == UserRole.PRESENTER
}
