package com.cloudflare.manager.ui.zones

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cloudflare.manager.R
import com.cloudflare.manager.domain.model.UiState
import com.cloudflare.manager.domain.model.Zone
import com.cloudflare.manager.ui.components.CfTopBar
import com.cloudflare.manager.ui.components.EmptyState
import com.cloudflare.manager.ui.components.ErrorMessage
import com.cloudflare.manager.ui.components.LoadingIndicator
import com.cloudflare.manager.ui.components.ZoneCard

@Composable
fun ZonesScreen(
    snackbarHostState: SnackbarHostState,
    onZoneClick: (Zone) -> Unit,
    viewModel: ZonesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val zonesState = state.zonesState

    LaunchedEffect(zonesState) {
        if (zonesState is UiState.Error) {
            snackbarHostState.showSnackbar(zonesState.message)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        CfTopBar(
            title = stringResource(R.string.zones),
            actions = {
                IconButton(onClick = viewModel::loadZones) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                }
            }
        )

        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = viewModel::onSearchQueryChange,
            label = { Text(stringResource(R.string.search)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        when (zonesState) {
            is UiState.Loading -> LoadingIndicator()
            is UiState.Error -> ErrorMessage(
                message = zonesState.message,
                onRetry = viewModel::loadZones
            )
            is UiState.Success -> {
                if (zonesState.data.isEmpty()) {
                    EmptyState(message = "暂无域名")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(zonesState.data, key = { it.id }) { zone ->
                            ZoneCard(
                                zone = zone,
                                onClick = { onZoneClick(zone) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}
