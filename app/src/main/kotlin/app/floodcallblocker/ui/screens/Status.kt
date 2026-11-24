package app.floodcallblocker.ui.screens

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.floodcallblocker.FloodApp
import kotlinx.coroutines.launch

/** Snapshot of the permissions/roles the app depends on (SPEC F-61). */
data class Prerequisites(
    val screeningRole: Boolean,
    val notifications: Boolean,
    val contacts: Boolean,
)

fun readPrerequisites(context: Context): Prerequisites {
    val roles = context.getSystemService(RoleManager::class.java)
    return Prerequisites(
        screeningRole =
            roles.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) &&
                roles.isRoleHeld(RoleManager.ROLE_CALL_SCREENING),
        notifications =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
        contacts =
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED,
    )
}

/** Handles to request each prerequisite plus its current state, refreshed on resume. */
class PrerequisiteActions(
    val state: Prerequisites,
    val requestScreeningRole: () -> Unit,
    val requestNotifications: () -> Unit,
    val requestContacts: () -> Unit,
)

@Composable
fun rememberPrerequisites(onContactsGranted: () -> Unit = {}): PrerequisiteActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(readPrerequisites(context)) }
    val refresh = { state = readPrerequisites(context) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }
    val contactsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            refresh()
            if (granted) {
                val app = context.applicationContext as FloodApp
                app.container.contactSync.observe(app, app.appScope)
                scope.launch { app.container.contactSync.syncAll() }
                onContactsGranted()
            }
        }

    return PrerequisiteActions(
        state = state,
        requestScreeningRole = {
            val roles = context.getSystemService(RoleManager::class.java)
            if (roles.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                roleLauncher.launch(roles.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
            }
        },
        requestNotifications = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        requestContacts = { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) },
    )
}

/** Fallback when the system will not show a permission dialog any more: open app settings. */
fun appSettingsIntent(context: Context): Intent =
    Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(android.net.Uri.fromParts("package", context.packageName, null))
