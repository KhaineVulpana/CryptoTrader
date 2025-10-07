package com.example.myandroidapp.security

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Centralised access point for encrypted shared preferences backed by the Android Keystore.
 *
 * A one-time migration copies all entries from the legacy shared preferences into the
 * encrypted storage to guarantee that previously persisted values remain available after the
 * upgrade.
 */
class SecurePreferencesManager private constructor(appContext: Context) {

    private val masterKey: MasterKey = MasterKey.Builder(appContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        appContext,
        SECURE_PREF_FILE,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val legacyPreferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(appContext)

    init {
        migrateLegacyPreferencesIfNeeded()
    }

    private fun migrateLegacyPreferencesIfNeeded() {
        if (encryptedPreferences.getBoolean(MIGRATION_COMPLETED_KEY, false)) {
            return
        }

        val legacyEntries = legacyPreferences.all
        if (legacyEntries.isEmpty()) {
            encryptedPreferences.edit().putBoolean(MIGRATION_COMPLETED_KEY, true).apply()
            return
        }

        val secureEditor = encryptedPreferences.edit()
        legacyEntries.forEach { (key, value) ->
            when (value) {
                is String -> secureEditor.putString(key, value)
                is Int -> secureEditor.putInt(key, value)
                is Boolean -> secureEditor.putBoolean(key, value)
                is Float -> secureEditor.putFloat(key, value)
                is Long -> secureEditor.putLong(key, value)
                is Set<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    val castValue = value as? Set<String>
                    if (castValue != null) {
                        secureEditor.putStringSet(key, castValue)
                    }
                }
            }
        }
        secureEditor.putBoolean(MIGRATION_COMPLETED_KEY, true)
        secureEditor.apply()

        // Wipe the legacy preferences once migration has succeeded.
        legacyPreferences.edit().clear().apply()
    }

    fun putString(key: String, value: String) {
        encryptedPreferences.edit().putString(key, value).apply()
    }

    fun getString(key: String): String? = encryptedPreferences.getString(key, null)

    fun putBoolean(key: String, value: Boolean) {
        encryptedPreferences.edit().putBoolean(key, value).apply()
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean =
        encryptedPreferences.getBoolean(key, defaultValue)

    fun putStringSet(key: String, values: Set<String>) {
        encryptedPreferences.edit().putStringSet(key, values).apply()
    }

    fun getStringSet(key: String, defaultValue: Set<String> = emptySet()): Set<String> =
        encryptedPreferences.getStringSet(key, defaultValue) ?: defaultValue

    fun remove(key: String) {
        encryptedPreferences.edit().remove(key).apply()
    }

    companion object {
        private const val SECURE_PREF_FILE = "secure_prefs"
        private const val MIGRATION_COMPLETED_KEY = "secure_prefs_migrated"

        @Volatile
        private var instance: SecurePreferencesManager? = null

        fun getInstance(context: Context): SecurePreferencesManager {
            return instance ?: synchronized(this) {
                instance ?: SecurePreferencesManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
