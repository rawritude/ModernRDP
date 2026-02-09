package com.modernrdp.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
    val connections by viewModel.connections.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            HomeTopBar(scrollBehavior = scrollBehavior)
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
            visible = connections.isEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            EmptyState(modifier = Modifier.padding(innerPadding))
        }

        AnimatedVisibility(
            visible = connections.isNotEmpty(),
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
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = connections,
                    key = { it.id },
                ) { connection ->
                    ConnectionCard(
                        connection = connection,
                        onConnect = {
                            viewModel.markConnected(connection.id)
                            onConnect(connection.id)
                        },
                        onEdit = { onEditConnection(connection.id) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(scrollBehavior: TopAppBarScrollBehavior) {
    LargeTopAppBar(
        title = { Text("ModernRDP") },
        actions = {
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
