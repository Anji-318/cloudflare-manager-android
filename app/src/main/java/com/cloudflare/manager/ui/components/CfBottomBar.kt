package com.cloudflare.manager.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.cloudflare.manager.ui.navigation.Screen

@Composable
fun CfBottomBar(
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit
) {
    val items = Screen.bottomNavItems

    NavigationBar {
        items.forEachIndexed { index, screen ->
            val selected = index == selectedIndex
            NavigationBarItem(
                icon = { screen.icon?.let { Icon(imageVector = it, contentDescription = screen.title) } },
                label = { Text(screen.title) },
                selected = selected,
                onClick = { onItemSelected(index) }
            )
        }
    }
}
