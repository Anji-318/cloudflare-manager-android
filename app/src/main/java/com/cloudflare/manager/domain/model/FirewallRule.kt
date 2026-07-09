package com.cloudflare.manager.domain.model

data class FirewallRule(
    val id: String,
    val description: String,
    val action: String,
    val paused: Boolean,
    val mode: String? = null
)
