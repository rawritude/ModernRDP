package com.modernrdp.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.modernrdp.ui.components.ConnectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddConnection: () -> Unit,
    onEditConnection: (Long) -> Unit,
    onConnect: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val groupedConnections by viewModel.groupedConnections.collectAsStateWithLifecycle()
    val showGroupDialog by viewModel.showGroupDialog.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val allEmpty = groupedConnections.all { it.connections.isEmpty() }

    if (showGroupDialog) {
        CreateGroupDialog(
            onDismiss = viewModel::dismissGroupDialog,
            onCreate = viewModel::createGroup,
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            HomeTopBar(
                scrollBehavior = scrollBehavior,
                onCreateGroup = viewModel::showCreateGroupDialog,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddConnection,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Connection") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        },
    ) { innerPadding ->
        AnimatedVisibility(
            visible = allEmpty,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            EmptyState(modifier = Modifier.padding(innerPadding))
        }

        AnimatedVisibility(
            visible = !allEmpty,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 88.dp,
                    start = 16.dp,
                    end = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (grouped in groupedConnections) {
                    // Group header (skip for ungrouped when it's the only section)
                    if (grouped.group != null || groupedConnections.size > 1) {
                        item(key = "group_${grouped.group?.id}") {
                            GroupHeader(
                                name = grouped.group?.name ?: "Ungrouped",
                                connectionCount = grouped.connections.size,
                                isExpanded = grouped.isExpanded,
                                onToggle = { viewModel.toggleGroupExpanded(grouped.group?.id) },
                            )
                        }
                    }

                    // Connections in this group
                    if (grouped.isExpanded) {
                        items(
                            items = grouped.connections,
                            key = { it.id },
                        ) { connection ->
                            ConnectionCard(
                                connection = connection,
                                onConnect = {
                                    viewModel.markConnected(connection.id)
                                    onConnect(connection.id)
                                },
                                onEdit = { onEditConnection(connection.id) },
                                modifier = Modifier
                                    .animateItem()
                                    .padding(
                                        start = if (grouped.group != null) 8.dp else 0.dp,
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(
    name: String,
    connectionCount: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$connectionCount",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (isExpanded) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun CreateGroupDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Group") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Group Name") },
                placeholder = { Text("Production Servers") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onCreate(name.trim()) },
                enabled = name.isNotBlank(),
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    scrollBehavior: TopAppBarScrollBehavior,
    onCreateGroup: () -> Unit,
) {
    LargeTopAppBar(
        title = { Text("ModernRDP") },
        actions = {
            IconButton(onClick = onCreateGroup) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = "New Group")
            }
            IconButton(onClick = { /* TODO: Settings */ }) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        },
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Laptop,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No connections yet",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tap + to add your first remote desktop",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}
