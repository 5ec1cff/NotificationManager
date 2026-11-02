@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.a13e300.notificationmanager

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarScrollBehavior
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberSearchBarScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun rememberFiltersScrollBehavior(listState: LazyListState, headerIndex: Int): SearchBarScrollBehavior {
    val scrollState = rememberSearchBarScrollState()
    val pinned = remember(listState, headerIndex) {
        derivedStateOf {
            val layout = listState.layoutInfo
            layout.visibleItemsInfo.any { it.index == headerIndex && it.offset <= layout.viewportStartOffset }
        }
    }
    LaunchedEffect(pinned.value) {
        if (!pinned.value) {
            scrollState.scrollOffset = 0f
            scrollState.contentOffset = 0f
        }
    }
    return SearchBarDefaults.enterAlwaysSearchBarScrollBehavior(
        scrollState = scrollState,
        // Let the intro scroll away before the filters consume scroll events.
        canScroll = { pinned.value },
    )
}

@Composable
internal fun CollapsingSearchHeader(
    scrollBehavior: SearchBarScrollBehavior,
    search: @Composable () -> Unit,
    filters: @Composable () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
        Column {
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp)) { search() }
            Box(scrollBehavior.searchBarScrollBehaviorModifier.fillMaxWidth().padding(top = 2.dp, bottom = 8.dp)) {
                filters()
            }
        }
    }
}
