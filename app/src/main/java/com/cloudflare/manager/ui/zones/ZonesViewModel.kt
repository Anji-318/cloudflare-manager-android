package com.cloudflare.manager.ui.zones

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudflare.manager.data.repository.AccountRepository
import com.cloudflare.manager.data.repository.ZoneRepository
import com.cloudflare.manager.domain.model.UiState
import com.cloudflare.manager.domain.model.Zone
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

data class ZonesUiState(
    val zonesState: UiState<List<Zone>> = UiState.Loading,
    val searchQuery: String = ""
)

@HiltViewModel
class ZonesViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val zoneRepository: ZoneRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ZonesUiState())
    val uiState: StateFlow<ZonesUiState> = _uiState

    private val _searchQuery = MutableStateFlow("")
    private val _zones = MutableStateFlow<UiState<List<Zone>>>(UiState.Loading)

    init {
        // 监听 zones 和 searchQuery 变化，更新 UI 状态
        _zones.onEach { zones ->
            updateUiState()
        }.launchIn(viewModelScope)

        _searchQuery.onEach {
            updateUiState()
        }.launchIn(viewModelScope)

        // 等待账户和 Token 准备好后再加载
        accountRepository.currentAccount
            .onEach { account ->
                android.util.Log.d("ZonesViewModel", "currentAccount=$account")
                if (account != null) {
                    loadZones()
                } else {
                    _zones.value = UiState.Error("未找到账户，请先添加账户")
                }
            }
            .launchIn(viewModelScope)
    }

    private fun updateUiState() {
        val zones = _zones.value
        val query = _searchQuery.value
        _uiState.value = ZonesUiState(
            zonesState = when (zones) {
                is UiState.Success -> {
                    val filtered = if (query.isBlank()) {
                        zones.data
                    } else {
                        zones.data.filter { it.name.contains(query, ignoreCase = true) }
                    }
                    UiState.Success(filtered)
                }
                else -> zones
            },
            searchQuery = query
        )
    }

    fun loadZones() {
        viewModelScope.launch {
            android.util.Log.d("ZonesViewModel", "loadZones called")
            _zones.value = UiState.Loading
            _zones.value = try {
                withTimeout(15_000) {
                    zoneRepository.listZones().fold(
                        onSuccess = { 
                            android.util.Log.d("ZonesViewModel", "loadZones success: ${it.size} zones")
                            UiState.Success(it) 
                        },
                        onFailure = { 
                            android.util.Log.e("ZonesViewModel", "loadZones error: ${it.message}")
                            UiState.Error(it.message ?: "加载失败") 
                        }
                    )
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                UiState.Error("请求超时，请检查网络和 Token 权限")
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }
}
