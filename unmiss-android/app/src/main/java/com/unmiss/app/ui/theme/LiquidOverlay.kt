package com.unmiss.app.ui.theme

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop

internal data class LiquidOverlayEntry(
    val onDismiss: () -> Unit,
    val content: @Composable (() -> Unit) -> Unit,
)

@Stable
class LiquidOverlayController {
    private var entry by mutableStateOf<LiquidOverlayEntry?>(null)

    internal val current: LiquidOverlayEntry?
        get() = entry

    fun showSheet(
        onDismissRequest: () -> Unit,
        content: @Composable (dismiss: () -> Unit) -> Unit,
    ) {
        entry = LiquidOverlayEntry(onDismissRequest, content)
    }

    fun dismiss() {
        val callback = entry?.onDismiss
        entry = null
        callback?.invoke()
    }

    internal fun clear() {
        entry = null
    }
}

val LocalLiquidOverlay = staticCompositionLocalOf<LiquidOverlayController> {
    error("LiquidOverlayHost is missing")
}

@Composable
fun LiquidOverlayHost(
    backdrop: LayerBackdrop,
    controller: LiquidOverlayController = remember { LiquidOverlayController() },
    content: @Composable BoxScope.() -> Unit,
) {
    val entry = controller.current
    BackHandler(enabled = entry != null) { controller.dismiss() }
    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalLiquidOverlay provides controller,
            content = { content() },
        )
        AnimatedVisibility(
            visible = entry != null,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier.fillMaxSize().clickable(
                    indication = null,
                    interactionSource = null,
                    onClick = controller::dismiss,
                ),
            ) {
                var dragOffset by remember(entry) { mutableFloatStateOf(0f) }
                AnimatedVisibility(
                    visible = entry != null,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it },
                ) {
                    androidx.compose.runtime.CompositionLocalProvider(LocalLiquidBackdrop provides backdrop) {
                        LiquidGlass(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                .graphicsLayer { translationY = dragOffset }
                                .pointerInput(entry) {
                                    detectVerticalDragGestures(
                                        onVerticalDrag = { _, amount -> dragOffset = (dragOffset + amount).coerceAtLeast(0f) },
                                        onDragEnd = {
                                            if (dragOffset > 120.dp.toPx()) controller.dismiss() else dragOffset = 0f
                                        },
                                        onDragCancel = { dragOffset = 0f },
                                    )
                                }
                                .clickable(
                                    indication = null,
                                    interactionSource = null,
                                    onClick = {},
                                ),
                            glassShape = RoundedCornerShape(30.dp),
                            material = GlassMaterial.REGULAR,
                            surfaceAlpha = 0.34f,
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                entry?.content?.invoke(controller::dismiss)
                            }
                        }
                    }
                }
            }
        }
    }
}
