package com.kerneldroid.karchiver.data.trash

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TrashEntry::class],
    version = 1,
    exportSchema = false
)
abstract class TrashDatabase : RoomDatabase() {

    abstract fun trashDao(): TrashDao

    companion object {
        private const val DATABASE_NAME = "karchiver-trash.db"

        @Volatile
        private var instance: TrashDatabase? = null

        fun get(context: Context): TrashDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TrashDatabase::class.java,
                    DATABASE_NAME
                )
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .build()
                    .also { instance = it }
            }
    }
}
