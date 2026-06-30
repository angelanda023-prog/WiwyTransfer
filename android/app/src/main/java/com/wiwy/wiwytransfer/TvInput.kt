package com.wiwy.wiwytransfer

import androidx.compose.foundation.focusable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Clic y pulsación-larga con el MANDO (D-pad). En Android TV el onLongClick de
 * Compose no funciona con el control; aquí medimos cuánto se mantiene el botón
 * central: < ~0.7 s = clic; >= 0.7 s = pulsación larga (seleccionar).
 */
fun Modifier.tvSelectable(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
): Modifier = composed {
    var downAt by remember { mutableStateOf(0L) }
    var isDown by remember { mutableStateOf(false) }
    var handledLong by remember { mutableStateOf(false) }
    this
        .onFocusChanged { onFocusChanged(it.isFocused) }
        .focusable()
        .onKeyEvent { e ->
            if (e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter) {
                when (e.type) {
                    KeyEventType.KeyDown -> {
                        if (!isDown) {
                            isDown = true
                            downAt = System.currentTimeMillis()
                            handledLong = false
                        } else if (!handledLong && System.currentTimeMillis() - downAt >= 700) {
                            // se mantuvo pulsado (repeticiones de tecla): seleccionar
                            handledLong = true
                            onLongClick()
                        }
                        true
                    }
                    KeyEventType.KeyUp -> {
                        isDown = false
                        if (!handledLong) {
                            if (System.currentTimeMillis() - downAt >= 700) onLongClick() else onClick()
                        }
                        true
                    }
                    else -> false
                }
            } else false
        }
}
