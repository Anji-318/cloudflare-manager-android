package com.cloudflare.manager.domain.model

data class WorkerScript(
    val id: String,
    val name: String,
    val createdOn: String? = null,
    val modifiedOn: String? = null
)

data class WorkerDomain(
    val id: String,
    val hostname: String,
    val zoneName: String? = null,
    val status: String? = null
)

data class WorkerRoute(
    val id: String,
    val pattern: String,
    val script: String? = null
)

data class WorkerSubdomainInfo(
    val subdomain: String?,
    val enabled: Boolean
)
