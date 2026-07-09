package com.cloudflare.manager.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class AccountDto(
    val id: String,
    val name: String,
    val settings: AccountSettingsDto? = null
)

@Serializable
data class AccountSettingsDto(
    val enforce_twofactor: Boolean? = null
)
