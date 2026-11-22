package com.canteen.student

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.room.Room
import com.canteen.student.data.CanteenDatabase
import com.canteen.student.data.OrderEntity
import com.canteen.student.ui.auth.AuthScreen
import com.canteen.student.ui.auth.UserRole
import com.canteen.student.ui.owner.CanteenOwnerMainScreen
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Demo-only local data. Replace with repository/backend data during integration.

data class Food(
    val id: Int,
    val name: String,
    val price: Int,
    val category: String,
    val available: Boolean = true
)

data class DemoOrder(val id: String, val summary: String, val total: Int, val mode: String, val service: String, val pickup: String, val status: String)

val menu = listOf(
    Food(1, "Veg Sandwich", 40, "Snacks"),
    Food(2, "Masala Dosa", 55, "Meals"),
    Food(3, "Samosa", 20, "Snacks"),
    Food(4, "Veg Fried Rice", 75, "Meals"),
    Food(5, "Tea", 15, "Beverages"),
    Food(6, "Cold Coffee", 45, "Beverages")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MainAppHost()
            }
        }
    }
}

@Composable
fun MainAppHost() {
    var currentUserRole by remember { mutableStateOf<UserRole?>(null) }

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
                onSwitchRoleOrLogout = {
                    currentUserRole = null
                }
            )
        }
        UserRole.OWNER -> {
            CanteenOwnerMainScreen(
                onSwitchRoleOrLogout = {
                    currentUserRole = null
                }
            )
        }
    }
}

