package com.cloudflare.manager.ui.kvd1

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cloudflare.manager.domain.model.UiState
import com.cloudflare.manager.ui.components.CfTopBar
import com.cloudflare.manager.ui.components.EmptyState
import com.cloudflare.manager.ui.components.ErrorMessage
import com.cloudflare.manager.ui.components.LoadingIndicator
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun D1DetailScreen(
    databaseId: String,
    databaseName: String,
    onBack: () -> Unit,
    viewModel: D1DetailViewModel = hiltViewModel()
) {
    LaunchedEffect(databaseId) {
        viewModel.setDatabase(databaseId, databaseName)
    }

    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val tabs = listOf("表结构", "SQL 查询", "数据浏览")

    Scaffold(
        topBar = {
            CfTopBar(
                title = "D1: $databaseName",
                onBack = onBack
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = state.selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = state.selectedTab == index,
                        onClick = { viewModel.onTabSelected(index) },
                        text = { Text(title) }
                    )
                }
            }

            when (state.selectedTab) {
                0 -> StructureTab(state = state, onSelectTable = viewModel::selectTable)
                1 -> SqlQueryTab(state = state, onSqlChange = viewModel::onSqlChange, onExecute = viewModel::executeQuery)
                2 -> DataBrowseTab(
                    state = state,
                    onSelectTable = viewModel::selectTable,
                    onRefresh = { state.selectedTable?.let(viewModel::loadTableData) },
                    onAddRow = viewModel::showAddRowDialog,
                    onEditRow = viewModel::showEditRowDialog,
                    onDeleteRow = viewModel::deleteRow
                )
            }
        }
    }

    if (state.showEditDialog) {
        val table = state.tableStructure
        if (table != null) {
            EditRowDialog(
                columns = table.columns,
                row = state.editingRow,
                isNewRow = state.isNewRow,
                onDismiss = viewModel::dismissEditDialog,
                onConfirm = viewModel::saveRow
            )
        }
    }
}

// ========== 表结构标签 ==========

