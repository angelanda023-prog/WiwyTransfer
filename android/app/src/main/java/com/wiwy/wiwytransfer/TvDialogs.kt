package com.wiwy.wiwytransfer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val DialogBg = Color(0xFF0C1424)
private val DialogBorder = Color(0xFF20304E)
private val DialogAccent = Color(0xFF2979FF)
private val DialogAccentDark = Color(0xFF1565C0)
private val DialogMuted = Color(0xFF9FB3CC)
private val DialogBtnDark = Color(0xFF18233A)

/**
 * Ventana emergente unificada de WiwyTransfer: mismo tamaño, centrada, con icono
 * con glow, título, cuerpo y botones (oscuro + azul). [content] permite añadir
 * contenido extra (p. ej. una barra de progreso).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WiwyDialog(
    onDismiss: () -> Unit,
    icon: ImageVector,
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    badge: String? = null,
    accent: Color = DialogAccent,
    dismissOnOutside: Boolean = true,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val primaryFocus = remember { FocusRequester() }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = dismissOnOutside,
            dismissOnBackPress = dismissOnOutside,
        ),
    ) {
        Column(
            Modifier
                .width(460.dp)
                .heightIn(min = 400.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(DialogBg)
                .border(1.dp, DialogBorder, RoundedCornerShape(28.dp))
                .padding(horizontal = 32.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            GlowIcon(icon, accent)
            Spacer(Modifier.height(24.dp))
            Text(
                title, color = Color.White, textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            )
            badge?.let {
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp))
                        .background(accent.copy(alpha = 0.18f))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(it, color = Color(0xFF7FB0FF), fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (body.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(body, color = DialogMuted, textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge)
            }
            content?.let {
                Spacer(Modifier.height(18.dp))
                it()
            }
            Spacer(Modifier.height(30.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (secondaryLabel != null) {
                    DialogButton(secondaryLabel, filled = false, accent = accent,
                        modifier = Modifier.weight(1f)) { onSecondary?.invoke() }
                }
                DialogButton(primaryLabel, filled = true, accent = accent,
                    modifier = Modifier.weight(1f).focusRequester(primaryFocus)) { onPrimary() }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { primaryFocus.requestFocus() } }
}

@Composable
private fun GlowIcon(icon: ImageVector, accent: Color) {
    Box(contentAlignment = Alignment.Center) {
        Box(Modifier.size(116.dp).clip(CircleShape).background(accent.copy(alpha = 0.12f)))
        Box(Modifier.size(96.dp).clip(CircleShape).background(accent.copy(alpha = 0.22f)))
        Box(
            Modifier.size(78.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(accent, DialogAccentDark))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(42.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogButton(
    label: String,
    filled: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.height(54.dp).onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(15.dp),
        color = if (filled) accent else DialogBtnDark,
        border = when {
            focused -> BorderStroke(2.dp, Color.White)
            !filled -> BorderStroke(1.dp, Color(0xFF2A3A5C))
            else -> null
        },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, color = Color.White, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium)
        }
    }
}
