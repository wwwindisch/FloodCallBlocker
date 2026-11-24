package app.floodcallblocker.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.floodcallblocker.AppContainer
import app.floodcallblocker.R
import app.floodcallblocker.data.LogEventEntity
import app.floodcallblocker.data.LogType
import app.floodcallblocker.ui.containerViewModel
import app.floodcallblocker.ui.formatDay
import app.floodcallblocker.ui.formatTime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class LogViewModel(
    c: AppContainer,
) : ViewModel() {
    val events = c.repository.observeLog().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen() {
    val vm = containerViewModel { LogViewModel(it) }
    val events by vm.events.collectAsState()
    val context = LocalContext.current
    // Filter by entry name (snapshot); null = all.
    var filter by remember { mutableStateOf<String?>(null) }
    val names = events.map { it.entryNameSnapshot }.distinct().sorted()
    val shown = events.filter { filter == null || it.entryNameSnapshot == filter }
    val grouped = shown.groupBy { formatDay(context, it.timestamp) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.nav_log)) }) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (names.size > 1) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text(stringResource(R.string.log_all)) })
                    names.forEach { n ->
                        FilterChip(selected = filter == n, onClick = { filter = n }, label = { Text(n) })
                    }
                }
            }
            if (shown.isEmpty()) {
                Text(
                    stringResource(R.string.log_empty),
                    Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(Modifier.fillMaxWidth(), rememberLazyListState(), PaddingValues(bottom = 16.dp)) {
                    grouped.forEach { (day, list) ->
                        item(key = "h-$day") {
                            Text(
                                day,
                                Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        items(list, key = { it.id }) { ev ->
                            ListItem(
                                headlineContent = { Text(eventText(ev)) },
                                supportingContent = { Text(ev.entryNameSnapshot) },
                                overlineContent = { Text(formatTime(context, ev.timestamp)) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun eventText(e: LogEventEntity): String {
    val context = LocalContext.current
    return when (e.type) {
        LogType.BLOCK_STARTED ->
            stringResource(
                R.string.log_block_started,
                pluralStringResource(R.plurals.calls_in_minutes, e.count ?: 0, e.count ?: 0, e.windowMinutes ?: 0),
                e.until?.let { formatTime(context, it) }.orEmpty(),
            )

        LogType.CALL_REJECTED -> stringResource(R.string.log_call_rejected)
        LogType.CALL_SILENCED -> stringResource(R.string.log_call_silenced)
        LogType.BLOCK_EXPIRED -> stringResource(R.string.log_block_expired)
        LogType.BLOCK_LIFTED -> stringResource(R.string.log_block_lifted)
    }
}