@Composable
private fun StructureTab(
    state: D1DetailUiState,
    onSelectTable: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (val tablesState = state.tablesState) {
            is UiState.Loading -> item { LoadingIndicator() }
            is UiState.Error -> item {
                ErrorMessage(message = tablesState.message, onRetry = null)
            }
            is UiState.Success -> {
                if (tablesState.data.isEmpty()) {
                    item { EmptyState(message = "暂无数据表") }
                } else {
                    tablesState.data.forEach { table ->
                        item {
                            TableStructureCard(
                                table = table,
                                isSelected = state.selectedTable == table.name,
                                onClick = { onSelectTable(table.name) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TableStructureCard(
    table: D1TableInfo,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = table.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            table.columns.forEach { column ->
                val pkMarker = if (column.isPrimaryKey) " (PK)" else ""
                Text(
                    text = "${column.name}: ${column.type}$pkMarker",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

// ========== SQL 查询标签 ==========

@Composable
private fun SqlQueryTab(
    state: D1DetailUiState,
    onSqlChange: (String) -> Unit,
    onExecute: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionCard(title = "SQL 查询") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.sqlQuery,
                        onValueChange = onSqlChange,
                        label = { Text("输入 SQL 语句") },
                        placeholder = { Text("SELECT * FROM table LIMIT 10") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        maxLines = 5
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (state.isExecuting) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .width(24.dp)
                                    .height(24.dp)
                            )
                        } else {
                            IconButton(onClick = onExecute) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "执行",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            SectionCard(title = "查询结果") {
                when (val queryState = state.queryState) {
                    is UiState.Loading -> LoadingIndicator()
                    is UiState.Error -> ErrorMessage(
                        message = queryState.message,
                        onRetry = null
                    )
                    is UiState.Success -> {
                        if (queryState.data.isEmpty()) {
                            EmptyState(message = "执行 SQL 查询以查看结果")
                        } else {
                            ResultTable(rows = queryState.data)
                        }
                    }
                }
            }
        }
    }
}

// ========== 数据浏览标签 ==========

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DataBrowseTab(
    state: D1DetailUiState,
    onSelectTable: (String) -> Unit,
    onRefresh: () -> Unit,
    onAddRow: () -> Unit,
    onEditRow: (Map<String, JsonElement>) -> Unit,
    onDeleteRow: (Map<String, JsonElement>) -> Unit
) {
    val tables = (state.tablesState as? UiState.Success)?.data ?: emptyList()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 表选择下拉框
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = state.selectedTable ?: "选择表",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("表") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        tables.forEach { table ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(table.name) },
                                onClick = {
                                    onSelectTable(table.name)
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                IconButton(onClick = onAddRow, enabled = state.selectedTable != null) {
                    Icon(Icons.Default.Add, contentDescription = "添加行")
                }
                IconButton(onClick = onRefresh, enabled = state.selectedTable != null) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
                }
            }
        }

        item {
            when (val dataState = state.tableDataState) {
                is UiState.Loading -> LoadingIndicator()
                is UiState.Error -> ErrorMessage(
                    message = dataState.message,
                    onRetry = null
                )
                is UiState.Success -> {
                    if (dataState.data.isEmpty()) {
                        EmptyState(message = if (state.selectedTable == null) "请选择表" else "表中没有数据")
                    } else {
                        val columns = state.tableStructure?.columns?.map { it.name }
                            ?: dataState.data.firstOrNull()?.keys?.toList()
                            ?: emptyList()
                        DataTable(
                            columns = columns,
                            rows = dataState.data,
                            onEdit = onEditRow,
                            onDelete = onDeleteRow
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DataTable(
    columns: List<String>,
    rows: List<Map<String, JsonElement>>,
    onEdit: (Map<String, JsonElement>) -> Unit,
    onDelete: (Map<String, JsonElement>) -> Unit
) {
    if (columns.isEmpty()) return

    val horizontalScroll = rememberScrollState()
    val columnWidth = 100.dp
    val actionWidth = 80.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(horizontalScroll)
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(vertical = 8.dp, horizontal = 4.dp)
            ) {
                columns.forEach { column ->
                    Text(
                        text = column,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.width(columnWidth).padding(horizontal = 2.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "操作",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.width(actionWidth).padding(horizontal = 2.dp)
                )
            }

            // Data rows
            rows.take(100).forEach { row ->
                Row(
                    modifier = Modifier
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    columns.forEach { column ->
                        val value = row[column]
                        Text(
                            text = value?.jsonPrimitive?.contentOrNull ?: value?.toString() ?: "null",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.width(columnWidth).padding(horizontal = 2.dp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(modifier = Modifier.width(actionWidth)) {
                        IconButton(
                            onClick = { onEdit(row) },
                            modifier = Modifier.width(32.dp).height(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "编辑",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(18.dp).height(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { onDelete(row) },
                            modifier = Modifier.width(32.dp).height(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.width(18.dp).height(18.dp)
                            )
                        }
                    }
                }
            }

            if (rows.size > 100) {
                Text(
                    text = "... 仅显示前 100 行",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

// ========== 编辑行对话框 ==========

@Composable
private fun EditRowDialog(
    columns: List<D1ColumnInfo>,
    row: Map<String, JsonElement>?,
    isNewRow: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Map<String, String>) -> Unit
) {
    val values = remember(columns, row) {
        mutableStateMapOf<String, String>().apply {
            columns.forEach { col ->
                val existingValue = row?.get(col.name)?.jsonPrimitive?.contentOrNull ?: ""
                this[col.name] = existingValue
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNewRow) "添加行" else "编辑行") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 400.dp)
            ) {
                columns.forEach { column ->
                    OutlinedTextField(
                        value = values[column.name] ?: "",
                        onValueChange = { values[column.name] = it },
                        label = {
                            Text(
                                "${column.name}${if (column.isPrimaryKey) " (PK)" else ""}"
                            )
                        },
                        readOnly = !isNewRow && column.isPrimaryKey,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val result = columns.associate { it.name to (values[it.name] ?: "") }
                    onConfirm(result)
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

// ========== 通用组件 ==========

@Composable
private fun ResultTable(rows: List<Map<String, JsonElement>>) {
    val columns = rows.firstOrNull()?.keys?.toList() ?: emptyList()
    if (columns.isEmpty()) return

    val horizontalScroll = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(horizontalScroll)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(vertical = 8.dp, horizontal = 4.dp)
            ) {
                columns.forEach { column ->
                    Text(
                        text = column,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.width(120.dp).padding(horizontal = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            rows.take(100).forEach { row ->
                Row(
                    modifier = Modifier
                        .padding(vertical = 4.dp, horizontal = 4.dp)
                ) {
                    columns.forEach { column ->
                        val value = row[column]
                        Text(
                            text = value?.jsonPrimitive?.contentOrNull ?: value?.toString() ?: "null",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.width(120.dp).padding(horizontal = 4.dp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (rows.size > 100) {
                Text(
                    text = "... 仅显示前 100 行",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
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
