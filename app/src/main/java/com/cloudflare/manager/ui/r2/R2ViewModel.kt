package com.cloudflare.manager.ui.r2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudflare.manager.data.repository.AccountRepository
import com.cloudflare.manager.data.repository.CloudflareRepository
import com.cloudflare.manager.domain.model.R2Bucket
import com.cloudflare.manager.domain.model.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class R2UiState(
    val bucketsState: UiState<List<R2Bucket>> = UiState.Loading,
    val showAddDialog: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class R2ViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val cloudflareRepository: CloudflareRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(R2UiState())
    val uiState: StateFlow<R2UiState> = _uiState

    init {
        accountRepository.currentAccount
            .onEach { loadBuckets() }
            .launchIn(viewModelScope)
        loadBuckets()
    }

    fun loadBuckets() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(bucketsState = UiState.Loading)
            val account = accountRepository.getCurrentAccount()
            val accountId = account?.accountId
            if (accountId.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(
                    bucketsState = UiState.Error("当前账户缺少 Account ID")
                )
                return@launch
            }
            val result = cloudflareRepository.listR2Buckets(accountId)
            _uiState.value = _uiState.value.copy(
                bucketsState = result.fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it.message ?: "加载失败") }
                )
            )
        }
    }

    fun createBucket(name: String) {
        viewModelScope.launch {
            val account = accountRepository.getCurrentAccount()
            val accountId = account?.accountId ?: return@launch
            val result = cloudflareRepository.createR2Bucket(accountId, name)
            _uiState.value = _uiState.value.copy(
                showAddDialog = false,
                message = if (result.isSuccess) "Bucket 创建成功" else (result.exceptionOrNull()?.message ?: "创建失败")
            )
            loadBuckets()
        }
    }

    fun deleteBucket(name: String) {
        viewModelScope.launch {
            val account = accountRepository.getCurrentAccount()
            val accountId = account?.accountId ?: return@launch
            val result = cloudflareRepository.deleteR2Bucket(accountId, name)
            _uiState.value = _uiState.value.copy(
                message = if (result.isSuccess) "Bucket 已删除" else (result.exceptionOrNull()?.message ?: "删除失败")
            )
            loadBuckets()
        }
    }

    fun showAddDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = true)
    }

    fun dismissAddDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = false)
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
