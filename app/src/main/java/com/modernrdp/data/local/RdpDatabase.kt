package com.modernrdp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.modernrdp.data.model.RdpConnection

@Database(entities = [RdpConnection::class], version = 1, exportSchema = false)
abstract class RdpDatabase : RoomDatabase() {
    abstract fun connectionDao(): ConnectionDao
}
