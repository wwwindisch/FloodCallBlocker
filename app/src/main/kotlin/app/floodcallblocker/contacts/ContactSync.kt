package app.floodcallblocker.contacts

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import app.floodcallblocker.core.PhoneNormalizer
import app.floodcallblocker.data.EntryType
import app.floodcallblocker.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Keeps contact entries in line with the address book (SPEC F-02). */
class ContactSync(
    private val contacts: ContactsReader,
    private val repository: Repository,
    private val normalizer: () -> PhoneNormalizer,
) {
    suspend fun syncAll() {
        if (!contacts.hasPermission()) return
        val n = normalizer()
        for (data in repository.getAllEntries()) {
            val key = data.entry.contactLookupKey
            if (data.entry.type != EntryType.CONTACT || key == null) continue
            when (val state = contacts.read(key)) {
                is ContactState.Found ->
                    repository.syncContact(
                        id = data.entry.id,
                        name = state.info.name,
                        e164s = state.info.numbers.mapNotNull { raw -> n.normalize(raw)?.let { it to raw } },
                        missing = false,
                    )

                ContactState.Gone -> repository.syncContact(data.entry.id, null, null, missing = true)
                ContactState.Unavailable -> Unit
            }
        }
    }

    private var observer: ContentObserver? = null
    private var pending: Job? = null

    /** Starts observing the address book; no-op without permission or when already observing. */
    fun observe(
        app: android.content.Context,
        scope: CoroutineScope,
    ) {
        if (observer != null || !contacts.hasPermission()) return
        val o =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    pending?.cancel()
                    pending =
                        scope.launch {
                            delay(DEBOUNCE_MS)
                            syncAll()
                        }
                }
            }
        app.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, o)
        observer = o
    }

    private companion object {
        const val DEBOUNCE_MS = 1_000L
    }
}
