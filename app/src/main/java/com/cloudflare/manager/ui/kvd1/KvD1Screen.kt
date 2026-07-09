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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
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
import com.cloudflare.manager.domain.model.D1Database
import com.cloudflare.manager.domain.model.KvNamespace
import com.cloudflare.manager.domain.model.UiState
import com.cloudflare.manager.ui.components.CfTopBar
import com.cloudflare.manager.ui.components.EmptyState
import com.cloudflare.manager.ui.components.ErrorMessage
import com.cloudflare.manager.ui.components.LoadingIndicator
import com.cloudflare.manager.ui.kv.KvD1ViewModel

@Composable
fun KvD1Screen(
    onBack: () -> Unit,
    onNavigateToKvDetail: (String, String) -> Unit,
    onNavigateToD1Detail: (String, String) -> Unit,
    viewModel: KvD1ViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    var showAddKvDialog by remember { mutableStateOf(false) }
    var showAddD1Dialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CfTopBar(
                title = "KV / D1",
                onBack = onBack,
                actions = {
                    IconButton(onClick = viewModel::loadAll) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    SectionHeader(
                        title = "KV 命名空间",
                        onAdd = { showAddKvDialog = true }
                    )
                    when (val kvState = state.kvState) {
                        is UiState.Loading -> LoadingIndicator()
                        is UiState.Error -> ErrorMessage(
                            message = kvState.message,
                            onRetry = viewModel::loadAll
                        )
                        is UiState.Success -> {
                            if (kvState.data.isEmpty()) {
                                EmptyState(message = "暂无 KV 命名空间")
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    kvState.data.forEach { namespace ->
                                        KvNamespaceCard(
                                            namespace = namespace,
                                            onClick = { onNavigateToKvDetail(namespace.id, namespace.title) },
                                            onDelete = { viewModel.deleteKv(namespace.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    SectionHeader(
                        title = "D1 数据库",
                        onAdd = { showAddD1Dialog = true }
                    )
                    when (val d1State = state.d1State) {
                        is UiState.Loading -> LoadingIndicator()
                        is UiState.Error -> ErrorMessage(
                            message = d1State.message,
                            onRetry = viewModel::loadAll
                        )
                        is UiState.Success -> {
                            if (d1State.data.isEmpty()) {
                                EmptyState(message = "暂无 D1 数据库")
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    d1State.data.forEach { database ->
                                        D1DatabaseCard(
                                            database = database,
                                            onClick = { onNavigateToD1Detail(database.uuid, database.name) },
                                            onDelete = { viewModel.deleteD1(database.uuid) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddKvDialog) {
        AddNameDialog(
            title = "新建 KV 命名空间",
            label = "名称",
            onDismiss = { showAddKvDialog = false },
            onConfirm = {
                viewModel.createKv(it)
                showAddKvDialog = false
            }
        )
    }
    if (showAddD1Dialog) {
        AddNameDialog(
            title = "新建 D1 数据库",
            label = "数据库名称",
            onDismiss = { showAddD1Dialog = false },
            onConfirm = {
                viewModel.createD1(it)
                showAddD1Dialog = false
            }
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge
        )
        IconButton(onClick = onAdd) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "添加",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun KvNamespaceCard(
    namespace: KvNamespace,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = namespace.title,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "ID：${namespace.id}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
private fun D1DatabaseCard(
    database: D1Database,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = database.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "版本：${database.version ?: "-"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
private fun AddNameDialog(
    title: String,
    label: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(label) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank()
            ) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
