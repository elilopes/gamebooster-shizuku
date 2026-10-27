package com.gfx.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * [PT-BR] Entidade Room para persistência permanente de jogos salvos manualmente pelo usuário.
 * Mantém os jogos na lista da aba Games mesmo após reinício do aplicativo sem necessidade de varredura.
 *
 * [EN] Room entity for permanent persistence of games manually added by the user.
 * Keeps games displayed in the Games tab across app restarts without requiring full system scan.
 */
@Entity(tableName = "saved_games")
data class SavedGameEntity(
    @PrimaryKey val packageName: String,
    val name: String,
    val addedTimestamp: Long = System.currentTimeMillis()
)
