package com.cloudflare.manager.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudflare.manager.data.repository.AccountRepository
import com.cloudflare.manager.data.repository.CloudflareRepository
import com.cloudflare.manager.data.repository.ZoneRepository
import com.cloudflare.manager.domain.model.AnalyticsSummary
import com.cloudflare.manager.domain.model.CountryStat
import com.cloudflare.manager.domain.model.UiState
import com.cloudflare.manager.domain.model.Zone
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

data class AnalyticsUiState(
    val zonesState: UiState<List<Zone>> = UiState.Loading,
    val analyticsState: UiState<Pair<AnalyticsSummary, List<CountryStat>>> = UiState.Loading,
    val selectedZone: Zone? = null,
    val rangeSeconds: Long = 86400
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val zoneRepository: ZoneRepository,
    private val cloudflareRepository: CloudflareRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState

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
                analyticsState = UiState.Loading
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
        loadAnalytics()
    }

    fun setRange(seconds: Long) {
        _uiState.value = _uiState.value.copy(rangeSeconds = seconds)
        loadAnalytics()
    }

    fun loadAnalytics() {
        val zone = _uiState.value.selectedZone ?: return
        val range = _uiState.value.rangeSeconds
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(analyticsState = UiState.Loading)
            val until = System.currentTimeMillis()
            val since = until - range * 1000
            val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
            val result = cloudflareRepository.getAnalytics(
                zone.id,
                formatter.format(java.util.Date(since)),
                formatter.format(java.util.Date(until))
            )
            _uiState.value = _uiState.value.copy(
                analyticsState = result.fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it.message ?: "加载失败") }
                )
            )
        }
    }
}
