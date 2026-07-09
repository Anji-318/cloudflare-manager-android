package com.cloudflare.manager.ui.tunnels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudflare.manager.data.repository.AccountRepository
import com.cloudflare.manager.data.repository.CloudflareRepository
import com.cloudflare.manager.domain.model.Tunnel
import com.cloudflare.manager.domain.model.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TunnelsUiState(
    val tunnelsState: UiState<List<Tunnel>> = UiState.Loading,
    val message: String? = null
)

@HiltViewModel
class TunnelsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val cloudflareRepository: CloudflareRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TunnelsUiState())
    val uiState: StateFlow<TunnelsUiState> = _uiState

    init {
        accountRepository.currentAccount
            .onEach { loadTunnels() }
            .launchIn(viewModelScope)
        loadTunnels()
    }

    fun loadTunnels() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(tunnelsState = UiState.Loading)
            val account = accountRepository.getCurrentAccount()
            val accountId = account?.accountId
            if (accountId.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(
                    tunnelsState = UiState.Error("当前账户缺少 Account ID")
                )
                return@launch
            }
            val result = cloudflareRepository.listTunnels(accountId)
            _uiState.value = _uiState.value.copy(
                tunnelsState = result.fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it.message ?: "加载失败") }
                )
            )
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
