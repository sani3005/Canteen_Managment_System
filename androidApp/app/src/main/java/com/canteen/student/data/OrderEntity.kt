package com.canteen.student.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey val id: String,
    val summary: String,
    val total: Int,
    val mode: String,
    val service: String,
    val pickup: String,
    val status: String
)