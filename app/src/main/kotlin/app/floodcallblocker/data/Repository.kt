package app.floodcallblocker.data

import androidx.room.withTransaction
import app.floodcallblocker.core.BlockAction
import app.floodcallblocker.core.CallEngine
import app.floodcallblocker.core.EntryState
import app.floodcallblocker.core.Rule
import app.floodcallblocker.core.Verdict
import app.floodcallblocker.notify.BlockNotifications
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** An entry with its running block (if any). */
data class EntryItem(
    val data: EntryWithNumbers,
    val block: BlockEntity?,
) {
    val entry get() = data.entry
    val title get() = data.title()
}

/** What the user edits; [id] is null for new entries. */
data class EntryDraft(
    val id: Long? = null,
    val type: EntryType = EntryType.NUMBER,
    val displayName: String = "",
    val contactLookupKey: String? = null,
    /** Pairs of (E.164, raw input). */
    val numbers: List<Pair<String, String>> = emptyList(),
    val rule: Rule = Rule(),
    val enabled: Boolean = true,
)

sealed interface SaveResult {
    data class Saved(
        val id: Long,
    ) : SaveResult

    /** [e164] is already used by the entry named [entryName]. */
    data class Duplicate(
        val e164: String,
        val entryName: String,
    ) : SaveResult
}

/** Result of a single screening decision. */
data class ScreeningResult(
    val verdict: Verdict,
    val action: BlockAction,
)

