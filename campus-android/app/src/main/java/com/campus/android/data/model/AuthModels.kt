package com.campus.android.data.model

import com.google.gson.annotations.SerializedName

data class PasswordLoginRequest(
    @SerializedName("login_id") val loginId: String,
    @SerializedName("password") val password: String
)

data class PasswordRegisterRequest(
    @SerializedName("security_email") val securityEmail: String,
    @SerializedName("verify_code") val verifyCode: String,
    @SerializedName("password") val password: String
)

data class PasswordResetRequest(
    @SerializedName("security_email") val securityEmail: String,
    @SerializedName("verify_code") val verifyCode: String,
    @SerializedName("new_password") val newPassword: String
)

data class ActivateAccountRequest(
    @SerializedName("security_email") val securityEmail: String,
    @SerializedName("verify_code") val verifyCode: String,
    @SerializedName("password") val password: String
)

data class VerifyOldSecurityEmailRequest(
    @SerializedName("old_verify_code") val oldVerifyCode: String
)

data class ChangeSecurityEmailRequest(
    @SerializedName("change_ticket") val changeTicket: String,
    @SerializedName("new_security_email") val newSecurityEmail: String,
    @SerializedName("new_verify_code") val newVerifyCode: String
)

data class ChangePasswordRequest(
    @SerializedName("old_password") val oldPassword: String,
    @SerializedName("new_password") val newPassword: String
)

data class EmailCodeSendRequest(
    @SerializedName("scene") val scene: String,
    @SerializedName("security_email") val securityEmail: String? = null,
    @SerializedName("change_ticket") val changeTicket: String? = null
)

data class RefreshTokenRequest(
    @SerializedName("refresh_token") val refreshToken: String
)

data class LoginData(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String?,
    @SerializedName("token_type") val tokenType: String?,
    @SerializedName("expires_in") val expiresIn: Long?,
    @SerializedName("user_info") val userInfo: UserInfo?
)

data class RefreshTokenData(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String?,
    @SerializedName("expires_in") val expiresIn: Long?
)

data class ChangeTicketData(
    @SerializedName("change_ticket") val changeTicket: String,
    @SerializedName("expires_in") val expiresIn: Long?
)
