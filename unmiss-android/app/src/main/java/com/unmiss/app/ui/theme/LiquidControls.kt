package com.unmiss.app.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.launch

enum class GlassButtonStyle { PRIMARY, SECONDARY, TONAL, DANGER }

@Composable
fun AdaptiveLiquidButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: GlassButtonStyle = GlassButtonStyle.PRIMARY,
    content: @Composable RowScope.() -> Unit,
) {
    if (!LocalLiquidGlassEnabled.current) {
        when (style) {
            GlassButtonStyle.PRIMARY -> Button(onClick, modifier, enabled, content = content)
            GlassButtonStyle.SECONDARY -> OutlinedButton(onClick, modifier, enabled, content = content)
            GlassButtonStyle.TONAL -> FilledTonalButton(onClick, modifier, enabled, content = content)
            GlassButtonStyle.DANGER -> OutlinedButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                content = content,
            )
        }
        return
    }

    val backdrop = LocalLiquidBackdrop.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.94f else 1f,
        spring(dampingRatio = 0.58f, stiffness = 640f),
        label = "liquid button press",
    )
    val primary = MaterialTheme.colorScheme.primary
    val error = MaterialTheme.colorScheme.error
    val contentColor = when (style) {
        GlassButtonStyle.PRIMARY -> Color.White
        GlassButtonStyle.SECONDARY -> primary
        GlassButtonStyle.TONAL -> MaterialTheme.colorScheme.onPrimaryContainer
        GlassButtonStyle.DANGER -> error
    }

    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Row(
            modifier = modifier
                .defaultMinSize(minHeight = 48.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = if (enabled) 1f else 0.46f
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(24.dp) },
                    effects = {
                        vibrancy()
                        blur((if (pressed) 2.dp else 5.dp).toPx())
                        lens(
                            (if (pressed) 18.dp else 11.dp).toPx(),
                            (if (pressed) 26.dp else 17.dp).toPx(),
                            chromaticAberration = pressed,
                        )
                    },
                    highlight = { Highlight.Default.copy(alpha = if (pressed) 0.9f else 0.58f) },
                    shadow = { Shadow(radius = 7.dp, color = Color.Black.copy(alpha = 0.08f)) },
                    innerShadow = { InnerShadow(radius = 5.dp, alpha = 0.34f) },
                    onDrawSurface = {
                        when (style) {
                            GlassButtonStyle.PRIMARY -> drawRect(primary.copy(alpha = 0.72f))
                            GlassButtonStyle.SECONDARY -> drawRect(Color.White.copy(alpha = 0.24f))
                            GlassButtonStyle.TONAL -> drawRect(primary.copy(alpha = 0.18f))
                            GlassButtonStyle.DANGER -> drawRect(error.copy(alpha = 0.10f))
                        }
                    },
                )
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                )
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
fun AdaptiveLiquidIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (!LocalLiquidGlassEnabled.current) {
        IconButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
        return
    }
    val backdrop = LocalLiquidBackdrop.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.88f else 1f,
        spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "liquid icon press",
    )
    Box(
        modifier = modifier
            .size(48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { CircleShape },
                effects = {
                    blur(4.dp.toPx())
                    lens(12.dp.toPx(), 20.dp.toPx(), chromaticAberration = pressed)
                },
                highlight = { Highlight.Default.copy(alpha = 0.62f) },
                shadow = { Shadow(radius = 5.dp, color = Color.Black.copy(alpha = 0.06f)) },
                onDrawSurface = { drawRect(Color.White.copy(alpha = 0.22f)) },
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun AdaptiveLiquidSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (!LocalLiquidGlassEnabled.current) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
        )
        return
    }

    val backdrop = LocalLiquidBackdrop.current
    val primary = MaterialTheme.colorScheme.primary
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val travelPx = with(density) { 22.dp.toPx() }
    val position = remember { androidx.compose.animation.core.Animatable(if (checked) 1f else 0f) }
    var dragging by remember { mutableStateOf(false) }

    LaunchedEffect(checked) {
        if (!dragging) {
            position.animateTo(
                if (checked) 1f else 0f,
                spring(dampingRatio = 0.62f, stiffness = 560f),
            )
        }
    }

    fun settle() {
        val target = position.value >= 0.5f
        dragging = false
        if (target != checked) onCheckedChange(target)
        scope.launch {
            position.animateTo(
                if (target) 1f else 0f,
                spring(dampingRatio = 0.58f, stiffness = 620f),
            )
        }
    }

    Box(
        modifier = modifier
            .size(width = 56.dp, height = 32.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.46f }
            .background(
                lerp(
                    Color(0xFFD4D7DD),
                    primary.copy(alpha = 0.78f),
                    position.value,
                ),
                RoundedCornerShape(16.dp),
            )
            .clickable(
                enabled = enabled,
                role = Role.Switch,
                onClick = { onCheckedChange(!checked) },
            )
            .draggable(
                enabled = enabled,
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    dragging = true
                    scope.launch {
                        position.snapTo((position.value + delta / travelPx).coerceIn(0f, 1f))
                    }
                },
                onDragStopped = { settle() },
            )
            .semantics { role = Role.Switch },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationX = 3.dp.toPx() + position.value * travelPx
                    scaleX = if (dragging) 1.22f else 1f
                    scaleY = if (dragging) 0.9f else 1f
                }
                .size(28.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = {
                        blur((if (dragging) 2.dp else 5.dp).toPx())
                        lens(
                            (if (dragging) 15.dp else 8.dp).toPx(),
                            (if (dragging) 22.dp else 14.dp).toPx(),
                            chromaticAberration = dragging,
                        )
                    },
                    highlight = { Highlight.Default.copy(alpha = if (dragging) 0.9f else 0.68f) },
                    shadow = { Shadow(radius = 5.dp, color = Color.Black.copy(alpha = 0.12f)) },
                    innerShadow = { InnerShadow(radius = 4.dp, alpha = 0.3f) },
                    onDrawSurface = {
                        drawRect(
                            primary.copy(
                                alpha = 0.18f + position.value * 0.62f,
                            ),
                        )
                        drawRect(Color.White.copy(alpha = 0.28f))
                    },
                ),
        )
    }
}
