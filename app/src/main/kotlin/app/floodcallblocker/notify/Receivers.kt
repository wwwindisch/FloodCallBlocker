package app.floodcallblocker.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.floodcallblocker.FloodApp
import app.floodcallblocker.ui.MainActivity
import kotlinx.coroutines.launch

/** "Lift block" action of the notification. */
class UnblockReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_LIFT) return
        val id = intent.getLongExtra(MainActivity.EXTRA_ENTRY_ID, -1)
        if (id < 0) return
        val app = context.applicationContext as FloodApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                app.container.repository.liftBlock(id)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_LIFT = "app.floodcallblocker.action.LIFT_BLOCK"
    }
}

/** Restores notifications of running blocks after reboot or app update (F-32). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = context.applicationContext as FloodApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                app.container.repository.maintenance(restoreNotifications = true)
            } finally {
                pending.finish()
            }
        }
    }
}
