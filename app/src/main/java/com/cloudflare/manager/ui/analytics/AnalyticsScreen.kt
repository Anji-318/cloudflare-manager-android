package com.cloudflare.manager.ui.analytics

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cloudflare.manager.domain.model.UiState
import com.cloudflare.manager.domain.model.Zone
import com.cloudflare.manager.ui.components.CfTopBar
import com.cloudflare.manager.ui.components.EmptyState
import com.cloudflare.manager.ui.components.ErrorMessage
import com.cloudflare.manager.ui.components.LoadingIndicator
import com.cloudflare.manager.ui.theme.CfOrange
import com.cloudflare.manager.ui.theme.SuccessGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onBack: () -> Unit,
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { CfTopBar(title = "分析报表", onBack = onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { ZoneSelector(state, viewModel) }
            item { RangeSelector(state, viewModel) }

            when (val analyticsState = state.analyticsState) {
                is UiState.Loading -> item { LoadingIndicator() }
                is UiState.Error -> item {
                    ErrorMessage(
                        message = analyticsState.message,
                        onRetry = viewModel::loadAnalytics
                    )
                }
                is UiState.Success -> {
                    val (summary, countries) = analyticsState.data
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard("总请求", summary.totalRequests.toString(), CfOrange, Modifier.weight(1f))
                            StatCard("威胁请求", summary.threatRequests.toString(), CfOrange, Modifier.weight(1f))
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard("缓存命中率", "${summary.cacheRate}%", SuccessGreen, Modifier.weight(1f))
                            StatCard("缓存请求", summary.cachedRequests.toString(), SuccessGreen, Modifier.weight(1f))
                        }
                    }
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "请求来源地区",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                if (countries.isEmpty()) {
                                    Text(
                                        text = "暂无地区数据",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    val max = countries.maxOfOrNull { it.value } ?: 1
                                    countries.take(5).forEach { country ->
                                        CountryBar(country, max)
                                        Spacer(modifier = Modifier.height(8.dp))
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
    state: AnalyticsUiState,
    viewModel: AnalyticsViewModel
) {
    val zones = (state.zonesState as? UiState.Success<List<Zone>>)?.data ?: emptyList()
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = state.selectedZone?.name ?: "选择域名",
            onValueChange = {},
            readOnly = true,
            label = { Text("域名") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            zones.forEach { zone ->
                DropdownMenuItem(
                    text = { Text(zone.name) },
                    onClick = {
                        viewModel.selectZone(zone)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun RangeSelector(
    state: AnalyticsUiState,
    viewModel: AnalyticsViewModel
) {
    val ranges = listOf(
        86400L to "最近 24 小时",
        604800L to "最近 7 天",
        2592000L to "最近 30 天"
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ranges.forEach { (seconds, label) ->
            val selected = state.rangeSeconds == seconds
            Card(
                onClick = { viewModel.setRange(seconds) },
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .padding(12.dp)
                        .align(Alignment.CenterHorizontally)
                )
            }
        }
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

@Composable
private fun CountryBar(country: com.cloudflare.manager.domain.model.CountryStat, max: Long) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(country.name, style = MaterialTheme.typography.bodyMedium)
            Text(country.value.toString(), style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(modifier = Modifier.height(4.dp))
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width * (if (max > 0) country.value.toFloat() / max else 0f)
                drawRoundRect(
                    color = CfOrange,
                    size = androidx.compose.ui.geometry.Size(width, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                )
            }
        }
    }
}
