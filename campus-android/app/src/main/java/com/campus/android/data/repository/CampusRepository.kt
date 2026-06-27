package com.campus.android.data.repository

import com.campus.android.core.network.ApiResult
import com.campus.android.core.network.CampusApi
import com.campus.android.core.network.CampusApiException
import com.campus.android.core.storage.SecureTokenStore
import com.campus.android.data.model.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.Response

class CampusRepository(
    private val api: CampusApi,
    private val tokenStore: SecureTokenStore
) {
    private val refreshMutex = Mutex()

    fun isLoggedIn(): Boolean = tokenStore.isLoggedIn()

    suspend fun login(loginId: String, password: String): LoginData {
        val data = unwrap(api.passwordLogin(PasswordLoginRequest(loginId, password)))
        tokenStore.saveLogin(data.accessToken, data.refreshToken, data.expiresIn)
        return data
    }

    suspend fun register(securityEmail: String, verifyCode: String, password: String): LoginData {
        val data = unwrap(api.passwordRegister(PasswordRegisterRequest(securityEmail, verifyCode, password)))
        tokenStore.saveLogin(data.accessToken, data.refreshToken, data.expiresIn)
        return data
    }

    suspend fun sendEmailCode(scene: String, securityEmail: String? = null, changeTicket: String? = null) {
        val request = EmailCodeSendRequest(
            scene = scene,
            securityEmail = securityEmail?.trim()?.takeIf { it.isNotBlank() },
            changeTicket = changeTicket?.trim()?.takeIf { it.isNotBlank() }
        )
        unwrapUnit(api.sendEmailCode(request))
    }

    suspend fun resetPassword(securityEmail: String, verifyCode: String, newPassword: String) {
        unwrapUnit(api.resetPassword(PasswordResetRequest(securityEmail, verifyCode, newPassword)))
    }

    suspend fun activateAccount(securityEmail: String, verifyCode: String, password: String) {
        authorizedUnit { api.activateAccount(ActivateAccountRequest(securityEmail, verifyCode, password)) }
    }

    suspend fun verifyOldSecurityEmail(verifyCode: String): ChangeTicketData {
        return authorized { api.verifyOldSecurityEmail(VerifyOldSecurityEmailRequest(verifyCode)) }
    }

    suspend fun changeSecurityEmail(changeTicket: String, newSecurityEmail: String, newVerifyCode: String) {
        authorizedUnit {
            api.changeSecurityEmail(
                ChangeSecurityEmailRequest(
                    changeTicket = changeTicket,
                    newSecurityEmail = newSecurityEmail,
                    newVerifyCode = newVerifyCode
                )
            )
        }
    }

    suspend fun changePassword(oldPassword: String, newPassword: String) {
        authorizedUnit { api.changePassword(ChangePasswordRequest(oldPassword, newPassword)) }
    }

    suspend fun logout() {
        runCatching { unwrapUnit(api.logout()) }
        tokenStore.clear()
    }

    fun clearLocalSession() {
        tokenStore.clear()
    }

    suspend fun me(): UserInfo = authorized { api.me() }

    suspend fun updateMe(nickname: String?, phone: String?, email: String?): UserInfo {
        return authorized { api.updateMe(UpdateUserRequest(nickname, phone, email)) }
    }

    suspend fun devices(page: Long = 1, size: Long = 20): PageResult<DeviceInfo> {
        return authorized { api.devices(page, size) }
    }

    suspend fun events(
        page: Long = 1,
        size: Long = 20,
        eventType: String? = null,
        fileStatus: String? = null,
        readStatus: String? = null,
        keyword: String? = null
    ): PageResult<EventInfo> {
        return authorized {
            api.searchEvents(
                page = page,
                size = size,
                eventType = eventType,
                fileStatus = fileStatus,
                readStatus = readStatus,
                keyword = keyword
            )
        }
    }

    suspend fun unreadEvents(): PageResult<EventInfo> {
        return authorized { api.searchEvents(readStatus = "UNREAD", page = 1, size = 20) }
    }

    suspend fun unpulledEvents(): List<EventInfo> = authorized { api.unpulledEvents() }

    suspend fun eventDetail(eventId: String): EventInfo {
        return authorized { api.eventDetail(eventId) }
    }

    suspend fun markEventRead(eventId: String) {
        authorizedUnit { api.markEventRead(eventId) }
    }

    suspend fun deleteEvent(eventId: String) {
        authorizedUnit { api.deleteEvent(eventId) }
    }

    suspend fun refreshEventUrl(eventId: String): RefreshUrlData {
        return authorized { api.refreshEventUrl(eventId) }
    }

    suspend fun bindDevice(deviceId: String, bindCode: String, deviceName: String?): DeviceInfo {
        return authorized { api.bindDevice(BindDeviceRequest(deviceId, bindCode, deviceName)) }
    }

    suspend fun updateDevice(deviceId: String, deviceName: String?, location: String?, note: String?): DeviceInfo {
        return authorized { api.updateDevice(deviceId, UpdateDeviceRequest(deviceName, location, note)) }
    }

    suspend fun unbindDevice(deviceId: String) {
        authorizedUnit { api.unbindDevice(deviceId) }
    }

    private suspend fun <T : Any> authorized(block: suspend () -> Response<ApiResult<T>>): T {
        ensureFreshAccessToken()
        val first = block()
        if (shouldRefresh(first) && refreshAccessToken()) {
            val second = block()
            if (shouldRefresh(second)) {
                tokenStore.clear()
                throw sessionExpired()
            }
            return unwrap(second)
        }
        if (shouldRefresh(first)) {
            tokenStore.clear()
            throw sessionExpired()
        }
        return unwrap(first)
    }

    private suspend fun authorizedUnit(block: suspend () -> Response<ApiResult<Unit>>) {
        ensureFreshAccessToken()
        val first = block()
        if (shouldRefresh(first) && refreshAccessToken()) {
            val second = block()
            if (shouldRefresh(second)) {
                tokenStore.clear()
                throw sessionExpired()
            }
            unwrapUnit(second)
            return
        }
        if (shouldRefresh(first)) {
            tokenStore.clear()
            throw sessionExpired()
        }
        unwrapUnit(first)
    }

    private fun <T> shouldRefresh(response: Response<ApiResult<T>>): Boolean {
        return response.code() == 401 || response.body()?.code == 401
    }

    private suspend fun refreshAccessToken(): Boolean {
        return refreshMutex.withLock {
            val refreshToken = tokenStore.refreshToken() ?: return@withLock false
            val response = api.refresh(RefreshTokenRequest(refreshToken))
            if (!response.isSuccessful || response.body()?.code != 0) {
                tokenStore.clear()
                return@withLock false
            }
            val data = response.body()?.data
            if (data == null) {
                tokenStore.clear()
                return@withLock false
            }
            tokenStore.saveAccessToken(data.accessToken, data.expiresIn)
            true
        }
    }

    suspend fun refreshSession(): Boolean = refreshAccessToken()

    private suspend fun ensureFreshAccessToken() {
        if (!tokenStore.isAccessTokenExpired()) return
        if (!refreshAccessToken()) {
            tokenStore.clear()
            throw sessionExpired()
        }
    }

    private fun sessionExpired(): CampusApiException {
        return CampusApiException(401, "登录状态已失效，请重新登录")
    }

    private fun <T : Any> unwrap(response: Response<ApiResult<T>>): T {
        if (!response.isSuccessful) {
            throw CampusApiException(response.code(), "网络请求失败")
        }
        val body = response.body() ?: throw CampusApiException(-1, "响应为空")
        if (body.code != 0) {
            throw CampusApiException(body.code, body.message ?: "请求失败")
        }
        return body.data ?: throw CampusApiException(-1, "响应数据为空")
    }

    private fun unwrapUnit(response: Response<ApiResult<Unit>>) {
        if (!response.isSuccessful) {
            throw CampusApiException(response.code(), "网络请求失败")
        }
        val body = response.body() ?: throw CampusApiException(-1, "响应为空")
        if (body.code != 0) {
            throw CampusApiException(body.code, body.message ?: "请求失败")
        }
    }
}
