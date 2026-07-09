package com.cloudflare.manager.ui.cache

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudflare.manager.data.repository.AccountRepository
import com.cloudflare.manager.data.repository.CloudflareRepository
import com.cloudflare.manager.data.repository.ZoneRepository
import com.cloudflare.manager.domain.model.UiState
import com.cloudflare.manager.domain.model.Zone
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class CacheUiState(
    val zonesState: UiState<List<Zone>> = UiState.Loading,
    val selectedZone: Zone? = null,
    val argoEnabled: Boolean = false,
    val polishEnabled: Boolean = false,
    val minifyEnabled: Boolean = false,
    val isLoading: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class CacheViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val zoneRepository: ZoneRepository,
    private val cloudflareRepository: CloudflareRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CacheUiState())
    val uiState: StateFlow<CacheUiState> = _uiState

    init {
        accountRepository.currentAccount
            .onEach { loadZones() }
            .launchIn(viewModelScope)

        loadZones()
    }

    fun loadZones() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                zonesState = UiState.Loading,
                selectedZone = null,
                argoEnabled = false,
                polishEnabled = false,
                minifyEnabled = false
            )
            val result = zoneRepository.listZones()
            _uiState.value = _uiState.value.copy(
                zonesState = result.fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it.message ?: "加载失败") }
                )
            )
            if (result.isSuccess && result.getOrNull()?.isNotEmpty() == true) {
                val zones = result.getOrDefault(emptyList())
                selectZone(zones.first())
            }
        }
    }

    fun selectZone(zone: Zone) {
        _uiState.value = _uiState.value.copy(selectedZone = zone)
        loadSettings()
    }

    fun loadSettings() {
        val zone = _uiState.value.selectedZone ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val argo = cloudflareRepository.getZoneSetting(zone.id, "argo_smart_routing").getOrDefault(false)
            val polish = cloudflareRepository.getZoneSetting(zone.id, "polish").getOrDefault(false)
            val minify = cloudflareRepository.getZoneSetting(zone.id, "minify").getOrDefault(false)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                argoEnabled = argo,
                polishEnabled = polish,
                minifyEnabled = minify
            )
        }
    }

    fun purgeCache() {
        val zone = _uiState.value.selectedZone ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = cloudflareRepository.purgeCache(zone.id)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                message = if (result.isSuccess) "缓存清除成功" else (result.exceptionOrNull()?.message ?: "清除失败")
            )
        }
    }

    fun toggleSetting(setting: String) {
        val zone = _uiState.value.selectedZone ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val current = when (setting) {
                "argo_smart_routing" -> _uiState.value.argoEnabled
                "polish" -> _uiState.value.polishEnabled
                "minify" -> _uiState.value.minifyEnabled
                else -> false
            }
            val newValue = !current
            val body = when (setting) {
                "argo_smart_routing" -> "\"${if (newValue) "on" else "off"}\""
                "polish" -> "\"${if (newValue) "lossless" else "off"}\""
                "minify" -> "{\"html\":$newValue,\"css\":$newValue,\"js\":$newValue}"
                else -> "{}"
            }
            val result = cloudflareRepository.updateZoneSetting(
                zone.id,
                setting,
                Json.parseToJsonElement(body)
            )
            _uiState.value = _uiState.value.copy(isLoading = false)
            if (result.isSuccess) {
                when (setting) {
                    "argo_smart_routing" -> _uiState.value = _uiState.value.copy(argoEnabled = newValue)
                    "polish" -> _uiState.value = _uiState.value.copy(polishEnabled = newValue)
                    "minify" -> _uiState.value = _uiState.value.copy(minifyEnabled = newValue)
                }
                _uiState.value = _uiState.value.copy(message = "设置已更新")
            } else {
                _uiState.value = _uiState.value.copy(
                    message = result.exceptionOrNull()?.message ?: "更新失败"
                )
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
