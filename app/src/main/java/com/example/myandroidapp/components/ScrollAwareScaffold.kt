package com.example.myandroidapp.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScrollAwareScaffold(
    searchBarContent: @Composable () -> Unit,
    topTabsContent: @Composable () -> Unit,
    content: LazyListScope.() -> Unit
) {
    val scrollState = rememberLazyListState()

    LazyColumn(state = scrollState, modifier = Modifier.fillMaxSize()) {
        item { topTabsContent() }
        stickyHeader { searchBarContent() }
        content()
    }
}
