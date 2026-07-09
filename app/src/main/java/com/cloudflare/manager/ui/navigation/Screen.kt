package com.cloudflare.manager.ui.navigation

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Web
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector? = null
) {
    // Bottom nav
    data object Dashboard : Screen("dashboard", "仪表盘", Icons.Default.Dashboard)
    data object Accounts : Screen("accounts", "账户", Icons.Default.People)
    data object Zones : Screen("zones", "域名", Icons.Default.Domain)
    data object Dns : Screen("dns/{zoneId}/{zoneName}", "DNS", Icons.Default.Dns) {
        fun createRoute(zoneId: String, zoneName: String): String {
            return "dns/$zoneId/${zoneName.encodeForRoute()}"
        }
    }
    data object More : Screen("more", "更多", Icons.Default.MoreHoriz)

    // More menu
    data object Workers : Screen("workers", "Workers", Icons.Default.Web)
    data object WorkerDetail : Screen("worker_detail/{scriptName}", "Worker 详情") {
        fun createRoute(scriptName: String): String {
            return "worker_detail/${scriptName.encodeForRoute()}"
        }
    }
    data object Pages : Screen("pages", "Pages", Icons.Default.Web)
    data object PagesDetail : Screen("pages_detail/{projectName}", "Pages 详情") {
        fun createRoute(projectName: String): String {
            return "pages_detail/${projectName.encodeForRoute()}"
        }
    }
    data object R2 : Screen("r2", "R2", Icons.Default.Storage)
    data object KvD1 : Screen("kv_d1", "KV / D1", Icons.Default.Storage)
    data object KvDetail : Screen("kv_detail/{namespaceId}/{namespaceTitle}", "KV 详情") {
        fun createRoute(namespaceId: String, namespaceTitle: String): String {
            return "kv_detail/$namespaceId/${namespaceTitle.encodeForRoute()}"
        }
    }
    data object D1Detail : Screen("d1_detail/{databaseId}/{databaseName}", "D1 详情") {
        fun createRoute(databaseId: String, databaseName: String): String {
            return "d1_detail/$databaseId/${databaseName.encodeForRoute()}"
        }
    }
    data object Tunnels : Screen("tunnels", "Tunnels", Icons.Default.Web)
    data object Firewall : Screen("firewall", "Firewall", Icons.Default.Security)
    data object Cache : Screen("cache", "缓存", Icons.Default.Settings)
    data object Analytics : Screen("analytics", "分析", Icons.Default.Analytics)
    data object Settings : Screen("settings", "设置", Icons.Default.Settings)

    // Help / about
    data object Help : Screen("help", "使用说明")

    // Detail / form
    data object AddAccount : Screen("add_account", "添加账户")
    data object DnsEdit : Screen("dns_edit/{zoneId}/{recordId}", "DNS 记录") {
        fun createRoute(zoneId: String, recordId: String? = null): String {
            return "dns_edit/$zoneId/${recordId ?: "new"}"
        }
    }

    data object ZoneDetail : Screen("zone_detail/{zoneId}/{zoneName}", "域名详情") {
        fun createRoute(zoneId: String, zoneName: String): String {
            return "zone_detail/$zoneId/${zoneName.encodeForRoute()}"
        }
    }

    companion object {
        val bottomNavItems = listOf(Dashboard, Accounts, Zones, More)
        val moreMenuItems = listOf(
            Workers, Pages, R2, KvD1, Tunnels, Firewall, Cache, Analytics
        )
    }
}

private fun String.encodeForRoute(): String {
    return Uri.encode(this)
}
