package app.floodcallblocker.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    // --- entries ---
    @Insert suspend fun insertEntry(entry: EntryEntity): Long

    @Update suspend fun updateEntry(entry: EntryEntity)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    @Transaction
    @Query("SELECT * FROM entries ORDER BY createdAt DESC, id DESC")
    fun observeEntries(): Flow<List<EntryWithNumbers>>

    @Transaction
    @Query("SELECT * FROM entries")
    suspend fun getAllEntries(): List<EntryWithNumbers>

    @Transaction
    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun getEntry(id: Long): EntryWithNumbers?

    @Transaction
    @Query("SELECT * FROM entries WHERE id = :id")
    fun observeEntry(id: Long): Flow<EntryWithNumbers?>

    // --- numbers ---
    @Insert suspend fun insertNumbers(numbers: List<EntryNumberEntity>)

    @Query("DELETE FROM entry_numbers WHERE entryId = :entryId")
    suspend fun deleteNumbers(entryId: Long)

    @Query("SELECT * FROM entry_numbers WHERE e164 IN (:e164s)")
    suspend fun findNumbers(e164s: List<String>): List<EntryNumberEntity>

    // --- calls ---
    @Insert suspend fun insertCall(call: CallAttemptEntity)

    @Query("SELECT timestamp FROM call_attempts WHERE entryId = :entryId")
    suspend fun getCallTimes(entryId: Long): List<Long>

    @Query("DELETE FROM call_attempts WHERE entryId = :entryId")
    suspend fun deleteCalls(entryId: Long)

    @Query("DELETE FROM call_attempts WHERE entryId = :entryId AND timestamp <= :cutoff")
    suspend fun deleteCallsUpTo(
        entryId: Long,
        cutoff: Long,
    )

    @Query("DELETE FROM call_attempts WHERE timestamp <= :cutoff")
    suspend fun deleteAllCallsUpTo(cutoff: Long)

    // --- blocks ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBlock(block: BlockEntity)

    @Query("SELECT * FROM blocks WHERE entryId = :entryId")
    suspend fun getBlock(entryId: Long): BlockEntity?

    @Query("SELECT * FROM blocks")
    suspend fun getBlocks(): List<BlockEntity>

    @Query("SELECT * FROM blocks")
    fun observeBlocks(): Flow<List<BlockEntity>>

    @Query("SELECT * FROM blocks WHERE entryId = :entryId")
    fun observeBlock(entryId: Long): Flow<BlockEntity?>

    @Query("DELETE FROM blocks WHERE entryId = :entryId")
    suspend fun deleteBlock(entryId: Long)

    // --- log ---
    @Insert suspend fun insertLog(event: LogEventEntity)

    @Query("SELECT * FROM log_events ORDER BY timestamp DESC, id DESC")
    fun observeLog(): Flow<List<LogEventEntity>>

    @Query("SELECT * FROM log_events ORDER BY timestamp DESC, id DESC")
    suspend fun getLog(): List<LogEventEntity>

    @Query("DELETE FROM log_events WHERE timestamp < :cutoff")
    suspend fun deleteLogOlderThan(cutoff: Long)

    @Query("DELETE FROM log_events")
    suspend fun clearLog()
}

@Database(
    entities = [
        EntryEntity::class,
        EntryNumberEntity::class,
        CallAttemptEntity::class,
        BlockEntity::class,
        LogEventEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@androidx.room.TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "floodcallblocker.db").build()
    }
}
