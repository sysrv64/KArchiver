package com.kerneldroid.karchiver.data.trash

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrashDao {

    @Query("SELECT * FROM trash_entries ORDER BY deletedAt DESC")
    fun observeAll(): Flow<List<TrashEntry>>

    @Query("SELECT * FROM trash_entries ORDER BY deletedAt DESC")
    suspend fun allOnce(): List<TrashEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: TrashEntry)

    @Query("DELETE FROM trash_entries WHERE id = :id")
    suspend fun remove(id: String)

    @Query("SELECT COUNT(*) FROM trash_entries")
    suspend fun count(): Int
}
