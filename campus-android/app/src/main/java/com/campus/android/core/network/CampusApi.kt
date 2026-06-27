package com.campus.android.core.network

import com.campus.android.data.model.*
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

interface CampusApi {

    @POST("auth/password/login")
    suspend fun passwordLogin(@Body request: PasswordLoginRequest): Response<ApiResult<LoginData>>

    @POST("auth/password/register")
    suspend fun passwordRegister(@Body request: PasswordRegisterRequest): Response<ApiResult<LoginData>>

    @POST("auth/password/reset")
    suspend fun resetPassword(@Body request: PasswordResetRequest): Response<ApiResult<Unit>>

    @POST("auth/account/activate")
    suspend fun activateAccount(@Body request: ActivateAccountRequest): Response<ApiResult<Unit>>

    @POST("auth/security-email/change/verify-old")
    suspend fun verifyOldSecurityEmail(@Body request: VerifyOldSecurityEmailRequest): Response<ApiResult<ChangeTicketData>>

    @POST("auth/security-email/change/confirm")
    suspend fun changeSecurityEmail(@Body request: ChangeSecurityEmailRequest): Response<ApiResult<Unit>>

    @POST("auth/password/change")
    suspend fun changePassword(@Body request: ChangePasswordRequest): Response<ApiResult<Unit>>

    @POST("auth/email-code/send")
    suspend fun sendEmailCode(@Body request: EmailCodeSendRequest): Response<ApiResult<Unit>>

    @POST("auth/refresh")
    suspend fun refresh(@Body request: RefreshTokenRequest): Response<ApiResult<RefreshTokenData>>

    @POST("auth/wx/logout")
    suspend fun logout(): Response<ApiResult<Unit>>

    @GET("users/me")
    suspend fun me(): Response<ApiResult<UserInfo>>

    @PUT("users/me")
    suspend fun updateMe(@Body request: UpdateUserRequest): Response<ApiResult<UserInfo>>

    @Multipart
    @POST("users/me/avatar")
    suspend fun uploadAvatar(@Part file: MultipartBody.Part): Response<ApiResult<AvatarData>>

    @GET("devices")
    suspend fun devices(
        @Query("page") page: Long = 1,
        @Query("size") size: Long = 20
    ): Response<ApiResult<PageResult<DeviceInfo>>>

    @GET("devices/search")
    suspend fun searchDevices(
        @Query("keyword") keyword: String? = null,
        @Query("online_status") onlineStatus: String? = null,
        @Query("page") page: Long = 1,
        @Query("size") size: Long = 20
    ): Response<ApiResult<PageResult<DeviceInfo>>>

    @POST("devices/bind")
    suspend fun bindDevice(@Body request: BindDeviceRequest): Response<ApiResult<DeviceInfo>>

    @PUT("devices/{device_id}/info")
    suspend fun updateDevice(
        @Path("device_id") deviceId: String,
        @Body request: UpdateDeviceRequest
    ): Response<ApiResult<DeviceInfo>>

    @DELETE("devices/{device_id}/binding")
    suspend fun unbindDevice(@Path("device_id") deviceId: String): Response<ApiResult<Unit>>

    @GET("events/unpulled")
    suspend fun unpulledEvents(): Response<ApiResult<List<EventInfo>>>

    @GET("events/search")
    suspend fun searchEvents(
        @Query("device_id") deviceId: String? = null,
        @Query("page") page: Long = 1,
        @Query("size") size: Long = 20,
        @Query("event_type") eventType: String? = null,
        @Query("file_status") fileStatus: String? = null,
        @Query("push_status") pushStatus: String? = null,
        @Query("read_status") readStatus: String? = null,
        @Query("keyword") keyword: String? = null
    ): Response<ApiResult<PageResult<EventInfo>>>

    @GET("events/{event_id}")
    suspend fun eventDetail(@Path("event_id") eventId: String): Response<ApiResult<EventInfo>>

    @PUT("events/{event_id}/read")
    suspend fun markEventRead(@Path("event_id") eventId: String): Response<ApiResult<Unit>>

    @DELETE("events/{event_id}")
    suspend fun deleteEvent(@Path("event_id") eventId: String): Response<ApiResult<Unit>>

    @POST("events/{event_id}/refresh-url")
    suspend fun refreshEventUrl(
        @Path("event_id") eventId: String,
        @Body request: RefreshUrlRequest = RefreshUrlRequest()
    ): Response<ApiResult<RefreshUrlData>>
}
