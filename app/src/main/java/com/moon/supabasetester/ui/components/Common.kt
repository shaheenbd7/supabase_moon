package com.moon.supabasetester.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.moon.supabasetester.data.LogLevel
import com.moon.supabasetester.ui.theme.ErrorRed
import com.moon.supabasetester.ui.theme.SuccessGreen

@Composable
fun SectionCard(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

@Composable
fun ScreenHeader(icon: ImageVector, title: String, blurb: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(icon, null, Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Column {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(blurb, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun BusyButton(text: String, busy: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
               tonal: Boolean = false) {
    val content: @Composable RowScope.() -> Unit = {
        if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        else Text(text)
    }
    if (tonal) FilledTonalButton(onClick, modifier, enabled = enabled && !busy, content = content)
    else Button(onClick, modifier, enabled = enabled && !busy, content = content)
}

@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
    visual: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
) {
    OutlinedTextField(
        value, onChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        visualTransformation = visual,
    )
}

@Composable
fun StatusDot(level: LogLevel?) {
    val color = when (level) {
        LogLevel.SUCCESS -> SuccessGreen
        LogLevel.ERROR -> ErrorRed
        else -> MaterialTheme.colorScheme.outline
    }
    Surface(shape = MaterialTheme.shapes.small, color = color, modifier = Modifier.size(10.dp)) {}
}

@Composable
fun Mono(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Text(text, modifier, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = color)
}

/** Compact last-result readout shown under a feature's controls. */
@Composable
fun ResultBox(text: String?, isError: Boolean = false) {
    if (text.isNullOrBlank()) return
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Mono(text, Modifier.fillMaxWidth().padding(10.dp))
    }
}
