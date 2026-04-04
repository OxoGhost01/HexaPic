package com.oxoghost.hexapic.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [DeletedItem::class], version = 1, exportSchema = false)
abstract class HexaPicDatabase : RoomDatabase() {

    abstract fun deletedItemDao(): DeletedItemDao

    companion object {
        @Volatile private var INSTANCE: HexaPicDatabase? = null

        fun get(context: Context): HexaPicDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                HexaPicDatabase::class.java,
                "hexapic.db"
            ).build().also { INSTANCE = it }
        }
    }
}
