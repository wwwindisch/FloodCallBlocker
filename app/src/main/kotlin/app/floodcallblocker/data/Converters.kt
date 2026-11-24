package app.floodcallblocker.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter fun entryTypeToString(v: EntryType): String = v.name

    @TypeConverter fun stringToEntryType(v: String): EntryType = EntryType.valueOf(v)

    @TypeConverter fun logTypeToString(v: LogType): String = v.name

    @TypeConverter fun stringToLogType(v: String): LogType = LogType.valueOf(v)
}
