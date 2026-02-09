package com.modernrdp.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.modernrdp.data.model.RdpConnection
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectionDao {
    @Query("SELECT * FROM connections ORDER BY lastConnected DESC, createdAt DESC")
    fun getAllConnections(): Flow<List<RdpConnection>>

    @Query("SELECT * FROM connections WHERE groupId = :groupId ORDER BY lastConnected DESC, createdAt DESC")
    fun getConnectionsByGroup(groupId: Long): Flow<List<RdpConnection>>

    @Query("SELECT * FROM connections WHERE groupId IS NULL ORDER BY lastConnected DESC, createdAt DESC")
    fun getUngroupedConnections(): Flow<List<RdpConnection>>

    @Query("SELECT * FROM connections WHERE id = :id")
    suspend fun getConnectionById(id: Long): RdpConnection?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConnection(connection: RdpConnection): Long

    @Update
    suspend fun updateConnection(connection: RdpConnection)

    @Delete
    suspend fun deleteConnection(connection: RdpConnection)

    @Query("UPDATE connections SET lastConnected = :timestamp WHERE id = :id")
    suspend fun updateLastConnected(id: Long, timestamp: Long)

    @Query("UPDATE connections SET groupId = :groupId WHERE id = :connectionId")
    suspend fun moveToGroup(connectionId: Long, groupId: Long?)
}
