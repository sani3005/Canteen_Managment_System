package com.canteen.student.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [OrderEntity::class],
    version = 1,
    exportSchema = false
)
abstract class CanteenDatabase : RoomDatabase() {
    abstract fun orderDao(): OrderDao
}