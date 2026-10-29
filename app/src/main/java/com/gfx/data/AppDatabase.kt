package com.gfx.data

import android.content.Context
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [GameProfile::class, SavedGameEntity::class, InstalledAppEntity::class], version = 3)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gameProfileDao(): GameProfileDao
    abstract fun savedGameDao(): SavedGameDao
    abstract fun installedAppDao(): InstalledAppDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "boost-db"
                ).fallbackToDestructiveMigration(dropAllTables = true).build().also { INSTANCE = it }
            }
        }
    }
}

