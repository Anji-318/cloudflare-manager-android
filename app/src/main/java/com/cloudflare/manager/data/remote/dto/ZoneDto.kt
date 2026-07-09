package com.cloudflare.manager.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ZoneDto(
    val id: String,
    val name: String,
    val status: String,
    val plan: PlanDto? = null,
    val name_servers: List<String>? = null,
    val account: AccountRefDto? = null,
    val paused: Boolean? = null,
    val type: String? = null
)

@Serializable
data class PlanDto(
    val id: String? = null,
    val name: String? = null
)

@Serializable
data class AccountRefDto(
    val id: String,
    val name: String
)
