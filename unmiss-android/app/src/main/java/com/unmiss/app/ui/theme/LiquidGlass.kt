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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

internal val LocalLiquidBackdrop = staticCompositionLocalOf<LayerBackdrop> {
    error("Liquid glass requires a LayerBackdrop")
}

val LocalLiquidGlassEnabled = staticCompositionLocalOf { true }
val LocalLiquidGlassIntensity = staticCompositionLocalOf { 0.65f }

@Composable
fun LiquidGlassCanvas(
    enabled: Boolean = true,
    intensity: Float = 0.65f,
    content: @Composable BoxScope.() -> Unit,
) {
    val backdrop = rememberLayerBackdrop()

    CompositionLocalProvider(
        LocalLiquidBackdrop provides backdrop,
        LocalLiquidGlassEnabled provides enabled,
        LocalLiquidGlassIntensity provides intensity.coerceIn(0f, 1f),
    ) {
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
                if (enabled) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color(0x287E8CFF), Color(0x007E8CFF)),
                            center = Offset(size.width * 0.48f, size.height * 0.38f),
                            radius = size.minDimension * 0.42f,
                        ),
                        radius = size.minDimension * 0.42f,
                        center = Offset(size.width * 0.48f, size.height * 0.38f),
                    )
                }
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
    glassShape: Shape? = null,
    panel: Boolean = false,
    navigation: Boolean = false,
    surfaceAlpha: Float = 0.52f,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = glassShape ?: RoundedCornerShape(cornerRadius.dp)
    val backdrop = LocalLiquidBackdrop.current
    val intensity = LocalLiquidGlassIntensity.current.coerceIn(0f, 1f)
    val enhanced = LocalLiquidGlassEnabled.current && intensity > 0f
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
                    if (intensity > 0f) {
                        vibrancy()
                        blur((if (navigation) 4.dp else if (panel) 10.dp else 8.dp).toPx() * intensity)
                        lens(
                            refractionHeight = (if (navigation) 13.dp else if (panel) 7.dp else 18.dp).toPx() * intensity,
                            refractionAmount = (if (navigation) 20.dp else if (panel) 10.dp else 24.dp).toPx() * intensity,
                            chromaticAberration = !panel && intensity >= 0.35f,
                        )
                    }
                },
                highlight = if (enhanced) {
                    { Highlight.Default.copy(alpha = 0.52f * intensity) }
                } else null,
                shadow = if (enhanced) {
                  {
                    Shadow(radius = 10.dp, color = Color.Black.copy(alpha = 0.07f * intensity))
                  }
                } else null,
                innerShadow = if (enhanced) {
                    { InnerShadow(radius = 7.dp, alpha = 0.3f * intensity) }
                } else null,
                onDrawSurface = {
                    drawRect(Color.White.copy(alpha = surfaceAlpha.coerceIn(0f, 1f) * intensity))
                },
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.96f * intensity),
                        Color.White.copy(alpha = 0.42f * intensity),
                        Color(0xFF5C84B7).copy(alpha = 0.16f * intensity),
                    ),
                ),
                shape = shape,
            )
            .then(clickable),
        content = content,
    )
}

@Composable
fun AdaptiveGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    color: Color = Color.White.copy(alpha = 0.86f),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    if (LocalLiquidGlassEnabled.current) {
        LiquidGlass(
            modifier = modifier,
            glassShape = shape,
            panel = true,
            onClick = onClick,
            content = content,
        )
    } else {
        if (onClick != null) {
            Surface(
                modifier = modifier,
                shape = shape,
                color = color,
                onClick = onClick,
            ) { Box(content = content) }
        } else {
            Surface(
                modifier = modifier,
                shape = shape,
                color = color,
            ) { Box(content = content) }
        }
    }
}

@Composable
fun Modifier.liquidNavigationIndicator(
    tint: Color,
    dragging: Boolean,
): Modifier {
    val backdrop = LocalLiquidBackdrop.current
    val intensity = LocalLiquidGlassIntensity.current.coerceIn(0f, 1f)
    val shape = RoundedCornerShape(28.dp)
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            if (intensity > 0f) {
                vibrancy()
                blur((if (dragging) 3.dp else 5.dp).toPx() * intensity)
                lens(
                    refractionHeight = (if (dragging) 18.dp else 12.dp).toPx() * intensity,
                    refractionAmount = (if (dragging) 28.dp else 18.dp).toPx() * intensity,
                    chromaticAberration = intensity >= 0.35f,
                )
            }
        },
        highlight = { Highlight.Default.copy(alpha = (if (dragging) 0.9f else 0.68f) * intensity) },
        shadow = {
            Shadow(
                radius = if (dragging) 12.dp else 7.dp,
                color = Color.Black.copy(alpha = (if (dragging) 0.12f else 0.07f) * intensity),
            )
        },
        innerShadow = {
            InnerShadow(radius = 6.dp, alpha = (if (dragging) 0.48f else 0.32f) * intensity)
        },
        onDrawSurface = {
            drawRect(Color.White.copy(alpha = 0.18f * intensity))
            drawRect(tint.copy(alpha = (if (dragging) 0.16f else 0.11f) * intensity))
        },
    ).border(
        width = 0.8.dp,
        color = Color.White.copy(alpha = 0.7f * intensity),
        shape = shape,
    )
}
