package com.cloudflare.manager.ui.workers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudflare.manager.data.repository.AccountRepository
import com.cloudflare.manager.data.repository.CloudflareRepository
import com.cloudflare.manager.domain.model.WorkerScript
import com.cloudflare.manager.domain.model.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WorkersUiState(
    val scriptsState: UiState<List<WorkerScript>> = UiState.Loading,
    val showUploadDialog: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class WorkersViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val cloudflareRepository: CloudflareRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkersUiState())
    val uiState: StateFlow<WorkersUiState> = _uiState

    init {
        accountRepository.currentAccount
            .onEach { loadScripts() }
            .launchIn(viewModelScope)
        loadScripts()
    }

    fun loadScripts() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(scriptsState = UiState.Loading)
            val account = accountRepository.getCurrentAccount()
            val accountId = account?.accountId
            if (accountId.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(
                    scriptsState = UiState.Error("当前账户缺少 Account ID")
                )
                return@launch
            }
            val result = cloudflareRepository.listWorkers(accountId)
            _uiState.value = _uiState.value.copy(
                scriptsState = result.fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it.message ?: "加载失败") }
                )
            )
        }
    }

    fun uploadScript(name: String, script: String) {
        viewModelScope.launch {
            val account = accountRepository.getCurrentAccount()
            val accountId = account?.accountId ?: return@launch
            val result = cloudflareRepository.uploadWorkerScript(accountId, name, script)
            _uiState.value = _uiState.value.copy(
                showUploadDialog = false,
                message = if (result.isSuccess) "脚本上传成功" else (result.exceptionOrNull()?.message ?: "上传失败")
            )
            loadScripts()
        }
    }

    fun deleteScript(name: String) {
        viewModelScope.launch {
            val account = accountRepository.getCurrentAccount()
            val accountId = account?.accountId ?: return@launch
            val result = cloudflareRepository.deleteWorker(accountId, name)
            _uiState.value = _uiState.value.copy(
                message = if (result.isSuccess) "脚本已删除" else (result.exceptionOrNull()?.message ?: "删除失败")
            )
            loadScripts()
        }
    }

    fun showUploadDialog() {
        _uiState.value = _uiState.value.copy(showUploadDialog = true)
    }

    fun dismissUploadDialog() {
        _uiState.value = _uiState.value.copy(showUploadDialog = false)
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
