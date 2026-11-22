package com.canteen.student.ui.owner

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

sealed class OwnerScreen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Orders : OwnerScreen(
        route = "owner_orders",
        title = "Orders",
        selectedIcon = Icons.Filled.List,
        unselectedIcon = Icons.Filled.List
    )

    object Menu : OwnerScreen(
        route = "owner_menu",
        title = "Menu",
        selectedIcon = Icons.Filled.Menu,
        unselectedIcon = Icons.Filled.Menu
    )

    object Analytics : OwnerScreen(
        route = "owner_analytics",
        title = "Analytics",
        selectedIcon = Icons.Filled.Info,
        unselectedIcon = Icons.Filled.Info
    )

    object Profile : OwnerScreen(
        route = "owner_profile",
        title = "Profile",
        selectedIcon = Icons.Filled.AccountCircle,
        unselectedIcon = Icons.Filled.AccountCircle
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanteenOwnerMainScreen(
    onLogout: (() -> Unit)? = null,
    navController: NavHostController = rememberNavController()
) {
    val items = listOf(
        OwnerScreen.Orders,
        OwnerScreen.Menu,
        OwnerScreen.Analytics,
        OwnerScreen.Profile
    )

    val ordersViewModel: OwnerOrdersViewModel = viewModel()
    val menuViewModel: MenuManagementViewModel = viewModel()
    val analyticsViewModel: AnalyticsViewModel = viewModel()
    val profileViewModel: CanteenProfileViewModel = viewModel()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Canteen Owner Portal", fontWeight = FontWeight.Bold) },
                actions = {
                    if (onLogout != null) {
                        IconButton(onClick = onLogout) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "Logout",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                items.forEach { screen ->
                    val isSelected = currentRoute == screen.route
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                                contentDescription = screen.title
                            )
                        },
                        label = { Text(screen.title) },
                        selected = isSelected,
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = OwnerScreen.Orders.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(OwnerScreen.Orders.route) {
                PendingOrdersScreen(viewModel = ordersViewModel)
            }
            composable(OwnerScreen.Menu.route) {
                MenuManagementScreen(viewModel = menuViewModel)
            }
            composable(OwnerScreen.Analytics.route) {
                AnalyticsScreen(viewModel = analyticsViewModel)
            }
            composable(OwnerScreen.Profile.route) {
                CanteenProfileScreen(viewModel = profileViewModel)
            }
        }
    }
}
