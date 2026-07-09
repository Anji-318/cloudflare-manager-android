package com.cloudflare.manager.domain.model

data class Tunnel(
    val id: String,
    val name: String,
    val status: String,
    val connections: Int = 0,
    val createdAt: String? = null
)
