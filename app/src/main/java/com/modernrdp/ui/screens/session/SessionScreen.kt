package com.modernrdp.ui.screens.session

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.modernrdp.rdp.FreeRdpBridge
import com.modernrdp.rdp.SessionState

@Composable
fun SessionScreen(
    connectionId: Long,
    onDisconnected: () -> Unit,
    viewModel: SessionViewModel = hiltViewModel(),
) {
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val framebuffer by viewModel.framebuffer.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val toolbarVisible by viewModel.toolbarVisible.collectAsStateWithLifecycle()

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    // Compute screen dimensions in pixels
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.roundToPx() }

    // Auto-connect when connection data loads
    LaunchedEffect(connection) {
        if (connection != null && sessionState == SessionState.DISCONNECTED) {
            viewModel.connect(screenWidthPx, screenHeightPx)
        }
    }

    // Handle screen size changes (fold/unfold)
    LaunchedEffect(screenWidthPx, screenHeightPx) {
        if (sessionState == SessionState.CONNECTED) {
            viewModel.onScreenResize(screenWidthPx, screenHeightPx)
        }
    }

    // Navigate back when disconnected after being connected
    LaunchedEffect(sessionState) {
        if (sessionState == SessionState.DISCONNECTED && connection != null) {
            // Only navigate back if we were previously connected
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when (sessionState) {
            SessionState.CONNECTING -> ConnectingOverlay(
                hostname = connection?.hostname ?: "",
            )
            SessionState.CONNECTED -> RdpCanvas(
                viewModel = viewModel,
                onToggleToolbar = viewModel::toggleToolbar,
            )
            SessionState.ERROR -> ErrorOverlay(
                onRetry = { viewModel.connect(screenWidthPx, screenHeightPx) },
                onBack = onDisconnected,
            )
            SessionState.DISCONNECTED, SessionState.DISCONNECTING -> {
                // Show nothing, waiting for connection or navigating away
            }
        }

        // Floating toolbar
        AnimatedVisibility(
            visible = toolbarVisible && sessionState == SessionState.CONNECTED,
            enter = slideInVertically { -it },
            exit = slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            SessionToolbar(
                hostname = connection?.hostname ?: "",
                onDisconnect = {
                    viewModel.disconnect()
                    onDisconnected()
                },
                onKeyboard = { /* TODO: Show soft keyboard */ },
                onFullscreen = { viewModel.toggleToolbar() },
            )
        }
    }
}

@Composable
private fun RdpCanvas(
    viewModel: SessionViewModel,
    onToggleToolbar: () -> Unit,
) {
    val framebuffer by viewModel.framebuffer.collectAsStateWithLifecycle()
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { pos ->
                        val rdpX = ((pos.x - offset.x) / scale).toInt()
                        val rdpY = ((pos.y - offset.y) / scale).toInt()
                        viewModel.onTouchEvent(
                            rdpX, rdpY,
                            FreeRdpBridge.MOUSE_FLAG_BUTTON1 or FreeRdpBridge.MOUSE_FLAG_DOWN,
                        )
                        viewModel.onTouchEvent(rdpX, rdpY, FreeRdpBridge.MOUSE_FLAG_BUTTON1)
                    },
                    onDoubleTap = { onToggleToolbar() },
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.5f, 3f)
                    offset = Offset(
                        x = offset.x + pan.x,
                        y = offset.y + pan.y,
                    )
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val rdpX = ((change.position.x - offset.x) / scale).toInt()
                    val rdpY = ((change.position.y - offset.y) / scale).toInt()
                    viewModel.onTouchEvent(rdpX, rdpY, FreeRdpBridge.MOUSE_FLAG_MOVE)
                }
            },
    ) {
        framebuffer?.let { bitmap ->
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            ) {
                drawImage(
                    image = bitmap.asImageBitmap(),
                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                )
            }
        }
    }
}

@Composable
private fun SessionToolbar(
    hostname: String,
    onDisconnect: () -> Unit,
    onKeyboard: () -> Unit,
    onFullscreen: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDisconnect) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Disconnect",
                    tint = MaterialTheme.colorScheme.error,
                )
            }

            Text(
                text = hostname,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
            )

            FilledTonalIconButton(
                onClick = onKeyboard,
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(),
            ) {
                Icon(Icons.Default.Keyboard, contentDescription = "Keyboard", modifier = Modifier.size(18.dp))
            }

            Spacer(modifier = Modifier.width(4.dp))

            FilledTonalIconButton(
                onClick = onFullscreen,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(Icons.Default.Fullscreen, contentDescription = "Fullscreen", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ConnectingOverlay(hostname: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.padding(16.dp))
            Text(
                text = "Connecting to $hostname...",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun ErrorOverlay(
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Connection Failed",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
            )
            Spacer(modifier = Modifier.padding(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalIconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                FilledTonalIconButton(onClick = onRetry) {
                    Icon(Icons.Default.ZoomIn, contentDescription = "Retry")
                }
            }
        }
    }
}
