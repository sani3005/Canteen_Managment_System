package com.canteen.student.ui.owner

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.canteen.student.data.model.PeakDay
import com.canteen.student.data.model.RushHour
import com.canteen.student.data.model.TopSellingItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val summary = uiState.summary

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analytics & Insights", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.fetchAnalytics() }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh Analytics")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Time Filter Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(TimeFilter.values()) { filter ->
                    FilterChip(
                        selected = uiState.selectedTimeFilter == filter,
                        onClick = { viewModel.setTimeFilter(filter) },
                        label = {
                            Text(
                                when (filter) {
                                    TimeFilter.TODAY -> "Today"
                                    TimeFilter.THIS_WEEK -> "This Week"
                                    TimeFilter.THIS_MONTH -> "This Month"
                                    TimeFilter.ALL_TIME -> "All Time"
                                }
                            )
                        }
                    )
                }
            }

            if (uiState.isLoading && summary.totalOrders == 0) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Compact Summary Metric Cards Grid
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MetricCard(
                                    title = "Total Revenue",
                                    value = "₹${"%.2f".format(summary.totalRevenue)}",
                                    icon = Icons.Default.Info,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricCard(
                                    title = "Total Orders",
                                    value = "${summary.totalOrders}",
                                    icon = Icons.Default.ShoppingCart,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MetricCard(
                                    title = "Parcel Orders",
                                    value = "${summary.parcelCount}",
                                    icon = Icons.Default.Send,
                                    color = Color(0xFFFF9800),
                                    modifier = Modifier.weight(1f)
                                )
                                MetricCard(
                                    title = "Eat-in Orders",
                                    value = "${summary.eatInCount}",
                                    icon = Icons.Default.Home,
                                    color = Color(0xFF9C27B0),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // 2. Peak Rush Hours Line Graph (9:00 AM to 5:00 PM)
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Peak Rush Hours (9:00 AM - 5:00 PM)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                PeakRushHoursLineChart(
                                    rushHours = summary.rushHours,
                                    lineColor = MaterialTheme.colorScheme.primary,
                                    axisTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // 3. Peak Days Line Graph (Days of Week)
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Peak Days of the Week",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                PeakDaysLineChart(
                                    peakDays = summary.peakDays,
                                    lineColor = MaterialTheme.colorScheme.secondary,
                                    axisTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // 4. Top Dishes Per Day of the Week Section
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.List,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Top 5 Dishes Per Day of Week",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Day Selector Chips
                                val daysOfWeek = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
                                val dayAbbrs = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(daysOfWeek.size) { index ->
                                        val fullDayName = daysOfWeek[index]
                                        val abbrName = dayAbbrs[index]
                                        val isSelected = uiState.selectedDayOfWeek == fullDayName

                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { viewModel.setSelectedDayOfWeek(fullDayName) },
                                            label = { Text(abbrName, fontSize = 12.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                val topDishesForSelectedDay = summary.topDishesByDay[uiState.selectedDayOfWeek]
                                    ?: emptyList()

                                if (topDishesForSelectedDay.isEmpty()) {
                                    Text(
                                        text = "No sales data for ${uiState.selectedDayOfWeek}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 12.dp)
                                    )
                                } else {
                                    topDishesForSelectedDay.take(5).forEachIndexed { index, topItem ->
                                        TopItemRow(topItem = topItem, rank = index + 1)
                                        if (index < topDishesForSelectedDay.take(5).size - 1) {
                                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
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

/**
 * Compact, smaller metric card layout for total revenue, total orders, parcel, and eat-in orders.
 */
@Composable
fun MetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Custom line chart for Peak Rush Hours (9:00 AM to 5:00 PM).
 */
@Composable
fun PeakRushHoursLineChart(
    rushHours: List<RushHour>,
    lineColor: Color,
    axisTextColor: Color
) {
    val textMeasurer = rememberTextMeasurer()
    val dotCenterColor = MaterialTheme.colorScheme.surface

    // 9 AM to 5 PM hours mapping
    val hoursRange = (9..17).toList()
    val timeLabels = listOf("9 AM", "10 AM", "11 AM", "12 PM", "1 PM", "2 PM", "3 PM", "4 PM", "5 PM")

    val dataPoints = hoursRange.map { hr ->
        rushHours.find { it.hour == hr }?.completedOrders ?: 0
    }

    val maxOrders = (dataPoints.maxOrNull() ?: 1).coerceAtLeast(1)

    val labelStyle = TextStyle(
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = axisTextColor
    )
    val valueStyle = TextStyle(
        fontSize = 9.sp,
        fontWeight = FontWeight.SemiBold,
        color = lineColor
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .padding(vertical = 4.dp)
    ) {
        val width = size.width
        val height = size.height

        val topPadding = 24.dp.toPx()
        val bottomPadding = 28.dp.toPx()
        val leftPadding = 20.dp.toPx()
        val rightPadding = 20.dp.toPx()

        val chartWidth = width - leftPadding - rightPadding
        val chartHeight = height - topPadding - bottomPadding

        val stepX = chartWidth / (dataPoints.size - 1).coerceAtLeast(1)

        val coordinates = dataPoints.mapIndexed { i, valCount ->
            val x = leftPadding + i * stepX
            val ratio = valCount.toFloat() / maxOrders
            val y = (topPadding + chartHeight) - (ratio * chartHeight)
            Offset(x, y)
        }

        // Horizontal Grid lines (0%, 50%, 100%)
        val gridLineColor = axisTextColor.copy(alpha = 0.12f)
        for (g in 0..2) {
            val yGrid = topPadding + (chartHeight / 2) * g
            drawLine(
                color = gridLineColor,
                start = Offset(leftPadding, yGrid),
                end = Offset(width - rightPadding, yGrid),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
            )
        }

        // Gradient Fill area under line
        val fillPath = Path().apply {
            if (coordinates.isNotEmpty()) {
                moveTo(coordinates.first().x, topPadding + chartHeight)
                lineTo(coordinates.first().x, coordinates.first().y)
                for (i in 1 until coordinates.size) {
                    val prev = coordinates[i - 1]
                    val curr = coordinates[i]
                    val control1 = Offset((prev.x + curr.x) / 2, prev.y)
                    val control2 = Offset((prev.x + curr.x) / 2, curr.y)
                    cubicTo(control1.x, control1.y, control2.x, control2.y, curr.x, curr.y)
                }
                lineTo(coordinates.last().x, topPadding + chartHeight)
                close()
            }
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.35f), lineColor.copy(alpha = 0.0f)),
                startY = topPadding,
                endY = topPadding + chartHeight
            )
        )

        // Smooth Line Stroke
        val strokePath = Path().apply {
            if (coordinates.isNotEmpty()) {
                moveTo(coordinates.first().x, coordinates.first().y)
                for (i in 1 until coordinates.size) {
                    val prev = coordinates[i - 1]
                    val curr = coordinates[i]
                    val control1 = Offset((prev.x + curr.x) / 2, prev.y)
                    val control2 = Offset((prev.x + curr.x) / 2, curr.y)
                    cubicTo(control1.x, control1.y, control2.x, control2.y, curr.x, curr.y)
                }
            }
        }

        drawPath(
            path = strokePath,
            color = lineColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Draw data dots, value labels, and X-axis labels
        coordinates.forEachIndexed { i, pos ->
            // Outer circle
            drawCircle(
                color = lineColor,
                radius = 4.5.dp.toPx(),
                center = pos
            )
            // Inner dot
            drawCircle(
                color = dotCenterColor,
                radius = 2.dp.toPx(),
                center = pos
            )

            // Value text above dot
            val valStr = "${dataPoints[i]}"
            val valResult = textMeasurer.measure(valStr, style = valueStyle)
            drawText(
                textLayoutResult = valResult,
                topLeft = Offset(pos.x - (valResult.size.width / 2), pos.y - valResult.size.height - 3.dp.toPx())
            )

            // X-axis label below graph
            val labelStr = timeLabels[i]
            val labelResult = textMeasurer.measure(labelStr, style = labelStyle)
            drawText(
                textLayoutResult = labelResult,
                topLeft = Offset(pos.x - (labelResult.size.width / 2), topPadding + chartHeight + 6.dp.toPx())
            )
        }
    }
}

/**
 * Custom line chart for Peak Days of the Week (Mon to Sun).
 */
@Composable
fun PeakDaysLineChart(
    peakDays: List<PeakDay>,
    lineColor: Color,
    axisTextColor: Color
) {
    val textMeasurer = rememberTextMeasurer()
    val dotCenterColor = MaterialTheme.colorScheme.surface

    val dayAbbrs = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val fullDays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    val dataPoints = dayAbbrs.mapIndexed { i, abbr ->
        peakDays.find { it.day.equals(abbr, ignoreCase = true) || it.dayFull.equals(fullDays[i], ignoreCase = true) }?.completedOrders ?: 0
    }

    val maxOrders = (dataPoints.maxOrNull() ?: 1).coerceAtLeast(1)

    val labelStyle = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = axisTextColor
    )
    val valueStyle = TextStyle(
        fontSize = 9.sp,
        fontWeight = FontWeight.SemiBold,
        color = lineColor
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .padding(vertical = 4.dp)
    ) {
        val width = size.width
        val height = size.height

        val topPadding = 24.dp.toPx()
        val bottomPadding = 28.dp.toPx()
        val leftPadding = 24.dp.toPx()
        val rightPadding = 24.dp.toPx()

        val chartWidth = width - leftPadding - rightPadding
        val chartHeight = height - topPadding - bottomPadding

        val stepX = chartWidth / (dataPoints.size - 1).coerceAtLeast(1)

        val coordinates = dataPoints.mapIndexed { i, valCount ->
            val x = leftPadding + i * stepX
            val ratio = valCount.toFloat() / maxOrders
            val y = (topPadding + chartHeight) - (ratio * chartHeight)
            Offset(x, y)
        }

        // Horizontal Grid lines
        val gridLineColor = axisTextColor.copy(alpha = 0.12f)
        for (g in 0..2) {
            val yGrid = topPadding + (chartHeight / 2) * g
            drawLine(
                color = gridLineColor,
                start = Offset(leftPadding, yGrid),
                end = Offset(width - rightPadding, yGrid),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
            )
        }

        // Fill area under line
        val fillPath = Path().apply {
            if (coordinates.isNotEmpty()) {
                moveTo(coordinates.first().x, topPadding + chartHeight)
                lineTo(coordinates.first().x, coordinates.first().y)
                for (i in 1 until coordinates.size) {
                    val prev = coordinates[i - 1]
                    val curr = coordinates[i]
                    val control1 = Offset((prev.x + curr.x) / 2, prev.y)
                    val control2 = Offset((prev.x + curr.x) / 2, curr.y)
                    cubicTo(control1.x, control1.y, control2.x, control2.y, curr.x, curr.y)
                }
                lineTo(coordinates.last().x, topPadding + chartHeight)
                close()
            }
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.35f), lineColor.copy(alpha = 0.0f)),
                startY = topPadding,
                endY = topPadding + chartHeight
            )
        )

        // Line Stroke
        val strokePath = Path().apply {
            if (coordinates.isNotEmpty()) {
                moveTo(coordinates.first().x, coordinates.first().y)
                for (i in 1 until coordinates.size) {
                    val prev = coordinates[i - 1]
                    val curr = coordinates[i]
                    val control1 = Offset((prev.x + curr.x) / 2, prev.y)
                    val control2 = Offset((prev.x + curr.x) / 2, curr.y)
                    cubicTo(control1.x, control1.y, control2.x, control2.y, curr.x, curr.y)
                }
            }
        }

        drawPath(
            path = strokePath,
            color = lineColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Draw dots, value text, and X-axis labels
        coordinates.forEachIndexed { i, pos ->
            drawCircle(
                color = lineColor,
                radius = 4.5.dp.toPx(),
                center = pos
            )
            drawCircle(
                color = dotCenterColor,
                radius = 2.dp.toPx(),
                center = pos
            )

            // Value label above dot
            val valStr = "${dataPoints[i]}"
            val valResult = textMeasurer.measure(valStr, style = valueStyle)
            drawText(
                textLayoutResult = valResult,
                topLeft = Offset(pos.x - (valResult.size.width / 2), pos.y - valResult.size.height - 3.dp.toPx())
            )

            // X-axis label below graph
            val labelStr = dayAbbrs[i]
            val labelResult = textMeasurer.measure(labelStr, style = labelStyle)
            drawText(
                textLayoutResult = labelResult,
                topLeft = Offset(pos.x - (labelResult.size.width / 2), topPadding + chartHeight + 6.dp.toPx())
            )
        }
    }
}

@Composable
fun TopItemRow(
    topItem: TopSellingItem,
    rank: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "#$rank",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = topItem.itemName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${topItem.quantitySold} units sold",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = "₹${"%.2f".format(topItem.totalRevenue)}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
