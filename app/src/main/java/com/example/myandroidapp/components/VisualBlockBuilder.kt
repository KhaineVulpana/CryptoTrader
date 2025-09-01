package com.example.myandroidapp.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.myandroidapp.shared.AutomationFilter

@Composable
fun VisualBlockBuilder(
    filters: SnapshotStateList<AutomationFilter>,
    onAdd: () -> Unit
) {
    Column {
        filters.forEachIndexed { i, filter ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = filter.metric,
                    onValueChange = { filters[i] = filter.copy(metric = it) },
                    label = { Text("Metric") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = filter.operator,
                    onValueChange = { filters[i] = filter.copy(operator = it) },
                    label = { Text("Operator") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = filter.value,
                    onValueChange = { filters[i] = filter.copy(value = it) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("Value") },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        Button(onClick = onAdd, modifier = Modifier.align(Alignment.End)) {
            Text("+ Add Condition")
        }
    }
}
