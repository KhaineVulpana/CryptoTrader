package com.example.myandroidapp.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun PlaceholderTable(rows: Int = 10, columns: Int = 4, rowHeight: Dp = 24.dp) {
    Column(Modifier.fillMaxWidth()) {
        repeat(rows) { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                repeat(columns) { col ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(rowHeight)
                            .background(Color.LightGray)
                            .padding(4.dp)
                    ) {
                        Text("R$row C$col", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
