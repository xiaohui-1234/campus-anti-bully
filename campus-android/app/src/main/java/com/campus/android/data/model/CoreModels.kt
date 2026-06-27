package com.campus.android.data.model

import com.google.gson.annotations.SerializedName

data class PageResult<T>(
    @SerializedName("records") val records: List<T> = emptyList(),
    @SerializedName("total") val total: Long = 0,
    @SerializedName("page") val page: Long = 1,
    @SerializedName("size") val size: Long = 20
)

data class UserInfo(
    @SerializedName("user_id") val userId: String,
    @SerializedName("nickname") val nickname: String?,
    @SerializedName("avatar_url") val avatarUrl: String?,
    @SerializedName("phone") val phone: String?,
    @SerializedName("email") val email: String?,
    @SerializedName("security_email_masked") val securityEmailMasked: String?,
    @SerializedName("security_email_verified") val securityEmailVerified: Boolean?,
    @SerializedName("password_enabled") val passwordEnabled: Boolean?,
    @SerializedName("role") val role: String?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("is_new_user") val isNewUser: Boolean?
)

data class UpdateUserRequest(
    @SerializedName("nickname") val nickname: String?,
    @SerializedName("phone") val phone: String?,
    @SerializedName("email") val email: String?
)

data class AvatarData(
    @SerializedName("avatar_url") val avatarUrl: String
)

data class DeviceInfo(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("product_type") val productType: String?,
    @SerializedName("device_name") val deviceName: String?,
    @SerializedName("location") val location: String?,
    @SerializedName("note") val note: String?,
    @SerializedName("online_status") val onlineStatus: String?,
    @SerializedName("last_online_time") val lastOnlineTime: String?,
    @SerializedName("bind_time") val bindTime: String?
)

data class BindDeviceRequest(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("bind_code") val bindCode: String,
    @SerializedName("device_name") val deviceName: String?
)

data class UpdateDeviceRequest(
    @SerializedName("device_name") val deviceName: String?,
    @SerializedName("location") val location: String?,
    @SerializedName("note") val note: String?
)

data class EventInfo(
    @SerializedName("event_id") val eventId: String,
    @SerializedName("device_id") val deviceId: String?,
    @SerializedName("event_type") val eventType: String?,
    @SerializedName("alarm_info") val alarmInfo: String?,
    @SerializedName("file_status") val fileStatus: String?,
    @SerializedName("file_url") val fileUrl: String?,
    @SerializedName("push_status") val pushStatus: String?,
    @SerializedName("read_status") val readStatus: String?,
    @SerializedName("event_time") val eventTime: String?
)

data class RefreshUrlRequest(
    @SerializedName("expire_seconds") val expireSeconds: Int = 3600
)

data class RefreshUrlData(
    @SerializedName("event_id") val eventId: String,
    @SerializedName("file_url") val fileUrl: String,
    @SerializedName("expire_seconds") val expireSeconds: Int,
    @SerializedName("expire_at") val expireAt: String?
)
