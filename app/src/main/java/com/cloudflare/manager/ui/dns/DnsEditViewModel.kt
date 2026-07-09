package com.cloudflare.manager.ui.dns

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudflare.manager.data.repository.DnsRepository
import com.cloudflare.manager.domain.model.DnsRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DnsEditUiState(
    val id: String? = null,
    val type: String = "A",
    val name: String = "",
    val content: String = "",
    val ttl: String = "1",
    val proxied: Boolean = true,
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false
)

@HiltViewModel
class DnsEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val dnsRepository: DnsRepository
) : ViewModel() {

    val zoneId: String = checkNotNull(savedStateHandle["zoneId"])
    private val recordId: String? = savedStateHandle.get<String>("recordId")?.takeIf { it != "new" }

    private val _uiState = MutableStateFlow(DnsEditUiState())
    val uiState: StateFlow<DnsEditUiState> = _uiState

    private var originalRecord: DnsRecord? = null

    init {
        recordId?.let { loadRecord(it) }
    }

    private fun loadRecord(id: String) {
        viewModelScope.launch {
            // We don't have a get-by-id API; load all and find.
            // In a real app you'd cache records or have a dedicated endpoint.
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = dnsRepository.listRecords(zoneId)
            val record = result.getOrNull()?.find { it.id == id }
            if (record != null) {
                originalRecord = record
                _uiState.value = DnsEditUiState(
                    id = record.id,
                    type = record.type,
                    name = record.name,
                    content = record.content,
                    ttl = record.ttl.toString(),
                    proxied = record.proxied
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "找不到该记录"
                )
            }
        }
    }

    fun onTypeChange(value: String) { _uiState.value = _uiState.value.copy(type = value) }
    fun onNameChange(value: String) { _uiState.value = _uiState.value.copy(name = value) }
    fun onContentChange(value: String) { _uiState.value = _uiState.value.copy(content = value) }
    fun onTtlChange(value: String) { _uiState.value = _uiState.value.copy(ttl = value) }
    fun onProxiedChange(value: Boolean) { _uiState.value = _uiState.value.copy(proxied = value) }

    fun save() {
        val state = _uiState.value
        if (state.name.isBlank() || state.content.isBlank()) {
            _uiState.value = state.copy(error = "名称和内容不能为空")
            return
        }
        val ttl = state.ttl.toIntOrNull() ?: 1
        val record = DnsRecord(
            id = state.id ?: "",
            type = state.type,
            name = state.name,
            content = state.content,
            ttl = ttl,
            proxied = state.proxied,
            zoneId = zoneId
        )

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)
            val result = if (state.id == null) {
                dnsRepository.createRecord(zoneId, record)
            } else {
                dnsRepository.updateRecord(zoneId, record)
            }
            _uiState.value = _uiState.value.copy(isLoading = false)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(success = true)
            } else {
                _uiState.value = _uiState.value.copy(
                    error = result.exceptionOrNull()?.message ?: "保存失败"
                )
            }
        }
    }
}
