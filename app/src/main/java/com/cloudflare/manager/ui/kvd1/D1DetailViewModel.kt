package com.cloudflare.manager.ui.kvd1

import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudflare.manager.data.repository.AccountRepository
import com.cloudflare.manager.data.repository.CloudflareRepository
import com.cloudflare.manager.domain.model.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import javax.inject.Inject

data class D1ColumnInfo(
    val name: String,
    val type: String,
    val isPrimaryKey: Boolean = false
)

data class D1TableInfo(
    val name: String,
    val columns: List<D1ColumnInfo>
)

data class D1DetailUiState(
    val databaseId: String = "",
    val databaseName: String = "",
    val selectedTab: Int = 0,
    // 表结构
    val tablesState: UiState<List<D1TableInfo>> = UiState.Loading,
    val selectedTable: String? = null,
    val tableStructure: D1TableInfo? = null,
    // SQL 查询
    val sqlQuery: String = "",
    val queryState: UiState<List<Map<String, JsonElement>>> = UiState.Success(emptyList()),
    val isExecuting: Boolean = false,
    // 数据浏览
    val tableDataState: UiState<List<Map<String, JsonElement>>> = UiState.Success(emptyList()),
    val isLoadingData: Boolean = false,
    // 行编辑
    val showEditDialog: Boolean = false,
    val editingRow: Map<String, JsonElement>? = null,
    val isNewRow: Boolean = false,
    // 消息
    val message: String? = null
)

