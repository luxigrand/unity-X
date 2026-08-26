package com.nexusneuro.consumer.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceRow(
    val id: String? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("device_key") val deviceKey: String,
    val name: String = "Telefon",
    @SerialName("is_primary") val isPrimary: Boolean = false,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
)

@Serializable
data class VitalsLatest(
    @SerialName("user_id") val userId: String,
    val bpm: Double? = null,
    val spo2: Double? = null,
    val availability: String = "UNKNOWN",
    @SerialName("measured_at") val measuredAt: String? = null,
    @SerialName("from_device_id") val fromDeviceId: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class VitalsUpsert(
    @SerialName("user_id") val userId: String,
    val bpm: Double? = null,
    val spo2: Double? = null,
    val availability: String = "UNKNOWN",
    @SerialName("measured_at") val measuredAt: String,
    @SerialName("from_device_id") val fromDeviceId: String? = null,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class ClaimArgs(
    @SerialName("p_device_key") val deviceKey: String,
    @SerialName("p_name") val name: String = "Telefon",
)

@Serializable
data class SetPrimaryArgs(
    @SerialName("p_device_key") val deviceKey: String,
)

@Serializable
data class TouchArgs(
    @SerialName("p_device_key") val deviceKey: String,
)
