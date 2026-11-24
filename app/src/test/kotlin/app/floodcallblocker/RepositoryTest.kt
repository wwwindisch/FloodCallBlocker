package app.floodcallblocker

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.floodcallblocker.core.BlockAction
import app.floodcallblocker.core.Rule
import app.floodcallblocker.core.Verdict
import app.floodcallblocker.data.AppDatabase
import app.floodcallblocker.data.BlockEntity
import app.floodcallblocker.data.EntryDraft
import app.floodcallblocker.data.LogType
import app.floodcallblocker.data.Repository
import app.floodcallblocker.data.SaveResult
import app.floodcallblocker.notify.BlockNotifications
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class RepositoryTest {
    private class FakeNotifications : BlockNotifications {
        val shown = mutableListOf<Long>()
        val cancelled = mutableListOf<Long>()

        override suspend fun show(
            entryId: Long,
            title: String,
            block: BlockEntity,
            alert: Boolean,
        ) {
            shown += entryId
        }

        override fun cancel(entryId: Long) {
            cancelled += entryId
        }
    }

    private val minute = 60_000L
    private var now = 0L
    private lateinit var db: AppDatabase
    private lateinit var notes: FakeNotifications
    private lateinit var repo: Repository

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        notes = FakeNotifications()
        now = 10 * 60 * minute // 10:00
        repo = Repository(db, notes) { now }
    }

    @After
    fun tearDown() = db.close()

    private fun draft(
        vararg numbers: String,
        rule: Rule = Rule(),
        name: String = "Max Mustermann",
    ) = EntryDraft(displayName = name, numbers = numbers.map { it to it }, rule = rule)

    private fun create(rule: Rule = Rule()): Long = runBlocking { (repo.save(draft("+4930123456", rule = rule)) as SaveResult.Saved).id }

    private fun callAt(
        minutesAfterTen: Int,
        id: Long,
    ): Verdict =
        runBlocking {
            now = (10 * 60 + minutesAfterTen) * minute
            repo.screen(repo.getEntry(id)!!).verdict
        }

    @Test
    fun `spec table end to end`() {
        val id = create()
        assertEquals(Verdict.ALLOW, callAt(0, id))
        assertEquals(Verdict.ALLOW, callAt(3, id))
        assertEquals(Verdict.ALLOW, callAt(9, id))
        assertEquals(Verdict.ALLOW, callAt(10, id))
        assertEquals(Verdict.BLOCK_TRIGGERED, callAt(12, id))
        assertEquals(listOf(id), notes.shown)
        val block = runBlocking { repo.getBlockUntil(id) }
        assertEquals((10 * 60 + 12 + 180) * minute, block)
        assertEquals(Verdict.BLOCK_ACTIVE, callAt(90, id))
        assertEquals(block, runBlocking { repo.getBlockUntil(id) })
        assertEquals(Verdict.ALLOW, callAt(195, id))
        // Expired block was removed and logged.
        assertNull(runBlocking { repo.getBlockUntil(id) })
        val types = runBlocking { repo.getLog() }.map { it.type }
        assertTrue(LogType.BLOCK_STARTED in types && LogType.BLOCK_EXPIRED in types)
        assertEquals(2, types.count { it == LogType.CALL_REJECTED })
    }

    @Test
    fun `silence action is logged as silenced`() {
        val id = create(Rule(allowedCalls = 0, action = BlockAction.SILENCE))
        assertEquals(Verdict.BLOCK_ALWAYS, callAt(0, id))
        assertEquals(LogType.CALL_SILENCED, runBlocking { repo.getLog() }.first().type)
        assertTrue(notes.shown.isEmpty())
    }

    @Test
    fun `same number cannot be in two entries`() {
        create()
        val result = runBlocking { repo.save(draft("+4930123456", name = "Other")) }
        assertTrue(result is SaveResult.Duplicate)
        assertEquals("Max Mustermann", (result as SaveResult.Duplicate).entryName)
    }

    @Test
    fun `lifting a block ends it and cancels the notification`() {
        val id = create(Rule(allowedCalls = 1))
        callAt(0, id)
        assertEquals(Verdict.BLOCK_TRIGGERED, callAt(1, id))
        runBlocking { repo.liftBlock(id) }
        assertNull(runBlocking { repo.getBlockUntil(id) })
        assertTrue(id in notes.cancelled)
        assertEquals(Verdict.ALLOW, callAt(2, id))
    }

    @Test
    fun `setting N to zero ends running block, 0 to N resets counting`() {
        val id = create(Rule(allowedCalls = 1))
        callAt(0, id)
        callAt(1, id)
        val entry = runBlocking { repo.getEntry(id)!! }
        runBlocking {
            repo.save(draft("+4930123456", rule = Rule(allowedCalls = 0)).copy(id = id))
        }
        assertNull(runBlocking { repo.getBlockUntil(id) })
        assertTrue(id in notes.cancelled)
        runBlocking { repo.save(draft("+4930123456", rule = Rule(allowedCalls = 1)).copy(id = id)) }
        assertEquals(Verdict.ALLOW, callAt(2, id))
        assertNotNull(entry)
    }

    @Test
    fun `disabling ends block and deleting snapshot can be restored`() {
        val id = create(Rule(allowedCalls = 1))
        callAt(0, id)
        callAt(1, id)
        runBlocking { repo.setEnabled(id, false) }
        assertNull(runBlocking { repo.getBlockUntil(id) })
        val snapshot = runBlocking { repo.delete(id) }!!
        assertNull(runBlocking { repo.getEntry(id) })
        runBlocking { repo.restore(snapshot) }
        assertNotNull(runBlocking { repo.getEntry(id) })
    }

    @Test
    fun `maintenance expires overdue blocks and prunes old log`() {
        val id = create(Rule(allowedCalls = 1, blockDurationMinutes = 10))
        callAt(0, id)
        callAt(1, id)
        now += 60 * minute
        runBlocking { repo.maintenance() }
        assertNull(runBlocking { repo.getBlockUntil(id) })
        now += 31L * 24 * 60 * minute
        runBlocking { repo.maintenance() }
        assertTrue(runBlocking { repo.getLog() }.isEmpty())
    }
}
