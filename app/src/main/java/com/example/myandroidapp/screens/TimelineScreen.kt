package com.example.myandroidapp.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myandroidapp.shared.SharedAppViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun TimelineScreen(viewModel: SharedAppViewModel) {
    val timeline by viewModel.timeline.collectAsStateWithLifecycle()
    val formatter = SimpleDateFormat("MMM d, HH:mm:ss", Locale.US)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (timeline.isEmpty()) {
            item { Text("Ledger is empty", style = MaterialTheme.typography.bodyMedium) }
        } else {
            items(timeline) { entry ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(entry.headline, style = MaterialTheme.typography.titleSmall)
                        Text(entry.detail, style = MaterialTheme.typography.bodySmall)
                        Text(formatter.format(entry.ts), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

