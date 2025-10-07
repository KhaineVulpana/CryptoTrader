package com.example.myandroidapp.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ComplianceOnboardingDialog(onAcknowledge: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {
            Button(onClick = onAcknowledge) {
                Text("I understand")
            }
        },
        title = { Text("Before you continue", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text("CryptoTrader automates trading workflows but does not provide financial or investment advice.")
                Spacer(Modifier.height(8.dp))
                Text("Exchange API credentials are encrypted using the Android Keystore and never transmitted without your consent. Keep read-only permissions where possible and revoke keys you no longer use.")
                Spacer(Modifier.height(8.dp))
                Text("Trading history, automation rules, and logs remain on your device. Clearing app data or uninstalling the application removes this information.")
                Spacer(Modifier.height(8.dp))
                Text("By continuing you acknowledge responsibility for complying with exchange terms, regional regulations, and safeguarding your credentials.")
            }
        }
    )
}
