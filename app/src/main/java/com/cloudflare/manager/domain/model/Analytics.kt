package com.cloudflare.manager.domain.model

data class AnalyticsSummary(
    val totalRequests: Long = 0,
    val threatRequests: Long = 0,
    val cachedRequests: Long = 0,
    val cacheRate: Int = 0
)

data class CountryStat(
    val name: String,
    val value: Long
)
