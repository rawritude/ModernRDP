package com.modernrdp

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.modernrdp.data.parser.RdpFileParser
import com.modernrdp.data.preferences.AppPreferences
import com.modernrdp.data.repository.ConnectionRepository
import com.modernrdp.navigation.ModernRdpNavGraph
import com.modernrdp.ui.theme.ModernRdpTheme
import com.modernrdp.util.BiometricHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var repository: ConnectionRepository

    @Inject
    lateinit var prefs: AppPreferences

    private var biometricAuthenticated by mutableStateOf(false)
    private var biometricRequired by mutableStateOf(false)
    private var biometricChecked by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check biometric preference
        lifecycleScope.launch {
            prefs.requireBiometric.collect { required ->
                biometricRequired = required
                if (!biometricChecked) {
                    biometricChecked = true
                    if (required && BiometricHelper.isAvailable(this@MainActivity)) {
                        promptBiometric()
                    } else {
                        biometricAuthenticated = true
                    }
                }
            }
        }

        setContent {
            val themeMode by prefs.themeMode.collectAsState(initial = "system")

            ModernRdpTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (biometricAuthenticated || !biometricRequired) {
                        val navController = rememberNavController()
                        ModernRdpNavGraph(navController = navController)
                    } else {
                        // Locked state -- show placeholder until biometric succeeds
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "Authentication required",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        }

        // Handle .rdp file intent
        handleRdpIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleRdpIntent(intent)
    }

    private fun promptBiometric() {
        BiometricHelper.authenticate(
            activity = this,
            title = "Unlock ModernRDP",
            subtitle = "Verify your identity to access your connections",
            onSuccess = { biometricAuthenticated = true },
            onError = { msg ->
                Toast.makeText(this, "Authentication failed: $msg", Toast.LENGTH_SHORT).show()
            },
        )
    }

    private fun handleRdpIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return

        lifecycleScope.launch {
            try {
                val inputStream = contentResolver.openInputStream(uri) ?: return@launch
                val connection = inputStream.use { RdpFileParser.parse(it) }
                repository.saveConnection(connection)
                Toast.makeText(
                    this@MainActivity,
                    "Imported: ${connection.hostname}",
                    Toast.LENGTH_SHORT,
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    "Failed to import .rdp file: ${e.message}",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }
}
