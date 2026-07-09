package com.cloudflare.manager.ui.kvd1

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.cloudflare.manager.domain.model.KvKey
import com.cloudflare.manager.domain.model.UiState
import com.cloudflare.manager.ui.components.CfTopBar
import com.cloudflare.manager.ui.components.EmptyState
import com.cloudflare.manager.ui.components.ErrorMessage
import com.cloudflare.manager.ui.components.LoadingIndicator

@Composable
fun KvDetailScreen(
    namespaceId: String,
    namespaceTitle: String,
    onBack: () -> Unit,
    viewModel: KvDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(namespaceId) {
        viewModel.setNamespace(namespaceId, namespaceTitle)
    }

    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            CfTopBar(
                title = "KV: $namespaceTitle",
                onBack = onBack
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::showAddKeyDialog) {
                Icon(Icons.Default.Add, contentDescription = "新增键值")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                KeysSection(
                    state = state.keysState,
                    onEdit = viewModel::showEditKeyDialog,
                    onDelete = viewModel::deleteKey,
                    onRetry = viewModel::loadKeys
                )
            }
        }
    }

    if (state.showKeyValueDialog) {
        KeyValueDialog(
            keyName = state.editingKey,
            value = state.currentValue,
            loading = state.currentValueLoading,
            isNewKey = state.isNewKey,
            onDismiss = viewModel::dismissKeyValueDialog,
            onValueChange = viewModel::onValueChange,
            onConfirm = { key, value ->
                viewModel.saveKeyValue(key, value)
            }
        )
    }
}

@Composable
private fun KeysSection(
    state: UiState<List<KvKey>>,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRetry: () -> Unit
) {
    SectionCard(title = "键列表") {
        when (state) {
            is UiState.Loading -> LoadingIndicator()
            is UiState.Error -> ErrorMessage(message = state.message, onRetry = onRetry)
            is UiState.Success -> {
                if (state.data.isEmpty()) {
                    EmptyState(message = "暂无键值")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.data.forEach { key ->
                            KeyItem(
                                key = key,
                                onEdit = { onEdit(key.name) },
                                onDelete = { onDelete(key.name) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyItem(
    key: KvKey,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onEdit,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = key.name,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (key.expiration != null) {
                    Text(
                        text = "过期: ${key.expiration}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "编辑",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyValueDialog(
    keyName: String,
    value: String,
    loading: Boolean,
    isNewKey: Boolean,
    onDismiss: () -> Unit,
    onValueChange: (String) -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var key by remember(keyName) { mutableStateOf(keyName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNewKey) "新增键值" else "编辑键值") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("键名") },
                    readOnly = !isNewKey,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .width(24.dp)
                            .height(24.dp)
                    )
                } else {
                    OutlinedTextField(
                        value = value,
                        onValueChange = onValueChange,
                        label = { Text("值") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        maxLines = 10
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (key.isNotBlank()) onConfirm(key.trim(), value) },
                enabled = key.isNotBlank() && !loading
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun SectionCard(
    title: String,
    action: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                action?.invoke()
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}
