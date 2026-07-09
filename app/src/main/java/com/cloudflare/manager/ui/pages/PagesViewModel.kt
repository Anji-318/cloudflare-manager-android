package com.cloudflare.manager.ui.pages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudflare.manager.data.repository.AccountRepository
import com.cloudflare.manager.data.repository.CloudflareRepository
import com.cloudflare.manager.domain.model.PageProject
import com.cloudflare.manager.domain.model.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PagesUiState(
    val projectsState: UiState<List<PageProject>> = UiState.Loading,
    val showAddDialog: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class PagesViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val cloudflareRepository: CloudflareRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PagesUiState())
    val uiState: StateFlow<PagesUiState> = _uiState

    init {
        accountRepository.currentAccount
            .onEach { loadProjects() }
            .launchIn(viewModelScope)
        loadProjects()
    }

    fun loadProjects() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(projectsState = UiState.Loading)
            val account = accountRepository.getCurrentAccount()
            val accountId = account?.accountId
            if (accountId.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(
                    projectsState = UiState.Error("当前账户缺少 Account ID")
                )
                return@launch
            }
            val result = cloudflareRepository.listPagesProjects(accountId)
            _uiState.value = _uiState.value.copy(
                projectsState = result.fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it.message ?: "加载失败") }
                )
            )
        }
    }

    fun createProject(name: String, productionBranch: String) {
        viewModelScope.launch {
            val account = accountRepository.getCurrentAccount()
            val accountId = account?.accountId ?: return@launch
            val result = cloudflareRepository.createPagesProject(
                accountId,
                name,
                productionBranch
            )
            _uiState.value = _uiState.value.copy(
                showAddDialog = false,
                message = if (result.isSuccess) "项目创建成功" else (result.exceptionOrNull()?.message ?: "创建失败")
            )
            loadProjects()
        }
    }

    fun createProjectPrompt() {
        _uiState.value = _uiState.value.copy(showAddDialog = true)
    }

    fun dismissAddDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = false)
    }

    fun deleteProject(name: String) {
        viewModelScope.launch {
            val account = accountRepository.getCurrentAccount()
            val accountId = account?.accountId ?: return@launch
            val result = cloudflareRepository.deletePagesProject(accountId, name)
            _uiState.value = _uiState.value.copy(
                message = if (result.isSuccess) "项目已删除" else (result.exceptionOrNull()?.message ?: "删除失败")
            )
            loadProjects()
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
