package com.example.myandroidapp.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myandroidapp.shared.PlannerState
import com.kevin.cryptotrader.contracts.PlanSafetyCheck
import com.kevin.cryptotrader.contracts.TransferPlan
import com.kevin.cryptotrader.contracts.TransferStep
import java.util.Locale

@Composable
fun BuyAnywherePlanner(
    state: PlannerState,
    modifier: Modifier = Modifier,
    onUpdateInputs: (String, Double) -> Unit,
    onGeneratePlan: () -> Unit,
    onOpenFunding: () -> Unit,
) {
    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Buy Anywhere Planner", style = MaterialTheme.typography.titleMedium)

        var assetInput by remember(state.asset) { mutableStateOf(state.asset) }
        var amountInput by remember(state.amount) { mutableStateOf(state.amount.toString()) }

        OutlinedTextField(
            value = assetInput,
            onValueChange = {
                assetInput = it.uppercase()
                assetInput.takeIf { it.isNotBlank() }?.let { symbol ->
                    amountInput.toDoubleOrNull()?.let { qty -> onUpdateInputs(symbol, qty) }
                }
            },
            label = { Text("Asset") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        OutlinedTextField(
            value = amountInput,
            onValueChange = {
                amountInput = it
                it.toDoubleOrNull()?.let { qty ->
                    onUpdateInputs(assetInput.uppercase(), qty)
                }
            },
            label = { Text("Amount") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onGeneratePlan, enabled = !state.isLoading) {
                Text("Generate plan")
            }
            TextButton(onClick = onOpenFunding) { Text("Open funding flow") }
        }

        when {
            state.isLoading -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text("Calculating optimal route...")
                }
            }
            state.error != null -> {
                Text(state.error, color = MaterialTheme.colorScheme.error)
            }
            state.plan != null -> {
                PlannerResult(plan = state.plan)
            }
            else -> {
                Text("Enter an asset and amount to preview a funding plan.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun PlannerResult(plan: TransferPlan) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Plan ${plan.id}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
        Text(
            "Total cost ${plan.totalCostUsd.formatUsd()} (fees ${plan.costBreakdown.totalFeesUsd.formatUsd()})",
            style = MaterialTheme.typography.bodyMedium,
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(plan.steps) { step ->
                when (step) {
                    is TransferStep.BuyOnExchange -> StepRow("Buy", "${step.qty} ${step.symbol} on ${step.exchangeId.uppercase()}")
                    is TransferStep.Withdraw -> StepRow(
                        "Withdraw",
                        "${step.amount} ${step.asset} via ${step.network} to ${step.address.takeLast(6)}",
                    )
                    is TransferStep.OnChainSwap -> StepRow(
                        "Swap",
                        "${step.amount} ${step.fromAsset}→${step.toAsset} on ${step.aggregator}",
                    )
                    is TransferStep.TransferInternal -> StepRow(
                        "Internal",
                        "${step.amount} ${step.asset} ${step.accountFrom}→${step.accountTo}",
                    )
                    is TransferStep.WalletTransfer -> StepRow(
                        "Wallet",
                        "${step.amount} ${step.asset} using ${step.connector}",
                    )
                }
            }
        }

        if (plan.safetyChecks.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Safety checks", style = MaterialTheme.typography.titleSmall)
                plan.safetyChecks.forEach { check -> SafetyRow(check) }
            }
        }
    }
}

@Composable
private fun StepRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SafetyRow(check: PlanSafetyCheck) {
    val color = when (check.status) {
        com.kevin.cryptotrader.contracts.SafetyStatus.PASSED -> MaterialTheme.colorScheme.primary
        com.kevin.cryptotrader.contracts.SafetyStatus.WARNING -> MaterialTheme.colorScheme.tertiary
        com.kevin.cryptotrader.contracts.SafetyStatus.PENDING -> MaterialTheme.colorScheme.secondary
    }
    Text("${check.description} (${check.status})", color = color, style = MaterialTheme.typography.bodySmall)
}

private fun Double.formatUsd(): String = "$" + String.format(Locale.US, "%,.2f", this)

