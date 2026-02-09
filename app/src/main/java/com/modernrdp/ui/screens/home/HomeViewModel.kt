package com.modernrdp.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.modernrdp.data.model.ConnectionGroup
import com.modernrdp.data.model.RdpConnection
import com.modernrdp.data.repository.ConnectionRepository
import com.modernrdp.rdp.SessionTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupedConnections(
    val group: ConnectionGroup?,
    val connections: List<RdpConnection>,
    val isExpanded: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: ConnectionRepository,
    private val sessionTracker: SessionTracker,
) : ViewModel() {

    private val groups = repository.getAllGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val allConnections = repository.getAllConnections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val searchResults: StateFlow<List<RdpConnection>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) flowOf(emptyList())
            else repository.searchConnections(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _collapsedGroups = MutableStateFlow<Set<Long?>>(emptySet())

    val groupedConnections: StateFlow<List<GroupedConnections>> =
        combine(groups, allConnections, _collapsedGroups) { groupList, connList, collapsed ->
            val result = mutableListOf<GroupedConnections>()

            for (group in groupList) {
                val groupConns = connList.filter { it.groupId == group.id }
                if (groupConns.isNotEmpty()) {
                    result.add(
                        GroupedConnections(
                            group = group,
                            connections = groupConns,
                            isExpanded = group.id !in collapsed,
                        )
                    )
                }
            }

            val ungrouped = connList.filter { it.groupId == null }
            if (ungrouped.isNotEmpty() || result.isEmpty()) {
                result.add(
                    GroupedConnections(
                        group = null,
                        connections = ungrouped,
                        isExpanded = null !in collapsed,
                    )
                )
            }

            result
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableGroups: StateFlow<List<ConnectionGroup>> = groups

    val activeSessions = sessionTracker.activeSessions

    private val _showGroupDialog = MutableStateFlow(false)
    val showGroupDialog: StateFlow<Boolean> = _showGroupDialog.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleGroupExpanded(groupId: Long?) {
        _collapsedGroups.value = _collapsedGroups.value.let {
            if (groupId in it) it - groupId else it + groupId
        }
    }

    fun deleteConnection(connection: RdpConnection) {
        viewModelScope.launch { repository.deleteConnection(connection) }
    }

    fun markConnected(connectionId: Long) {
        viewModelScope.launch { repository.markConnected(connectionId) }
    }

    fun showCreateGroupDialog() { _showGroupDialog.value = true }

    fun dismissGroupDialog() { _showGroupDialog.value = false }

    fun createGroup(name: String) {
        viewModelScope.launch {
            repository.saveGroup(ConnectionGroup(name = name))
            _showGroupDialog.value = false
        }
    }

    fun deleteGroup(group: ConnectionGroup) {
        viewModelScope.launch { repository.deleteGroup(group) }
    }

    fun moveConnectionToGroup(connectionId: Long, groupId: Long?) {
        viewModelScope.launch { repository.moveToGroup(connectionId, groupId) }
    }

    /** Quick connect: creates a temporary connection and navigates to it. */
    fun quickConnect(hostname: String, port: Int, username: String, password: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val connection = RdpConnection(
                name = hostname,
                hostname = hostname,
                port = port,
                username = username,
                password = password,
            )
            val id = repository.saveConnection(connection)
            onCreated(id)
        }
    }
}
