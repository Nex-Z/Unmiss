package com.unmiss.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import com.unmiss.app.ui.theme.LocalLiquidOverlay

@Composable
fun LiquidFilterSheet(
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val overlay = LocalLiquidOverlay.current
    DisposableEffect(overlay) {
        overlay.showSheet(onDismissRequest = onDismissRequest) {
            Column(modifier = Modifier.fillMaxWidth(), content = content)
        }
        onDispose { overlay.clear() }
    }
}
