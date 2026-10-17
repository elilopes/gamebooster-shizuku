package com.example.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [GameProfile::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gameProfileDao(): GameProfileDao
}
