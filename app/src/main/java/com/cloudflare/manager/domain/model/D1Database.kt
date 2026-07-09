package com.cloudflare.manager.domain.model

data class D1Database(
    val uuid: String,
    val name: String,
    val version: String? = null,
    val createdAt: String? = null
)
