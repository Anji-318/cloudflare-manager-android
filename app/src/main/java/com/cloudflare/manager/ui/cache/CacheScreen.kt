package com.cloudflare.manager.ui.cache

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cloudflare.manager.domain.model.UiState
import com.cloudflare.manager.domain.model.Zone
import com.cloudflare.manager.ui.components.CfTopBar
import com.cloudflare.manager.ui.components.LoadingIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CacheScreen(
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState? = null,
    viewModel: CacheViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState?.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = { CfTopBar(title = "缓存与性能", onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ZoneSelector(state, viewModel)

            if (state.isLoading) {
                LoadingIndicator()
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "清除缓存",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = viewModel::purgeCache,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                        Text("清除所有缓存", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "性能开关",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ToggleItem(
                        title = "Argo Smart Routing",
                        subtitle = "智能路由加速",
                        checked = state.argoEnabled,
                        onCheckedChange = { viewModel.toggleSetting("argo_smart_routing") }
                    )
                    ToggleItem(
                        title = "Polish 图像优化",
                        subtitle = "自动压缩图像",
                        checked = state.polishEnabled,
                        onCheckedChange = { viewModel.toggleSetting("polish") }
                    )
                    ToggleItem(
                        title = "Auto Minify",
                        subtitle = "压缩 HTML/CSS/JS",
                        checked = state.minifyEnabled,
                        onCheckedChange = { viewModel.toggleSetting("minify") }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZoneSelector(
    state: CacheUiState,
    viewModel: CacheViewModel
) {
    val zones = (state.zonesState as? UiState.Success<List<Zone>>)?.data ?: emptyList()
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = state.selectedZone?.name ?: "选择域名",
            onValueChange = {},
            readOnly = true,
            label = { Text("域名") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            zones.forEach { zone ->
                DropdownMenuItem(
                    text = { Text(zone.name) },
                    onClick = {
                        viewModel.selectZone(zone)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ToggleItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = { onCheckedChange() }
        )
    }
}
