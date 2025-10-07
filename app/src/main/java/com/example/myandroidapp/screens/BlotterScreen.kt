package com.example.myandroidapp.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myandroidapp.components.BlotterGrid
import com.example.myandroidapp.shared.SharedAppViewModel

@Composable
fun BlotterScreen(viewModel: SharedAppViewModel) {
    val rows by viewModel.blotter.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Blotter", style = MaterialTheme.typography.titleLarge)
        BlotterGrid(rows = rows)
    }
}

