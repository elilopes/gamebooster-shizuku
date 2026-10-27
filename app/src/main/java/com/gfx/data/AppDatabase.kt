package com.gfx.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [GameProfile::class, SavedGameEntity::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gameProfileDao(): GameProfileDao
    abstract fun savedGameDao(): SavedGameDao
}

