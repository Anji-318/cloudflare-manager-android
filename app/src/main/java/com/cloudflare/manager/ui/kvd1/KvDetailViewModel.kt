package com.cloudflare.manager.ui.kvd1

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudflare.manager.data.repository.AccountRepository
import com.cloudflare.manager.data.repository.CloudflareRepository
import com.cloudflare.manager.domain.model.KvKey
import com.cloudflare.manager.domain.model.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class KvDetailUiState(
    val namespaceId: String = "",
    val namespaceTitle: String = "",
    val keysState: UiState<List<KvKey>> = UiState.Loading,
    val currentValue: String = "",
    val currentValueLoading: Boolean = false,
    val showKeyValueDialog: Boolean = false,
    val editingKey: String = "",
    val isNewKey: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class KvDetailViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val cloudflareRepository: CloudflareRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(KvDetailUiState())
    val uiState: StateFlow<KvDetailUiState> = _uiState

    private var accountId: String? = null

    fun setNamespace(namespaceId: String, namespaceTitle: String) {
        if (_uiState.value.namespaceId == namespaceId) return
        _uiState.value = _uiState.value.copy(
            namespaceId = namespaceId,
            namespaceTitle = namespaceTitle
        )
        viewModelScope.launch {
            accountId = accountRepository.getCurrentAccount()?.accountId
            if (accountId.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(
                    keysState = UiState.Error("当前账户缺少 Account ID")
                )
                return@launch
            }
            loadKeys()
        }
    }

    fun loadKeys() {
        val id = accountId ?: return
        val nsId = _uiState.value.namespaceId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(keysState = UiState.Loading)
            val result = cloudflareRepository.listKvKeys(id, nsId)
            _uiState.value = _uiState.value.copy(
                keysState = result.fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it.message ?: "加载失败") }
                )
            )
        }
    }

    fun showAddKeyDialog() {
        _uiState.value = _uiState.value.copy(
            showKeyValueDialog = true,
            editingKey = "",
            currentValue = "",
            isNewKey = true
        )
    }

    fun showEditKeyDialog(key: String) {
        val id = accountId ?: return
        val nsId = _uiState.value.namespaceId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                showKeyValueDialog = true,
                editingKey = key,
                currentValue = "",
                currentValueLoading = true,
                isNewKey = false
            )
            val result = cloudflareRepository.getKvValue(id, nsId, key)
            _uiState.value = _uiState.value.copy(
                currentValue = result.getOrDefault(""),
                currentValueLoading = false
            )
        }
    }

    fun dismissKeyValueDialog() {
        _uiState.value = _uiState.value.copy(showKeyValueDialog = false)
    }

    fun onValueChange(value: String) {
        _uiState.value = _uiState.value.copy(currentValue = value)
    }

    fun saveKeyValue(key: String, value: String) {
        val id = accountId ?: return
        val nsId = _uiState.value.namespaceId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(showKeyValueDialog = false)
            val result = cloudflareRepository.putKvValue(id, nsId, key, value)
            _uiState.value = _uiState.value.copy(
                message = if (result.isSuccess) "键值已保存" else (result.exceptionOrNull()?.message ?: "保存失败")
            )
            loadKeys()
        }
    }

    fun deleteKey(key: String) {
        val id = accountId ?: return
        val nsId = _uiState.value.namespaceId
        viewModelScope.launch {
            val result = cloudflareRepository.deleteKvValue(id, nsId, key)
            _uiState.value = _uiState.value.copy(
                message = if (result.isSuccess) "键值已删除" else (result.exceptionOrNull()?.message ?: "删除失败")
            )
            loadKeys()
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
