package com.cloudflare.manager.ui.dns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
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
import com.cloudflare.manager.domain.model.DnsRecord
import com.cloudflare.manager.domain.model.UiState
import com.cloudflare.manager.ui.components.CfTopBar
import com.cloudflare.manager.ui.components.DnsRecordCard
import com.cloudflare.manager.ui.components.EmptyState
import com.cloudflare.manager.ui.components.ErrorMessage
import com.cloudflare.manager.ui.components.LoadingIndicator

@Composable
fun DnsScreen(
    zoneId: String,
    zoneName: String,
    snackbarHostState: SnackbarHostState,
    onAddRecord: () -> Unit,
    onEditRecord: (DnsRecord) -> Unit,
    onBack: () -> Unit,
    viewModel: DnsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = { CfTopBar(title = "DNS · $zoneName", onBack = onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddRecord) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_dns_record))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            when (val recordsState = state.recordsState) {
                is UiState.Loading -> LoadingIndicator()
                is UiState.Error -> ErrorMessage(
                    message = recordsState.message,
                    onRetry = viewModel::loadRecords
                )
                is UiState.Success -> {
                    if (recordsState.data.isEmpty()) {
                        EmptyState(message = "暂无 DNS 记录")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(recordsState.data, key = { it.id }) { record ->
                                DnsRecordCard(
                                    record = record,
                                    onEdit = { onEditRecord(record) },
                                    onDelete = { viewModel.deleteRecord(record) },
                                    onToggleProxy = { enabled ->
                                        viewModel.updateRecord(record.copy(proxied = enabled))
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
