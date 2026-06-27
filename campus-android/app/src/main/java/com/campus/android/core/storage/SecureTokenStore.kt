package com.campus.android.core.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureTokenStore(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    @Synchronized
    fun saveLogin(accessToken: String, refreshToken: String?, expiresIn: Long?) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putLong(KEY_EXPIRES_AT, expiresAt(expiresIn))
            .apply()
    }

    @Synchronized
    fun saveAccessToken(accessToken: String, expiresIn: Long?) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putLong(KEY_EXPIRES_AT, expiresAt(expiresIn))
            .apply()
    }

    fun accessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    fun refreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    fun isLoggedIn(): Boolean = !accessToken().isNullOrBlank() || !refreshToken().isNullOrBlank()

    fun isAccessTokenExpired(clockSkewMillis: Long = TOKEN_REFRESH_SKEW_MILLIS): Boolean {
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        if (expiresAt <= 0L) return false
        return System.currentTimeMillis() + clockSkewMillis >= expiresAt
    }

    @Synchronized
    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun expiresAt(expiresIn: Long?): Long {
        val seconds = expiresIn ?: 0L
        if (seconds <= 0L) return 0L
        return System.currentTimeMillis() + seconds * 1000L
    }

    private companion object {
        const val FILE_NAME = "campus_secure_tokens"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_EXPIRES_AT = "expires_at"
        const val TOKEN_REFRESH_SKEW_MILLIS = 60_000L
    }
}
