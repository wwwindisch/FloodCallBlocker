package app.floodcallblocker.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

data class ContactInfo(
    val lookupKey: String,
    val name: String,
    val numbers: List<String>,
)

sealed interface ContactState {
    data class Found(
        val info: ContactInfo,
    ) : ContactState

    /** Contact no longer exists in the address book. */
    data object Gone : ContactState

    /** Permission missing: nothing can be said. */
    data object Unavailable : ContactState
}

class ContactsReader(
    private val context: Context,
) {
    private val resolver get() = context.contentResolver

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Reads the contact the user picked with the system picker. */
    fun readPicked(uri: Uri): ContactInfo? {
        if (!hasPermission()) return null
        val key =
            resolver.query(uri, arrayOf(ContactsContract.Contacts.LOOKUP_KEY), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            } ?: return null
        return (read(key) as? ContactState.Found)?.info
    }

    /** Current state of the contact identified by [lookupKey]. */
    fun read(lookupKey: String): ContactState {
        if (!hasPermission()) return ContactState.Unavailable
        // lookupContact follows merges/re-created contacts via the lookup key.
        val contactUri =
            ContactsContract.Contacts.lookupContact(
                resolver,
                Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, Uri.encode(lookupKey)),
            ) ?: return ContactState.Gone
        var contactId: Long = -1
        var name = ""
        var currentKey = lookupKey
        resolver
            .query(
                contactUri,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                    ContactsContract.Contacts.LOOKUP_KEY,
                ),
                null,
                null,
                null,
            )?.use {
                if (it.moveToFirst()) {
                    contactId = it.getLong(0)
                    name = it.getString(1).orEmpty()
                    currentKey = it.getString(2) ?: lookupKey
                }
            }
        if (contactId < 0) return ContactState.Gone
        val numbers = mutableListOf<String>()
        resolver
            .query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                arrayOf(contactId.toString()),
                null,
            )?.use {
                while (it.moveToNext()) it.getString(0)?.let(numbers::add)
            }
        return ContactState.Found(ContactInfo(currentKey, name, numbers.distinct()))
    }

    /** Tries to find a contact by phone number (used when importing a backup, F-52). */
    fun findByNumber(number: String): ContactInfo? {
        if (!hasPermission()) return null
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        val key =
            resolver.query(uri, arrayOf(ContactsContract.PhoneLookup.LOOKUP_KEY), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            } ?: return null
        return (read(key) as? ContactState.Found)?.info
    }
}
