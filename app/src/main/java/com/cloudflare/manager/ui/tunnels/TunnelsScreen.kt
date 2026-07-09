package com.cloudflare.manager.ui.tunnels

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cloudflare.manager.domain.model.Tunnel
import com.cloudflare.manager.domain.model.UiState
import com.cloudflare.manager.ui.components.CfTopBar
import com.cloudflare.manager.ui.components.EmptyState
import com.cloudflare.manager.ui.components.ErrorMessage
import com.cloudflare.manager.ui.components.LoadingIndicator
import com.cloudflare.manager.ui.theme.SuccessGreen

@Composable
fun TunnelsScreen(
    onBack: () -> Unit,
    viewModel: TunnelsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            CfTopBar(
                title = "Tunnels",
                onBack = onBack,
                actions = {
                    IconButton(onClick = viewModel::loadTunnels) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            when (val tunnelsState = state.tunnelsState) {
                is UiState.Loading -> LoadingIndicator()
                is UiState.Error -> ErrorMessage(
                    message = tunnelsState.message,
                    onRetry = viewModel::loadTunnels
                )
                is UiState.Success -> {
                    if (tunnelsState.data.isEmpty()) {
                        EmptyState(message = "暂无 Tunnel")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(tunnelsState.data, key = { it.id }) { tunnel ->
                                TunnelCard(tunnel)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TunnelCard(tunnel: Tunnel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = tunnel.name,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tunnel.status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (tunnel.status == "健康") SuccessGreen else MaterialTheme.colorScheme.error
                )
                Text(
                    text = "${tunnel.connections} 个连接",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
