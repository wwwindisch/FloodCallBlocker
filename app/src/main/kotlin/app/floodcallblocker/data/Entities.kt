package app.floodcallblocker.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

enum class EntryType { NUMBER, CONTACT }

enum class LogType { BLOCK_STARTED, CALL_REJECTED, CALL_SILENCED, BLOCK_EXPIRED, BLOCK_LIFTED }

@Entity(tableName = "entries")
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: EntryType,
    /** Empty for number entries without a name. */
    val displayName: String,
    val contactLookupKey: String? = null,
    val contactMissing: Boolean = false,
    val allowedCalls: Int,
    val windowMinutes: Int,
    val blockDurationMinutes: Int,
    /** Name of [app.floodcallblocker.core.BlockAction]. */
    val action: String,
    val enabled: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "entry_numbers",
    foreignKeys = [
        ForeignKey(
            entity = EntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    // F-05: a normalised number may only occur in one entry.
    indices = [Index("entryId"), Index(value = ["e164"], unique = true)],
)
data class EntryNumberEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    val e164: String,
    val rawInput: String,
)

@Entity(
    tableName = "call_attempts",
    foreignKeys = [
        ForeignKey(
            entity = EntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("entryId")],
)
data class CallAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    val timestamp: Long,
)

@Entity(
    tableName = "blocks",
    foreignKeys = [
        ForeignKey(
            entity = EntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class BlockEntity(
    @PrimaryKey val entryId: Long,
    val startedAt: Long,
    val until: Long,
    val triggerCount: Int,
    /** Window of the rule at the time the block started (for display). */
    val windowMinutes: Int,
)

@Entity(tableName = "log_events", indices = [Index("timestamp")])
data class LogEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Not a foreign key: log lines outlive deleted entries. */
    val entryId: Long?,
    val entryNameSnapshot: String,
    val type: LogType,
    val timestamp: Long,
    val count: Int? = null,
    val windowMinutes: Int? = null,
    val until: Long? = null,
)

data class EntryWithNumbers(
    @Embedded val entry: EntryEntity,
    @Relation(parentColumn = "id", entityColumn = "entryId") val numbers: List<EntryNumberEntity>,
)
