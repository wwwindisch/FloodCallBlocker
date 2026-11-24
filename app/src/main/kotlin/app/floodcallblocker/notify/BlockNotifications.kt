package app.floodcallblocker.notify

import app.floodcallblocker.data.BlockEntity

/** Abstraction over the notification of a running block (replaceable in tests). */
interface BlockNotifications {
    /** Shows or refreshes the notification; [alert] allows the style's heads-up/popup behaviour. */
    suspend fun show(
        entryId: Long,
        title: String,
        block: BlockEntity,
        alert: Boolean,
    )

    fun cancel(entryId: Long)
}
