package com.unmiss.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

@Composable
fun InstalledAppIcon(
    packageName: String,
    fallbackLabel: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val icon = remember(packageName) {
        runCatching {
            context.packageManager.getApplicationIcon(packageName)
                .toBitmap(width = 96, height = 96)
                .asImageBitmap()
        }.getOrNull()
    }
    val shape = RoundedCornerShape(11.dp)

    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = "$fallbackLabel 图标",
            modifier = modifier.clip(shape),
            contentScale = ContentScale.Fit,
        )
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(fallbackLabel.take(1).uppercase(), fontWeight = FontWeight.Bold)
            }
        }
    }
}
