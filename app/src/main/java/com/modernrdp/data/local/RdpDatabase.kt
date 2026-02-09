package com.modernrdp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.modernrdp.data.model.ConnectionGroup
import com.modernrdp.data.model.RdpConnection

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Create connection_groups table
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `connection_groups` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `color` INTEGER NOT NULL DEFAULT 0,
                `sortOrder` INTEGER NOT NULL DEFAULT 0,
                `createdAt` INTEGER NOT NULL DEFAULT 0
            )"""
        )
        // Add groupId column to connections
        db.execSQL("ALTER TABLE `connections` ADD COLUMN `groupId` INTEGER DEFAULT NULL REFERENCES `connection_groups`(`id`) ON DELETE SET NULL")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_connections_groupId` ON `connections` (`groupId`)")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `connections` ADD COLUMN `macAddress` TEXT NOT NULL DEFAULT ''")
    }
}

@Database(
    entities = [RdpConnection::class, ConnectionGroup::class],
    version = 3,
    exportSchema = false,
)
abstract class RdpDatabase : RoomDatabase() {
    abstract fun connectionDao(): ConnectionDao
    abstract fun groupDao(): GroupDao
}
