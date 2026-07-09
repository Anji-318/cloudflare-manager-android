package com.cloudflare.manager.ui.workers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudflare.manager.data.repository.AccountRepository
import com.cloudflare.manager.data.repository.CloudflareRepository
import com.cloudflare.manager.data.repository.ZoneRepository
import com.cloudflare.manager.domain.model.UiState
import com.cloudflare.manager.domain.model.WorkerDomain
import com.cloudflare.manager.domain.model.WorkerRoute
import com.cloudflare.manager.domain.model.Zone
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

data class WorkerDetailUiState(
    val scriptName: String = "",
    val scriptContent: String = "",
    val scriptContentLoading: Boolean = false,
    val scriptContentError: String? = null,
    val domainsState: UiState<List<WorkerDomain>> = UiState.Loading,
    val zonesState: UiState<List<Zone>> = UiState.Loading,
    val routesState: UiState<List<WorkerRoute>> = UiState.Loading,
    val selectedZone: Zone? = null,
    val accountSubdomain: String? = null,
    val subdomainEnabled: Boolean = false,
    val isTogglingSubdomain: Boolean = false,
    val isSavingScript: Boolean = false,
    val message: String? = null,
    val showAddDomainDialog: Boolean = false,
    val showAddRouteDialog: Boolean = false,
    val showAddSecretDialog: Boolean = false,
    val showDeleteSecretDialog: Boolean = false
)

