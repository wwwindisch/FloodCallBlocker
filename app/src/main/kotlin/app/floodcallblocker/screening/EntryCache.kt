package app.floodcallblocker.screening

import app.floodcallblocker.data.AppDao
import app.floodcallblocker.data.EntryWithNumbers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * In-memory index "E.164 number → active entry" so that screening does not have to scan the
 * database (SPEC F-25). Kept current by collecting Room's invalidation flow.
 */
class EntryCache(
    private val dao: AppDao,
) {
    @Volatile private var index: Map<String, EntryWithNumbers>? = null

    fun start(scope: CoroutineScope) {
        scope.launch { dao.observeEntries().collect { index = build(it) } }
    }

    /** Returns the enabled entry owning [e164], or null. */
    suspend fun lookup(e164: String): EntryWithNumbers? {
        val current = index ?: build(dao.getAllEntries()).also { index = it }
        return current[e164]
    }

    private fun build(entries: List<EntryWithNumbers>): Map<String, EntryWithNumbers> =
        buildMap {
            entries.filter { it.entry.enabled }.forEach { e -> e.numbers.forEach { put(it.e164, e) } }
        }
}
