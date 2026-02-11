package com.modernrdp.ui.screens.session

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.modernrdp.rdp.CertificateInfo
import com.modernrdp.rdp.FreeRdpBridge
import com.modernrdp.rdp.SessionState

@Composable
fun SessionScreen(
    connectionId: Long,
    onDisconnected: () -> Unit,
    viewModel: SessionViewModel = hiltViewModel(),
) {
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val toolbarVisible by viewModel.toolbarVisible.collectAsStateWithLifecycle()
    val pendingCert by viewModel.pendingCertificate.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val retryCount by viewModel.retryCount.collectAsStateWithLifecycle()

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.roundToPx() }

    val snackbarHostState = remember { SnackbarHostState() }
    var showDisconnectConfirm by remember { mutableStateOf(false) }
    val confirmDisconnect by viewModel.confirmDisconnect.collectAsStateWithLifecycle()

    // Disconnect confirmation dialog
    if (showDisconnectConfirm) {
        AlertDialog(
            onDismissRequest = { showDisconnectConfirm = false },
            title = { Text("Disconnect?") },
            text = { Text("Are you sure you want to disconnect from ${connection?.hostname ?: "this server"}?") },
            confirmButton = {
                TextButton(onClick = {
                    showDisconnectConfirm = false
                    viewModel.disconnect()
                    onDisconnected()
                }) { Text("Disconnect", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectConfirm = false }) { Text("Cancel") }
            },
        )
    }

    // Collect one-shot UI events
    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is UiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    LaunchedEffect(connection) {
        if (connection != null && sessionState == SessionState.DISCONNECTED) {
            viewModel.connect(screenWidthPx, screenHeightPx)
        }
    }

    LaunchedEffect(screenWidthPx, screenHeightPx) {
        if (sessionState == SessionState.CONNECTED) {
            viewModel.onScreenResize(screenWidthPx, screenHeightPx)
        }
    }

    // Certificate verification dialog
    pendingCert?.let { cert ->
        CertificateDialog(
            certInfo = cert,
            onAccept = { viewModel.respondToCertificate(true) },
            onReject = { viewModel.respondToCertificate(false) },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when (sessionState) {
            SessionState.CONNECTING -> ConnectingOverlay(
                hostname = connection?.hostname ?: "",
                retryCount = retryCount,
            )
            SessionState.CONNECTED -> RdpCanvas(
                viewModel = viewModel,
                onToggleToolbar = viewModel::toggleToolbar,
            )
            SessionState.ERROR -> ErrorOverlay(
                errorMessage = errorMessage,
                retryCount = retryCount,
                onRetry = { viewModel.retry(screenWidthPx, screenHeightPx) },
                onBack = onDisconnected,
            )
            SessionState.DISCONNECTED, SessionState.DISCONNECTING -> {}
        }

        AnimatedVisibility(
            visible = toolbarVisible && sessionState == SessionState.CONNECTED,
            enter = slideInVertically { -it },
            exit = slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            SessionToolbar(
                hostname = connection?.hostname ?: "",
                onDisconnect = {
                    if (confirmDisconnect) {
                        showDisconnectConfirm = true
                    } else {
                        viewModel.disconnect()
                        onDisconnected()
                    }
                },
                onClipboard = { viewModel.syncLocalClipboard() },
                onKeyboard = { viewModel.toggleKeyboard() },
                onResetZoom = null, // Reset handled in canvas
                onFullscreen = { viewModel.toggleToolbar() },
            )
        }

        // Snackbar for feedback
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
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
    // Observe frame version to trigger recomposition when bitmap pixels change in-place
    val frameVersion by viewModel.frameVersion.collectAsStateWithLifecycle()
    val keyboardVisible by viewModel.keyboardVisible.collectAsStateWithLifecycle()
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Hidden text field for keyboard input
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Tap gestures: single tap = click, double tap = toggle toolbar, long press = right click
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { pos ->
                        val rdpX = ((pos.x - offset.x) / scale).toInt().coerceAtLeast(0)
                        val rdpY = ((pos.y - offset.y) / scale).toInt().coerceAtLeast(0)
                        viewModel.onTouchEvent(
                            rdpX, rdpY,
                            FreeRdpBridge.MOUSE_FLAG_BUTTON1 or FreeRdpBridge.MOUSE_FLAG_DOWN,
                        )
                        viewModel.onTouchEvent(rdpX, rdpY, FreeRdpBridge.MOUSE_FLAG_BUTTON1)
                    },
                    onDoubleTap = { onToggleToolbar() },
                    onLongPress = { pos ->
                        // Long press = right click
                        val rdpX = ((pos.x - offset.x) / scale).toInt().coerceAtLeast(0)
                        val rdpY = ((pos.y - offset.y) / scale).toInt().coerceAtLeast(0)
                        viewModel.onTouchEvent(
                            rdpX, rdpY,
                            FreeRdpBridge.MOUSE_FLAG_BUTTON2 or FreeRdpBridge.MOUSE_FLAG_DOWN,
                        )
                        viewModel.onTouchEvent(rdpX, rdpY, FreeRdpBridge.MOUSE_FLAG_BUTTON2)
                    },
                )
            }
            // Pinch-to-zoom and two-finger pan
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(0.25f, 5f)
                    // Adjust offset to zoom toward the center of the gesture
                    val scaleFactor = newScale / scale
                    offset = Offset(
                        x = offset.x * scaleFactor + pan.x,
                        y = offset.y * scaleFactor + pan.y,
                    )
                    scale = newScale
                }
            }
            // Single-finger drag = mouse move
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val rdpX = ((change.position.x - offset.x) / scale).toInt().coerceAtLeast(0)
                    val rdpY = ((change.position.y - offset.y) / scale).toInt().coerceAtLeast(0)
                    viewModel.onTouchEvent(rdpX, rdpY, FreeRdpBridge.MOUSE_FLAG_MOVE)
                }
            }
            // Scroll wheel via pointer scroll events
            .pointerInput(Unit) {
                awaitEachGesture {
                    val event = awaitFirstDown(requireUnconsumed = false)
                    event.consume()
                    while (true) {
                        val pointerEvent = awaitPointerEvent()
                        if (pointerEvent.type == PointerEventType.Scroll) {
                            val scrollDelta = pointerEvent.changes.firstOrNull()?.scrollDelta
                            if (scrollDelta != null && scrollDelta.y != 0f) {
                                viewModel.onScrollWheel(-scrollDelta.y)
                            }
                            pointerEvent.changes.forEach { it.consume() }
                        }
                    }
                }
            },
    ) {
        framebuffer?.let { bitmap ->
            // Reading frameVersion forces recomposition when bitmap pixels update in-place.
            // Without this, StateFlow.distinctUntilChanged would skip emissions of the
            // same Bitmap reference, even though its pixel contents have changed.
            @Suppress("UNUSED_EXPRESSION")
            frameVersion

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

        // Invisible text field for capturing keyboard/IME input
        BasicTextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                val typed = newValue.text.removePrefix(textFieldValue.text)
                if (typed.isNotEmpty()) {
                    viewModel.onTextInput(typed)
                }
                textFieldValue = TextFieldValue("")
            },
            modifier = Modifier
                .focusRequester(focusRequester)
                .size(1.dp)
                .align(Alignment.BottomCenter),
            textStyle = TextStyle(fontSize = 1.sp, color = Color.Transparent),
            cursorBrush = SolidColor(Color.Transparent),
        )

        // Toggle keyboard visibility
        LaunchedEffect(keyboardVisible) {
            if (keyboardVisible) {
                focusRequester.requestFocus()
                keyboardController?.show()
            } else {
                keyboardController?.hide()
            }
        }
    }
}

