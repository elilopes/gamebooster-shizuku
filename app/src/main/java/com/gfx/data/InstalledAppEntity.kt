package com.gfx.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "installed_apps")
data class InstalledAppEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val isGame: Boolean,
    val engine: String,
    val lastUpdated: Long = System.currentTimeMillis()
)
