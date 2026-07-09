package com.cloudflare.manager.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudflare.manager.data.repository.AccountRepository
import com.cloudflare.manager.domain.model.Account
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class AccountsUiState(
    val accounts: List<Account> = emptyList(),
    val currentAccount: Account? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null
)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountsUiState())
    val uiState: StateFlow<AccountsUiState> = _uiState

    init {
        accountRepository.accounts
            .onEach { accounts ->
                _uiState.value = _uiState.value.copy(accounts = accounts)
            }
            .launchIn(viewModelScope)

        accountRepository.currentAccount
            .onEach { current ->
                _uiState.value = _uiState.value.copy(currentAccount = current)
            }
            .launchIn(viewModelScope)
    }

    fun selectAccount(account: Account) {
        viewModelScope.launch {
            if (account.accountId.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(
                    error = "该账户缺少 Account ID，请删除后重新添加，并确保 Token 拥有\u201c帐户 - 帐户（读取）\u201d权限。"
                )
                return@launch
            }
            // Move database operations to IO dispatcher to avoid blocking main thread
            withContext(Dispatchers.IO) {
                accountRepository.selectAccount(account.id)
            }
            _uiState.value = _uiState.value.copy(message = "已切换到 ${account.name}")
        }
    }

    fun deleteAccount(account: Account) {
        viewModelScope.launch {
            accountRepository.deleteAccount(account.id)
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
