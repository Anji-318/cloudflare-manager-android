package com.cloudflare.manager.ui.pages

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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.cloudflare.manager.domain.model.PageDeployment
import com.cloudflare.manager.domain.model.PageDomain
import com.cloudflare.manager.domain.model.UiState
import com.cloudflare.manager.ui.components.CfTopBar
import com.cloudflare.manager.ui.components.EmptyState
import com.cloudflare.manager.ui.components.ErrorMessage
import com.cloudflare.manager.ui.components.LoadingIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PagesDetailScreen(
    projectName: String,
    onBack: () -> Unit,
    viewModel: PagesDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(projectName) {
        viewModel.setProjectName(projectName)
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
                title = projectName,
                onBack = onBack
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                DeploymentsSection(
                    state = state.deploymentsState,
                    onRetry = viewModel::loadDeployments
                )
            }
            item {
                PagesDomainsSection(
                    state = state.domainsState,
                    onAdd = viewModel::showAddDomainDialog,
                    onDelete = viewModel::deleteDomain,
                    onRetry = viewModel::retryDomain,
                    onRetryLoad = viewModel::loadDomains
                )
            }
        }
    }

    if (state.showAddDomainDialog) {
        AddPagesDomainDialog(
            onDismiss = viewModel::dismissAddDomainDialog,
            onConfirm = viewModel::createDomain
        )
    }
}

@Composable
private fun DeploymentsSection(
    state: UiState<List<PageDeployment>>,
    onRetry: () -> Unit
) {
    SectionCard(
        title = "部署列表",
        action = {
            TextButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = "刷新")
                Spacer(modifier = Modifier.width(4.dp))
                Text("刷新")
            }
        }
    ) {
        when (state) {
            is UiState.Loading -> LoadingIndicator()
            is UiState.Error -> ErrorMessage(message = state.message, onRetry = onRetry)
            is UiState.Success -> {
                if (state.data.isEmpty()) {
                    EmptyState(message = "暂无部署记录")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.data.forEach { deployment ->
                            DeploymentItem(deployment = deployment)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeploymentItem(deployment: PageDeployment) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = deployment.environment ?: "production",
                style = MaterialTheme.typography.bodyLarge
            )
            if (!deployment.url.isNullOrBlank()) {
                Text(
                    text = deployment.url,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = "创建时间：${deployment.createdOn ?: "-"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PagesDomainsSection(
    state: UiState<List<PageDomain>>,
    onAdd: () -> Unit,
    onDelete: (String) -> Unit,
    onRetry: (String) -> Unit,
    onRetryLoad: () -> Unit
) {
    SectionCard(
        title = "自定义域名",
        action = {
            TextButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("添加")
            }
        }
    ) {
        when (state) {
            is UiState.Loading -> LoadingIndicator()
            is UiState.Error -> ErrorMessage(message = state.message, onRetry = onRetryLoad)
            is UiState.Success -> {
                if (state.data.isEmpty()) {
                    EmptyState(message = "暂无自定义域名")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.data.forEach { domain ->
                            PagesDomainItem(
                                domain = domain,
                                onDelete = { onDelete(domain.name) },
                                onRetry = { onRetry(domain.name) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PagesDomainItem(
    domain: PageDomain,
    onDelete: () -> Unit,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                    text = domain.name,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "${domain.zoneName ?: ""} ${domain.status ?: ""}".trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row {
                IconButton(onClick = onRetry) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "重新验证",
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
private fun AddPagesDomainDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加自定义域名") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("域名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank()
            ) {
                Text("添加")
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
