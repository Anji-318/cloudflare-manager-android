package com.cloudflare.manager.ui.dns

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudflare.manager.data.repository.DnsRepository
import com.cloudflare.manager.domain.model.DnsRecord
import com.cloudflare.manager.domain.model.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DnsUiState(
    val recordsState: UiState<List<DnsRecord>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class DnsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val dnsRepository: DnsRepository
) : ViewModel() {

    val zoneId: String = checkNotNull(savedStateHandle["zoneId"])

    private val _uiState = MutableStateFlow(DnsUiState())
    val uiState: StateFlow<DnsUiState> = _uiState

    init {
        loadRecords()
    }

    fun loadRecords() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            val result = dnsRepository.listRecords(zoneId)
            _uiState.value = _uiState.value.copy(
                isRefreshing = false,
                recordsState = result.fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it.message ?: "加载失败") }
                )
            )
        }
    }

    fun deleteRecord(record: DnsRecord) {
        viewModelScope.launch {
            val result = dnsRepository.deleteRecord(zoneId, record.id)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(message = "记录已删除")
                loadRecords()
            } else {
                _uiState.value = _uiState.value.copy(
                    message = result.exceptionOrNull()?.message ?: "删除失败"
                )
            }
        }
    }

    fun updateRecord(record: DnsRecord) {
        viewModelScope.launch {
            val result = dnsRepository.updateRecord(zoneId, record)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(message = "代理状态已更新")
                loadRecords()
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
