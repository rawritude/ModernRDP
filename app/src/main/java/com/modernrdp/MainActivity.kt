package com.modernrdp

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.modernrdp.data.parser.RdpFileParser
import com.modernrdp.data.repository.ConnectionRepository
import com.modernrdp.navigation.ModernRdpNavGraph
import com.modernrdp.navigation.Routes
import com.modernrdp.ui.theme.ModernRdpTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var repository: ConnectionRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ModernRdpTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    ModernRdpNavGraph(navController = navController)
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

    private fun handleRdpIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return

        lifecycleScope.launch {
            try {
                val inputStream = contentResolver.openInputStream(uri) ?: return@launch
                val connection = inputStream.use { RdpFileParser.parse(it) }
                val id = repository.saveConnection(connection)
                Toast.makeText(
                    this@MainActivity,
                    "Imported: ${connection.hostname}",
                    Toast.LENGTH_SHORT,
                ).show()
                // Navigate to the imported connection would require navController access
                // For now, the user will see it in the connection list
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
