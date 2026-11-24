package app.floodcallblocker.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import app.floodcallblocker.FloodApp
import app.floodcallblocker.data.Settings
import app.floodcallblocker.ui.theme.FloodTheme

class MainActivity : ComponentActivity() {
    /** Entry requested by tapping a notification; -1 for none. */
    private var requestedEntry by mutableLongStateOf(-1L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedEntry = intent.getLongExtra(EXTRA_ENTRY_ID, -1L)
        val container = (application as FloodApp).container
        setContent {
            val settings by container.settings.settings.collectAsState(initial = null)
            val s: Settings? = settings
            FloodTheme(s?.themeMode ?: app.floodcallblocker.data.ThemeMode.SYSTEM, s?.dynamicColor ?: true) {
                if (s != null) {
                    AppNavigation(
                        settings = s,
                        requestedEntry = requestedEntry,
                        onRequestedEntryHandled = { requestedEntry = -1L },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedEntry = intent.getLongExtra(EXTRA_ENTRY_ID, -1L)
    }

    companion object {
        const val EXTRA_ENTRY_ID = "entry_id"
    }
}
