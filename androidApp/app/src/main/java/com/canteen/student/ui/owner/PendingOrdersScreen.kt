package com.canteen.student.ui.owner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.canteen.student.data.model.Order
import com.canteen.student.data.model.OrderStatus
import com.canteen.student.data.model.OrderType
import com.canteen.student.ui.owner.components.OtpVerificationDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingOrdersScreen(
    viewModel: OwnerOrdersViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    val filteredOrders = uiState.orders.filter { order ->
        when (uiState.statusFilter) {
            null -> true
            else -> order.status == uiState.statusFilter
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Your orders",
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${uiState.orders.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.fetchOrders() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Orders"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Filter Chips Row
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = uiState.statusFilter == null,
                        onClick = { viewModel.setStatusFilter(null) },
                        label = { Text("All Active (${uiState.orders.size})") }
                    )
                }
                item {
                    val count = uiState.orders.count { it.status == OrderStatus.PENDING }
                    FilterChip(
                        selected = uiState.statusFilter == OrderStatus.PENDING,
                        onClick = { viewModel.setStatusFilter(OrderStatus.PENDING) },
                        label = { Text("Pending ($count)") }
                    )
                }
                item {
                    val count = uiState.orders.count { it.status == OrderStatus.PREPARING }
                    FilterChip(
                        selected = uiState.statusFilter == OrderStatus.PREPARING,
                        onClick = { viewModel.setStatusFilter(OrderStatus.PREPARING) },
                        label = { Text("Preparing ($count)") }
                    )
                }
                item {
                    val count = uiState.orders.count { it.status == OrderStatus.READY }
                    FilterChip(
                        selected = uiState.statusFilter == OrderStatus.READY,
                        onClick = { viewModel.setStatusFilter(OrderStatus.READY) },
                        label = { Text("Ready ($count)") }
                    )
                }
            }

            // Error Banner
            uiState.errorMessage?.let { error ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Loading state or Content list
            if (uiState.isLoading && uiState.orders.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (filteredOrders.isEmpty()) {
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
                            text = "No pending orders",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "New orders will appear here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(filteredOrders, key = { it.id }) { order ->
                        OrderCard(
                            order = order,
                            onStatusChange = { nextStatus ->
                                viewModel.updateOrderStatus(order.id, nextStatus)
                            },
                            onVerifyOtpClick = {
                                viewModel.openOtpDialog(order)
                            }
                        )
                    }
                }
            }
        }

        // OTP Dialog Modal
        uiState.selectedOrderForOtp?.let { selectedOrder ->
            OtpVerificationDialog(
                order = selectedOrder,
                otpInput = uiState.otpInput,
                isVerifying = uiState.otpVerifying,
                errorMessage = uiState.otpErrorMessage,
                successMessage = uiState.otpSuccessMessage,
                onOtpChange = { viewModel.setOtpInput(it) },
                onVerify = { viewModel.verifyOtp() },
                onDismiss = { viewModel.closeOtpDialog() }
            )
        }
    }
}

@Composable
fun OrderCard(
    order: Order,
    onStatusChange: (OrderStatus) -> Unit,
    onVerifyOtpClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: ID, Time, Pre-Order & Type Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Order ID: ${order.id}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Time: ${order.orderTime.substring(11)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Order Type Badge (Parcel vs Eat-In)
                Surface(
                    color = if (order.orderType == OrderType.PARCEL)
                        Color(0xFFFFECB3) else Color(0xFFE1BEE7),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (order.orderType == OrderType.PARCEL) "Parcel" else "Eat-in",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }

            // Pre-order Delivery Time Banner (if applicable)
            if (!order.preOrderTime.isNull_or_blank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .height(18.dp)
                            .width(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Pre-order Delivery Time: ${order.preOrderTime}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))

            // Student Info
            Text(
                text = "Student: ${order.studentName} (${order.studentPhone})",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text="Order Items: ",
                style=MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            // Itemized list
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                order.items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${item.quantity}x ${item.itemName}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (!item.notes.isNull_or_blank()) {
                                Text(
                                    text = "Note: ${item.notes}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        Text(
                            text = "₹${item.price * item.quantity}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))

            // Footer: Total & Status / Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Total Amount",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "₹${order.totalAmount}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Action Button based on status
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (order.status) {
                        OrderStatus.PENDING -> {
                            Button(
                                onClick = { onStatusChange(OrderStatus.PREPARING) },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .height(18.dp)
                                        .width(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Start Preparing")
                            }
                        }

                        OrderStatus.PREPARING -> {
                            Button(
                                onClick = { onStatusChange(OrderStatus.READY) },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .height(18.dp)
                                        .width(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Mark Ready")
                            }
                        }

                        OrderStatus.READY -> {
                            Button(
                                onClick = onVerifyOtpClick,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .height(18.dp)
                                        .width(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Verify OTP")
                            }
                        }

                        else -> {
                            Text(
                                text = order.status.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

// Extension helper
private fun String?.isNull_or_blank(): Boolean = this == null || this.trim().isEmpty()
