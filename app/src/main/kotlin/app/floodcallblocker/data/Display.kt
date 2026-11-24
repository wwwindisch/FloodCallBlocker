package app.floodcallblocker.data

import app.floodcallblocker.core.BlockAction
import app.floodcallblocker.core.PhoneNormalizer
import app.floodcallblocker.core.Rule

private val formatter = PhoneNormalizer()

/** Human-readable form of an E.164 number; falls back to the input. */
fun formatNumber(e164: String): String = formatter.parse(e164)?.formatted ?: e164

/** Name to show for an entry: its display name, else the formatted first number. */
fun EntryWithNumbers.title(): String = entry.displayName.ifBlank { numbers.firstOrNull()?.let { formatNumber(it.e164) } ?: "?" }

fun EntryEntity.rule(): Rule =
    Rule(
        allowedCalls = allowedCalls,
        windowMinutes = windowMinutes,
        blockDurationMinutes = blockDurationMinutes,
        action = runCatching { BlockAction.valueOf(action) }.getOrDefault(BlockAction.REJECT),
    )
