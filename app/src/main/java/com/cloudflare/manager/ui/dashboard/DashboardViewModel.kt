package com.cloudflare.manager.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudflare.manager.data.repository.AccountRepository
import com.cloudflare.manager.data.repository.CloudflareRepository
import com.cloudflare.manager.data.repository.DnsRepository
import com.cloudflare.manager.data.repository.ZoneRepository
import com.cloudflare.manager.domain.model.Account
import com.cloudflare.manager.domain.model.DnsRecord
import com.cloudflare.manager.domain.model.Zone
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val currentAccount: Account? = null,
    val accountCount: Int = 0,
    val zoneCount: Int = 0,
    val dnsCount: Int = 0,
    val requestCount: Long = 0,
    val zones: List<Zone> = emptyList(),
    val selectedZone: Zone? = null,
    val dnsRecords: List<DnsRecord> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val zoneRepository: ZoneRepository,
    private val dnsRepository: DnsRepository,
    private val cloudflareRepository: CloudflareRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState

    init {
        accountRepository.accounts
            .onEach { accounts ->
                _uiState.value = _uiState.value.copy(accountCount = accounts.size)
            }
            .launchIn(viewModelScope)

        accountRepository.currentAccount
            .onEach { current ->
                _uiState.value = _uiState.value.copy(currentAccount = current)
                if (current != null) {
                    loadDashboardData()
                }
            }
            .launchIn(viewModelScope)
    }

    fun loadDashboardData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val zonesResult = zoneRepository.listZones()
                val zones = zonesResult.getOrDefault(emptyList())
                val selected = _uiState.value.selectedZone
                    ?: zones.firstOrNull()
                    ?: _uiState.value.selectedZone

                _uiState.value = _uiState.value.copy(
                    zones = zones,
                    zoneCount = zones.size,
                    selectedZone = selected,
                    isLoading = false
                )

                // 加载选中域名的 DNS
                if (selected != null) {
                    loadDnsForZone(selected.id)
                }

                // 获取所有域名的当天请求数总和
                loadTotalRequests(zones)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "加载失败"
                )
            }
        }
    }

    fun selectZone(zone: Zone) {
        _uiState.value = _uiState.value.copy(selectedZone = zone)
        viewModelScope.launch {
            loadDnsForZone(zone.id)
        }
    }

    private suspend fun loadDnsForZone(zoneId: String) {
        try {
            val dnsResult = dnsRepository.listRecords(zoneId)
            val records = dnsResult.getOrDefault(emptyList())
            _uiState.value = _uiState.value.copy(
                dnsRecords = records,
                dnsCount = records.size
            )
        } catch (e: Exception) {
            android.util.Log.e("DashboardViewModel", "loadDns error: ${e.message}")
        }
    }

    private suspend fun loadTotalRequests(zones: List<Zone>) {
        if (zones.isEmpty()) return

        val now = System.currentTimeMillis() / 1000
        val todayStart = now - (now % 86400)  // 当天零点

        var totalRequests = 0L
        zones.forEach { zone ->
            try {
                val analyticsResult = cloudflareRepository.getAnalytics(
                    zone.id,
                    since = todayStart.toString(),
                    until = now.toString()
                )
                analyticsResult.onSuccess { (summary, _) ->
                    totalRequests += summary.totalRequests
                }
            } catch (e: Exception) {
                android.util.Log.e("DashboardViewModel", "analytics error for ${zone.name}: ${e.message}")
            }
        }

        _uiState.value = _uiState.value.copy(requestCount = totalRequests)
    }
}
