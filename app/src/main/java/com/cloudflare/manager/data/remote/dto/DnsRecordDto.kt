package com.cloudflare.manager.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class DnsRecordDto(
    val id: String,
    val type: String,
    val name: String,
    val content: String,
    val ttl: Int = 1,
    val proxied: Boolean = false,
    val zone_id: String? = null,
    val zone_name: String? = null,
    val created_on: String? = null,
    val modified_on: String? = null,
    val data: JsonElement? = null,
    val meta: JsonElement? = null
)

@Serializable
data class DnsRecordRequest(
    val type: String,
    val name: String,
    val content: String,
    val ttl: Int = 1,
    val proxied: Boolean = false
)
