package app.floodcallblocker.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class NotificationStyle { QUIET, POPUP, OFF }

data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val notificationStyle: NotificationStyle = NotificationStyle.QUIET,
    val onboardingDone: Boolean = false,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(
    private val context: Context,
) {
    private val themeKey = stringPreferencesKey("theme_mode")
    private val dynamicKey = booleanPreferencesKey("dynamic_color")
    private val styleKey = stringPreferencesKey("notification_style")
    private val onboardingKey = booleanPreferencesKey("onboarding_done")

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): Settings = settings.first()

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[themeKey] = mode.name }

    suspend fun setDynamicColor(enabled: Boolean) = edit { it[dynamicKey] = enabled }

    suspend fun setNotificationStyle(style: NotificationStyle) = edit { it[styleKey] = style.name }

    suspend fun setOnboardingDone(done: Boolean) = edit { it[onboardingKey] = done }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }

    private fun Preferences.toSettings() =
        Settings(
            themeMode = this[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            dynamicColor = this[dynamicKey] ?: true,
            notificationStyle =
                this[styleKey]?.let { runCatching { NotificationStyle.valueOf(it) }.getOrNull() }
                    ?: NotificationStyle.QUIET,
            onboardingDone = this[onboardingKey] ?: false,
        )
}
