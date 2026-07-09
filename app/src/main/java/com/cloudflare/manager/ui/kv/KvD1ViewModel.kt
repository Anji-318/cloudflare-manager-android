package com.cloudflare.manager.ui.kv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudflare.manager.data.repository.AccountRepository
import com.cloudflare.manager.data.repository.CloudflareRepository
import com.cloudflare.manager.domain.model.D1Database
import com.cloudflare.manager.domain.model.KvNamespace
import com.cloudflare.manager.domain.model.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class KvD1UiState(
    val kvState: UiState<List<KvNamespace>> = UiState.Loading,
    val d1State: UiState<List<D1Database>> = UiState.Loading,
    val message: String? = null
)

@HiltViewModel
class KvD1ViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val cloudflareRepository: CloudflareRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(KvD1UiState())
    val uiState: StateFlow<KvD1UiState> = _uiState

    init {
        accountRepository.currentAccount
            .onEach { loadAll() }
            .launchIn(viewModelScope)
        loadAll()
    }

    fun loadAll() {
        loadKv()
        loadD1()
    }

    private fun loadKv() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(kvState = UiState.Loading)
            val account = accountRepository.getCurrentAccount()
            val accountId = account?.accountId
            if (accountId.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(
                    kvState = UiState.Error("当前账户缺少 Account ID")
                )
                return@launch
            }
            val result = cloudflareRepository.listKvNamespaces(accountId)
            _uiState.value = _uiState.value.copy(
                kvState = result.fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it.message ?: "加载失败") }
                )
            )
        }
    }

    private fun loadD1() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(d1State = UiState.Loading)
            val account = accountRepository.getCurrentAccount()
            val accountId = account?.accountId
            if (accountId.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(
                    d1State = UiState.Error("当前账户缺少 Account ID")
                )
                return@launch
            }
            val result = cloudflareRepository.listD1Databases(accountId)
            _uiState.value = _uiState.value.copy(
                d1State = result.fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it.message ?: "加载失败") }
                )
            )
        }
    }

    fun createKv(title: String) {
        viewModelScope.launch {
            val account = accountRepository.getCurrentAccount()
            val accountId = account?.accountId ?: return@launch
            val result = cloudflareRepository.createKvNamespace(accountId, title)
            _uiState.value = _uiState.value.copy(
                message = if (result.isSuccess) "KV 命名空间创建成功" else (result.exceptionOrNull()?.message ?: "创建失败")
            )
            loadKv()
        }
    }

    fun deleteKv(id: String) {
        viewModelScope.launch {
            val account = accountRepository.getCurrentAccount()
            val accountId = account?.accountId ?: return@launch
            val result = cloudflareRepository.deleteKvNamespace(accountId, id)
            _uiState.value = _uiState.value.copy(
                message = if (result.isSuccess) "KV 命名空间已删除" else (result.exceptionOrNull()?.message ?: "删除失败")
            )
            loadKv()
        }
    }

    fun createD1(name: String) {
        viewModelScope.launch {
            val account = accountRepository.getCurrentAccount()
            val accountId = account?.accountId ?: return@launch
            val result = cloudflareRepository.createD1Database(accountId, name)
            _uiState.value = _uiState.value.copy(
                message = if (result.isSuccess) "D1 数据库创建成功" else (result.exceptionOrNull()?.message ?: "创建失败")
            )
            loadD1()
        }
    }

    fun deleteD1(id: String) {
        viewModelScope.launch {
            val account = accountRepository.getCurrentAccount()
            val accountId = account?.accountId ?: return@launch
            val result = cloudflareRepository.deleteD1Database(accountId, id)
            _uiState.value = _uiState.value.copy(
                message = if (result.isSuccess) "D1 数据库已删除" else (result.exceptionOrNull()?.message ?: "删除失败")
            )
            loadD1()
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
