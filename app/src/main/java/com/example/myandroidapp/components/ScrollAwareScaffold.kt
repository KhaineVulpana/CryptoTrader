package com.example.myandroidapp.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ScrollAwareScaffold(
    searchBarContent: @Composable () -> Unit,
    topTabsContent: @Composable () -> Unit,
    content: @Composable (LazyListState) -> Unit
) {
    val scrollState = rememberLazyListState()
    val showSearchBar = remember { derivedStateOf { scrollState.firstVisibleItemScrollOffset <= 10 } }

    Column(modifier = Modifier.fillMaxSize()) {
        if (showSearchBar.value) {
            Column {
                topTabsContent()
                searchBarContent()
            }
        } else {
            searchBarContent()
        }
        Spacer(Modifier.height(8.dp))
        content(scrollState)
    }
}
