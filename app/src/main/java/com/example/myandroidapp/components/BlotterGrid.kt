package com.example.myandroidapp.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myandroidapp.shared.BlotterRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BlotterGrid(rows: List<BlotterRow>, modifier: Modifier = Modifier) {
    if (rows.isEmpty()) {
        Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
            Text("No orders or fills yet", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    val formatter = rememberTimestampFormatter()

    Column(modifier = modifier) {
        Surface(
            tonalElevation = 4.dp,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                HeaderCell("Time")
                HeaderCell("Account")
                HeaderCell("Symbol")
                HeaderCell("Side")
                HeaderCell("Qty")
                HeaderCell("Price")
                HeaderCell("Status")
            }
        }

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(rows) { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Cell(formatter(row.ts))
                    Cell(row.accountId)
                    Cell(row.symbol)
                    val sideColor = if (row.side.equals("BUY", true)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    Cell(row.side.uppercase(), color = sideColor)
                    Cell(String.format(Locale.US, "%.2f", row.qty))
                    Cell(row.price?.let { String.format(Locale.US, "%.2f", it) } ?: "-")
                    Cell(row.status)
                }
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
}

@Composable
private fun Cell(text: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
}

@Composable
private fun rememberTimestampFormatter(): (Long) -> String {
    val formatter = SimpleDateFormat("HH:mm:ss", Locale.US)
    return { ts -> formatter.format(Date(ts)) }
}

