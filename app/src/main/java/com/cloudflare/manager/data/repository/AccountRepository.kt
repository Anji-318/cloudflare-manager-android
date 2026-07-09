package com.cloudflare.manager.data.repository

import com.cloudflare.manager.data.local.AccountDao
import com.cloudflare.manager.data.local.AccountEntity
import com.cloudflare.manager.data.local.SecureTokenManager
import com.cloudflare.manager.data.remote.CloudflareApiService
import com.cloudflare.manager.data.remote.CloudflareResponse
import com.cloudflare.manager.data.remote.errorMessage
import com.cloudflare.manager.domain.model.Account
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    private val accountDao: AccountDao,
    private val tokenManager: SecureTokenManager,
    private val sessionManager: SessionManager,
    private val apiService: CloudflareApiService
) {
    val accounts: Flow<List<Account>> = accountDao.observeAll().map { list ->
        list.map { it.toDomain() }
    }

    val currentAccount: Flow<Account?> = accountDao.observeCurrent().map { it?.toDomain() }

    suspend fun getCurrentAccount(): Account? = accountDao.getCurrent()?.toDomain()

    suspend fun getToken(accountId: String): String? = tokenManager.getToken(accountId)

    suspend fun verifyToken(token: String): Result<Unit> {
        val previousToken = sessionManager.currentToken.value
        sessionManager.setToken(token)
        return try {
            val response = apiService.verifyToken()
            if (response.success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.errorMessage()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            sessionManager.setToken(previousToken)
        }
    }

    suspend fun saveAccount(name: String, email: String, token: String): Result<Account> {
        return try {
            val accountId = fetchAccountId(token)
            val id = "acc_${System.currentTimeMillis()}"
            val entity = AccountEntity(
                id = id,
                name = name,
                email = email,
                accountId = accountId,
                isCurrent = true
            )
            tokenManager.saveToken(id, token)
            sessionManager.setToken(token)
            accountDao.clearCurrent()
            accountDao.insert(entity)
            Result.success(entity.toDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun selectAccount(id: String) {
        val token = tokenManager.getToken(id)
        sessionManager.setToken(token)
        accountDao.clearCurrent()
        accountDao.setCurrent(id)
    }

    suspend fun deleteAccount(id: String) {
        val deletedToken = tokenManager.getToken(id)
        accountDao.deleteById(id)
        tokenManager.deleteToken(id)
        if (sessionManager.currentToken.value == deletedToken) {
            sessionManager.setToken(null)
        }
    }

    suspend fun clearAll() {
        accountDao.deleteAll()
        tokenManager.clear()
        sessionManager.setToken(null)
    }

    private suspend fun fetchAccountId(token: String): String {
        val previousToken = sessionManager.currentToken.value
        sessionManager.setToken(token)
        return try {
            val response: CloudflareResponse<List<com.cloudflare.manager.data.remote.dto.AccountDto>> =
                apiService.listAccounts()
            val id = response.result?.firstOrNull()?.id
            if (id.isNullOrBlank()) {
                throw Exception("无法获取 Account ID，请确认 Token 拥有“帐户 - 帐户（读取）”权限，并且 Account Resources 包含目标账户。")
            }
            id
        } catch (e: Exception) {
            throw Exception("无法获取 Account ID，请确认 Token 拥有“帐户 - 帐户（读取）”权限，并且网络可访问 Cloudflare API。原因：${e.message}")
        } finally {
            sessionManager.setToken(previousToken)
        }
    }

    private fun AccountEntity.toDomain(): Account = Account(
        id = id,
        name = name,
        email = email,
        accountId = accountId
    )
}