@Composable
fun PreOrderDateTimePicker(
    selectedTime: String,
    onTimeSelected: (String) -> Unit
) {
    val context = LocalContext.current

    Button(
        onClick = {
            val now = Calendar.getInstance()

            DatePickerDialog(
                context,
                { _, year, month, day ->
                    TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            val selected = Calendar.getInstance().apply {
                                set(year, month, day, hour, minute, 0)
                                set(Calendar.MILLISECOND, 0)
                            }

                            if (selected.timeInMillis > System.currentTimeMillis()) {
                                val formatter = SimpleDateFormat(
                                    "dd MMM yyyy, hh:mm a",
                                    Locale.getDefault()
                                )
                                onTimeSelected(formatter.format(selected.time))
                            } else {
                                onTimeSelected("")
                                android.widget.Toast.makeText(
                                    context,
                                    "Please select a future date and time",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        now.get(Calendar.HOUR_OF_DAY),
                        now.get(Calendar.MINUTE),
                        false
                    ).show()
                },
                now.get(Calendar.YEAR),
                now.get(Calendar.MONTH),
                now.get(Calendar.DAY_OF_MONTH)
            ).apply {
                datePicker.minDate = System.currentTimeMillis() - 1000
            }.show()
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            if (selectedTime.isEmpty())
                "Select Pickup Date & Time"
            else
                selectedTime
        )
    }
}

@Composable
fun StudentApp(
    onSwitchRoleOrLogout: (() -> Unit)? = null
) {
    val context = LocalContext.current.applicationContext

    val database = remember {
        Room.databaseBuilder(
            context,
            CanteenDatabase::class.java,
            "canteen_database"
        ).build()
    }

    val orderDao = remember(database) {
        database.orderDao()
    }

    val orders by orderDao.getAllOrders().collectAsState(initial = emptyList())

    val scope = rememberCoroutineScope()

    val cart = remember { mutableStateMapOf<Int, Int>() }
    var page by remember { mutableStateOf("menu") }
    var selectedCategory by remember { mutableStateOf("All") }
    var orderMode by remember { mutableStateOf("Order Now") }
    var service by remember { mutableStateOf("Parcel") }
    var pickupTime by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val total = menu.sumOf { it.price * (cart[it.id] ?: 0) }

    Scaffold(
        topBar = {
            Surface(shadowElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Canteen Student • ${page.replaceFirstChar { it.uppercase() }}",
                        style = MaterialTheme.typography.titleLarge
                    )
                    if (onSwitchRoleOrLogout != null) {
                        IconButton(onClick = onSwitchRoleOrLogout) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Switch Role / Logout"
                            )
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
                        label = { Text(tab.replaceFirstChar { it.uppercase() }) }
                    )
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            when (page) {
                "menu" -> {
                    Text(
                        "Today's Menu",
                        style = MaterialTheme.typography.headlineSmall
                    )

                    Text(
                        "Sample menu • will sync with owner backend later",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Spacer(Modifier.height(12.dp))

                    val categories = listOf("All", "Snacks", "Meals", "Beverages")

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categories.forEach { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = { selectedCategory = category },
                                label = { Text(category) }
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    val filteredMenu = if (selectedCategory == "All") {
                        menu
                    } else {
                        menu.filter { it.category == selectedCategory }
                    }

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredMenu) { food ->
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
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                food.name,
                                                style = MaterialTheme.typography.titleMedium
                                            )

                                            Text(
                                                "₹${food.price}",
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                        }

                                        Text(
                                            text = if (food.available) "Available" else "Sold Out",
                                            color = if (food.available)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }

                                    if (food.available) {
                                        val quantity = cart[food.id] ?: 0

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "In Cart: $quantity",
                                                style = MaterialTheme.typography.bodyMedium
                                            )

                                            Button(
                                                onClick = {
                                                    cart[food.id] = quantity + 1
                                                }
                                            ) {
                                                Text("Add +")
                                            }
                                        }
                                    } else {
                                        Text(
                                            "Currently unavailable",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                "cart" -> {
                    Text(
                        "Your Cart",
                        style = MaterialTheme.typography.headlineSmall
                    )

                    Spacer(Modifier.height(12.dp))

                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (total == 0) {
                            Text(
                                text = "🛒",
                                style = MaterialTheme.typography.displayLarge,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )

                            Text(
                                "Your cart is empty!",
                                style = MaterialTheme.typography.titleLarge
                            )

                            Text(
                                "Add some delicious food from today's menu.",
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Spacer(Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    error = ""
                                    page = "menu"
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Browse Menu")
                            }
                        } else {
                            menu.filter { (cart[it.id] ?: 0) > 0 }.forEach { food ->
                                ElevatedCard {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(food.name)
                                            Text("₹${food.price} × ${cart[food.id]}")
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                val n = (cart[food.id] ?: 0) - 1
                                                if (n == 0) {
                                                    cart.remove(food.id)
                                                } else {
                                                    cart[food.id] = n
                                                }
                                            }
                                        ) {
                                            Text("−")
                                        }

                                        Spacer(Modifier.width(4.dp))

                                        OutlinedButton(
                                            onClick = {
                                                cart[food.id] = (cart[food.id] ?: 0) + 1
                                            }
                                        ) {
                                            Text("+")
                                        }
                                    }
                                }
                            }

                            Text(
                                "Total: ₹$total",
                                style = MaterialTheme.typography.titleLarge
                            )

                            Text("When do you want your food?")

                            Row {
                                RadioButton(
                                    selected = orderMode == "Order Now",
                                    onClick = { orderMode = "Order Now" }
                                )
                                Text("Order Now", Modifier.padding(top = 12.dp))

                                Spacer(Modifier.width(12.dp))

                                RadioButton(
                                    selected = orderMode == "Pre-Order",
                                    onClick = { orderMode = "Pre-Order" }
                                )
                                Text("Pre-Order", Modifier.padding(top = 12.dp))
                            }

                            if (orderMode == "Pre-Order") {
                                PreOrderDateTimePicker(
                                    selectedTime = pickupTime,
                                    onTimeSelected = { pickupTime = it }
                                )

                                Spacer(Modifier.height(8.dp))

                                ElevatedCard(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = "💳 Pre-Order Payment",
                                            style = MaterialTheme.typography.titleMedium
                                        )

                                        HorizontalDivider()

                                        Text(
                                            text = "Order Amount: ₹$total",
                                            style = MaterialTheme.typography.titleLarge
                                        )

                                        Text(
                                            text = "Payment Status",
                                            style = MaterialTheme.typography.titleSmall
                                        )

                                        Text(
                                            text = "Payment integration pending",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodyMedium
                                        )

                                        Text(
                                            text = "Advance payment is required before a Pre-Order can be placed.",
                                            style = MaterialTheme.typography.bodySmall
                                        )

                                        Button(
                                            onClick = {},
                                            enabled = false,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Pay Now - Coming Soon")
                                        }
                                    }
                                }
                            }

                            Text("Order type")

                            Row {
                                RadioButton(
                                    selected = service == "Parcel",
                                    onClick = { service = "Parcel" }
                                )
                                Text("Parcel", Modifier.padding(top = 12.dp))

                                Spacer(Modifier.width(12.dp))

                                RadioButton(
                                    selected = service == "Eat-in",
                                    onClick = { service = "Eat-in" }
                                )
                                Text("Eat-in", Modifier.padding(top = 12.dp))
                            }

                            if (error.isNotBlank()) {
                                Text(
                                    error,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            Button(
                                onClick = {
                                    if (total == 0) {
                                        error = "Add at least one item"
                                    } else {
                                        val id = "DEMO-${System.currentTimeMillis()}"

                                        val summary = menu
                                            .filter { (cart[it.id] ?: 0) > 0 }
                                            .joinToString {
                                                "${it.name} × ${cart[it.id]}"
                                            }

                                        scope.launch {
                                            orderDao.insertOrder(
                                                OrderEntity(
                                                    id = id,
                                                    summary = summary,
                                                    total = total,
                                                    mode = orderMode,
                                                    service = service,
                                                    pickup = "Immediate",
                                                    status = "DEMO • Not sent to canteen"
                                                )
                                            )
                                        }

                                        cart.clear()
                                        error = ""
                                        page = "orders"
                                    }
                                },
                                enabled = orderMode == "Order Now",
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (orderMode == "Pre-Order")
                                        "Payment integration required"
                                    else
                                        "Place Demo Order"
                                )
                            }
                        }
                    }
                }

                "orders" -> {
                    Text(
                        "My Orders",
                        style = MaterialTheme.typography.headlineSmall
                    )

                    Text(
                        "Your saved order history and tracking",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Spacer(Modifier.height(12.dp))

                    if (orders.isEmpty()) {
                        Text("No orders yet. Please order food from the menu.")
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(orders) { order ->
                                var showDeleteDialog by remember { mutableStateOf(false) }

                                ElevatedCard(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            "Order ID: ${order.id}",
                                            style = MaterialTheme.typography.titleMedium
                                        )

                                        HorizontalDivider()

                                        Text(
                                            "Food Items",
                                            style = MaterialTheme.typography.titleSmall
                                        )

                                        Text(order.summary)

                                        Text(
                                            "Total Amount: ₹${order.total}",
                                            style = MaterialTheme.typography.titleMedium
                                        )

                                        Text("Order Mode: ${order.mode}")
                                        Text("Order Type: ${order.service}")
                                        Text("Pickup Time: ${order.pickup}")

                                        HorizontalDivider()

                                        Text(
                                            "Order Status",
                                            style = MaterialTheme.typography.titleSmall
                                        )

                                        val statusColor = when (order.status) {
                                            "Pending" -> MaterialTheme.colorScheme.tertiary
                                            "Preparing" -> MaterialTheme.colorScheme.primary
                                            "Ready for Pickup" -> MaterialTheme.colorScheme.secondary
                                            "Completed" -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }

                                        Text(
                                            text = order.status,
                                            color = statusColor,
                                            style = MaterialTheme.typography.titleMedium
                                        )

                                        val trackingSteps = listOf(
                                            "Pending",
                                            "Preparing",
                                            "Ready for Pickup",
                                            "Completed"
                                        )

                                        val currentStep = trackingSteps.indexOf(order.status)

                                        if (currentStep >= 0) {
                                            Text(
                                                "Order Progress",
                                                style = MaterialTheme.typography.titleSmall
                                            )

                                            LinearProgressIndicator(
                                                progress = {
                                                    (currentStep + 1).toFloat() / trackingSteps.size
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            )

                                            trackingSteps.forEachIndexed { index, step ->
                                                Text(
                                                    text = if (index <= currentStep)
                                                        "✓ $step"
                                                    else
                                                        "○ $step",
                                                    color = if (index <= currentStep)
                                                        MaterialTheme.colorScheme.primary
                                                    else
                                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }

                                        HorizontalDivider()

                                        Text(
                                            "Pickup Verification",
                                            style = MaterialTheme.typography.titleSmall
                                        )

                                        ElevatedCard(
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
                                                    text = "🔒",
                                                    style = MaterialTheme.typography.headlineLarge
                                                )

                                                Text(
                                                    text = "OTP Not Available Yet",
                                                    style = MaterialTheme.typography.titleMedium
                                                )

                                                Text(
                                                    text = "Your pickup OTP will appear here when it is provided by the canteen system.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }

                                        Spacer(Modifier.height(8.dp))

                                        OutlinedButton(
                                            onClick = {
                                                showDeleteDialog = true
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                "Delete Order",
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }

                                        if (showDeleteDialog) {
                                            AlertDialog(
                                                onDismissRequest = {
                                                    showDeleteDialog = false
                                                },
                                                title = {
                                                    Text("Delete Order?")
                                                },
                                                text = {
                                                    Text(
                                                        "Are you sure you want to delete this order? This action cannot be undone."
                                                    )
                                                },
                                                confirmButton = {
                                                    TextButton(
                                                        onClick = {
                                                            showDeleteDialog = false

                                                            scope.launch {
                                                                orderDao.deleteOrderById(order.id)
                                                            }
                                                        }
                                                    ) {
                                                        Text(
                                                            "Delete",
                                                            color = MaterialTheme.colorScheme.error
                                                        )
                                                    }
                                                },
                                                dismissButton = {
                                                    TextButton(
                                                        onClick = {
                                                            showDeleteDialog = false
                                                        }
                                                    ) {
                                                        Text("Cancel")
                                                    }
                                                }
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