@Composable
private fun SessionToolbar(
    hostname: String,
    onDisconnect: () -> Unit,
    onClipboard: () -> Unit,
    onKeyboard: () -> Unit,
    onResetZoom: (() -> Unit)?,
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
                onClick = onClipboard,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(Icons.Default.ContentPaste, contentDescription = "Paste", modifier = Modifier.size(18.dp))
            }

            Spacer(modifier = Modifier.width(4.dp))

            FilledTonalIconButton(
                onClick = onKeyboard,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(Icons.Default.Keyboard, contentDescription = "Keyboard", modifier = Modifier.size(18.dp))
            }

            if (onResetZoom != null) {
                Spacer(modifier = Modifier.width(4.dp))

                FilledTonalIconButton(
                    onClick = onResetZoom,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Default.FitScreen, contentDescription = "Reset zoom", modifier = Modifier.size(18.dp))
                }
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
private fun CertificateDialog(
    certInfo: CertificateInfo,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onReject,
        title = {
            Text(if (certInfo.hostMismatch) "Certificate Mismatch" else "Untrusted Certificate")
        },
        text = {
            Column {
                if (certInfo.hostMismatch) {
                    Text(
                        "The server certificate does not match the hostname.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                CertField("Host", certInfo.host)
                CertField("Subject", certInfo.subject)
                CertField("Issuer", certInfo.issuer)
                CertField("Fingerprint", certInfo.fingerprint)
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept) { Text("Accept") }
        },
        dismissButton = {
            TextButton(onClick = onReject) { Text("Reject", color = MaterialTheme.colorScheme.error) }
        },
    )
}

@Composable
private fun CertField(label: String, value: String) {
    if (value.isNotBlank()) {
        Column(modifier = Modifier.padding(vertical = 2.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ConnectingOverlay(hostname: String, retryCount: Int) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Connecting to $hostname...", style = MaterialTheme.typography.bodyLarge, color = Color.White)
            if (retryCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Retry attempt $retryCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun ErrorOverlay(
    errorMessage: String?,
    retryCount: Int,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                "Connection Failed",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
            )

            if (!errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }

            if (retryCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Failed after $retryCount retries",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Back")
                }
                FilledTonalButton(onClick = onRetry) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Retry")
                }
            }
        }
    }
}
