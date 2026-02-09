package com.modernrdp.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.modernrdp.data.model.RdpConnection
import com.modernrdp.data.repository.ConnectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: ConnectionRepository,
) : ViewModel() {

    val connections: StateFlow<List<RdpConnection>> = repository.getAllConnections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
}
