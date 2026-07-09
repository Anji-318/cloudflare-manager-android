package com.cloudflare.manager.ui.firewall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cloudflare.manager.domain.model.FirewallRule
import com.cloudflare.manager.domain.model.UiState
import com.cloudflare.manager.domain.model.Zone
import com.cloudflare.manager.ui.components.CfTopBar
import com.cloudflare.manager.ui.components.EmptyState
import com.cloudflare.manager.ui.components.ErrorMessage
import com.cloudflare.manager.ui.components.LoadingIndicator
import com.cloudflare.manager.ui.theme.CfOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirewallScreen(
    onBack: () -> Unit,
    viewModel: FirewallViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { CfTopBar(title = "Firewall & WAF", onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            ZoneSelector(state, viewModel)
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    title = "防火墙规则",
                    value = (state.rulesState as? UiState.Success)?.data?.size?.toString() ?: "-",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "IP 访问规则",
                    value = state.accessRuleCount.toString(),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            when (val rulesState = state.rulesState) {
                is UiState.Loading -> LoadingIndicator()
                is UiState.Error -> ErrorMessage(
                    message = rulesState.message,
                    onRetry = viewModel::loadFirewall
                )
                is UiState.Success -> {
                    if (rulesState.data.isEmpty()) {
                        EmptyState(message = "暂无防火墙规则")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(rulesState.data, key = { it.id }) { rule ->
                                RuleCard(rule)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZoneSelector(
    state: FirewallUiState,
    viewModel: FirewallViewModel
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
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineLarge,
                color = CfOrange
            )
        }
    }
}

@Composable
private fun RuleCard(rule: FirewallRule) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = rule.description,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = rule.action.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (rule.paused) "已暂停" else "启用中",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (rule.paused) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}
