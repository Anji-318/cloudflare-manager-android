package com.cloudflare.manager.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class CloudflareResponse<T>(
    val success: Boolean = false,
    val errors: List<CloudflareError> = emptyList(),
    val messages: List<JsonElement> = emptyList(),
    val result: T? = null,
    val result_info: JsonElement? = null
)

@Serializable
data class CloudflareError(
    val code: Int = 0,
    val message: String = ""
)

fun <T> CloudflareResponse<T>.errorMessage(): String {
    return if (errors.isNotEmpty()) {
        errors.joinToString("; ") { "[${it.code}] ${it.message}" }
    } else {
        "Unknown API error"
    }
}