@HiltViewModel
class WorkerDetailViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val cloudflareRepository: CloudflareRepository,
    private val zoneRepository: ZoneRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkerDetailUiState())
    val uiState: StateFlow<WorkerDetailUiState> = _uiState

    private var accountId: String? = null

    fun setScriptName(name: String) {
        if (_uiState.value.scriptName == name) return
        _uiState.value = _uiState.value.copy(scriptName = name)
        viewModelScope.launch {
            accountId = accountRepository.getCurrentAccount()?.accountId
            if (accountId.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(
                    scriptContentError = "当前账户缺少 Account ID",
                    domainsState = UiState.Error("当前账户缺少 Account ID"),
                    zonesState = UiState.Error("当前账户缺少 Account ID"),
                    routesState = UiState.Error("当前账户缺少 Account ID")
                )
                return@launch
            }
            loadScriptContent()
            loadDomains()
            loadAccountSubdomain()
            loadZones()
        }
    }

    fun loadScriptContent() {
        val id = accountId ?: return
        val name = _uiState.value.scriptName
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(scriptContentLoading = true, scriptContentError = null)
            val result = cloudflareRepository.getWorkerScript(id, name)
            _uiState.value = _uiState.value.copy(
                scriptContentLoading = false,
                scriptContent = result.getOrDefault(""),
                scriptContentError = result.exceptionOrNull()?.message
            )
        }
    }

    fun onScriptContentChange(content: String) {
        _uiState.value = _uiState.value.copy(scriptContent = content)
    }

    fun saveScript() {
        val id = accountId ?: return
        val name = _uiState.value.scriptName
        val content = _uiState.value.scriptContent
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSavingScript = true)
            val result = cloudflareRepository.uploadWorkerScript(id, name, content)
            _uiState.value = _uiState.value.copy(
                isSavingScript = false,
                message = if (result.isSuccess) "脚本保存成功" else (result.exceptionOrNull()?.message ?: "保存失败")
            )
        }
    }

    fun loadDomains() {
        val id = accountId ?: return
        val name = _uiState.value.scriptName
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(domainsState = UiState.Loading)
            val result = cloudflareRepository.listWorkerDomains(id, name)
            _uiState.value = _uiState.value.copy(
                domainsState = result.fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it.message ?: "加载失败") }
                )
            )
        }
    }

    fun loadAccountSubdomain() {
        val id = accountId ?: return
        viewModelScope.launch {
            val result = cloudflareRepository.getWorkersSubdomain(id)
            result.onSuccess { info ->
                _uiState.value = _uiState.value.copy(
                    accountSubdomain = info.subdomain,
                    subdomainEnabled = info.enabled
                )
            }
        }
    }

    fun toggleSubdomain(enabled: Boolean) {
        val id = accountId ?: return
        val name = _uiState.value.scriptName
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTogglingSubdomain = true)
            val result = cloudflareRepository.setWorkersSubdomain(id, name, enabled)
            _uiState.value = _uiState.value.copy(
                isTogglingSubdomain = false,
                subdomainEnabled = if (result.isSuccess) enabled else _uiState.value.subdomainEnabled,
                message = if (result.isSuccess) "workers.dev 开关已更新" else (result.exceptionOrNull()?.message ?: "更新失败")
            )
        }
    }

    fun loadZones() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(zonesState = UiState.Loading)
            val result = try {
                withTimeout(15_000) {
                    zoneRepository.listZones()
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                Result.failure(Exception("请求超时，请检查网络和 Token 权限"))
            }
            val zones = result.getOrDefault(emptyList())
            _uiState.value = _uiState.value.copy(
                zonesState = result.fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it.message ?: "加载失败") }
                ),
                selectedZone = zones.firstOrNull()
            )
            loadRoutes()
        }
    }

    fun onZoneSelected(zone: Zone) {
        _uiState.value = _uiState.value.copy(selectedZone = zone)
        loadRoutes()
    }

    fun loadRoutes() {
        val zone = _uiState.value.selectedZone
        if (zone == null) {
            _uiState.value = _uiState.value.copy(routesState = UiState.Success(emptyList()))
            return
        }
        val name = _uiState.value.scriptName
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(routesState = UiState.Loading)
            val result = cloudflareRepository.listWorkerRoutes(zone.id)
            _uiState.value = _uiState.value.copy(
                routesState = result.map { routes ->
                    routes.filter { it.script == name }
                }.fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it.message ?: "加载失败") }
                )
            )
        }
    }

    fun createDomain(zone: Zone, hostname: String) {
        val id = accountId ?: return
        val name = _uiState.value.scriptName
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(showAddDomainDialog = false)
            val result = cloudflareRepository.createWorkerDomain(id, name, zone.id, hostname)
            _uiState.value = _uiState.value.copy(
                message = if (result.isSuccess) "自定义域名已添加" else (result.exceptionOrNull()?.message ?: "添加失败")
            )
            loadDomains()
        }
    }

    fun deleteDomain(domainId: String) {
        val id = accountId ?: return
        viewModelScope.launch {
            val result = cloudflareRepository.deleteWorkerDomain(id, domainId)
            _uiState.value = _uiState.value.copy(
                message = if (result.isSuccess) "自定义域名已删除" else (result.exceptionOrNull()?.message ?: "删除失败")
            )
            loadDomains()
        }
    }

    fun createRoute(zone: Zone, pattern: String) {
        val name = _uiState.value.scriptName
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(showAddRouteDialog = false)
            val result = cloudflareRepository.createWorkerRoute(zone.id, pattern, name)
            _uiState.value = _uiState.value.copy(
                message = if (result.isSuccess) "Route 已添加" else (result.exceptionOrNull()?.message ?: "添加失败")
            )
            loadRoutes()
        }
    }

    fun deleteRoute(zone: Zone, routeId: String) {
        viewModelScope.launch {
            val result = cloudflareRepository.deleteWorkerRoute(zone.id, routeId)
            _uiState.value = _uiState.value.copy(
                message = if (result.isSuccess) "Route 已删除" else (result.exceptionOrNull()?.message ?: "删除失败")
            )
            loadRoutes()
        }
    }

    fun showAddDomainDialog() {
        _uiState.value = _uiState.value.copy(showAddDomainDialog = true)
    }

    fun dismissAddDomainDialog() {
        _uiState.value = _uiState.value.copy(showAddDomainDialog = false)
    }

    fun showAddRouteDialog() {
        _uiState.value = _uiState.value.copy(showAddRouteDialog = true)
    }

    fun dismissAddRouteDialog() {
        _uiState.value = _uiState.value.copy(showAddRouteDialog = false)
    }

    fun addSecret(name: String, value: String) {
        val id = accountId ?: return
        val script = _uiState.value.scriptName
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(showAddSecretDialog = false)
            val result = cloudflareRepository.putWorkerSecret(id, script, name, value)
            _uiState.value = _uiState.value.copy(
                message = if (result.isSuccess) "Secret 已保存" else (result.exceptionOrNull()?.message ?: "保存失败")
            )
        }
    }

    fun deleteSecret(name: String) {
        val id = accountId ?: return
        val script = _uiState.value.scriptName
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(showDeleteSecretDialog = false)
            val result = cloudflareRepository.deleteWorkerSecret(id, script, name)
            _uiState.value = _uiState.value.copy(
                message = if (result.isSuccess) "Secret 已删除" else (result.exceptionOrNull()?.message ?: "删除失败")
            )
        }
    }

    fun showAddSecretDialog() {
        _uiState.value = _uiState.value.copy(showAddSecretDialog = true)
    }

    fun dismissAddSecretDialog() {
        _uiState.value = _uiState.value.copy(showAddSecretDialog = false)
    }

    fun showDeleteSecretDialog() {
        _uiState.value = _uiState.value.copy(showDeleteSecretDialog = true)
    }

    fun dismissDeleteSecretDialog() {
        _uiState.value = _uiState.value.copy(showDeleteSecretDialog = false)
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
