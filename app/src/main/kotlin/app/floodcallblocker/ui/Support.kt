package app.floodcallblocker.ui

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import app.floodcallblocker.AppContainer
import app.floodcallblocker.FloodApp
import app.floodcallblocker.R
import kotlinx.coroutines.delay
import java.util.Date

@Composable
fun rememberContainer(): AppContainer = (LocalContext.current.applicationContext as FloodApp).container

/** Creates a ViewModel that needs the [AppContainer]. */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = rememberContainer()
    return viewModel(
        key = key,
        factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = create(container) as T
            },
    )
}

/** Current time, refreshed every [intervalMs] so that countdown-like states stay correct. */
@Composable
fun rememberNow(intervalMs: Long = 15_000L): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(intervalMs) {
        while (true) {
            delay(intervalMs)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/** "3 h 30 min", "5 min", "2 d" … from a number of minutes. */
fun formatDuration(
    context: Context,
    totalMinutes: Int,
): String {
    val days = totalMinutes / (24 * 60)
    val hours = totalMinutes / 60 % 24
    val minutes = totalMinutes % 60
    return buildList {
        if (days > 0) add(context.getString(R.string.duration_d, days))
        if (hours > 0) add(context.getString(R.string.duration_h, hours))
        if (minutes > 0 || isEmpty()) add(context.getString(R.string.duration_min, minutes))
    }.joinToString(" ")
}

fun formatTime(
    context: Context,
    millis: Long,
): String = DateFormat.getTimeFormat(context).format(Date(millis))

fun formatDay(
    context: Context,
    millis: Long,
): String = DateFormat.getMediumDateFormat(context).format(Date(millis))

/** Renders `**bold**` markers of a string resource as bold text. */
fun boldMarkdown(text: String): AnnotatedString =
    buildAnnotatedString {
        text.split("**").forEachIndexed { i, part ->
            if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) } else append(part)
        }
    }
