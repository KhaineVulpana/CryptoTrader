package com.example.myandroidapp.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.myandroidapp.security.BiometricAuthenticator
import com.example.myandroidapp.security.ExchangeKeyRepository
import com.example.myandroidapp.security.SecurePreferencesManager

@Composable
fun SettingsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val fragmentActivity = remember(context) {
        requireNotNull(context as? FragmentActivity) {
            "SettingsScreen requires to be hosted in a FragmentActivity context"
        }
    }
    val authenticator = remember(fragmentActivity) { BiometricAuthenticator(fragmentActivity) }
    val repository = remember(context) { ExchangeKeyRepository.getInstance(context) }
    // Ensure encrypted preferences are eagerly initialised so migration from legacy storage
    // happens even if the user navigates directly to settings.
    remember(context) { SecurePreferencesManager.getInstance(context) }

    var exchangeId by rememberSaveable { mutableStateOf("") }
    var apiKey by rememberSaveable { mutableStateOf("") }
    var apiSecret by rememberSaveable { mutableStateOf("") }
    var statusMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var storedExchanges by remember { mutableStateOf(repository.getStoredExchanges().sorted()) }
    var revealedExchange by remember { mutableStateOf<String?>(null) }
    var revealedKeys by remember { mutableStateOf<ExchangeKeyRepository.ExchangeKeys?>(null) }

    // Refresh stored exchanges whenever the repository content changes externally.
    LaunchedEffect(Unit) {
        storedExchanges = repository.getStoredExchanges().sorted()
    }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Compliance & Disclaimers",
            style = MaterialTheme.typography.titleLarge
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("\u2022 CryptoTrader provides tooling for account management and automation only; it does not deliver investment or trading advice.")
            Text("\u2022 API keys remain encrypted on-device using the Android Keystore. Keys are never transmitted to external services without your explicit action.")
            Text("\u2022 You are responsible for configuring exchange permissions and revoking credentials if your device is lost or compromised.")
        }

        Text(
            text = "Data Retention",
            style = MaterialTheme.typography.titleMedium
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("\u2022 Trading history, automation rules, and ledger snapshots are stored locally within the app's encrypted databases.")
            Text("\u2022 You can clear stored exchange credentials per exchange using the controls below; this immediately removes keys from encrypted storage.")
            Text("\u2022 Diagnostic analytics are not collected. Logs remain on-device and are cleared when you uninstall the application.")
        }

        Divider()

        Text(
            text = "Secure Exchange Keys",
            style = MaterialTheme.typography.titleMedium
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = exchangeId,
                onValueChange = { exchangeId = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Exchange identifier") },
                singleLine = true
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API key") },
                singleLine = true
            )
            OutlinedTextField(
                value = apiSecret,
                onValueChange = { apiSecret = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API secret") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            Button(
                onClick = {
                    runCatching {
                        repository.saveExchangeKeys(exchangeId, apiKey, apiSecret)
                    }.onSuccess {
                        statusMessage = "Credentials saved for ${exchangeId.trim()}"
                        storedExchanges = repository.getStoredExchanges().sorted()
                        revealedExchange = null
                        revealedKeys = null
                        exchangeId = ""
                        apiKey = ""
                        apiSecret = ""
                    }.onFailure {
                        statusMessage = it.message ?: "Unable to store credentials"
                    }
                },
                enabled = exchangeId.isNotBlank() && apiKey.isNotBlank() && apiSecret.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Store securely")
            }
        }

        if (storedExchanges.isNotEmpty()) {
            Divider()
            Text(
                text = "Stored exchanges",
                style = MaterialTheme.typography.titleMedium
            )
            storedExchanges.forEach { storedExchange ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(storedExchange, style = MaterialTheme.typography.bodyLarge)
                    if (revealedExchange == storedExchange && revealedKeys != null) {
                        Text("API key: ${revealedKeys!!.apiKey}")
                        Text("API secret: ${revealedKeys!!.apiSecret}")
                    } else {
                        Text("Credentials protected. Authenticate to reveal.")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            authenticator.authenticate(
                                title = "Authenticate to reveal keys",
                                subtitle = storedExchange,
                                description = "Biometric authentication is required before viewing API credentials.",
                                onSuccess = {
                                    val keys = repository.getExchangeKeys(storedExchange)
                                    if (keys != null) {
                                        revealedExchange = storedExchange
                                        revealedKeys = keys
                                        statusMessage = null
                                    } else {
                                        statusMessage = "No credentials found for $storedExchange"
                                    }
                                },
                                onError = { error ->
                                    statusMessage = error
                                }
                            )
                        }) {
                            Text("Reveal")
                        }
                        OutlinedButton(onClick = {
                            repository.clearExchangeKeys(storedExchange)
                            storedExchanges = repository.getStoredExchanges().sorted()
                            if (revealedExchange == storedExchange) {
                                revealedExchange = null
                                revealedKeys = null
                            }
                            statusMessage = "Removed credentials for $storedExchange"
                        }) {
                            Text("Remove")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        statusMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.primary)
        }

        Divider()
        TextButton(onClick = onNavigateBack, modifier = Modifier.fillMaxWidth()) {
            Text("Close settings")
        }
    }
}
