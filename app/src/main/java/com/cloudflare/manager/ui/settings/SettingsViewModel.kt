package com.cloudflare.manager.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudflare.manager.data.local.SettingsDataStore
import com.cloudflare.manager.data.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val message: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    init {
        combine(
            settingsDataStore.darkMode,
            settingsDataStore.followSystemTheme
        ) { darkMode, followSystem ->
            val mode = when {
                followSystem -> ThemeMode.SYSTEM
                darkMode == true -> ThemeMode.DARK
                else -> ThemeMode.LIGHT
            }
            _uiState.value = _uiState.value.copy(themeMode = mode)
        }.launchIn(viewModelScope)
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            when (mode) {
                ThemeMode.SYSTEM -> settingsDataStore.setFollowSystemTheme(true)
                ThemeMode.LIGHT -> {
                    settingsDataStore.setDarkMode(false)
                    settingsDataStore.setFollowSystemTheme(false)
                }
                ThemeMode.DARK -> {
                    settingsDataStore.setDarkMode(true)
                    settingsDataStore.setFollowSystemTheme(false)
                }
            }
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            accountRepository.clearAll()
            _uiState.value = _uiState.value.copy(message = "所有数据已清除")
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
