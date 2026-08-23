package com.unmiss.app.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

private val LocalLiquidBackdrop = staticCompositionLocalOf<LayerBackdrop> {
    error("Liquid glass requires a LayerBackdrop")
}

@Composable
fun LiquidGlassCanvas(content: @Composable BoxScope.() -> Unit) {
    val backdrop = rememberLayerBackdrop()

    CompositionLocalProvider(LocalLiquidBackdrop provides backdrop) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop),
            ) {
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color(0xFFF8F9FC),
                            Color(0xFFF2F4F8),
                            Color(0xFFF6F7FA),
                        ),
                    ),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0x3873B8FF), Color(0x0073B8FF)),
                        center = Offset(size.width * 1.02f, size.height * 0.06f),
                        radius = size.minDimension * 0.64f,
                    ),
                    radius = size.minDimension * 0.64f,
                    center = Offset(size.width * 1.02f, size.height * 0.06f),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0x2E7CDCC8), Color(0x007CDCC8)),
                        center = Offset(size.width * -0.12f, size.height * 0.72f),
                        radius = size.minDimension * 0.52f,
                    ),
                    radius = size.minDimension * 0.52f,
                    center = Offset(size.width * -0.12f, size.height * 0.72f),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0x24FFB38A), Color(0x00FFB38A)),
                        center = Offset(size.width * 0.90f, size.height * 0.96f),
                        radius = size.minDimension * 0.38f,
                    ),
                    radius = size.minDimension * 0.38f,
                    center = Offset(size.width * 0.90f, size.height * 0.96f),
                )
            }
            content()
        }
    }
}

@Composable
fun LiquidGlass(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 28,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius.dp)
    val backdrop = LocalLiquidBackdrop.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = spring(stiffness = 650f, dampingRatio = 0.72f),
        label = "glass press",
    )
    val clickable = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick,
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(8.dp.toPx())
                    lens(
                        refractionHeight = 18.dp.toPx(),
                        refractionAmount = 24.dp.toPx(),
                        chromaticAberration = true,
                    )
                },
                onDrawSurface = {
                    drawRect(Color.White.copy(alpha = 0.52f))
                },
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.96f),
                        Color.White.copy(alpha = 0.42f),
                        Color(0xFF5C84B7).copy(alpha = 0.16f),
                    ),
                ),
                shape = shape,
            )
            .then(clickable),
        content = content,
    )
}
