package com.nexusneuro.consumer.data

import android.content.Context
import android.os.Build
import android.provider.Settings
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant

class AccountRepository(context: Context) {
    private val appContext = context.applicationContext
    private val client = SupabaseProvider.client
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val deviceKey: String =
        Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown-device"

    private val _sessionReady = MutableStateFlow(false)
    val sessionReady: StateFlow<Boolean> = _sessionReady.asStateFlow()

    private val _userId = MutableStateFlow<String?>(null)
    val userId: StateFlow<String?> = _userId.asStateFlow()

    private val _email = MutableStateFlow<String?>(null)
    val email: StateFlow<String?> = _email.asStateFlow()

    private val _isPrimary = MutableStateFlow(false)
    val isPrimary: StateFlow<Boolean> = _isPrimary.asStateFlow()

    private val _deviceId = MutableStateFlow<String?>(null)
    val deviceId: StateFlow<String?> = _deviceId.asStateFlow()

    private val _cloudVitals = MutableStateFlow<VitalsLatest?>(null)
    val cloudVitals: StateFlow<VitalsLatest?> = _cloudVitals.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        scope.launch {
            client.auth.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        _userId.value = status.session.user?.id
                        _email.value = status.session.user?.email
                        _sessionReady.value = true
                    }
                    else -> {
                        _sessionReady.value = false
                        _userId.value = null
                        _email.value = null
                        _isPrimary.value = false
                        _deviceId.value = null
                        _cloudVitals.value = null
                    }
                }
            }
        }
    }

    suspend fun signUp(email: String, password: String) {
        _error.value = null
        client.auth.signUpWith(Email, redirectUrl = AuthDeepLink.REDIRECT) {
            this.email = email.trim()
            this.password = password
        }
    }

    suspend fun signIn(email: String, password: String) {
        _error.value = null
        client.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    suspend fun resetPassword(email: String) {
        _error.value = null
        client.auth.resetPasswordForEmail(
            email = email.trim(),
            redirectUrl = AuthDeepLink.REDIRECT,
        )
    }

    suspend fun signOut() {
        client.auth.signOut()
    }

    suspend fun registerAndClaim(deviceName: String = Build.MODEL) {
        val row = client.postgrest.rpc(
            function = "claim_primary_device",
            parameters = ClaimArgs(deviceKey = deviceKey, name = deviceName),
        ).decodeAs<DeviceRow>()
        _isPrimary.value = row.isPrimary
        _deviceId.value = row.id
        refreshVitals()
    }

    suspend fun makePrimary() {
        try {
            val row = client.postgrest.rpc(
                function = "set_primary_device",
                parameters = SetPrimaryArgs(deviceKey = deviceKey),
            ).decodeAs<DeviceRow>()
            _isPrimary.value = row.isPrimary
            _deviceId.value = row.id
            _error.value = null
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            _error.value = if (
                msg.contains("PRIMARY_ACTIVE", ignoreCase = true) ||
                msg.contains("ana cihaz", ignoreCase = true)
            ) {
                "Başka bir ana cihaz hâlâ aktif. Önce orada çıkış yapın veya 2 dk bekleyin."
            } else {
                msg.ifBlank { "Ana cihaz yapılamadı" }
            }
            throw e
        }
    }

    suspend fun heartbeat() {
        if (_userId.value == null) return
        try {
            client.postgrest.rpc(
                function = "touch_device",
                parameters = TouchArgs(deviceKey = deviceKey),
            )
            val rows = client.from("devices").select(Columns.ALL) {
                filter { eq("device_key", deviceKey) }
            }.decodeList<DeviceRow>()
            val me = rows.firstOrNull()
            if (me != null) {
                _isPrimary.value = me.isPrimary
                _deviceId.value = me.id
            }
        } catch (_: Exception) {
        }
    }

    suspend fun upsertVitals(bpm: Double?, spo2: Double?, availability: String) {
        val uid = _userId.value ?: return
        if (!_isPrimary.value) return
        val now = Instant.now().toString()
        val payload = VitalsUpsert(
            userId = uid,
            bpm = bpm,
            spo2 = spo2,
            availability = availability,
            measuredAt = now,
            fromDeviceId = _deviceId.value,
            updatedAt = now,
        )
        client.from("vitals_latest").upsert(payload)
        _cloudVitals.value = VitalsLatest(
            userId = uid,
            bpm = bpm,
            spo2 = spo2,
            availability = availability,
            measuredAt = now,
            fromDeviceId = _deviceId.value,
            updatedAt = now,
        )
    }

    suspend fun refreshVitals() {
        val uid = _userId.value ?: return
        val rows = client.from("vitals_latest").select(Columns.ALL) {
            filter { eq("user_id", uid) }
        }.decodeList<VitalsLatest>()
        _cloudVitals.value = rows.firstOrNull()
    }

    fun clearError() {
        _error.value = null
    }
}
