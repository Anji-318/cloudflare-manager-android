package com.cloudflare.manager.ui.workers

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextAlign

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.cloudflare.manager.domain.model.UiState
import com.cloudflare.manager.domain.model.WorkerDomain
import com.cloudflare.manager.domain.model.WorkerRoute
import com.cloudflare.manager.domain.model.Zone
import com.cloudflare.manager.ui.components.CfTopBar
import com.cloudflare.manager.ui.components.EmptyState
import com.cloudflare.manager.ui.components.ErrorMessage
import com.cloudflare.manager.ui.components.LoadingIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkerDetailScreen(
    scriptName: String,
    onBack: () -> Unit,
    viewModel: WorkerDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(scriptName) {
        viewModel.setScriptName(scriptName)
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
                title = scriptName,
                onBack = onBack,
                actions = {
                    if (state.isSavingScript) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 12.dp).width(24.dp).height(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = viewModel::saveScript) {
                            Icon(Icons.Default.Done, contentDescription = "保存脚本")
                        }
                    }
                }
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
                CodeSection(
                    content = state.scriptContent,
                    loading = state.scriptContentLoading,
                    error = state.scriptContentError,
                    onContentChange = viewModel::onScriptContentChange,
                    onRetry = viewModel::loadScriptContent
                )
            }

            item {
                SubdomainSection(
                    scriptName = scriptName,
                    accountSubdomain = state.accountSubdomain,
                    enabled = state.subdomainEnabled,
                    toggling = state.isTogglingSubdomain,
                    onToggle = viewModel::toggleSubdomain
                )
            }

            item {
                SecretsSection(
                    onAdd = viewModel::showAddSecretDialog,
                    onDelete = viewModel::showDeleteSecretDialog
                )
            }

            item {
                DomainsSection(
                    state = state.domainsState,
                    onAdd = viewModel::showAddDomainDialog,
                    onDelete = viewModel::deleteDomain,
                    onRetry = viewModel::loadDomains
                )
            }

            item {
                RoutesSection(
                    zonesState = state.zonesState,
                    routesState = state.routesState,
                    selectedZone = state.selectedZone,
                    onZoneSelected = viewModel::onZoneSelected,
                    onAdd = viewModel::showAddRouteDialog,
                    onDelete = { zone, route ->
                        viewModel.deleteRoute(zone, route.id)
                    },
                    onRetryZones = viewModel::loadZones,
                    onRetryRoutes = viewModel::loadRoutes
                )
            }
        }
    }

    if (state.showAddDomainDialog) {
        val zones = (state.zonesState as? UiState.Success)?.data ?: emptyList()
        AddDomainDialog(
            zones = zones,
            initialZone = state.selectedZone,
            onDismiss = viewModel::dismissAddDomainDialog,
            onConfirm = { zone, hostname ->
                viewModel.createDomain(zone, hostname)
            }
        )
    }

    if (state.showAddRouteDialog) {
        val zones = (state.zonesState as? UiState.Success)?.data ?: emptyList()
        AddRouteDialog(
            zones = zones,
            initialZone = state.selectedZone,
            onDismiss = viewModel::dismissAddRouteDialog,
            onConfirm = { zone, pattern ->
                viewModel.createRoute(zone, pattern)
            }
        )
    }

    if (state.showAddSecretDialog) {
        AddSecretDialog(
            onDismiss = viewModel::dismissAddSecretDialog,
            onConfirm = viewModel::addSecret
        )
    }

    if (state.showDeleteSecretDialog) {
        DeleteSecretDialog(
            onDismiss = viewModel::dismissDeleteSecretDialog,
            onConfirm = viewModel::deleteSecret
        )
    }
}

