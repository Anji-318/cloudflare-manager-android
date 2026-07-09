package com.cloudflare.manager.domain.model

data class PageProject(
    val name: String,
    val productionBranch: String? = null,
    val latestDeployment: String? = null,
    val subdomain: String? = null,
    val createdOn: String? = null
)

data class PageDomain(
    val id: String,
    val name: String,
    val status: String? = null,
    val zoneName: String? = null
)

data class PageDeployment(
    val id: String,
    val environment: String? = null,
    val url: String? = null,
    val createdOn: String? = null
)
