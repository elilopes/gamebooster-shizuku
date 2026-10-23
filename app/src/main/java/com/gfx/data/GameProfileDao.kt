package com.gfx.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface GameProfileDao {
    @Query("SELECT * FROM game_profiles")
    suspend fun getAllProfiles(): List<GameProfile>

    @Query("SELECT * FROM game_profiles WHERE packageName = :packageName")
    suspend fun getProfile(packageName: String): GameProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: GameProfile)
}
