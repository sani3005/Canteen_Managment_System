package com.canteen.student

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.canteen.student.data.api.SessionManager
import com.canteen.student.ui.auth.AuthScreen
import com.canteen.student.ui.auth.UserRole
import com.canteen.student.ui.owner.CanteenOwnerMainScreen
import com.canteen.student.ui.student.StudentViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SessionManager.init(applicationContext)
        setContent {
            MaterialTheme {
                MainAppHost()
            }
        }
    }
}

@Composable
fun MainAppHost() {
    var currentUserRole by remember {
        mutableStateOf(if (SessionManager.isLoggedIn) SessionManager.currentRole else null)
    }

    when (currentUserRole) {
        null -> {
            AuthScreen(
                onLoginSuccess = { role ->
                    currentUserRole = role
                }
            )
        }
        UserRole.STUDENT -> {
            StudentApp(
                onLogout = {
                    SessionManager.clearSession()
                    currentUserRole = null
                }
            )
        }
        UserRole.OWNER -> {
            CanteenOwnerMainScreen(
                onLogout = {
                    SessionManager.clearSession()
                    currentUserRole = null
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentApp(
    onLogout: (() -> Unit)? = null,
    viewModel: StudentViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var page by remember { mutableStateOf("menu") }
    var service by remember { mutableStateOf("Parcel") }

    val filteredMenu = uiState.menuItems.filter { item ->
        val matchesCategory = uiState.selectedCategory == "All" || item.category.equals(uiState.selectedCategory, ignoreCase = true)
        val matchesSearch = uiState.searchQuery.isBlank() ||
                item.name.contains(uiState.searchQuery, ignoreCase = true) ||
                item.description.contains(uiState.searchQuery, ignoreCase = true)
        matchesCategory && matchesSearch
    }

    val cartItemDetails = uiState.cart.mapNotNull { (itemId, qty) ->
        val item = uiState.menuItems.find { it.itemId == itemId }
        if (item != null) item to qty else null
    }

    val cartTotal = cartItemDetails.sumOf { (item, qty) -> item.price * qty }

    Scaffold(
        topBar = {
            Surface(shadowElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Campus Canteen",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            viewModel.fetchMenu()
                            viewModel.fetchOrders()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Sync Backend Data"
                            )
                        }
                        if (onLogout != null) {
                            IconButton(onClick = onLogout) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                    contentDescription = "Logout",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar {
                listOf("menu", "cart", "orders").forEach { tab ->
                    NavigationBarItem(
                        selected = page == tab,
                        onClick = { page = tab },
                        icon = { Text(when (tab) { "menu" -> "🍽"; "cart" -> "🛒"; else -> "📋" }) },
                        label = {
                            Text(
                                if (tab == "cart" && uiState.cart.isNotEmpty())
                                    "Cart (${uiState.cart.values.sum()})"
                                else
                                    tab.replaceFirstChar { it.uppercase() }
                            )
                        }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Global Error Banner
            uiState.errorMessage?.let { error ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.dismissError() }) {
                            Text("Dismiss")
                        }
                    }
                }
            }

            when (page) {
                "menu" -> {
                    Text(
                        "Today's Live Menu",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        "Synced directly with canteen backend",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(12.dp))

                    // Search Field
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Search dishes...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(Modifier.height(10.dp))

                    // Category Filters
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uiState.categories.forEach { category ->
                            FilterChip(
                                selected = uiState.selectedCategory == category,
                                onClick = { viewModel.setSelectedCategory(category) },
                                label = { Text(category) }
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    if (uiState.isLoadingMenu && uiState.menuItems.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else if (filteredMenu.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.ShoppingCart,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .height(64.dp)
                                        .width(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "No menu items available",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filteredMenu, key = { it.itemId }) { item ->
                                ElevatedCard(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        item.name,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(
                                                            text = item.category,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                                        )
                                                    }
                                                }

                                                if (item.description.isNotBlank()) {
                                                    Spacer(Modifier.height(4.dp))
                                                    Text(
                                                        item.description,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }

                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    "₹${item.price}",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            Text(
                                                text = if (item.isAvailable) "Available" else "Sold Out",
                                                color = if (item.isAvailable)
                                                    MaterialTheme.colorScheme.primary
                                                else
                                                    MaterialTheme.colorScheme.error,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        if (item.isAvailable) {
                                            val quantity = uiState.cart[item.itemId] ?: 0

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    "In Cart: $quantity",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )

                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    if (quantity > 0) {
                                                        OutlinedButton(
                                                            onClick = { viewModel.removeFromCart(item.itemId) }
                                                        ) {
                                                            Text("−")
                                                        }
                                                    }
                                                    Button(
                                                        onClick = { viewModel.addToCart(item.itemId) }
                                                    ) {
                                                        Text(if (quantity > 0) "+ Add More" else "Add +")
                                                    }
                                                }
                                            }
                                        } else {
                                            Text(
                                                "Currently unavailable from canteen counter",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                "cart" -> {
                    Text(
                        "Your Cart",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(12.dp))

                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Order Success Banner
                        uiState.orderSuccessMessage?.let { successMsg ->
                            ElevatedCard(
                                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "🎉 Order Placed!",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = successMsg,
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Button(
                                        onClick = {
                                            viewModel.dismissSuccessMessage()
                                            page = "orders"
                                        }
                                    ) {
                                        Text("View Order Progress & OTP")
                                    }
                                }
                            }
                        }

                        if (cartItemDetails.isEmpty() && uiState.orderSuccessMessage == null) {
                            Text(
                                text = "🛒",
                                style = MaterialTheme.typography.displayLarge,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )

                            Text(
                                "Your cart is empty!",
                                style = MaterialTheme.typography.titleLarge,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text(
                                "Add delicious dishes from today's menu to place your order.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(Modifier.height(12.dp))

                            Button(
                                onClick = { page = "menu" },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Browse Menu")
                            }
                        } else if (cartItemDetails.isNotEmpty()) {
                            cartItemDetails.forEach { (item, qty) ->
                                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                item.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                "₹${item.price} × $qty = ₹${item.price * qty}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            OutlinedButton(
                                                onClick = { viewModel.removeFromCart(item.itemId) }
                                            ) {
                                                Text("−")
                                            }

                                            Spacer(Modifier.width(6.dp))

                                            OutlinedButton(
                                                onClick = { viewModel.addToCart(item.itemId) }
                                            ) {
                                                Text("+")
                                            }
                                        }
                                    }
                                }
                            }

                            HorizontalDivider()

                            Text(
                                "Total Amount: ₹$cartTotal",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(Modifier.height(4.dp))

                            Text(
                                "Order Packaging Option",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = service == "Parcel",
                                        onClick = { service = "Parcel" }
                                    )
                                    Text("📦 Takeaway Parcel")
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = service == "Eat-in",
                                        onClick = { service = "Eat-in" }
                                    )
                                    Text("🍽️ Eat in Canteen")
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            Button(
                                onClick = { viewModel.placeOrder(service) },
                                enabled = !uiState.isPlacingOrder,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                if (uiState.isPlacingOrder) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Text(
                                        "Place Order (₹$cartTotal)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                "orders" -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "My Orders",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Live order status & pickup verification OTP",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(onClick = { viewModel.fetchOrders() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh Orders")
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    if (uiState.isLoadingOrders && uiState.orders.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else if (uiState.orders.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.ShoppingCart,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "No order history found",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Place your first order from today's menu!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(uiState.orders, key = { it.orderId }) { order ->
                                ElevatedCard(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "Order #${order.orderId}",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Surface(
                                                color = if (order.orderType == "parcel")
                                                    MaterialTheme.colorScheme.secondaryContainer
                                                else
                                                    MaterialTheme.colorScheme.tertiaryContainer,
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = if (order.orderType == "parcel") "📦 Parcel" else "🍽️ Dine-In",
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        Text(
                                            "Placed at: ${order.createdAt}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        HorizontalDivider()

                                        Text(
                                            "Total Amount: ₹${order.totalAmount}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )

                                        HorizontalDivider()

                                        Text(
                                            "Order Status",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )

                                        val statusFormatted = order.status.replaceFirstChar { it.uppercase() }
                                        val statusColor = when (order.status.lowercase()) {
                                            "pending" -> MaterialTheme.colorScheme.tertiary
                                            "preparing" -> MaterialTheme.colorScheme.primary
                                            "ready" -> MaterialTheme.colorScheme.secondary
                                            "completed" -> MaterialTheme.colorScheme.primary
                                            "cancelled" -> MaterialTheme.colorScheme.error
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }

                                        Text(
                                            text = statusFormatted,
                                            color = statusColor,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )

                                        val trackingSteps = listOf("pending", "preparing", "ready", "completed")
                                        val currentStep = trackingSteps.indexOf(order.status.lowercase())

                                        if (currentStep >= 0) {
                                            LinearProgressIndicator(
                                                progress = { (currentStep + 1).toFloat() / trackingSteps.size },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }

                                        HorizontalDivider()

                                        Text(
                                            "Pickup Verification OTP",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )

                                        if (!order.pickupOtp.isNullOrEmpty()) {
                                            ElevatedCard(
                                                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(16.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = "🔑 Your Pickup OTP",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                    Text(
                                                        text = order.pickupOtp,
                                                        style = MaterialTheme.typography.headlineLarge,
                                                        fontWeight = FontWeight.Bold,
                                                        letterSpacing = 4.sp,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    Text(
                                                        text = "Show this 6-digit OTP code at the canteen counter to verify & collect your food.",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        textAlign = TextAlign.Center,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                }
                                            }
                                        } else {
                                            ElevatedCard(
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(12.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text(
                                                        text = "🔒 OTP Not Available Yet",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    Text(
                                                        text = if (order.status.lowercase() == "completed")
                                                            "Order has been picked up & completed."
                                                        else if (order.status.lowercase() == "cancelled")
                                                            "Order was cancelled."
                                                        else
                                                            "Your 6-digit pickup OTP will appear here as soon as the canteen marks your order as 'Ready'.",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        textAlign = TextAlign.Center,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
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
        }
    }
}
