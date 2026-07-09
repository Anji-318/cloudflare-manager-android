package com.cloudflare.manager.ui.accounts

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cloudflare.manager.R
import com.cloudflare.manager.ui.components.CfTopBar

private const val TOKEN_CREATE_URL = "https://dash.cloudflare.com/profile/api-tokens/create"
private val TOKEN_REGEX = Regex("^[A-Za-z0-9_-]{30,}$")

@Composable
fun AddAccountScreen(
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    viewModel: AddAccountViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var tokenVisible by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(state.success) {
        if (state.success) {
            snackbarHostState.showSnackbar("账户保存成功")
            onBack()
        }
    }

    DisposableEffect(lifecycleOwner, state.token) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = clipboard.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val text = clip.getItemAt(0).text?.toString() ?: ""
                    if (TOKEN_REGEX.matches(text)) {
                        if (state.token.isBlank()) {
                            viewModel.onTokenChange(text)
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = { CfTopBar(title = stringResource(R.string.add_account), onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text(stringResource(R.string.account_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                label = { Text(stringResource(R.string.email_remark)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = state.token,
                onValueChange = viewModel::onTokenChange,
                label = { Text(stringResource(R.string.api_token)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                maxLines = 4,
                visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                trailingIcon = {
                    IconButton(onClick = { tokenVisible = !tokenVisible }) {
                        Icon(
                            imageVector = if (tokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (tokenVisible) "Hide token" else "Show token"
                        )
                    }
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { uriHandler.openUri(TOKEN_CREATE_URL) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.generate_token))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.token_encrypted_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = viewModel::saveAccount,
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
