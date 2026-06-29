package com.wiwy.wiwytransfer.qs

import android.util.Base64

/** Codificación del nombre de servicio mDNS y del endpoint info de Quick Share. */
object QsEndpoint {
    const val SERVICE_TYPE = "_FC9F5ED42C8A._tcp"

    private const val ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

    fun generateEndpointId(): ByteArray {
        val bytes = ByteArray(4)
        for (i in 0..3) bytes[i] = ALPHABET[(Math.random() * ALPHABET.length).toInt()].code.toByte()
        return bytes
    }

    private fun b64url(data: ByteArray): String =
        Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    fun b64urlDecode(s: String): ByteArray =
        Base64.decode(s, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    /** Nombre de servicio (10 bytes): 0x23 + endpointId(4) + 0xFC 0x9F 0x5E + 0 0. */
    fun serviceName(endpointId: ByteArray): String {
        val b = byteArrayOf(
            0x23,
            endpointId[0], endpointId[1], endpointId[2], endpointId[3],
            0xFC.toByte(), 0x9F.toByte(), 0x5E, 0x00, 0x00,
        )
        return b64url(b)
    }

    /** Endpoint info para el TXT "n": bitfield + 16 aleatorios + nombre con prefijo de longitud. */
    fun endpointInfo(name: String, deviceType: Int = 3 /* computer */): String {
        val out = ArrayList<Byte>()
        out.add((deviceType shl 1).toByte())
        out.addAll(Crypto.randomBytes(16).toList())
        var nameBytes = name.toByteArray(Charsets.UTF_8)
        if (nameBytes.size > 255) nameBytes = nameBytes.copyOf(255)
        out.add(nameBytes.size.toByte())
        out.addAll(nameBytes.toList())
        return b64url(out.toByteArray())
    }

    /** Extrae el nombre del dispositivo de un endpoint info decodificado (TXT "n"). */
    fun parseName(info: ByteArray): String? {
        if (info.size <= 17) return null
        val hasName = (info[0].toInt() and 0x10) == 0
        if (!hasName) return null
        val len = info[17].toInt() and 0xFF
        if (info.size < 18 + len) return null
        return String(info, 18, len, Charsets.UTF_8)
    }
}
