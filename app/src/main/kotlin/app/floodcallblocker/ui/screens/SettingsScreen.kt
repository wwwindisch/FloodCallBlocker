package app.floodcallblocker.ui.screens

import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import app.floodcallblocker.BuildConfig
import app.floodcallblocker.R
import app.floodcallblocker.data.BackupException
import app.floodcallblocker.data.ConflictStrategy
import app.floodcallblocker.data.ImportPreview
import app.floodcallblocker.data.NotificationStyle
import app.floodcallblocker.data.ThemeMode
import app.floodcallblocker.ui.rememberContainer
import kotlinx.coroutines.launch
import app.floodcallblocker.data.Settings as AppSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: AppSettings) {
    val container = rememberContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var includeLog by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<ImportPreview?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    fun say(
        res: Int,
        vararg args: Any,
    ) = scope.launch { snackbar.showSnackbar(context.getString(res, *args)) }

    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                runCatching {
                    val text = container.backup.export(includeLog)
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
                }.onSuccess { say(R.string.export_done) }.onFailure { say(R.string.export_failed) }
            }
        }
    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                try {
                    val text =
                        context.contentResolver
                            .openInputStream(uri)
                            ?.use { it.readBytes().decodeToString() }
                            .orEmpty()
                    preview = container.backup.preview(text)
                } catch (e: BackupException) {
                    say(R.string.import_invalid)
                } catch (e: java.io.IOException) {
                    say(R.string.import_invalid)
                }
            }
        }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.nav_settings)) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SectionTitle(R.string.settings_design)
            ThemeMode.entries.forEach { mode ->
                ChoiceRow(
                    selected = settings.themeMode == mode,
                    label =
                        stringResource(
                            when (mode) {
                                ThemeMode.SYSTEM -> R.string.theme_system
                                ThemeMode.LIGHT -> R.string.theme_light
                                ThemeMode.DARK -> R.string.theme_dark
                            },
                        ),
                ) { scope.launch { container.settings.setThemeMode(mode) } }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.material_you))
                        Text(
                            stringResource(R.string.material_you_help),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = settings.dynamicColor, onCheckedChange = { scope.launch { container.settings.setDynamicColor(it) } })
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionTitle(R.string.settings_notification)
            NotificationStyle.entries.forEach { style ->
                ChoiceRow(
                    selected = settings.notificationStyle == style,
                    label =
                        stringResource(
                            when (style) {
                                NotificationStyle.QUIET -> R.string.notif_quiet
                                NotificationStyle.POPUP -> R.string.notif_popup
                                NotificationStyle.OFF -> R.string.notif_off
                            },
                        ),
                    help =
                        stringResource(
                            when (style) {
                                NotificationStyle.QUIET -> R.string.notif_quiet_help
                                NotificationStyle.POPUP -> R.string.notif_popup_help
                                NotificationStyle.OFF -> R.string.notif_off_help
                            },
                        ),
                ) {
                    scope.launch {
                        container.settings.setNotificationStyle(style)
                        container.repository.refreshNotifications()
                    }
                }
            }
            if (settings.notificationStyle != NotificationStyle.OFF) {
                val importance = container.notifier.channelImportance(settings.notificationStyle)
                if (importance == NotificationManager.IMPORTANCE_NONE) {
                    Text(
                        stringResource(R.string.notif_disabled_in_android),
                        Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                } else if (importance != null &&
                    (
                        (settings.notificationStyle == NotificationStyle.QUIET && importance != NotificationManager.IMPORTANCE_LOW) ||
                            (settings.notificationStyle == NotificationStyle.POPUP && importance != NotificationManager.IMPORTANCE_HIGH)
                    )
                ) {
                    Text(
                        stringResource(R.string.notif_changed_in_android),
                        Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = { context.startActivity(container.notifier.channelSettingsIntent(settings.notificationStyle)) },
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) { Text(stringResource(R.string.notif_more_settings)) }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionTitle(R.string.settings_backup)
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = includeLog, onCheckedChange = { includeLog = it })
                Text(stringResource(R.string.export_include_log))
            }
            Row(Modifier.padding(horizontal = 16.dp)) {
                Button(onClick = { exportLauncher.launch("floodcallblocker-backup.json") }) {
                    Text(stringResource(R.string.action_export))
                }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                    modifier = Modifier.padding(start = 8.dp),
                ) { Text(stringResource(R.string.action_import)) }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            SectionTitle(R.string.settings_more)
            TextButton(onClick = { confirmClear = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(stringResource(R.string.action_clear_log))
            }
            Text(
                stringResource(R.string.battery_help),
                Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = {
                    try {
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    } catch (_: ActivityNotFoundException) {
                        context.startActivity(appSettingsIntent(context))
                    }
                },
                modifier = Modifier.padding(horizontal = 8.dp),
            ) { Text(stringResource(R.string.battery_open)) }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionTitle(R.string.settings_about)
            Text(
                stringResource(R.string.about_text, BuildConfig.VERSION_NAME),
                Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(
                onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, context.getString(R.string.source_url).toUri()))
                    } catch (_: ActivityNotFoundException) {
                        // No browser installed: nothing to do.
                    }
                },
                modifier = Modifier.padding(horizontal = 8.dp),
            ) { Text(stringResource(R.string.about_source)) }
        }
    }

    preview?.let { p ->
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text(stringResource(R.string.import_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.import_summary, p.newCount, p.conflicts.size))
                    if (p.conflicts.isNotEmpty()) {
                        Text(
                            p.conflicts.joinToString("\n") { "• $it" },
                            Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                Column {
                    if (p.conflicts.isNotEmpty()) {
                        TextButton(onClick = {
                            preview = null
                            scope.launch { say(R.string.import_done, container.backup.import(p, ConflictStrategy.OVERWRITE)) }
                        }) { Text(stringResource(R.string.import_overwrite)) }
                    }
                    TextButton(onClick = {
                        preview = null
                        scope.launch { say(R.string.import_done, container.backup.import(p, ConflictStrategy.SKIP)) }
                    }) { Text(stringResource(if (p.conflicts.isEmpty()) R.string.action_import else R.string.import_skip)) }
                }
            },
            dismissButton = { TextButton(onClick = { preview = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.action_clear_log)) },
            text = { Text(stringResource(R.string.confirm_clear_log)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch { container.repository.clearLog() }
                }) { Text(stringResource(R.string.action_clear_log)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun SectionTitle(res: Int) {
    Text(
        stringResource(res),
        Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ChoiceRow(
    selected: Boolean,
    label: String,
    help: String? = null,
    onSelect: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 16.dp)) {
            Text(label)
            if (help != null) {
                Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
