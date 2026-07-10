package com.cloudflare.manager.ui.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cloudflare.manager.BuildConfig
import com.cloudflare.manager.R
import com.cloudflare.manager.domain.model.Zone
import com.cloudflare.manager.ui.components.LoadingIndicator
import com.cloudflare.manager.ui.navigation.Screen
import com.cloudflare.manager.ui.theme.CfBlue
import com.cloudflare.manager.ui.theme.CfOrange
import com.cloudflare.manager.ui.theme.SuccessGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    snackbarHostState: SnackbarHostState,
    onNavigate: (String) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        DashboardTopBar(
            onOpenHelp = { onNavigate(Screen.Help.route) },
            onOpenSettings = { onNavigate(Screen.Settings.route) }
        )

        Box(modifier = Modifier.fillMaxSize()) {
            if (state.isLoading && !state.isRefreshing) {
                LoadingIndicator()
            } else {
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item { WelcomeSection(account = state.currentAccount) }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                StatCard(
                                    title = "账户",
                                    value = state.accountCount.toString(),
                                    color = CfBlue,
                                    modifier = Modifier.weight(1f)
                                )
                                StatCard(
                                    title = "域名",
                                    value = state.zoneCount.toString(),
                                    color = CfOrange,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                StatCard(
                                    title = "DNS",
                                    value = state.dnsCount.toString(),
                                    color = SuccessGreen,
                                    modifier = Modifier.weight(1f)
                                )
                                StatCard(
                                    title = "今日请求",
                                    value = formatRequestCount(state.requestCount),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        item {
                            Text(
                                text = "域名列表（点击切换）",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        items(state.zones, key = { it.id }) { zone ->
                            ZoneListItem(
                                zone = zone,
                                isSelected = zone.id == state.selectedZone?.id,
                                onClick = { viewModel.selectZone(zone) }
                            )
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick = { onNavigate(Screen.AddAccount.route) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_account))
            }
        }
    }
}

@Composable
private fun ZoneListItem(
    zone: Zone,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = zone.name,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "状态：${zone.status}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "已选中",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun formatRequestCount(count: Long): String {
    return when {
        count <= 0 -> "-"
        else -> "%,d".format(java.util.Locale.getDefault(), count)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardTopBar(
    onOpenHelp: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showVersionDialog by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val repoUrl = stringResource(R.string.opensource_repo_url)

    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier
                        .size(36.dp)
                        .clip(MaterialTheme.shapes.small)
                )
                Text(text = stringResource(R.string.app_name))
            }
        },
        actions = {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "更多"
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("使用说明") },
                        onClick = {
                            menuExpanded = false
                            onOpenHelp()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("版本号") },
                        onClick = {
                            menuExpanded = false
                            showVersionDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("开源仓库") },
                        onClick = {
                            menuExpanded = false
                            if (repoUrl.isNotBlank()) {
                                uriHandler.openUri(repoUrl)
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("设置") },
                        onClick = {
                            menuExpanded = false
                            onOpenSettings()
                        }
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier
            .height(56.dp)
            .shadow(elevation = 4.dp)
    )

    if (showVersionDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showVersionDialog = false },
            title = { Text("关于") },
            text = {
                Column {
                    Text("应用名称：${stringResource(R.string.app_name)}")
                    Text("版本号：${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                }
            },
            confirmButton = {
                TextButton(onClick = { showVersionDialog = false }) {
                    Text("确定")
                }
            }
        )
    }
}

@Composable
private fun WelcomeSection(account: com.cloudflare.manager.domain.model.Account?) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "仪表盘",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = account?.let { "当前账户：${it.name}" } ?: "请添加或选择一个账户",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
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
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineLarge,
                color = color
            )
        }
    }
}
