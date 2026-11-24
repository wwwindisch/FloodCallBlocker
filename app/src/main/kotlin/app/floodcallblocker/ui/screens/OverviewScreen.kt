package app.floodcallblocker.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.floodcallblocker.AppContainer
import app.floodcallblocker.R
import app.floodcallblocker.core.Rule
import app.floodcallblocker.data.EntryDraft
import app.floodcallblocker.data.EntryItem
import app.floodcallblocker.data.EntryType
import app.floodcallblocker.data.EntryWithNumbers
import app.floodcallblocker.data.SaveResult
import app.floodcallblocker.data.rule
import app.floodcallblocker.ui.containerViewModel
import app.floodcallblocker.ui.formatDuration
import app.floodcallblocker.ui.formatTime
import app.floodcallblocker.ui.rememberNow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AddContactResult {
    data class Added(
        val id: Long,
    ) : AddContactResult

    data class Duplicate(
        val entryName: String,
    ) : AddContactResult

    data object NoNumbers : AddContactResult

    data object Failed : AddContactResult
}

class OverviewViewModel(
    private val c: AppContainer,
) : ViewModel() {
    val items = c.repository.observeItems().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val deleted = c.deletedEntry

    fun consumeDeleted() {
        c.deletedEntry.value = null
    }

    fun lift(id: Long) = viewModelScope.launch { c.repository.liftBlock(id) }

    fun setEnabled(
        id: Long,
        enabled: Boolean,
    ) = viewModelScope.launch { c.repository.setEnabled(id, enabled) }

    fun maintenance() = viewModelScope.launch { c.repository.maintenance() }

    fun undoDelete(snapshot: EntryWithNumbers) = viewModelScope.launch { c.repository.restore(snapshot) }

    /** Creates a contact entry with default rule from the contact the user picked. */
    suspend fun addContact(uri: android.net.Uri): AddContactResult {
        val info = c.contacts.readPicked(uri) ?: return AddContactResult.Failed
        val normalizer = c.normalizer()
        val numbers = info.numbers.mapNotNull { raw -> normalizer.normalize(raw)?.let { it to raw } }.distinctBy { it.first }
        if (numbers.isEmpty()) return AddContactResult.NoNumbers
        return when (
            val r =
                c.repository.save(
                    EntryDraft(type = EntryType.CONTACT, displayName = info.name, contactLookupKey = info.lookupKey, numbers = numbers),
                )
        ) {
            is SaveResult.Saved -> AddContactResult.Added(r.id)
            is SaveResult.Duplicate -> AddContactResult.Duplicate(r.entryName)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(onOpenEntry: (Long?) -> Unit) {
    val vm = containerViewModel { OverviewViewModel(it) }
    val items by vm.items.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val now = rememberNow()
    var menuOpen by remember { mutableStateOf(false) }

    val pickContact =
        rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                when (val r = vm.addContact(uri)) {
                    is AddContactResult.Added -> onOpenEntry(r.id)
                    is AddContactResult.Duplicate -> snackbar.showSnackbar(context.getString(R.string.error_number_in_use, r.entryName))
                    AddContactResult.NoNumbers -> snackbar.showSnackbar(context.getString(R.string.error_contact_no_numbers))
                    AddContactResult.Failed -> snackbar.showSnackbar(context.getString(R.string.error_contact_failed))
                }
            }
        }
    val prerequisites = rememberPrerequisites(onContactsGranted = { pickContact.launch(null) })

    // "Entry deleted – undo" raised by the edit screen.
    val deleted by vm.deleted.collectAsState()
    LaunchedEffect(deleted) {
        val snapshot = deleted ?: return@LaunchedEffect
        vm.consumeDeleted()
        val result =
            snackbar.showSnackbar(
                context.getString(R.string.entry_deleted),
                actionLabel = context.getString(R.string.action_undo),
                duration = androidx.compose.material3.SnackbarDuration.Long,
            )
        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) vm.undoDelete(snapshot)
    }

    // Expired blocks are ended (and logged) as soon as the screen notices them.
    val anyExpired = items?.any { it.block != null && it.block.until <= now } == true
    LaunchedEffect(anyExpired) { if (anyExpired) vm.maintenance() }

    Scaffold(
        contentWindowInsets =
            androidx.compose.foundation.layout
                .WindowInsets(0, 0, 0, 0),
        topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            androidx.compose.foundation.layout.Box {
                FloatingActionButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.add_by_number)) },
                        onClick = {
                            menuOpen = false
                            onOpenEntry(null)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.add_by_contact)) },
                        onClick = {
                            menuOpen = false
                            if (prerequisites.state.contacts) pickContact.launch(null) else prerequisites.requestContacts()
                        },
                    )
                }
            }
        },
    ) { padding ->
        val list = items
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!prerequisites.state.screeningRole) {
                item {
                    StatusCard(
                        text = stringResource(R.string.status_screening_off),
                        button = stringResource(R.string.status_fix),
                        onClick = prerequisites.requestScreeningRole,
                    )
                }
            }
            if (list != null && list.isEmpty()) {
                item { EmptyState() }
            }
            items(list.orEmpty(), key = { it.entry.id }) { item ->
                EntryCard(
                    item = item,
                    now = now,
                    onClick = { onOpenEntry(item.entry.id) },
                    onLift = { vm.lift(item.entry.id) },
                    onToggle = { vm.setEnabled(item.entry.id, it) },
                )
            }
        }
    }
}

@Composable
private fun StatusCard(
    text: String,
    button: String,
    onClick: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null)
            Text(text, Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onClick) { Text(button) }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.empty_body),
            Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EntryCard(
    item: EntryItem,
    now: Long,
    onClick: () -> Unit,
    onLift: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val e = item.entry
    val rule = e.rule()
    val blockedUntil = item.block?.until?.takeIf { it > now }
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        ruleSummary(rule),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (e.contactMissing) {
                        Text(
                            stringResource(R.string.contact_missing),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Switch(
                    checked = e.enabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                val (label, highlight) =
                    when {
                        !e.enabled -> stringResource(R.string.status_paused) to false
                        rule.alwaysBlocked -> stringResource(R.string.status_always_blocked) to true
                        blockedUntil != null ->
                            stringResource(R.string.status_blocked_until, formatTime(context, blockedUntil)) to true

                        else -> stringResource(R.string.status_monitored) to false
                    }
                AssistChip(
                    onClick = onClick,
                    label = { Text(label) },
                    leadingIcon =
                        if (highlight) {
                            { Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.padding(0.dp)) }
                        } else {
                            null
                        },
                    colors =
                        if (highlight) {
                            AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                        } else {
                            AssistChipDefaults.assistChipColors()
                        },
                )
                if (blockedUntil != null) {
                    TextButton(onClick = onLift) { Text(stringResource(R.string.action_lift_block)) }
                }
            }
        }
    }
}

/** One-line summary of a rule: "max. 2× in 5 min → 3 h block" / "Always blocked". */
@Composable
fun ruleSummary(rule: Rule): String {
    val context = LocalContext.current
    return if (rule.alwaysBlocked) {
        stringResource(R.string.rule_summary_always)
    } else {
        stringResource(
            R.string.rule_summary,
            rule.allowedCalls,
            formatDuration(context, rule.windowMinutes),
            formatDuration(context, rule.blockDurationMinutes),
        )
    }
}
