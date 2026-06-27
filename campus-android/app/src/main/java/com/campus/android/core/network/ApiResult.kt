package com.campus.android.core.network

import com.google.gson.annotations.SerializedName

data class ApiResult<T>(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String?,
    @SerializedName("data") val data: T?
)

class CampusApiException(
    val code: Int,
    override val message: String
) : RuntimeException(message)
