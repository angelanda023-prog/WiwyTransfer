package com.wiwy.wiwytransfer.qs

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow

/** Registro en memoria de los pasos del protocolo, para mostrarlo en pantalla (TV). */
object QsDebug {
    val lines = MutableStateFlow<List<String>>(emptyList())

    fun log(msg: String) {
        Log.i("WiwyQS", msg)
        val t = android.text.format.DateFormat.format("HH:mm:ss", System.currentTimeMillis())
        lines.value = (lines.value + "$t  $msg").takeLast(18)
    }

    fun clear() { lines.value = emptyList() }
}
