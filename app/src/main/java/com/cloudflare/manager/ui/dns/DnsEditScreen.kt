package com.cloudflare.manager.ui.dns

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cloudflare.manager.R
import com.cloudflare.manager.ui.components.CfTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsEditScreen(
    zoneId: String,
    recordId: String?,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    viewModel: DnsEditViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val recordTypes = listOf("A", "AAAA", "CNAME", "MX", "TXT", "SRV", "NS", "CAA")
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    LaunchedEffect(state.success) {
        if (state.success) {
            snackbarHostState.showSnackbar("保存成功")
            onBack()
        }
    }

    Scaffold(
        topBar = {
            CfTopBar(
                title = if (recordId == null) stringResource(R.string.add_dns_record) else stringResource(R.string.edit_dns_record),
                onBack = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = state.type,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.record_type)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    recordTypes.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type) },
                            onClick = {
                                viewModel.onTypeChange(type)
                                expanded = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text(stringResource(R.string.record_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = state.content,
                onValueChange = viewModel::onContentChange,
                label = { Text(stringResource(R.string.record_content)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = state.ttl,
                onValueChange = viewModel::onTtlChange,
                label = { Text(stringResource(R.string.ttl)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                )
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.proxied),
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = state.proxied,
                    onCheckedChange = viewModel::onProxiedChange
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}
