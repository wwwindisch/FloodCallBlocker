package app.floodcallblocker

import android.app.Application
import android.content.Context
import android.telephony.TelephonyManager
import app.floodcallblocker.contacts.ContactSync
import app.floodcallblocker.contacts.ContactsReader
import app.floodcallblocker.core.PhoneNormalizer
import app.floodcallblocker.data.AppDatabase
import app.floodcallblocker.data.BackupManager
import app.floodcallblocker.data.Repository
import app.floodcallblocker.data.SettingsStore
import app.floodcallblocker.notify.BlockNotifier
import app.floodcallblocker.screening.EntryCache
import app.floodcallblocker.screening.Screener
import app.floodcallblocker.work.CleanupWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale

/** Manual dependency container (lighter than Hilt for an app this size). */
class AppContainer(
    context: Context,
) {
    val settings = SettingsStore(context)
    val notifier = BlockNotifier(context, settings)
    val database = AppDatabase.create(context)
    val repository = Repository(database, notifier)
    val contacts = ContactsReader(context)
    val cache = EntryCache(database.dao())

    /** F-11: SIM country → system country → DE. */
    val normalizer: () -> PhoneNormalizer = {
        val tm = context.getSystemService(TelephonyManager::class.java)
        val region =
            tm?.simCountryIso?.takeIf { it.length == 2 }
                ?: Locale.getDefault().country.takeIf { it.length == 2 }
                ?: "DE"
        PhoneNormalizer(region)
    }
    val screener = Screener(cache, repository, normalizer)
    val contactSync = ContactSync(contacts, repository, normalizer)

    /** Last deleted entry, so the overview can offer "undo" after the edit screen closed. */
    val deletedEntry = kotlinx.coroutines.flow.MutableStateFlow<app.floodcallblocker.data.EntryWithNumbers?>(null)
    val backup = BackupManager(repository, contacts, normalizer)
}

class FloodApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifier.ensureChannels()
        container.cache.start(appScope)
        CleanupWorker.schedule(this)
        appScope.launch {
            container.repository.maintenance()
            container.contactSync.syncAll()
        }
        container.contactSync.observe(this, appScope)
    }
}
