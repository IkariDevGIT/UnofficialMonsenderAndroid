package io.github.ikaridevgit.unofficialmonsender.ui.filters

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import io.github.ikaridevgit.unofficialmonsender.ui.components.MonPageTitle
import io.github.ikaridevgit.unofficialmonsender.ui.components.MonTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterScaffold(title: String, onBack: () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { MonTopBar(title = { MonPageTitle(title) }, scrollBehavior = scrollBehavior, onBack = onBack) },
        content = content,
    )
}
