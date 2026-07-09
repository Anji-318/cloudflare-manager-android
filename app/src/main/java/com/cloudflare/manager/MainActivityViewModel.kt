package com.cloudflare.manager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudflare.manager.data.local.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ThemeSettings(
    val isDarkMode: Boolean? = null,
    val followSystem: Boolean = true
)

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    settingsDataStore: SettingsDataStore
) : ViewModel() {

    val settings = combine(
        settingsDataStore.darkMode,
        settingsDataStore.followSystemTheme
    ) { darkMode, followSystem ->
        ThemeSettings(isDarkMode = darkMode, followSystem = followSystem)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeSettings())
}
