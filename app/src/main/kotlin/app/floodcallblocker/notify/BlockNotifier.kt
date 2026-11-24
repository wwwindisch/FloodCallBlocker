package app.floodcallblocker.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.floodcallblocker.R
import app.floodcallblocker.data.BlockEntity
import app.floodcallblocker.data.NotificationStyle
import app.floodcallblocker.data.SettingsStore
import app.floodcallblocker.ui.MainActivity
import java.util.Date

/** Posts the ongoing "blocked until …" notification (SPEC F-30 – F-34). */
class BlockNotifier(
    private val context: Context,
    private val settings: SettingsStore,
) : BlockNotifications {
    private val manager = NotificationManagerCompat.from(context)

    /** Creates both channels once; never deleted afterwards (see SPEC F-34). */
    fun ensureChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_QUIET, context.getString(R.string.channel_quiet), NotificationManager.IMPORTANCE_LOW)
                .apply { description = context.getString(R.string.channel_quiet_desc) },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_POPUP, context.getString(R.string.channel_popup), NotificationManager.IMPORTANCE_HIGH)
                .apply {
                    description = context.getString(R.string.channel_popup_desc)
                    setSound(null, null)
                    enableVibration(false)
                },
        )
    }

    override suspend fun show(
        entryId: Long,
        title: String,
        block: BlockEntity,
        alert: Boolean,
    ) {
        val style = settings.current().notificationStyle
        if (style == NotificationStyle.OFF) {
            cancel(entryId)
            return
        }
        post(entryId, title, block, style, alert)
    }

    /** Re-posts a notification silently, e.g. after a reboot or a style change. */
    fun post(
        entryId: Long,
        title: String,
        block: BlockEntity,
        style: NotificationStyle,
        alert: Boolean,
    ) {
        if (style == NotificationStyle.OFF) return
        val now = System.currentTimeMillis()
        if (block.until <= now) return
        if (!canPost()) return
        ensureChannels()

        val channel = if (style == NotificationStyle.POPUP) CHANNEL_POPUP else CHANNEL_QUIET
        val time = DateFormat.getTimeFormat(context).format(Date(block.until))
        val text =
            context.getString(
                R.string.notification_text,
                time,
                context.resources.getQuantityString(
                    R.plurals.calls_in_minutes,
                    block.triggerCount,
                    block.triggerCount,
                    block.windowMinutes,
                ),
            )

        val open =
            PendingIntent.getActivity(
                context,
                entryId.toInt(),
                Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_ENTRY_ID, entryId)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val lift =
            PendingIntent.getBroadcast(
                context,
                entryId.toInt(),
                Intent(context, UnblockReceiver::class.java)
                    .setAction(UnblockReceiver.ACTION_LIFT)
                    .putExtra(MainActivity.EXTRA_ENTRY_ID, entryId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        val notification =
            NotificationCompat
                .Builder(context, channel)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(!alert)
                .setShowWhen(true)
                .setWhen(block.until)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setTimeoutAfter(block.until - now)
                .setContentIntent(open)
                .addAction(0, context.getString(R.string.action_lift_block), lift)
                .build()
        try {
            manager.notify(notificationId(entryId), notification)
        } catch (_: SecurityException) {
            // Permission revoked between check and post: the block works without a notification (F-33).
        }
    }

    override fun cancel(entryId: Long) {
        manager.cancel(notificationId(entryId))
    }

    /** `areNotificationsEnabled` also covers a missing POST_NOTIFICATIONS grant on Android 13+. */
    private fun canPost(): Boolean = manager.areNotificationsEnabled()

    /** Importance of the channel for [style] as currently set in Android, or null for [NotificationStyle.OFF]. */
    fun channelImportance(style: NotificationStyle): Int? {
        val id = channelId(style) ?: return null
        ensureChannels()
        return context.getSystemService(NotificationManager::class.java).getNotificationChannel(id)?.importance
    }

    /** Intent that opens Android's settings for the channel used by [style]. */
    fun channelSettingsIntent(style: NotificationStyle): Intent =
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, channelId(style) ?: CHANNEL_QUIET)

    private fun channelId(style: NotificationStyle) =
        when (style) {
            NotificationStyle.QUIET -> CHANNEL_QUIET
            NotificationStyle.POPUP -> CHANNEL_POPUP
            NotificationStyle.OFF -> null
        }

    companion object {
        const val CHANNEL_QUIET = "blocks_quiet"
        const val CHANNEL_POPUP = "blocks_popup"

        fun notificationId(entryId: Long): Int = (entryId and 0x7FFFFFF).toInt() + 1000
    }
}
