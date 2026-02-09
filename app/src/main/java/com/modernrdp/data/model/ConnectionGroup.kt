package com.modernrdp.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "connection_groups")
data class ConnectionGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int = 0, // Material color index
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)