@HiltViewModel
class D1DetailViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val cloudflareRepository: CloudflareRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(D1DetailUiState())
    val uiState: StateFlow<D1DetailUiState> = _uiState

    private var accountId: String? = null

    fun setDatabase(databaseId: String, databaseName: String) {
        if (_uiState.value.databaseId == databaseId) return
        _uiState.value = _uiState.value.copy(
            databaseId = databaseId,
            databaseName = databaseName
        )
        viewModelScope.launch {
            accountId = accountRepository.getCurrentAccount()?.accountId
            if (accountId.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(
                    tablesState = UiState.Error("当前账户缺少 Account ID")
                )
                return@launch
            }
            loadTables()
        }
    }

    fun onTabSelected(index: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = index)
    }

    // ========== 表结构 ==========

    fun loadTables() {
        val id = accountId ?: return
        val dbId = _uiState.value.databaseId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(tablesState = UiState.Loading)
            val result = cloudflareRepository.queryD1(
                id, dbId,
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name"
            )
            val tableNames = result.getOrDefault(emptyList()).map {
                it["name"]?.jsonPrimitive?.contentOrNull ?: ""
            }.filter { it.isNotBlank() }

            // 加载每个表的结构
            val tables = mutableListOf<D1TableInfo>()
            tableNames.forEach { tableName ->
                val structureResult = cloudflareRepository.queryD1(
                    id, dbId, "PRAGMA table_info($tableName)"
                )
                val columns = structureResult.getOrDefault(emptyList()).map { row ->
                    D1ColumnInfo(
                        name = row["name"]?.jsonPrimitive?.contentOrNull ?: "",
                        type = row["type"]?.jsonPrimitive?.contentOrNull ?: "",
                        isPrimaryKey = row["pk"]?.jsonPrimitive?.contentOrNull == "1"
                    )
                }.filter { it.name.isNotBlank() }
                tables.add(D1TableInfo(name = tableName, columns = columns))
            }

            _uiState.value = _uiState.value.copy(
                tablesState = result.fold(
                    onSuccess = { UiState.Success(tables) },
                    onFailure = { UiState.Error(it.message ?: "加载失败") }
                )
            )
        }
    }

    fun selectTable(tableName: String) {
        val tables = (_uiState.value.tablesState as? UiState.Success)?.data ?: emptyList()
        val table = tables.find { it.name == tableName }
        _uiState.value = _uiState.value.copy(
            selectedTable = tableName,
            tableStructure = table
        )
        loadTableData(tableName)
    }

    // ========== SQL 查询 ==========

    fun onSqlChange(sql: String) {
        _uiState.value = _uiState.value.copy(sqlQuery = sql)
    }

    fun executeQuery() {
        val id = accountId ?: return
        val dbId = _uiState.value.databaseId
        val sql = _uiState.value.sqlQuery.trim()
        if (sql.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isExecuting = true,
                queryState = UiState.Loading
            )
            val result = cloudflareRepository.queryD1(id, dbId, sql)
            _uiState.value = _uiState.value.copy(
                isExecuting = false,
                queryState = result.fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it.message ?: "查询失败") }
                ),
                message = if (result.isSuccess) "查询成功" else (result.exceptionOrNull()?.message ?: "查询失败")
            )
        }
    }

    // ========== 数据浏览 ==========

    fun loadTableData(tableName: String) {
        val id = accountId ?: return
        val dbId = _uiState.value.databaseId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoadingData = true,
                tableDataState = UiState.Loading
            )
            val result = cloudflareRepository.queryD1(
                id, dbId, "SELECT * FROM $tableName LIMIT 100"
            )
            _uiState.value = _uiState.value.copy(
                isLoadingData = false,
                tableDataState = result.fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it.message ?: "加载失败") }
                )
            )
        }
    }

    fun showAddRowDialog() {
        _uiState.value = _uiState.value.copy(
            showEditDialog = true,
            editingRow = null,
            isNewRow = true
        )
    }

    fun showEditRowDialog(row: Map<String, JsonElement>) {
        _uiState.value = _uiState.value.copy(
            showEditDialog = true,
            editingRow = row,
            isNewRow = false
        )
    }

    fun dismissEditDialog() {
        _uiState.value = _uiState.value.copy(showEditDialog = false)
    }

    fun saveRow(values: Map<String, String>) {
        val id = accountId ?: return
        val dbId = _uiState.value.databaseId
        val tableName = _uiState.value.selectedTable ?: return
        val table = _uiState.value.tableStructure ?: return
        val isNew = _uiState.value.isNewRow

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(showEditDialog = false)

            val result = if (isNew) {
                // INSERT
                val columns = values.keys.joinToString(", ") { "\"$it\"" }
                val vals = values.values.joinToString(", ") { "'$it'" }
                cloudflareRepository.queryD1(id, dbId, "INSERT INTO $tableName ($columns) VALUES ($vals)")
            } else {
                // UPDATE
                val oldRow = _uiState.value.editingRow ?: return@launch
                val pkColumn = table.columns.find { it.isPrimaryKey }
                val setClause = values.entries.joinToString(", ") { (k, v) ->
                    if (v.isBlank()) "\"$k\" = NULL" else "\"$k\" = '$v'"
                }
                if (pkColumn != null) {
                    val pkValue = oldRow[pkColumn.name]?.jsonPrimitive?.contentOrNull
                        ?: oldRow[pkColumn.name]?.toString()
                    cloudflareRepository.queryD1(
                        id, dbId,
                        "UPDATE $tableName SET $setClause WHERE \"${pkColumn.name}\" = '$pkValue'"
                    )
                } else {
                    // 没有主键，用所有旧值作为 WHERE 条件
                    val whereClause = oldRow.entries.joinToString(" AND ") { (k, v) ->
                        val valStr = v.jsonPrimitive?.contentOrNull ?: v.toString()
                        "\"$k\" = '$valStr'"
                    }
                    cloudflareRepository.queryD1(
                        id, dbId,
                        "UPDATE $tableName SET $setClause WHERE $whereClause"
                    )
                }
            }

            _uiState.value = _uiState.value.copy(
                message = if (result.isSuccess) "保存成功" else (result.exceptionOrNull()?.message ?: "保存失败")
            )
            loadTableData(tableName)
        }
    }

    fun deleteRow(row: Map<String, JsonElement>) {
        val id = accountId ?: return
        val dbId = _uiState.value.databaseId
        val tableName = _uiState.value.selectedTable ?: return
        val table = _uiState.value.tableStructure ?: return

        viewModelScope.launch {
            val pkColumn = table.columns.find { it.isPrimaryKey }
            val result = if (pkColumn != null) {
                val pkValue = row[pkColumn.name]?.jsonPrimitive?.contentOrNull
                    ?: row[pkColumn.name]?.toString()
                cloudflareRepository.queryD1(
                    id, dbId,
                    "DELETE FROM $tableName WHERE \"${pkColumn.name}\" = '$pkValue'"
                )
            } else {
                val whereClause = row.entries.joinToString(" AND ") { (k, v) ->
                    val valStr = v.jsonPrimitive?.contentOrNull ?: v.toString()
                    "\"$k\" = '$valStr'"
                }
                cloudflareRepository.queryD1(
                    id, dbId,
                    "DELETE FROM $tableName WHERE $whereClause"
                )
            }

            _uiState.value = _uiState.value.copy(
                message = if (result.isSuccess) "删除成功" else (result.exceptionOrNull()?.message ?: "删除失败")
            )
            loadTableData(tableName)
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
