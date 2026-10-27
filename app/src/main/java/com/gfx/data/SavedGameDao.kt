package com.gfx.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * [PT-BR] DAO para operações com a tabela de jogos salvos (inserção manual, consulta e remoção).
 * [EN] DAO for operations with the saved games table (manual insertion, query, and removal).
 */
@Dao
interface SavedGameDao {
    @Query("SELECT * FROM saved_games ORDER BY addedTimestamp ASC")
    suspend fun getAllSavedGames(): List<SavedGameEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedGame(game: SavedGameEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(games: List<SavedGameEntity>)

    @Query("DELETE FROM saved_games WHERE packageName = :packageName")
    suspend fun deleteSavedGame(packageName: String)

    @Query("SELECT EXISTS(SELECT 1 FROM saved_games WHERE packageName = :packageName)")
    suspend fun isGameSaved(packageName: String): Boolean
}
