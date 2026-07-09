package com.cloudflare.manager.ui.help

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cloudflare.manager.ui.components.CfTopBar

private const val TOKEN_URL = "https://dash.cloudflare.com/profile/api-tokens"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = { CfTopBar(title = "使用说明", onBack = onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { QuickStartSection() }
            item { TokenSection(onOpenLink = { uriHandler.openUri(TOKEN_URL) }) }
            item { PermissionSection() }
            item { ScopeSection() }
            item { TroubleshootingSection() }
        }
    }
}

@Composable
private fun QuickStartSection() {
    HelpCard(title = "快速开始") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StepText("1", "按下方步骤创建 Cloudflare API Token。")
            StepText("2", "打开应用，点击底部导航“账户”→“添加账户”。")
            StepText("3", "填写账户名称、邮箱（可选），粘贴 API Token。")
            StepText("4", "点击“验证并保存”，应用会自动获取 Account ID 并加载域名列表。")
        }
    }
}

@Composable
private fun TokenSection(onOpenLink: () -> Unit) {
    HelpCard(title = "API Token 获取") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "登录 Cloudflare Dashboard 后进入 API Token 页面，选择“Create Token”→“Custom token”。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onOpenLink) {
                Icon(
                    imageVector = Icons.Default.OpenInBrowser,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                Text("打开 Cloudflare Token 页面")
            }
        }
    }
}

@Composable
private fun PermissionSection() {
    HelpCard(title = "推荐权限配置") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "根据要管理的资源，在 Permissions 区域添加以下权限：",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PermissionTable(
                rows = listOf(
                    Triple("帐户", "D1", "编辑"),
                    Triple("帐户", "Cloudflare Pages", "编辑"),
                    Triple("帐户", "Workers R2 存储", "编辑"),
                    Triple("帐户", "Workers KV 存储", "编辑"),
                    Triple("帐户", "Workers 脚本", "编辑"),
                    Triple("帐户", "帐户", "读取"),
                    Triple("帐户", "帐户设置", "读取"),
                    Triple("区域", "区域设置", "编辑"),
                    Triple("区域", "区域", "读取"),
                    Triple("区域", "防火墙服务", "编辑"),
                    Triple("区域", "Workers 路由", "编辑"),
                    Triple("区域", "DNS", "编辑"),
                    Triple("区域", "Analytics", "读取"),
                    Triple("帐户", "Cloudflare Tunnel", "编辑")
                )
            )
            InfoCard {
                Text(
                    text = "精简版：若只需域名 + DNS + 缓存 + 防火墙 + 分析，最少需要：区域 - 区域（读取）、区域设置（编辑）、DNS（编辑）、防火墙服务（编辑）、Analytics（读取）；帐户 - 帐户（读取）、帐户设置（读取）。",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            InfoCard {
                Text(
                    text = "扩展版：如需 Workers 自定义域名 / Zone Routes 或 Pages 自定义域名，请额外勾选：帐户 - 帐户（读取）、Workers 脚本（编辑）、Cloudflare Pages（编辑）；区域 - Workers 路由（编辑）、区域（读取）。",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun ScopeSection() {
    HelpCard(title = "设置资源范围") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Account Resources：选择你的 Cloudflare 账户。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Zone Resources：",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            StepText("•", "Include > All zones —— 管理该账户下所有域名")
            StepText("•", "Include > Specific zone —— 只管理指定域名")
        }
    }
}

@Composable
private fun TroubleshootingSection() {
    HelpCard(title = "常见问题") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FaqItem(
                question = "Token 验证失败？",
                answer = "请确认 Token 已启用，权限至少包含“帐户 - 帐户（读取）”、“帐户 - 帐户设置（读取）”和“区域 - 区域（读取）”，且资源范围包含你的账户/域名。"
            )
            FaqItem(
                question = "某些页面没有数据？",
                answer = "说明 Token 缺少对应权限。例如 Workers 页面需要“帐户 - Workers 脚本（编辑）”，R2 页面需要“帐户 - Workers R2 存储（编辑）”。"
            )
            FaqItem(
                question = "应用打不开或白屏？",
                answer = "请检查网络连接，并确认本机时间与标准时间一致。如仍有问题，可尝试清除应用数据后重新添加账户。"
            )
        }
    }
}

@Composable
private fun HelpCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun StepText(number: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = number,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PermissionTable(rows: List<Triple<String, String, String>>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                TableHeaderCell("范围", Modifier.weight(1.2f))
                TableHeaderCell("资源", Modifier.weight(2f))
                TableHeaderCell("权限", Modifier.weight(1f))
            }
            rows.forEach { (scope, resource, permission) ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    TableCell(scope, Modifier.weight(1.2f))
                    TableCell(resource, Modifier.weight(2f))
                    TableCell(permission, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TableHeaderCell(text: String, modifier: Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun TableCell(text: String, modifier: Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(vertical = 6.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            content()
        }
    }
}

@Composable
private fun FaqItem(question: String, answer: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = question,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = answer,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
