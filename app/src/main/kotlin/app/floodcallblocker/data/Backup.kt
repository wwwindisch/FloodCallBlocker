package app.floodcallblocker.data

import app.floodcallblocker.contacts.ContactsReader
import app.floodcallblocker.core.BlockAction
import app.floodcallblocker.core.PhoneNormalizer
import app.floodcallblocker.core.Rule
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BackupFile(
    val schemaVersion: Int = SCHEMA_VERSION,
    val exportedAt: Long,
    val entries: List<BackupEntry>,
    val log: List<BackupLog>? = null,
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

@Serializable
data class BackupEntry(
    val type: String,
    val displayName: String,
    val numbers: List<String>,
    val allowedCalls: Int,
    val windowMinutes: Int,
    val blockDurationMinutes: Int,
    val action: String,
    val enabled: Boolean,
)

@Serializable
data class BackupLog(
    val entryName: String,
    val type: String,
    val timestamp: Long,
    val count: Int? = null,
    val windowMinutes: Int? = null,
    val until: Long? = null,
)

/** Outcome of parsing a backup file, before anything is written. */
data class ImportPreview(
    val file: BackupFile,
    val newCount: Int,
    /** Names of imported entries that share a number with existing entries. */
    val conflicts: List<String>,
)

class BackupException(
    message: String,
) : Exception(message)

enum class ConflictStrategy { SKIP, OVERWRITE }

class BackupManager(
    private val repository: Repository,
    private val contacts: ContactsReader,
    private val normalizer: () -> PhoneNormalizer,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    suspend fun export(includeLog: Boolean): String {
        val entries =
            repository.getAllEntries().map {
                BackupEntry(
                    type = it.entry.type.name,
                    displayName = it.entry.displayName,
                    numbers = it.numbers.map { n -> n.e164 },
                    allowedCalls = it.entry.allowedCalls,
                    windowMinutes = it.entry.windowMinutes,
                    blockDurationMinutes = it.entry.blockDurationMinutes,
                    action = it.entry.action,
                    enabled = it.entry.enabled,
                )
            }
        val log =
            if (includeLog) {
                repository.getLog().map {
                    BackupLog(it.entryNameSnapshot, it.type.name, it.timestamp, it.count, it.windowMinutes, it.until)
                }
            } else {
                null
            }
        return json.encodeToString(BackupFile.serializer(), BackupFile(exportedAt = clock(), entries = entries, log = log))
    }

    suspend fun preview(text: String): ImportPreview {
        val file =
            try {
                json.decodeFromString(BackupFile.serializer(), text)
            } catch (e: Exception) {
                throw BackupException("Not a FloodCallBlocker backup: ${e.message}")
            }
        if (file.schemaVersion > BackupFile.SCHEMA_VERSION) {
            throw BackupException("Backup was created by a newer version (schema ${file.schemaVersion})")
        }
        val existing = repository.getAllEntries().flatMap { e -> e.numbers.map { it.e164 } }.toSet()
        val conflicts = file.entries.filter { e -> e.numbers.any { it in existing } }
        return ImportPreview(
            file,
            file.entries.size - conflicts.size,
            conflicts.map {
                it.displayName.ifBlank { it.numbers.firstOrNull().orEmpty() }
            },
        )
    }

    /** Writes the previewed entries; returns how many entries were imported. */
    suspend fun import(
        preview: ImportPreview,
        strategy: ConflictStrategy,
    ): Int {
        var imported = 0
        val n = normalizer()
        for (b in preview.file.entries) {
            val numbers = b.numbers.mapNotNull { raw -> n.normalize(raw)?.let { it to raw } }.distinctBy { it.first }
            if (numbers.isEmpty()) continue
            val owners = repository.getAllEntries().filter { e -> e.numbers.any { o -> numbers.any { it.first == o.e164 } } }
            if (owners.isNotEmpty()) {
                if (strategy == ConflictStrategy.SKIP) continue
                owners.forEach { repository.delete(it.entry.id) }
            }
            var type = runCatching { EntryType.valueOf(b.type) }.getOrDefault(EntryType.NUMBER)
            var lookupKey: String? = null
            if (type == EntryType.CONTACT) {
                // F-52: try to find the contact again; otherwise fall back to a number entry.
                val found = numbers.firstNotNullOfOrNull { contacts.findByNumber(it.first) }
                if (found != null) lookupKey = found.lookupKey else type = EntryType.NUMBER
            }
            val result =
                repository.save(
                    EntryDraft(
                        type = type,
                        displayName = b.displayName,
                        contactLookupKey = lookupKey,
                        numbers = numbers,
                        rule =
                            Rule(
                                b.allowedCalls,
                                b.windowMinutes,
                                b.blockDurationMinutes,
                                runCatching { BlockAction.valueOf(b.action) }.getOrDefault(BlockAction.REJECT),
                            ).coerced(),
                        enabled = b.enabled,
                    ),
                )
            if (result is SaveResult.Saved) imported++
        }
        return imported
    }
}
