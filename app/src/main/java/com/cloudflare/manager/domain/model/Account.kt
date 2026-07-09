package com.cloudflare.manager.domain.model

data class Account(
    val id: String,
    val name: String,
    val email: String = "",
    val accountId: String? = null
)
