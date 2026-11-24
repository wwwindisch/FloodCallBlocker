package app.floodcallblocker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.floodcallblocker.AppContainer
import app.floodcallblocker.R
import app.floodcallblocker.core.BlockAction
import app.floodcallblocker.core.ParsedNumber
import app.floodcallblocker.core.Rule
import app.floodcallblocker.data.EntryDraft
import app.floodcallblocker.data.EntryType
import app.floodcallblocker.data.SaveResult
import app.floodcallblocker.data.formatNumber
import app.floodcallblocker.data.rule
import app.floodcallblocker.data.title
import app.floodcallblocker.ui.boldMarkdown
import app.floodcallblocker.ui.containerViewModel
import app.floodcallblocker.ui.formatDuration
import app.floodcallblocker.ui.formatTime
import kotlinx.coroutines.launch

/** Editing state for one entry; text fields are kept as strings so half-typed input is possible. */
class EditViewModel(
    private val c: AppContainer,
    private val entryId: Long?,
) : ViewModel() {
    var loaded by mutableStateOf(entryId == null)
    var type by mutableStateOf(EntryType.NUMBER)
    var name by mutableStateOf("")
    var numberInput by mutableStateOf("")
    var contactNumbers by mutableStateOf(emptyList<String>())
    var contactLookupKey: String? = null
    var contactMissing by mutableStateOf(false)
    var allowed by mutableStateOf(Rule.DEFAULT_ALLOWED_CALLS.toString())
    var window by mutableStateOf(Rule.DEFAULT_WINDOW_MINUTES.toString())
    var hours by mutableStateOf((Rule.DEFAULT_BLOCK_MINUTES / 60).toString())
    var minutes by mutableStateOf((Rule.DEFAULT_BLOCK_MINUTES % 60).toString())
    var action by mutableStateOf(BlockAction.REJECT)
    var enabled by mutableStateOf(true)
    var blockedUntil by mutableStateOf<Long?>(null)
    var error by mutableStateOf<String?>(null)
    var duplicateOf by mutableStateOf<String?>(null)

    init {
        if (entryId != null) {
            viewModelScope.launch {
                val data = c.repository.getEntry(entryId)
                if (data != null) {
                    val e = data.entry
                    val rule = e.rule()
                    type = e.type
                    name = e.displayName
                    contactLookupKey = e.contactLookupKey
                    contactMissing = e.contactMissing
                    numberInput =
                        data.numbers
                            .firstOrNull()
                            ?.e164
                            .orEmpty()
                    contactNumbers = data.numbers.map { formatNumber(it.e164) }
                    allowed = rule.allowedCalls.toString()
                    window = rule.windowMinutes.toString()
                    hours = (rule.blockDurationMinutes / 60).toString()
                    minutes = (rule.blockDurationMinutes % 60).toString()
                    action = rule.action
                    enabled = e.enabled
                    blockedUntil = c.repository.getBlockUntil(entryId)
                }
                loaded = true
            }
        }
    }

    val isNew get() = entryId == null
    val parsedNumber: ParsedNumber? get() = c.normalizer().parse(numberInput)

    val allowedValue get() = allowed.toIntOrNull()?.takeIf { it in 0..Rule.MAX_ALLOWED_CALLS }
    val windowValue get() = window.toIntOrNull()?.takeIf { it in Rule.MIN_WINDOW_MINUTES..Rule.MAX_WINDOW_MINUTES }
    val durationValue: Int?
        get() {
            val h = hours.ifBlank { "0" }.toIntOrNull() ?: return null
            val m = minutes.ifBlank { "0" }.toIntOrNull() ?: return null
            if (h < 0 || m < 0) return null
            return (h * 60 + m).takeIf { it in Rule.MIN_BLOCK_MINUTES..Rule.MAX_BLOCK_MINUTES }
        }

    /** The rule as currently entered, or null if a field is invalid. */
    val rule: Rule?
        get() {
            val a = allowedValue ?: return null
            val w = windowValue ?: return null
            val d = durationValue ?: return null
            return Rule(a, w, d, action)
        }

    val canSave: Boolean
        get() = rule != null && (type == EntryType.CONTACT || parsedNumber != null)

    /** Saves; returns true on success. Sets [duplicateOf] if the number belongs to another entry. */
    suspend fun save(): Boolean {
        val rule = rule ?: return false
        val numbers =
            if (type == EntryType.CONTACT) {
                c.repository
                    .getEntry(entryId ?: return false)
                    ?.numbers
                    ?.map { it.e164 to it.rawInput } ?: return false
            } else {
                val p = parsedNumber ?: return false
                listOf(p.e164 to numberInput.trim())
            }
        val result =
            c.repository.save(
                EntryDraft(
                    id = entryId,
                    type = type,
                    displayName = name,
                    contactLookupKey = contactLookupKey,
                    numbers = numbers,
                    rule = rule,
                    enabled = enabled,
                ),
            )
        return when (result) {
            is SaveResult.Saved -> true
            is SaveResult.Duplicate -> {
                duplicateOf = result.entryName
                false
            }
        }
    }

    suspend fun delete() {
        val id = entryId ?: return
        c.deletedEntry.value = c.repository.delete(id)
    }

    suspend fun liftBlock() {
        val id = entryId ?: return
        c.repository.liftBlock(id)
        blockedUntil = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    entryId: Long?,
    onClose: () -> Unit,
) {
    val vm = containerViewModel(key = "edit-$entryId") { EditViewModel(it, entryId) }
    val scope = rememberCoroutineScope()
    var showInfo by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (vm.isNew) R.string.edit_title_new else R.string.edit_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    TextButton(
                        enabled = vm.canSave,
                        onClick = { scope.launch { if (vm.save()) onClose() } },
                    ) { Text(stringResource(R.string.action_save)) }
                },
            )
        },
    ) { padding ->
        if (!vm.loaded) return@Scaffold
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (vm.contactMissing) {
                Text(stringResource(R.string.contact_missing), color = MaterialTheme.colorScheme.error)
            }
            OutlinedTextField(
                value = vm.name,
                onValueChange = { vm.name = it },
                label = { Text(stringResource(R.string.field_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (vm.type == EntryType.CONTACT) {
                Text(stringResource(R.string.field_contact_numbers), style = MaterialTheme.typography.labelLarge)
                vm.contactNumbers.forEach { Text(it) }
                Text(
                    stringResource(R.string.help_contact_numbers),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                NumberInput(vm)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.section_rule), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { showInfo = true }) {
                    Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.rule_info_title))
                }
            }
            RuleFields(vm)

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Text(
                    explanation(vm),
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            vm.blockedUntil?.let { until ->
                if (until > System.currentTimeMillis()) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.status_blocked_until, formatTime(LocalContext.current, until)))
                            TextButton(onClick = { scope.launch { vm.liftBlock() } }) {
                                Text(stringResource(R.string.action_lift_block))
                            }
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.field_enabled), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.help_enabled),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = vm.enabled, onCheckedChange = { vm.enabled = it })
            }

            if (!vm.isNew) {
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text(stringResource(R.string.rule_info_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(boldMarkdown(stringResource(R.string.rule_info_body)))
                }
            },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text(stringResource(R.string.action_ok)) } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = { Text(stringResource(R.string.confirm_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        vm.delete()
                        onClose()
                    }
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    vm.duplicateOf?.let { owner ->
        AlertDialog(
            onDismissRequest = { vm.duplicateOf = null },
            title = { Text(stringResource(R.string.error_duplicate_title)) },
            text = { Text(stringResource(R.string.error_number_in_use, owner)) },
            confirmButton = { TextButton(onClick = { vm.duplicateOf = null }) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

@Composable
private fun NumberInput(vm: EditViewModel) {
    val parsed = vm.parsedNumber
    val showError = vm.numberInput.isNotBlank() && parsed == null
    OutlinedTextField(
        value = vm.numberInput,
        onValueChange = { vm.numberInput = it },
        label = { Text(stringResource(R.string.field_number)) },
        singleLine = true,
        isError = showError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        supportingText = {
            when {
                showError -> Text(stringResource(R.string.error_invalid_number))
                parsed != null -> {
                    val country = parsed.countryName()
                    Text(if (country != null) "${parsed.formatted} · $country" else parsed.formatted)
                }

                else -> Text(stringResource(R.string.help_number))
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun RuleFields(vm: EditViewModel) {
    val always = vm.allowedValue == 0
    NumberField(
        label = stringResource(R.string.field_allowed),
        value = vm.allowed,
        onChange = { vm.allowed = it },
        help = stringResource(R.string.help_allowed),
        error = vm.allowedValue == null,
        errorText = stringResource(R.string.error_range, 0, Rule.MAX_ALLOWED_CALLS),
        min = 0,
        max = Rule.MAX_ALLOWED_CALLS,
    )
    NumberField(
        label = stringResource(R.string.field_window),
        value = vm.window,
        onChange = { vm.window = it },
        help = stringResource(R.string.help_window),
        error = vm.windowValue == null,
        errorText = stringResource(R.string.error_range, Rule.MIN_WINDOW_MINUTES, Rule.MAX_WINDOW_MINUTES),
        min = Rule.MIN_WINDOW_MINUTES,
        max = Rule.MAX_WINDOW_MINUTES,
        enabled = !always,
    )
    Column {
        Text(
            stringResource(R.string.field_duration),
            style = MaterialTheme.typography.labelLarge,
            color = if (always) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            OutlinedTextField(
                value = vm.hours,
                onValueChange = { vm.hours = it.filter(Char::isDigit).take(3) },
                label = { Text(stringResource(R.string.unit_hours)) },
                singleLine = true,
                enabled = !always,
                isError = vm.durationValue == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = vm.minutes,
                onValueChange = { vm.minutes = it.filter(Char::isDigit).take(4) },
                label = { Text(stringResource(R.string.unit_minutes)) },
                singleLine = true,
                enabled = !always,
                isError = vm.durationValue == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            if (vm.durationValue == null) stringResource(R.string.error_duration) else stringResource(R.string.help_duration),
            style = MaterialTheme.typography.bodySmall,
            color = if (vm.durationValue == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp),
        )
    }
    Column {
        Text(stringResource(R.string.field_action), style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            val options = listOf(BlockAction.REJECT to R.string.action_reject, BlockAction.SILENCE to R.string.action_silence)
            options.forEachIndexed { i, (action, label) ->
                SegmentedButton(
                    selected = vm.action == action,
                    onClick = { vm.action = action },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                ) { Text(stringResource(label)) }
            }
        }
        Text(
            stringResource(R.string.help_action),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp),
        )
    }
}

/** Numeric field with − / + buttons and a help text underneath. */
@Composable
private fun NumberField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    help: String,
    error: Boolean,
    errorText: String,
    min: Int,
    max: Int,
    enabled: Boolean = true,
) {
    val current = value.toIntOrNull()
    Row(verticalAlignment = Alignment.Top) {
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(it.filter(Char::isDigit).take(4)) },
            label = { Text(label) },
            singleLine = true,
            enabled = enabled,
            isError = error,
            supportingText = { Text(if (error) errorText else help) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        Column(Modifier.padding(start = 8.dp, top = 8.dp)) {
            Row {
                OutlinedButton(
                    enabled = enabled && (current ?: min) > min,
                    onClick = { onChange(((current ?: min) - 1).coerceAtLeast(min).toString()) },
                ) { Text("−") }
                OutlinedButton(
                    enabled = enabled && (current ?: min) < max,
                    onClick = { onChange(((current ?: (min - 1)) + 1).coerceAtMost(max).toString()) },
                    modifier = Modifier.padding(start = 8.dp),
                ) { Text("+") }
            }
        }
    }
}

/** Live-updating sentence that describes the behaviour with the current values (SPEC F-03a). */
@Composable
private fun explanation(vm: EditViewModel): androidx.compose.ui.text.AnnotatedString {
    val context = LocalContext.current
    val rule =
        vm.rule ?: return androidx.compose.ui.text
            .AnnotatedString(stringResource(R.string.explain_invalid))
    val name =
        vm.name.ifBlank {
            vm.parsedNumber?.formatted ?: vm.contactNumbers.firstOrNull() ?: stringResource(R.string.explain_someone)
        }
    val reject = rule.action == BlockAction.REJECT
    val text =
        if (rule.alwaysBlocked) {
            stringResource(if (reject) R.string.explain_always_reject else R.string.explain_always_silence, name)
        } else {
            stringResource(
                if (reject) R.string.explain_reject else R.string.explain_silence,
                name,
                rule.allowedCalls,
                formatDuration(context, rule.windowMinutes),
                rule.allowedCalls + 1,
                formatDuration(context, rule.blockDurationMinutes),
            )
        }
    return boldMarkdown(text)
}
