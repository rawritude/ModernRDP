package com.modernrdp.data.repository

import com.modernrdp.data.crypto.CredentialEncryption
import com.modernrdp.data.local.ConnectionDao
import com.modernrdp.data.local.GroupDao
import com.modernrdp.data.model.ConnectionGroup
import com.modernrdp.data.model.RdpConnection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionRepository @Inject constructor(
    private val connectionDao: ConnectionDao,
    private val groupDao: GroupDao,
    private val encryption: CredentialEncryption,
) {
    /**
     * Get all connections with passwords decrypted for use.
     */
    fun getAllConnections(): Flow<List<RdpConnection>> =
        connectionDao.getAllConnections().map { list -> list.map { decryptConnection(it) } }

    fun getConnectionsByGroup(groupId: Long): Flow<List<RdpConnection>> =
        connectionDao.getConnectionsByGroup(groupId).map { list -> list.map { decryptConnection(it) } }

    fun getUngroupedConnections(): Flow<List<RdpConnection>> =
        connectionDao.getUngroupedConnections().map { list -> list.map { decryptConnection(it) } }

    fun searchConnections(query: String): Flow<List<RdpConnection>> =
        connectionDao.searchConnections(query).map { list -> list.map { decryptConnection(it) } }

    suspend fun getConnectionById(id: Long): RdpConnection? =
        connectionDao.getConnectionById(id)?.let { decryptConnection(it) }

    /**
     * Save a connection — passwords are encrypted before storage.
     */
    suspend fun saveConnection(connection: RdpConnection): Long =
        connectionDao.insertConnection(encryptConnection(connection))

    suspend fun updateConnection(connection: RdpConnection) =
        connectionDao.updateConnection(encryptConnection(connection))

    suspend fun deleteConnection(connection: RdpConnection) =
        connectionDao.deleteConnection(connection)

    suspend fun markConnected(id: Long) =
        connectionDao.updateLastConnected(id, System.currentTimeMillis())

    suspend fun moveToGroup(connectionId: Long, groupId: Long?) =
        connectionDao.moveToGroup(connectionId, groupId)

    // --- Groups ---

    fun getAllGroups(): Flow<List<ConnectionGroup>> = groupDao.getAllGroups()

    suspend fun getGroupById(id: Long): ConnectionGroup? = groupDao.getGroupById(id)

    suspend fun saveGroup(group: ConnectionGroup): Long = groupDao.insertGroup(group)

    suspend fun updateGroup(group: ConnectionGroup) = groupDao.updateGroup(group)

    suspend fun deleteGroup(group: ConnectionGroup) = groupDao.deleteGroup(group)

    // --- Encryption helpers ---

    private fun encryptConnection(conn: RdpConnection): RdpConnection = conn.copy(
        password = if (!encryption.isEncrypted(conn.password)) {
            encryption.encrypt(conn.password)
        } else conn.password,
        gatewayPassword = if (!encryption.isEncrypted(conn.gatewayPassword)) {
            encryption.encrypt(conn.gatewayPassword)
        } else conn.gatewayPassword,
    )

    private fun decryptConnection(conn: RdpConnection): RdpConnection = conn.copy(
        password = encryption.decrypt(conn.password),
        gatewayPassword = encryption.decrypt(conn.gatewayPassword),
    )
}
