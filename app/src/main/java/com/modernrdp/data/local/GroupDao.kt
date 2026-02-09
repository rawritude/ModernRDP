package com.modernrdp.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.modernrdp.data.model.ConnectionGroup
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Query("SELECT * FROM connection_groups ORDER BY sortOrder ASC, name ASC")
    fun getAllGroups(): Flow<List<ConnectionGroup>>

    @Query("SELECT * FROM connection_groups WHERE id = :id")
    suspend fun getGroupById(id: Long): ConnectionGroup?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: ConnectionGroup): Long

    @Update
    suspend fun updateGroup(group: ConnectionGroup)

    @Delete
    suspend fun deleteGroup(group: ConnectionGroup)
}
