package com.photosentinel.health.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.photosentinel.health.ui.screens.AIChatScreen
import com.photosentinel.health.ui.screens.ExecutionScreen
import com.photosentinel.health.ui.screens.HealthTipsScreen
import com.photosentinel.health.ui.screens.HomeScreen
import com.photosentinel.health.ui.screens.ProfileScreen
import com.photosentinel.health.ui.theme.AccentCyan
import com.photosentinel.health.ui.theme.BgCard
import com.photosentinel.health.ui.theme.BgPrimary
import com.photosentinel.health.ui.theme.TextTertiary

sealed class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    data object Home : BottomNavItem("home", "采集", Icons.Default.Home)
    data object AIChat : BottomNavItem("ai_chat", "AI问答", Icons.AutoMirrored.Filled.Chat)
    data object Execution : BottomNavItem("execution", "执行台", Icons.Default.PlayArrow)
    data object Tips : BottomNavItem("tips", "建议", Icons.Default.FavoriteBorder)
    data object Profile : BottomNavItem("profile", "我的", Icons.Default.Person)
}

@Composable
fun MainApp() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        BottomNavItem.Home,
        BottomNavItem.AIChat,
        BottomNavItem.Execution,
        BottomNavItem.Tips,
        BottomNavItem.Profile
    )

    Scaffold(
        containerColor = BgPrimary,
        bottomBar = {
            NavigationBar(
                containerColor = BgCard,
                tonalElevation = 0.dp
            ) {
                tabs.forEachIndexed { index, item ->
                    NavigationBarItem(
                        icon = { androidx.compose.material3.Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AccentCyan,
                            selectedTextColor = AccentCyan,
                            unselectedIconColor = TextTertiary,
                            unselectedTextColor = TextTertiary,
                            indicatorColor = Color.Transparent
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        val modifier = Modifier.padding(innerPadding)
        when (selectedTab) {
            0 -> HomeScreen(modifier)
            1 -> AIChatScreen(modifier)
            2 -> ExecutionScreen(modifier)
            3 -> HealthTipsScreen(modifier)
            4 -> ProfileScreen(modifier)
        }
    }
}
