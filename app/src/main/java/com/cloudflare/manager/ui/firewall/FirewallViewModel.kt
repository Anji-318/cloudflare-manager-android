package com.cloudflare.manager.ui.firewall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudflare.manager.data.repository.AccountRepository
import com.cloudflare.manager.data.repository.CloudflareRepository
import com.cloudflare.manager.data.repository.ZoneRepository
import com.cloudflare.manager.domain.model.FirewallRule
import com.cloudflare.manager.domain.model.UiState
import com.cloudflare.manager.domain.model.Zone
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FirewallUiState(
    val zonesState: UiState<List<Zone>> = UiState.Loading,
    val selectedZone: Zone? = null,
    val rulesState: UiState<List<FirewallRule>> = UiState.Loading,
    val threatCount: Int = 0,
    val accessRuleCount: Int = 0,
    val message: String? = null
)

@HiltViewModel
class FirewallViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val zoneRepository: ZoneRepository,
    private val cloudflareRepository: CloudflareRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FirewallUiState())
    val uiState: StateFlow<FirewallUiState> = _uiState

    init {
        accountRepository.currentAccount
            .onEach { loadZones() }
            .launchIn(viewModelScope)
        loadZones()
    }

    fun loadZones() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(zonesState = UiState.Loading)
            val result = zoneRepository.listZones()
            _uiState.value = _uiState.value.copy(
                zonesState = result.fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it.message ?: "加载失败") }
                )
            )
            if (result.isSuccess && result.getOrNull()?.isNotEmpty() == true) {
                val zones = result.getOrDefault(emptyList())
                if (_uiState.value.selectedZone == null) {
                    selectZone(zones.first())
                }
            }
        }
    }

    fun selectZone(zone: Zone) {
        _uiState.value = _uiState.value.copy(selectedZone = zone)
        loadFirewall()
    }

    fun loadFirewall() {
        val zone = _uiState.value.selectedZone ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(rulesState = UiState.Loading)
            val rulesResult = cloudflareRepository.listFirewallRules(zone.id)
            val accessResult = cloudflareRepository.countAccessRules(zone.id)
            _uiState.value = _uiState.value.copy(
                rulesState = rulesResult.fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it.message ?: "加载失败") }
                ),
                accessRuleCount = accessResult.getOrDefault(0)
            )
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
