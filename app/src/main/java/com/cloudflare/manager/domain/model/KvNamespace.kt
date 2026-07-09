package com.cloudflare.manager.domain.model

data class KvNamespace(
    val id: String,
    val title: String
)

data class KvKey(
    val name: String,
    val expiration: Long? = null,
    val metadata: String? = null
)