@Composable
private fun CodeSection(
    content: String,
    loading: Boolean,
    error: String?,
    onContentChange: (String) -> Unit,
    onRetry: () -> Unit
) {
    val bgColor = Color(0xFF1E1E1E)
    val lineNumberColor = Color(0xFF888888)
    val textColor = Color(0xFFE0E0E0)
    val cursorColor = Color(0xFF528BFF)
    val dividerColor = Color(0xFF333333)
    val fontSize = 14.sp
    val lineHeight = 20.sp
    val lineHeightDp = 20.dp

    SectionCard(title = "脚本代码") {
        when {
            loading -> LoadingIndicator()
            error != null -> ErrorMessage(message = error, onRetry = onRetry)
            else -> {
                val textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    color = textColor
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 400.dp)
                        .background(bgColor)
                ) {
                    CodeEditor(
                        content = content,
                        onContentChange = onContentChange,
                        textStyle = textStyle,
                        lineHeight = lineHeightDp,
                        lineNumberColor = lineNumberColor,
                        dividerColor = dividerColor,
                        cursorColor = cursorColor
                    )
                }
            }
        }
    }
}

@Composable
private fun CodeEditor(
    content: String,
    onContentChange: (String) -> Unit,
    textStyle: TextStyle,
    lineHeight: Dp,
    lineNumberColor: Color,
    dividerColor: Color,
    cursorColor: Color
) {
    val lines = remember(content) {
        if (content.isEmpty()) listOf("") else content.split('\n')
    }
    val lineCount = lines.size
    val digits = lineCount.toString().length.coerceAtLeast(2)
    val lineNumberWidth = (digits * 10 + 16).dp

    val listState = rememberLazyListState()
    val horizontalScroll = rememberScrollState()

    // Single LazyColumn with Row items - no duplicated virtualization
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        items(lineCount, key = { it }) { index ->
            Row(
                modifier = Modifier.height(lineHeight),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Line number - fixed, never scrolls horizontally
                Text(
                    text = (index + 1).toString(),
                    fontSize = textStyle.fontSize,
                    lineHeight = textStyle.lineHeight,
                    fontFamily = textStyle.fontFamily,
                    color = lineNumberColor,
                    modifier = Modifier
                        .width(lineNumberWidth)
                        .height(lineHeight)
                        .padding(horizontal = 8.dp),
                    textAlign = TextAlign.End
                )

                // Divider - fixed
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(dividerColor)
                )

                // Code area - horizontally scrollable as a whole
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(lineHeight)
                        .horizontalScroll(horizontalScroll)
                ) {
                    var lineText by remember(content) {
                        mutableStateOf(lines.getOrElse(index) { "" })
                    }
                    BasicTextField(
                        value = lineText,
                        onValueChange = { newValue ->
                            lineText = newValue
                            // TODO: sync back to parent
                        },
                        modifier = Modifier
                            .width(2000.dp)
                            .height(lineHeight)
                            .padding(horizontal = 8.dp),
                        textStyle = textStyle,
                        cursorBrush = SolidColor(cursorColor),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.None
                        ),
                        singleLine = true,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun SubdomainSection(
    scriptName: String,
    accountSubdomain: String?,
    enabled: Boolean,
    toggling: Boolean,
    onToggle: (Boolean) -> Unit
) {
    SectionCard(title = "workers.dev 子域名") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (!accountSubdomain.isNullOrBlank()) {
                        "$scriptName.$accountSubdomain.workers.dev"
                    } else {
                        "workers.dev 子域名"
                    },
                    style = MaterialTheme.typography.bodyLarge
                )
                if (!accountSubdomain.isNullOrBlank()) {
                    Text(
                        text = if (enabled) "已启用" else "已禁用",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (toggling) {
                CircularProgressIndicator(modifier = Modifier.width(24.dp).height(24.dp), strokeWidth = 2.dp)
            } else {
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    enabled = !accountSubdomain.isNullOrBlank()
                )
            }
        }
    }
}

