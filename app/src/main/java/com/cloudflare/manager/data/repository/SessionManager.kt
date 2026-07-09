package com.cloudflare.manager.data.repository

import com.cloudflare.manager.data.local.AccountDao
import com.cloudflare.manager.data.local.SecureTokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(
    accountDao: AccountDao,
    private val tokenManager: SecureTokenManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _currentToken = MutableStateFlow<String?>(null)
    val currentToken: StateFlow<String?> = _currentToken.asStateFlow()

    init {
        accountDao.observeCurrent()
            .onEach { entity ->
                _currentToken.value = entity?.let { tokenManager.getToken(it.id) }
            }
            .launchIn(scope)
    }

    fun setToken(token: String?) {
        _currentToken.value = token
    }
}
