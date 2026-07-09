package com.cloudflare.manager.ui.pages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudflare.manager.data.repository.AccountRepository
import com.cloudflare.manager.data.repository.CloudflareRepository
import com.cloudflare.manager.domain.model.PageDeployment
import com.cloudflare.manager.domain.model.PageDomain
import com.cloudflare.manager.domain.model.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PagesDetailUiState(
    val projectName: String = "",
    val deploymentsState: UiState<List<PageDeployment>> = UiState.Loading,
    val domainsState: UiState<List<PageDomain>> = UiState.Loading,
    val showAddDomainDialog: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class PagesDetailViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val cloudflareRepository: CloudflareRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PagesDetailUiState())
    val uiState: StateFlow<PagesDetailUiState> = _uiState

    private var accountId: String? = null

    fun setProjectName(name: String) {
        if (_uiState.value.projectName == name) return
        _uiState.value = _uiState.value.copy(projectName = name)
        viewModelScope.launch {
            accountId = accountRepository.getCurrentAccount()?.accountId
            if (accountId.isNullOrBlank()) {
                val error = "当前账户缺少 Account ID"
                _uiState.value = _uiState.value.copy(
                    deploymentsState = UiState.Error(error),
                    domainsState = UiState.Error(error)
                )
                return@launch
            }
            loadDeployments()
            loadDomains()
        }
    }

    fun loadDeployments() {
        val id = accountId ?: return
        val name = _uiState.value.projectName
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(deploymentsState = UiState.Loading)
            val result = cloudflareRepository.listPagesDeployments(id, name)
            _uiState.value = _uiState.value.copy(
                deploymentsState = result.fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it.message ?: "加载失败") }
                )
            )
        }
    }

    fun loadDomains() {
        val id = accountId ?: return
        val name = _uiState.value.projectName
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(domainsState = UiState.Loading)
            val result = cloudflareRepository.listPagesDomains(id, name)
            _uiState.value = _uiState.value.copy(
                domainsState = result.fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it.message ?: "加载失败") }
                )
            )
        }
    }

    fun createDomain(domain: String) {
        val id = accountId ?: return
        val name = _uiState.value.projectName
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(showAddDomainDialog = false)
            val result = cloudflareRepository.createPagesDomain(id, name, domain)
            _uiState.value = _uiState.value.copy(
                message = if (result.isSuccess) "自定义域名已添加" else (result.exceptionOrNull()?.message ?: "添加失败")
            )
            loadDomains()
        }
    }

    fun deleteDomain(domainName: String) {
        val id = accountId ?: return
        val name = _uiState.value.projectName
        viewModelScope.launch {
            val result = cloudflareRepository.deletePagesDomain(id, name, domainName)
            _uiState.value = _uiState.value.copy(
                message = if (result.isSuccess) "自定义域名已删除" else (result.exceptionOrNull()?.message ?: "删除失败")
            )
            loadDomains()
        }
    }

    fun retryDomain(domainName: String) {
        val id = accountId ?: return
        val name = _uiState.value.projectName
        viewModelScope.launch {
            val result = cloudflareRepository.retryPagesDomain(id, name, domainName)
            _uiState.value = _uiState.value.copy(
                message = if (result.isSuccess) "已重新验证" else (result.exceptionOrNull()?.message ?: "验证失败")
            )
            loadDomains()
        }
    }

    fun showAddDomainDialog() {
        _uiState.value = _uiState.value.copy(showAddDomainDialog = true)
    }

    fun dismissAddDomainDialog() {
        _uiState.value = _uiState.value.copy(showAddDomainDialog = false)
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
