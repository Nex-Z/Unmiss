package com.unmiss.app.ui.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun LiquidChip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable () -> Unit,
) {
    if (!LocalLiquidGlassEnabled.current || LocalLiquidGlassIntensity.current <= 0f) {
        FilterChip(selected = selected, onClick = onClick, modifier = modifier, label = label)
        return
    }
    val primary = MaterialTheme.colorScheme.primary
    LiquidGlass(
        modifier = modifier.height(36.dp),
        glassShape = RoundedCornerShape(18.dp),
        material = if (selected) GlassMaterial.PROMINENT else GlassMaterial.CLEAR,
        surfaceAlpha = if (selected) 0.28f else 0.12f,
        onClick = onClick,
    ) {
        CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides if (selected) primary else MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { label() }
        }
    }
}

@Composable
fun LiquidTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    if (!LocalLiquidGlassEnabled.current || LocalLiquidGlassIntensity.current <= 0f) {
        androidx.compose.material3.OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            placeholder = { Text(placeholder) },
            singleLine = singleLine,
            leadingIcon = leading,
            trailingIcon = trailing,
            visualTransformation = visualTransformation,
        )
        return
    }
    LiquidGlass(
        modifier = modifier,
        glassShape = RoundedCornerShape(22.dp),
        material = GlassMaterial.REGULAR,
        surfaceAlpha = 0.22f,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            singleLine = singleLine,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = visualTransformation,
            decorationBox = { inner ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    leading?.let { Box(Modifier.padding(end = 10.dp)) { it() } }
                    Box(modifier = Modifier.weight(1f)) {
                        if (value.isEmpty()) Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        inner()
                    }
                    trailing?.let { Box(Modifier.padding(start = 8.dp)) { it() } }
                }
            },
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun LiquidSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
) {
    if (!LocalLiquidGlassEnabled.current || LocalLiquidGlassIntensity.current <= 0f) {
        Slider(value = value, onValueChange = onValueChange, modifier = modifier, valueRange = valueRange)
        return
    }
    val primary = MaterialTheme.colorScheme.primary
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        valueRange = valueRange,
        colors = SliderDefaults.colors(
            thumbColor = Color.Transparent,
            activeTrackColor = primary.copy(alpha = 0.72f),
            inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
        thumb = {
            LiquidGlass(
                modifier = Modifier.size(28.dp),
                glassShape = CircleShape,
                material = GlassMaterial.PROMINENT,
                surfaceAlpha = 0.26f,
                content = {},
            )
        },
    )
}

@Composable
fun LiquidPopover(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        LiquidGlass(
            modifier = modifier,
            glassShape = RoundedCornerShape(22.dp),
            material = GlassMaterial.REGULAR,
            surfaceAlpha = 0.3f,
            content = content,
        )
    }
}
