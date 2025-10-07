package com.example.myandroidapp.security

import android.content.Context

/**
 * Repository responsible for persisting exchange API credentials inside encrypted storage.
 */
class ExchangeKeyRepository private constructor(
    private val securePreferencesManager: SecurePreferencesManager
) {

    fun saveExchangeKeys(exchangeId: String, apiKey: String, apiSecret: String) {
        val trimmedId = exchangeId.trim()
        require(trimmedId.isNotEmpty()) { "Exchange identifier must not be blank." }

        val existing = securePreferencesManager.getStringSet(EXCHANGES_KEY).toMutableSet()
        existing.add(trimmedId)
        securePreferencesManager.putStringSet(EXCHANGES_KEY, existing)
        securePreferencesManager.putString(apiKeyKey(trimmedId), apiKey)
        securePreferencesManager.putString(apiSecretKey(trimmedId), apiSecret)
    }

    fun getExchangeKeys(exchangeId: String): ExchangeKeys? {
        val apiKey = securePreferencesManager.getString(apiKeyKey(exchangeId)) ?: return null
        val apiSecret = securePreferencesManager.getString(apiSecretKey(exchangeId)) ?: return null
        return ExchangeKeys(apiKey, apiSecret)
    }

    fun clearExchangeKeys(exchangeId: String) {
        val exchanges = securePreferencesManager.getStringSet(EXCHANGES_KEY).toMutableSet()
        exchanges.remove(exchangeId)
        securePreferencesManager.putStringSet(EXCHANGES_KEY, exchanges)
        securePreferencesManager.remove(apiKeyKey(exchangeId))
        securePreferencesManager.remove(apiSecretKey(exchangeId))
    }

    fun getStoredExchanges(): Set<String> = securePreferencesManager.getStringSet(EXCHANGES_KEY)

    data class ExchangeKeys(val apiKey: String, val apiSecret: String)

    companion object {
        private const val EXCHANGES_KEY = "stored_exchanges"

        fun getInstance(context: Context): ExchangeKeyRepository {
            val securePrefs = SecurePreferencesManager.getInstance(context)
            return ExchangeKeyRepository(securePrefs)
        }

        private fun apiKeyKey(exchangeId: String) = "${exchangeId}_api_key"
        private fun apiSecretKey(exchangeId: String) = "${exchangeId}_api_secret"
    }
}