@Composable
private fun DomainsSection(
    state: UiState<List<WorkerDomain>>,
    onAdd: () -> Unit,
    onDelete: (String) -> Unit,
    onRetry: () -> Unit
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
            is UiState.Error -> ErrorMessage(message = state.message, onRetry = onRetry)
            is UiState.Success -> {
                if (state.data.isEmpty()) {
                    EmptyState(message = "暂无自定义域名")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.data.forEach { domain ->
                            DomainItem(domain = domain, onDelete = { onDelete(domain.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DomainItem(
    domain: WorkerDomain,
    onDelete: () -> Unit
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
                    text = domain.hostname,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "${domain.zoneName ?: ""} ${domain.status ?: ""}".trim(),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutesSection(
    zonesState: UiState<List<Zone>>,
    routesState: UiState<List<WorkerRoute>>,
    selectedZone: Zone?,
    onZoneSelected: (Zone) -> Unit,
    onAdd: () -> Unit,
    onDelete: (Zone, WorkerRoute) -> Unit,
    onRetryZones: () -> Unit,
    onRetryRoutes: () -> Unit
) {
    SectionCard(
        title = "Zone Routes",
        action = {
            TextButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("添加")
            }
        }
    ) {
        when (zonesState) {
            is UiState.Loading -> LoadingIndicator()
            is UiState.Error -> ErrorMessage(message = zonesState.message, onRetry = onRetryZones)
            is UiState.Success -> {
                if (zonesState.data.isEmpty()) {
                    EmptyState(message = "没有可用的 Zone")
                } else {
                    ZoneSelector(
                        zones = zonesState.data,
                        selected = selectedZone,
                        onSelected = onZoneSelected
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    when (routesState) {
                        is UiState.Loading -> LoadingIndicator()
                        is UiState.Error -> ErrorMessage(
                            message = routesState.message,
                            onRetry = onRetryRoutes
                        )
                        is UiState.Success -> {
                            if (routesState.data.isEmpty()) {
                                EmptyState(message = "当前 Zone 暂无关联路由")
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    routesState.data.forEach { route ->
                                        RouteItem(
                                            route = route,
                                            onDelete = { onDelete(selectedZone ?: return@RouteItem, route) }
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZoneSelector(
    zones: List<Zone>,
    selected: Zone?,
    onSelected: (Zone) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selected?.name ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("选择 Zone") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            zones.forEach { zone ->
                DropdownMenuItem(
                    text = { Text(zone.name) },
                    onClick = {
                        onSelected(zone)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun RouteItem(
    route: WorkerRoute,
    onDelete: () -> Unit
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
            Text(
                text = route.pattern,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge
            )
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
private fun SecretsSection(
    onAdd: () -> Unit,
    onDelete: () -> Unit
) {
    SectionCard(title = "Secrets") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Secret 不支持列表查询，请通过名称添加或删除。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAdd, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("添加")
                }
                Button(onClick = onDelete, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("删除")
                }
            }
        }
    }
}

@Composable
private fun AddSecretDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加 Secret") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("值") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim(), value) },
                enabled = name.isNotBlank()
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
private fun DeleteSecretDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除 Secret") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Secret 名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank()
            ) {
                Text("删除")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddDomainDialog(
    zones: List<Zone>,
    initialZone: Zone?,
    onDismiss: () -> Unit,
    onConfirm: (Zone, String) -> Unit
) {
    var selected by remember { mutableStateOf(initialZone ?: zones.firstOrNull()) }
    var hostname by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加自定义域名") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (zones.isEmpty()) {
                    Text("没有可用的 Zone，请先确认 Token 拥有 Zone 读取权限")
                } else {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = selected?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("选择 Zone") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            zones.forEach { zone ->
                                DropdownMenuItem(
                                    text = { Text(zone.name) },
                                    onClick = {
                                        selected = zone
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = hostname,
                    onValueChange = { hostname = it },
                    label = { Text("域名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    selected?.let { onConfirm(it, hostname.trim()) }
                },
                enabled = selected != null && hostname.isNotBlank()
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRouteDialog(
    zones: List<Zone>,
    initialZone: Zone?,
    onDismiss: () -> Unit,
    onConfirm: (Zone, String) -> Unit
) {
    var selected by remember { mutableStateOf(initialZone ?: zones.firstOrNull()) }
    var pattern by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加 Zone Route") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (zones.isEmpty()) {
                    Text("没有可用的 Zone")
                } else {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = selected?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("选择 Zone") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            zones.forEach { zone ->
                                DropdownMenuItem(
                                    text = { Text(zone.name) },
                                    onClick = {
                                        selected = zone
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("匹配模式，如 example.com/*") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    selected?.let { onConfirm(it, pattern.trim()) }
                },
                enabled = selected != null && pattern.isNotBlank()
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
