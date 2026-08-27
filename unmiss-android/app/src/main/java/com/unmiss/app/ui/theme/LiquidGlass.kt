package com.unmiss.app.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlin.math.pow

internal val LocalLiquidBackdrop = staticCompositionLocalOf<LayerBackdrop> {
    error("Liquid glass requires a LayerBackdrop")
}

val LocalLiquidGlassEnabled = staticCompositionLocalOf { true }
val LocalLiquidGlassIntensity = staticCompositionLocalOf { 1f }

enum class GlassMaterial { CLEAR, REGULAR, PROMINENT }

@Composable
fun LiquidGlassCanvas(
    enabled: Boolean = true,
    intensity: Float = 1f,
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
                drawRect(Color(0xFFF2F3F7))
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
    material: GlassMaterial = if (navigation) GlassMaterial.PROMINENT else if (panel) GlassMaterial.REGULAR else GlassMaterial.CLEAR,
    surfaceAlpha: Float? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = glassShape ?: RoundedCornerShape(cornerRadius.dp)
    val backdrop = LocalLiquidBackdrop.current
    val intensity = LocalLiquidGlassIntensity.current.coerceIn(0f, 1f)
    if (!LocalLiquidGlassEnabled.current || intensity <= 0f) {
        if (onClick != null) {
            Surface(
                modifier = modifier,
                shape = shape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                onClick = onClick,
            ) { Box(content = content) }
        } else {
            Surface(
                modifier = modifier,
                shape = shape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) { Box(content = content) }
        }
        return
    }
    val materialResponse = intensity.pow(0.82f)
    val blurRadius = when (material) {
        GlassMaterial.CLEAR -> 2f
        GlassMaterial.REGULAR -> 8f
        GlassMaterial.PROMINENT -> 8f
    }
    val refractionHeight = when (material) {
        GlassMaterial.CLEAR -> 12f
        GlassMaterial.REGULAR -> 18f
        GlassMaterial.PROMINENT -> 24f
    }
    val refractionAmount = when (material) {
        GlassMaterial.CLEAR -> 22f
        GlassMaterial.REGULAR -> 24f
        GlassMaterial.PROMINENT -> 24f
    }
    val resolvedSurfaceAlpha = surfaceAlpha ?: when (material) {
        GlassMaterial.CLEAR -> 0.18f
        GlassMaterial.REGULAR -> 0.32f
        GlassMaterial.PROMINENT -> 0.40f
    }
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
                        if (!navigation) vibrancy()
                        blur(blurRadius.dp.toPx() * materialResponse)
                        lens(
                            refractionHeight = refractionHeight.dp.toPx() * materialResponse,
                            refractionAmount = refractionAmount.dp.toPx() * materialResponse,
                            chromaticAberration = material == GlassMaterial.PROMINENT && intensity >= 0.65f,
                        )
                    }
                },
                highlight = if (enhanced) {
                    { Highlight.Default.copy(alpha = (if (material == GlassMaterial.CLEAR) 0.56f else 0.72f) * materialResponse) }
                } else null,
                shadow = if (enhanced) {
                  {
                    Shadow(radius = if (material == GlassMaterial.PROMINENT) 12.dp else 7.dp, color = Color.Black.copy(alpha = 0.08f * materialResponse))
                  }
                } else null,
                innerShadow = if (enhanced) {
                    { InnerShadow(radius = 7.dp, alpha = 0.34f * materialResponse) }
                } else null,
                onDrawSurface = {
                    drawRect(Color.White.copy(alpha = resolvedSurfaceAlpha.coerceIn(0f, 1f) * materialResponse))
                },
            )
            .background(
                color = Color.Transparent,
                shape = shape,
            )
            .border(
                width = 0.8.dp,
                brush = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.9f * materialResponse),
                        Color.White.copy(alpha = 0.3f * materialResponse),
                        Color.Black.copy(alpha = 0.08f * materialResponse),
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
    material: GlassMaterial = GlassMaterial.REGULAR,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    if (LocalLiquidGlassEnabled.current) {
        LiquidGlass(
            modifier = modifier,
            glassShape = shape,
            panel = true,
            material = material,
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
    backdrop: Backdrop = LocalLiquidBackdrop.current,
): Modifier {
    val intensity = LocalLiquidGlassIntensity.current.coerceIn(0f, 1f)
    val navigationResponse = if (intensity == 0f) 0f else 0.4f + intensity * 0.9f
    val shape = RoundedCornerShape(28.dp)
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            if (intensity > 0f) {
                blur((if (dragging) 7.dp else 12.dp).toPx() * navigationResponse)
                lens(
                    refractionHeight = (if (dragging) 13.dp else 9.dp).toPx() * navigationResponse,
                    refractionAmount = (if (dragging) 22.dp else 16.dp).toPx() * navigationResponse,
                    chromaticAberration = intensity >= 0.58f,
                )
            }
        },
        highlight = { Highlight.Default.copy(alpha = (if (dragging) 0.98f else 0.82f) * intensity) },
        shadow = {
            Shadow(
                radius = if (dragging) 12.dp else 7.dp,
                color = Color.Black.copy(alpha = (if (dragging) 0.15f else 0.10f) * intensity),
            )
        },
        innerShadow = {
            InnerShadow(radius = 7.dp, alpha = (if (dragging) 0.56f else 0.42f) * intensity)
        },
        onDrawSurface = {
            drawRect(Color.White.copy(alpha = 0.18f * intensity))
            drawRect(tint.copy(alpha = (if (dragging) 0.20f else 0.14f) * intensity))
        },
    ).border(
        width = 1.1.dp,
        color = Color.White.copy(alpha = 0.9f * intensity),
        shape = shape,
    )
}
