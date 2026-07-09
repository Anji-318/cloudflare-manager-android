package com.cloudflare.manager.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudflare.manager.data.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddAccountUiState(
    val name: String = "",
    val email: String = "",
    val token: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false
)

@HiltViewModel
class AddAccountViewModel @Inject constructor(
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddAccountUiState())
    val uiState: StateFlow<AddAccountUiState> = _uiState

    fun onNameChange(value: String) {
        _uiState.value = _uiState.value.copy(name = value)
    }

    fun onEmailChange(value: String) {
        _uiState.value = _uiState.value.copy(email = value)
    }

    fun onTokenChange(value: String) {
        _uiState.value = _uiState.value.copy(token = value)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun saveAccount() {
        val state = _uiState.value
        if (state.name.isBlank() || state.token.isBlank()) {
            _uiState.value = state.copy(error = "账户名称和 API Token 不能为空")
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)

            val verifyResult = accountRepository.verifyToken(state.token)
            if (verifyResult.isFailure) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Token 验证失败：${verifyResult.exceptionOrNull()?.message ?: "未知错误"}"
                )
                return@launch
            }

            val result = accountRepository.saveAccount(state.name, state.email, state.token)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(isLoading = false, success = true)
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message ?: "保存失败"
                )
            }
        }
    }
}
