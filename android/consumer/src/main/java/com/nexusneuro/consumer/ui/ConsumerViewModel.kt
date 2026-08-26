package com.nexusneuro.consumer.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexusneuro.consumer.data.AccountRepository
import com.nexusneuro.consumer.wear.VitalsProtocol
import com.nexusneuro.consumer.wear.WearVitalsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ConsumerViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = AccountRepository(application)

    var emailInput by mutableStateOf("")
    var passwordInput by mutableStateOf("")
    var isRegisterMode by mutableStateOf(false)
    var busy by mutableStateOf(false)
        private set
    var authError by mutableStateOf<String?>(null)
        private set

    var loggedIn by mutableStateOf(false)
        private set
    var userEmail by mutableStateOf<String?>(null)
        private set
    var isPrimary by mutableStateOf(false)
        private set

    var displayBpm by mutableStateOf<Float?>(null)
        private set
    var displaySpo2 by mutableStateOf<Float?>(null)
        private set
    var connectionLabel by mutableStateOf("Bağlantı yok")
        private set
    var statusLabel by mutableStateOf("Giriş yapın")
        private set
    var primaryHint by mutableStateOf<String?>(null)
        private set

    private var loopJob: Job? = null

    init {
        viewModelScope.launch {
            repo.sessionReady.collect { ready ->
                loggedIn = ready
                if (ready) {
                    userEmail = repo.email.value
                    onLoggedIn()
                } else {
                    stopLoops()
                    WearVitalsRepository.acceptWearMessages = false
                    displayBpm = null
                    displaySpo2 = null
                    connectionLabel = "Bağlantı yok"
                    statusLabel = "Giriş yapın"
                }
            }
        }
        viewModelScope.launch {
            repo.isPrimary.collect { primary ->
                isPrimary = primary
                WearVitalsRepository.acceptWearMessages = primary
                primaryHint = if (primary) {
                    null
                } else {
                    "Bu cihaz ana değil — veriler buluttan gelir"
                }
            }
        }
        viewModelScope.launch {
            repo.error.collect { err ->
                if (err != null) authError = err
            }
        }
    }

    private fun onLoggedIn() {
        viewModelScope.launch {
            try {
                repo.registerAndClaim()
                startLoops()
            } catch (e: Exception) {
                authError = e.message ?: "Cihaz kaydı başarısız"
            }
        }
    }

    private fun startLoops() {
        stopLoops()
        loopJob = viewModelScope.launch {
            var lastUpload = 0L
            while (isActive) {
                try {
                    repo.heartbeat()
                    isPrimary = repo.isPrimary.value
                    WearVitalsRepository.acceptWearMessages = isPrimary

                    if (isPrimary) {
                        val w = WearVitalsRepository.vitals.value
                        if (w.isFresh() && w.bpm != null) {
                            displayBpm = w.bpm
                            displaySpo2 = w.spo2
                            connectionLabel = "Saat bağlı"
                            statusLabel = "Canlı nabız (ana cihaz)"
                            val now = System.currentTimeMillis()
                            if (now - lastUpload >= 2000L) {
                                repo.upsertVitals(
                                    bpm = w.bpm.toDouble(),
                                    spo2 = w.spo2?.toDouble(),
                                    availability = w.availability,
                                )
                                lastUpload = now
                            }
                        } else {
                            connectionLabel = "Saat bekleniyor"
                            statusLabel = "Saatte unity-X → BAŞLAT"
                            // keep last BPM briefly
                            val age = if (w.timestampMs > 0) {
                                System.currentTimeMillis() - w.timestampMs
                            } else {
                                Long.MAX_VALUE
                            }
                            if (age > VitalsProtocol.FRESH_MS * 3) {
                                displayBpm = null
                                displaySpo2 = null
                            }
                        }
                    } else {
                        repo.refreshVitals()
                        val cloud = repo.cloudVitals.value
                        val measured = cloud?.measuredAt
                        val fresh = measured != null // show last known
                        if (cloud?.bpm != null) {
                            displayBpm = cloud.bpm.toFloat()
                            displaySpo2 = cloud.spo2?.toFloat()
                            connectionLabel = "Bulut (ana cihaz)"
                            statusLabel = "Ana cihaz ölçüm yapıyor"
                        } else {
                            displayBpm = null
                            displaySpo2 = null
                            connectionLabel = "Ana cihaz bekleniyor"
                            statusLabel = "Ana telefonda saati başlatın"
                        }
                        @Suppress("UNUSED_VARIABLE")
                        val unused = fresh
                    }
                } catch (e: Exception) {
                    statusLabel = e.message ?: "Senkron hatası"
                }
                delay(1_000)
            }
        }
    }

    private fun stopLoops() {
        loopJob?.cancel()
        loopJob = null
    }

    fun submitAuth() {
        viewModelScope.launch {
            busy = true
            authError = null
            try {
                if (isRegisterMode) {
                    if (passwordInput.length < 6) {
                        authError = "Şifre en az 6 karakter olmalı"
                        return@launch
                    }
                    repo.signUp(emailInput, passwordInput)
                    try {
                        repo.signIn(emailInput, passwordInput)
                    } catch (_: Exception) {
                        authError =
                            "Kayıt alındı. Maildeki onay linkine tıklayın, sonra giriş yapın."
                    }
                } else {
                    repo.signIn(emailInput, passwordInput)
                }
            } catch (e: Exception) {
                authError = friendlyAuthError(e)
            } finally {
                busy = false
            }
        }
    }

    fun sendPasswordReset() {
        viewModelScope.launch {
            if (emailInput.isBlank()) {
                authError = "Önce e-posta adresinizi yazın"
                return@launch
            }
            busy = true
            authError = null
            try {
                repo.resetPassword(emailInput)
                authError = "Şifre sıfırlama maili gönderildi. Gelen kutuyu kontrol edin."
            } catch (e: Exception) {
                authError = friendlyAuthError(e)
            } finally {
                busy = false
            }
        }
    }

    private fun friendlyAuthError(e: Exception): String {
        val raw = (e.message ?: "").lowercase()
        return when {
            "invalid_credentials" in raw || "invalid login" in raw ->
                "E-posta veya şifre hatalı. Şifreyi unuttuysanız aşağıdan sıfırlayın."
            "user_already" in raw || "already registered" in raw || "already been registered" in raw ->
                "Bu e-posta zaten kayıtlı. Giriş yapın veya şifre sıfırlayın."
            "email_not_confirmed" in raw || "not confirmed" in raw ->
                "E-posta henüz onaylanmamış. Maildeki linke tıklayın."
            "network" in raw || "unable to resolve" in raw || "timeout" in raw ->
                "İnternet bağlantısını kontrol edin."
            else -> {
                // Strip huge HTTP dumps — keep short
                val firstLine = e.message?.lineSequence()?.firstOrNull()?.take(120)
                firstLine?.takeIf { it.isNotBlank() } ?: "İşlem başarısız"
            }
        }
    }

    fun claimPrimary() {
        viewModelScope.launch {
            busy = true
            authError = null
            try {
                repo.makePrimary()
                isPrimary = true
                WearVitalsRepository.acceptWearMessages = true
            } catch (_: Exception) {
                // authError set in repo
            } finally {
                busy = false
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            stopLoops()
            WearVitalsRepository.acceptWearMessages = false
            repo.signOut()
        }
    }
}
