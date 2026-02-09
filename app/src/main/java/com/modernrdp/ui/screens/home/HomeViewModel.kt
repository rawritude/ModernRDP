package com.modernrdp.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.modernrdp.data.model.ConnectionGroup
import com.modernrdp.data.model.RdpConnection
import com.modernrdp.data.repository.ConnectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupedConnections(
    val group: ConnectionGroup?,     // null = ungrouped
    val connections: List<RdpConnection>,
    val isExpanded: Boolean = true,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: ConnectionRepository,
) : ViewModel() {

    private val groups = repository.getAllGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val allConnections = repository.getAllConnections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Track which groups are collapsed
    private val _collapsedGroups = MutableStateFlow<Set<Long?>>(emptySet())

    /**
     * Connections organized by group for display.
     */
    val groupedConnections: StateFlow<List<GroupedConnections>> =
        combine(groups, allConnections, _collapsedGroups) { groupList, connList, collapsed ->
            val result = mutableListOf<GroupedConnections>()

            // Add each group with its connections
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

            // Add ungrouped connections
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

    private val _showGroupDialog = MutableStateFlow(false)
    val showGroupDialog: StateFlow<Boolean> = _showGroupDialog.asStateFlow()

    fun toggleGroupExpanded(groupId: Long?) {
        _collapsedGroups.value = _collapsedGroups.value.let {
            if (groupId in it) it - groupId else it + groupId
        }
    }

    fun deleteConnection(connection: RdpConnection) {
        viewModelScope.launch {
            repository.deleteConnection(connection)
        }
    }

    fun markConnected(connectionId: Long) {
        viewModelScope.launch {
            repository.markConnected(connectionId)
        }
    }

    fun showCreateGroupDialog() {
        _showGroupDialog.value = true
    }

    fun dismissGroupDialog() {
        _showGroupDialog.value = false
    }

    fun createGroup(name: String) {
        viewModelScope.launch {
            repository.saveGroup(ConnectionGroup(name = name))
            _showGroupDialog.value = false
        }
    }

    fun deleteGroup(group: ConnectionGroup) {
        viewModelScope.launch {
            repository.deleteGroup(group)
        }
    }

    fun moveConnectionToGroup(connectionId: Long, groupId: Long?) {
        viewModelScope.launch {
            repository.moveToGroup(connectionId, groupId)
        }
    }
}
