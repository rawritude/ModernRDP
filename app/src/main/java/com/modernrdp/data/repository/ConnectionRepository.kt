package com.modernrdp.data.repository

import com.modernrdp.data.local.ConnectionDao
import com.modernrdp.data.model.RdpConnection
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionRepository @Inject constructor(
    private val connectionDao: ConnectionDao,
) {
    fun getAllConnections(): Flow<List<RdpConnection>> =
        connectionDao.getAllConnections()

    suspend fun getConnectionById(id: Long): RdpConnection? =
        connectionDao.getConnectionById(id)

    suspend fun saveConnection(connection: RdpConnection): Long =
        connectionDao.insertConnection(connection)

    suspend fun updateConnection(connection: RdpConnection) =
        connectionDao.updateConnection(connection)

    suspend fun deleteConnection(connection: RdpConnection) =
        connectionDao.deleteConnection(connection)

    suspend fun markConnected(id: Long) =
        connectionDao.updateLastConnected(id, System.currentTimeMillis())
}
