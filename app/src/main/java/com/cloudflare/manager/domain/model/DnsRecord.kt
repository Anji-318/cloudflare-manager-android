package com.cloudflare.manager.domain.model

data class DnsRecord(
    val id: String,
    val type: String,
    val name: String,
    val content: String,
    val ttl: Int,
    val proxied: Boolean,
    val zoneId: String? = null
)
