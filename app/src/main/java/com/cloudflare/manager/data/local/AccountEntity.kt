package com.cloudflare.manager.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val email: String = "",
    val accountId: String? = null,
    val isCurrent: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
