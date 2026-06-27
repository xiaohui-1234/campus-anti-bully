package com.campus.android.core.security

import android.util.Log

object SensitiveLog {
    private val sensitivePatterns = listOf(
        Regex("(?i)authorization\\s*[:=]\\s*bearer\\s+[^\\s,;]+"),
        Regex("(?i)access_token\\s*[:=]\\s*[^\\s,;]+"),
        Regex("(?i)refresh_token\\s*[:=]\\s*[^\\s,;]+"),
        Regex("(?i)token\\s*[:=]\\s*[^\\s,;]+"),
        Regex("(?i)upload_url\\s*[:=]\\s*[^\\s,;]+"),
        Regex("(?i)file_url\\s*[:=]\\s*[^\\s,;]+"),
        Regex("(?i)object_key\\s*[:=]\\s*[^\\s,;]+"),
        Regex("(?i)device_secret\\s*[:=]\\s*[^\\s,;]+"),
        Regex("(?i)wifi_password\\s*[:=]\\s*[^\\s,;]+"),
        Regex("(?i)secretKey\\s*[:=]\\s*[^\\s,;]+")
    )

    fun d(tag: String, message: String) {
        Log.d(tag, redact(message))
    }

    fun i(tag: String, message: String) {
        Log.i(tag, redact(message))
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w(tag, withThrowableName(message, throwable))
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, withThrowableName(message, throwable))
    }

    private fun withThrowableName(message: String, throwable: Throwable?): String {
        val suffix = throwable?.javaClass?.simpleName?.let { " ($it)" }.orEmpty()
        return redact(message) + suffix
    }

    private fun redact(raw: String): String {
        return sensitivePatterns.fold(raw) { text, pattern ->
            pattern.replace(text) { match ->
                val prefix = match.value.substringBefore("=", match.value.substringBefore(":"))
                "$prefix=<redacted>"
            }
        }
    }
}
