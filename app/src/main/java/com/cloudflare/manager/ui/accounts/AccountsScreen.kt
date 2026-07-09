package com.cloudflare.manager.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cloudflare.manager.R
import com.cloudflare.manager.domain.model.Account
import com.cloudflare.manager.ui.components.AccountCard
import com.cloudflare.manager.ui.components.CfTopBar
import com.cloudflare.manager.ui.components.EmptyState

@Composable
fun AccountsScreen(
    snackbarHostState: SnackbarHostState,
    onAddAccount: () -> Unit,
    viewModel: AccountsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var accountToDelete by remember { mutableStateOf<Account?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        CfTopBar(title = stringResource(R.string.accounts))

        Box(modifier = Modifier.fillMaxSize()) {
            if (state.accounts.isEmpty()) {
                EmptyState(message = "暂无账户，点击右下角添加")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.accounts, key = { it.id }) { account ->
                        AccountCard(
                            account = account,
                            isSelected = account.id == state.currentAccount?.id,
                            onClick = { viewModel.selectAccount(account) },
                            onDelete = { accountToDelete = account },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            FloatingActionButton(
                onClick = onAddAccount,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_account))
            }
        }
    }

    if (accountToDelete != null) {
        AlertDialog(
            onDismissRequest = { accountToDelete = null },
            title = { Text(stringResource(R.string.confirm_delete)) },
            text = { Text("确定要删除账户 ${accountToDelete!!.name} 吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAccount(accountToDelete!!)
                        accountToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { accountToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
