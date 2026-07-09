package com.cloudflare.manager.domain.model

data class Zone(
    val id: String,
    val name: String,
    val status: String,
    val planName: String,
    val nameServer: String?,
    val accountId: String? = null
)