class Repository(
    private val db: AppDatabase,
    private val notifications: BlockNotifications,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.dao()
    private val screenLock = Mutex()

    // ---------------------------------------------------------------- queries

    fun observeItems(): Flow<List<EntryItem>> =
        combine(dao.observeEntries(), dao.observeBlocks()) { entries, blocks ->
            val byEntry = blocks.associateBy { it.entryId }
            entries.map { EntryItem(it, byEntry[it.entry.id]) }
        }

    fun observeItem(id: Long): Flow<EntryItem?> =
        combine(dao.observeEntry(id), dao.observeBlock(id)) { entry, block -> entry?.let { EntryItem(it, block) } }

    suspend fun getEntry(id: Long): EntryWithNumbers? = dao.getEntry(id)

    suspend fun getBlockUntil(id: Long): Long? = dao.getBlock(id)?.until

    suspend fun getAllEntries(): List<EntryWithNumbers> = dao.getAllEntries()

    fun observeLog(): Flow<List<LogEventEntity>> = dao.observeLog()

    suspend fun getLog(): List<LogEventEntity> = dao.getLog()

    // ---------------------------------------------------------------- editing

    suspend fun save(draft: EntryDraft): SaveResult {
        val now = clock()
        val rule = draft.rule.coerced()
        var endedBlockFor: Long? = null
        val result =
            db.withTransaction {
                val e164s = draft.numbers.map { it.first }
                val clash = dao.findNumbers(e164s).firstOrNull { it.entryId != draft.id }
                if (clash != null) {
                    val owner = dao.getEntry(clash.entryId)
                    return@withTransaction SaveResult.Duplicate(clash.e164, owner?.title().orEmpty())
                }
                val id: Long
                if (draft.id == null) {
                    id = dao.insertEntry(draft.toEntity(rule, createdAt = now, updatedAt = now))
                } else {
                    id = draft.id
                    val old = dao.getEntry(id)?.entry ?: return@withTransaction SaveResult.Saved(id)
                    dao.updateEntry(
                        draft
                            .toEntity(rule, createdAt = old.createdAt, updatedAt = now)
                            .copy(id = id, contactMissing = old.contactMissing && draft.type == EntryType.CONTACT),
                    )
                    dao.deleteNumbers(id)
                    // F-04 / section 8: switching off or to "always blocked" ends a running block at once.
                    val endsBlock = !draft.enabled || rule.alwaysBlocked
                    if (endsBlock && dao.getBlock(id) != null) {
                        dao.deleteBlock(id)
                        endedBlockFor = id
                        logEvent(id, nameOf(draft), LogType.BLOCK_LIFTED, now)
                    }
                    // 0 → ≥ 1 (or paused → active): counting starts afresh.
                    if ((old.allowedCalls == 0 && rule.allowedCalls > 0) || !draft.enabled) dao.deleteCalls(id)
                }
                dao.insertNumbers(draft.numbers.map { (e164, raw) -> EntryNumberEntity(entryId = id, e164 = e164, rawInput = raw) })
                SaveResult.Saved(id)
            }
        endedBlockFor?.let(notifications::cancel)
        return result
    }

    /** Deletes an entry; returns a snapshot for "undo". */
    suspend fun delete(id: Long): EntryWithNumbers? {
        val snapshot = dao.getEntry(id) ?: return null
        dao.deleteEntry(id)
        notifications.cancel(id)
        return snapshot
    }

    /** Re-inserts a snapshot from [delete] (blocks and counted calls are not restored). */
    suspend fun restore(snapshot: EntryWithNumbers) {
        db.withTransaction {
            if (dao.getEntry(snapshot.entry.id) != null) return@withTransaction
            if (dao.findNumbers(snapshot.numbers.map { it.e164 }).isNotEmpty()) return@withTransaction
            dao.insertEntry(snapshot.entry)
            dao.insertNumbers(snapshot.numbers)
        }
    }

    suspend fun setEnabled(
        id: Long,
        enabled: Boolean,
    ) {
        val current = dao.getEntry(id) ?: return
        save(
            EntryDraft(
                id = id,
                type = current.entry.type,
                displayName = current.entry.displayName,
                contactLookupKey = current.entry.contactLookupKey,
                numbers = current.numbers.map { it.e164 to it.rawInput },
                rule = current.entry.rule(),
                enabled = enabled,
            ),
        )
    }

    /** Replaces name and numbers of a contact entry after an address-book change. */
    suspend fun syncContact(
        id: Long,
        name: String?,
        e164s: List<Pair<String, String>>?,
        missing: Boolean,
    ) {
        db.withTransaction {
            val current = dao.getEntry(id) ?: return@withTransaction
            dao.updateEntry(
                current.entry.copy(
                    displayName = name ?: current.entry.displayName,
                    contactMissing = missing,
                    updatedAt = clock(),
                ),
            )
            if (e164s != null) {
                val clashes =
                    dao
                        .findNumbers(e164s.map { it.first })
                        .filter { it.entryId != id }
                        .map { it.e164 }
                        .toSet()
                val usable = e164s.filter { it.first !in clashes }.distinctBy { it.first }
                if (usable.isNotEmpty()) {
                    dao.deleteNumbers(id)
                    dao.insertNumbers(usable.map { (e164, raw) -> EntryNumberEntity(entryId = id, e164 = e164, rawInput = raw) })
                }
            }
        }
    }

    // ---------------------------------------------------------------- blocks

    /** Ends a running block immediately ("Sperre aufheben"). */
    suspend fun liftBlock(id: Long) {
        val entry = dao.getEntry(id)
        db.withTransaction {
            if (dao.getBlock(id) != null) {
                dao.deleteBlock(id)
                logEvent(id, entry?.title().orEmpty(), LogType.BLOCK_LIFTED, clock())
            }
        }
        notifications.cancel(id)
    }

    /**
     * Housekeeping: ends expired blocks (and logs them), optionally re-posts notifications of
     * running blocks, and deletes stale data (SPEC F-42, section 8).
     */
    suspend fun maintenance(restoreNotifications: Boolean = false) {
        val now = clock()
        for (block in dao.getBlocks()) {
            val entry = dao.getEntry(block.entryId)
            if (block.until <= now) {
                db.withTransaction {
                    dao.deleteBlock(block.entryId)
                    logEvent(block.entryId, entry?.title().orEmpty(), LogType.BLOCK_EXPIRED, block.until)
                }
                notifications.cancel(block.entryId)
            } else if (restoreNotifications && entry != null) {
                notifications.show(block.entryId, entry.title(), block, alert = false)
            }
        }
        dao.deleteLogOlderThan(now - LOG_RETENTION_MILLIS)
        dao.deleteAllCallsUpTo(now - Rule.MAX_WINDOW_MINUTES * Rule.MINUTE_MILLIS)
    }

    /** Re-posts the notifications of all running blocks, e.g. after the style setting changed (F-34). */
    suspend fun refreshNotifications() {
        val now = clock()
        for (block in dao.getBlocks()) {
            val entry = dao.getEntry(block.entryId) ?: continue
            if (block.until > now) notifications.show(block.entryId, entry.title(), block, alert = false)
        }
    }

    // ---------------------------------------------------------------- screening

    /**
     * Applies the rule engine to one incoming call of [entry] and persists the outcome (F-22).
     * Serialised, so simultaneous calls (call waiting) are counted independently.
     */
    suspend fun screen(entry: EntryWithNumbers): ScreeningResult =
        screenLock.withLock {
            val now = clock()
            val e = entry.entry
            val rule = e.rule()
            val title = entry.title()
            val result =
                db.withTransaction {
                    var block = dao.getBlock(e.id)
                    if (block != null && block.until <= now) {
                        dao.deleteBlock(e.id)
                        logEvent(e.id, title, LogType.BLOCK_EXPIRED, block.until)
                        notifications.cancel(e.id)
                        block = null
                    }
                    val state = EntryState(block?.until, dao.getCallTimes(e.id))
                    val evaluation = CallEngine.evaluate(rule, state, now)
                    when (evaluation.verdict) {
                        Verdict.ALLOW -> {
                            dao.deleteCallsUpTo(e.id, now - rule.windowMillis)
                            dao.insertCall(CallAttemptEntity(entryId = e.id, timestamp = now))
                        }

                        Verdict.BLOCK_ALWAYS -> {
                            if (block != null) dao.deleteBlock(e.id)
                            logBlockedCall(e.id, title, rule.action, now)
                        }

                        Verdict.BLOCK_ACTIVE -> logBlockedCall(e.id, title, rule.action, now)

                        Verdict.BLOCK_TRIGGERED -> {
                            dao.deleteCalls(e.id)
                            val started =
                                BlockEntity(
                                    entryId = e.id,
                                    startedAt = now,
                                    until = checkNotNull(evaluation.newState.blockedUntil),
                                    triggerCount = evaluation.triggerCount,
                                    windowMinutes = rule.windowMinutes,
                                )
                            dao.upsertBlock(started)
                            logEvent(
                                e.id,
                                title,
                                LogType.BLOCK_STARTED,
                                now,
                                count = evaluation.triggerCount,
                                windowMinutes = rule.windowMinutes,
                                until = started.until,
                            )
                            logBlockedCall(e.id, title, rule.action, now)
                            block = started
                        }
                    }
                    evaluation.verdict to block
                }
            if (result.first == Verdict.BLOCK_TRIGGERED) {
                result.second?.let { notifications.show(e.id, title, it, alert = true) }
            }
            ScreeningResult(result.first, rule.action)
        }

    // ---------------------------------------------------------------- log

    suspend fun clearLog() = dao.clearLog()

    private suspend fun logBlockedCall(
        entryId: Long,
        name: String,
        action: BlockAction,
        now: Long,
    ) {
        val type = if (action == BlockAction.SILENCE) LogType.CALL_SILENCED else LogType.CALL_REJECTED
        logEvent(entryId, name, type, now)
    }

    private suspend fun logEvent(
        entryId: Long?,
        name: String,
        type: LogType,
        timestamp: Long,
        count: Int? = null,
        windowMinutes: Int? = null,
        until: Long? = null,
    ) = dao.insertLog(
        LogEventEntity(
            entryId = entryId,
            entryNameSnapshot = name,
            type = type,
            timestamp = timestamp,
            count = count,
            windowMinutes = windowMinutes,
            until = until,
        ),
    )

    private fun nameOf(draft: EntryDraft): String =
        draft.displayName.ifBlank {
            draft.numbers
                .firstOrNull()
                ?.first
                ?.let(::formatNumber) ?: "?"
        }

    private fun EntryDraft.toEntity(
        rule: Rule,
        createdAt: Long,
        updatedAt: Long,
    ) = EntryEntity(
        type = type,
        displayName = displayName.trim(),
        contactLookupKey = contactLookupKey,
        allowedCalls = rule.allowedCalls,
        windowMinutes = rule.windowMinutes,
        blockDurationMinutes = rule.blockDurationMinutes,
        action = rule.action.name,
        enabled = enabled,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    companion object {
        const val LOG_RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000
    }
}
