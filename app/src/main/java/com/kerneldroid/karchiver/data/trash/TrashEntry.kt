package com.kerneldroid.karchiver.data.trash

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "trash_entries",
    indices = [Index("deletedAt")]
)
data class TrashEntry(
    @PrimaryKey val id: String,
    val originalPath: String,
    val storedPath: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val deletedAt: Long
)
