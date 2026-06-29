package com.wiwy.wiwytransfer.qs

import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.security.KeyPair
import kotlin.math.abs

class NearbyException(message: String) : Exception(message)

/**
 * Base de la conexión Nearby/Quick Share: framing, handshake UKEY2 y canal seguro.
 * Port del NearbyConnection.swift del Mac, con I/O por sockets bloqueantes.
 */
abstract class NearbyConnection(protected val socket: Socket) {

    companion object {
        const val SANE_FRAME_LENGTH = 5 * 1024 * 1024
        private const val TAG = "WiwyQS"
        // Salt D2D (igual que en el Mac / NearDrop)
        private val D2D_SALT = byteArrayOf(
            0x82.toByte(), 0xAA.toByte(), 0x55, 0xA0.toByte(), 0xD3.toByte(), 0x97.toByte(),
            0xF8.toByte(), 0x83.toByte(), 0x46, 0xCA.toByte(), 0x1C, 0xEE.toByte(), 0x8D.toByte(),
            0x39, 0x09, 0xB9.toByte(), 0x5F, 0x13, 0xFA.toByte(), 0x7D, 0xEB.toByte(), 0x1D,
            0x4A, 0xB3.toByte(), 0x83.toByte(), 0x76, 0xB8.toByte(), 0x25, 0x6D, 0xA8.toByte(),
            0x55, 0x10,
        )
    }

    private val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
    private val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))

    protected var keyPair: KeyPair? = null
    protected var ukeyClientInitMsgData: ByteArray? = null
    protected var ukeyServerInitMsgData: ByteArray? = null

    protected var decryptKey: ByteArray? = null
    protected var encryptKey: ByteArray? = null
    protected var recvHmacKey: ByteArray? = null
    protected var sendHmacKey: ByteArray? = null

    private var serverSeq = 0
    private var clientSeq = 0
    private val sendLock = Any()

    var pinCode: String? = null; protected set
    protected var encryptionDone = false
    var lastError: String? = null
    @Volatile private var closed = false

    private val payloadBuffers = HashMap<Long, java.io.ByteArrayOutputStream>()

    // ---- Métodos a implementar por receptor/emisor ----
    protected abstract fun isServer(): Boolean
    protected abstract fun connectionReady()
    protected abstract fun processReceivedFrame(frameData: ByteArray)
    protected abstract fun processTransferSetupFrame(frame: SharingFrame)
    protected open fun processFileChunk(frame: PayloadTransferFrame) {}
    protected open fun processBytesPayload(payload: ByteArray, id: Long): Boolean = false
    protected open fun onClosed() {}

    /** Bucle de lectura (ejecutar en un hilo de IO). */
    fun loop() {
        try {
            connectionReady()
            while (!closed) {
                val len = input.readInt()
                if (len <= 0 || len > SANE_FRAME_LENGTH) throw NearbyException("Longitud de frame inválida: $len")
                val buf = ByteArray(len)
                input.readFully(buf)
                processReceivedFrame(buf)
            }
        } catch (e: Exception) {
            if (!closed) {
                lastError = e.message ?: e.javaClass.simpleName
                Log.w(TAG, "Conexión terminada: ${e.message}")
            }
        } finally {
            close()
        }
    }

    fun sendFrame(frame: ByteArray) {
        if (closed) return
        synchronized(output) {
            output.writeInt(frame.size)
            output.write(frame)
            output.flush()
        }
    }

    fun close() {
        if (closed) return
        closed = true
        runCatching { socket.close() }
        onClosed()
    }

    fun isClosed() = closed

    // ---- Canal seguro ----

    protected fun encryptAndSendOfflineFrame(frame: OfflineFrame) {
        // Atómico: asignar nº de secuencia y enviar deben ir juntos para que el
        // orden de secuencia sea monótono aunque haya varios hilos enviando.
        synchronized(sendLock) {
            serverSeq += 1
            val d2d = DeviceToDeviceMessage.newBuilder()
                .setSequenceNumber(serverSeq)
                .setMessage(frame.toByteString())
                .build()
            val iv = Crypto.randomBytes(16)
            val encrypted = Crypto.aesCbcEncrypt(encryptKey!!, iv, d2d.toByteArray())
            val meta = GcmMetadata.newBuilder()
                .setType(GcmType.DEVICE_TO_DEVICE_MESSAGE)
                .setVersion(1)
                .build()
            val hb = HeaderAndBody.newBuilder()
                .setBody(encrypted.toByteString())
                .setHeader(
                    SmHeader.newBuilder()
                        .setEncryptionScheme(EncScheme.AES_256_CBC)
                        .setSignatureScheme(SigScheme.HMAC_SHA256)
                        .setIv(iv.toByteString())
                        .setPublicMetadata(meta.toByteString())
                        .build()
                )
                .build()
            val hbBytes = hb.toByteArray()
            val smsg = SecureMessage.newBuilder()
                .setHeaderAndBody(hbBytes.toByteString())
                .setSignature(Crypto.hmacSha256(sendHmacKey!!, hbBytes).toByteString())
                .build()
            sendFrame(smsg.toByteArray())
        }
    }

    protected fun sendTransferSetupFrame(frame: SharingFrame) {
        sendBytesPayload(frame.toByteArray(), kotlin.random.Random.nextLong())
    }

    protected fun sendBytesPayload(data: ByteArray, id: Long) {
        val full = PayloadTransferFrame.newBuilder()
            .setPacketType(PacketType.DATA)
            .setPayloadHeader(
                PayloadHeader.newBuilder()
                    .setId(id)
                    .setType(PayloadType.BYTES)
                    .setTotalSize(data.size.toLong())
                    .setIsSensitive(false)
            )
            .setPayloadChunk(
                PayloadChunk.newBuilder()
                    .setOffset(0).setFlags(0).setBody(data.toByteString())
            )
            .build()
        encryptAndSendOfflineFrame(wrapPayload(full))

        val last = PayloadTransferFrame.newBuilder()
            .setPacketType(PacketType.DATA)
            .setPayloadHeader(
                PayloadHeader.newBuilder()
                    .setId(id)
                    .setType(PayloadType.BYTES)
                    .setTotalSize(data.size.toLong())
                    .setIsSensitive(false)
            )
            .setPayloadChunk(
                PayloadChunk.newBuilder()
                    .setOffset(data.size.toLong()).setFlags(1)
            )
            .build()
        encryptAndSendOfflineFrame(wrapPayload(last))
    }

    private fun wrapPayload(transfer: PayloadTransferFrame): OfflineFrame =
        OfflineFrame.newBuilder()
            .setVersion(OfflineVersion.V1)
            .setV1(
                ConnV1Frame.newBuilder()
                    .setType(ConnFrameType.PAYLOAD_TRANSFER)
                    .setPayloadTransfer(transfer)
            )
            .build()

    protected fun decryptAndProcessReceivedSecureMessage(smsg: SecureMessage) {
        val hbBytes = smsg.headerAndBody.toByteArray()
        val hmac = Crypto.hmacSha256(recvHmacKey!!, hbBytes)
        if (!hmac.contentEquals(smsg.signature.toByteArray())) throw NearbyException("hmac!=signature")
        val hb = HeaderAndBody.parseFrom(hbBytes)
        val decrypted = Crypto.aesCbcDecrypt(decryptKey!!, hb.header.iv.toByteArray(), hb.body.toByteArray())
        val d2d = DeviceToDeviceMessage.parseFrom(decrypted)
        clientSeq += 1
        if (d2d.sequenceNumber != clientSeq) throw NearbyException("Secuencia incorrecta: ${d2d.sequenceNumber} != $clientSeq")
        val offline = OfflineFrame.parseFrom(d2d.message)

        if (offline.hasV1() && offline.v1.type == ConnFrameType.PAYLOAD_TRANSFER) {
            val pt = offline.v1.payloadTransfer
            val header = pt.payloadHeader
            val chunk = pt.payloadChunk
            when (header.type) {
                PayloadType.BYTES -> {
                    val id = header.id
                    if (header.totalSize > SANE_FRAME_LENGTH) {
                        payloadBuffers.remove(id); throw NearbyException("Payload demasiado grande")
                    }
                    val buf = payloadBuffers.getOrPut(id) { java.io.ByteArrayOutputStream() }
                    if (chunk.body != null && !chunk.body.isEmpty) buf.write(chunk.body.toByteArray())
                    if ((chunk.flags.toInt() and 1) == 1) {
                        payloadBuffers.remove(id)
                        val data = buf.toByteArray()
                        if (!processBytesPayload(data, id)) {
                            processTransferSetupFrame(SharingFrame.parseFrom(data))
                        }
                    }
                }
                PayloadType.FILE -> processFileChunk(pt)
                else -> {}
            }
        } else if (offline.hasV1() && offline.v1.type == ConnFrameType.KEEP_ALIVE) {
            sendKeepAlive(true)
        }
    }

    // ---- Derivación de claves (UKEY2) ----

    protected fun finalizeKeyExchange(peerKey: GenericPublicKey) {
        val x = peerKey.ecP256PublicKey.x.toByteArray()
        val y = peerKey.ecP256PublicKey.y.toByteArray()
        val peerPub = Crypto.publicKeyFromXY(x, y)
        val shared = Crypto.ecdh(keyPair!!.private, peerPub)
        val dhs = Crypto.sha256(shared)

        val ukeyInfo = ukeyClientInitMsgData!! + ukeyServerInitMsgData!!
        val authString = Crypto.hkdf(dhs, "UKEY2 v1 auth".toByteArray(), ukeyInfo, 32)
        val nextSecret = Crypto.hkdf(dhs, "UKEY2 v1 next".toByteArray(), ukeyInfo, 32)
        pinCode = pinCodeFromAuthKey(authString)

        val d2dClient = Crypto.hkdf(nextSecret, D2D_SALT, "client".toByteArray(), 32)
        val d2dServer = Crypto.hkdf(nextSecret, D2D_SALT, "server".toByteArray(), 32)
        val smSalt = Crypto.sha256("SecureMessage".toByteArray())
        val clientKey = Crypto.hkdf(d2dClient, smSalt, "ENC:2".toByteArray(), 32)
        val clientHmac = Crypto.hkdf(d2dClient, smSalt, "SIG:1".toByteArray(), 32)
        val serverKey = Crypto.hkdf(d2dServer, smSalt, "ENC:2".toByteArray(), 32)
        val serverHmac = Crypto.hkdf(d2dServer, smSalt, "SIG:1".toByteArray(), 32)

        if (isServer()) {
            decryptKey = clientKey; recvHmacKey = clientHmac
            encryptKey = serverKey; sendHmacKey = serverHmac
        } else {
            decryptKey = serverKey; recvHmacKey = serverHmac
            encryptKey = clientKey; sendHmacKey = clientHmac
        }
    }

    private fun pinCodeFromAuthKey(key: ByteArray): String {
        var hash = 0
        var multiplier = 1
        for (b in key) {
            val byte = b.toInt()
            hash = (hash + byte * multiplier) % 9973
            multiplier = (multiplier * 31) % 9973
        }
        return String.format("%04d", abs(hash))
    }

    // ---- Frames de control ----

    protected fun sendUkey2Alert(type: Ukey2AlertType) {
        val alert = Ukey2Alert.newBuilder().setType(type).build()
        val msg = Ukey2Message.newBuilder()
            .setMessageType(Ukey2Type.ALERT)
            .setMessageData(alert.toByteString())
            .build()
        runCatching { sendFrame(msg.toByteArray()) }
        close()
    }

    protected fun sendKeepAlive(ack: Boolean) {
        val frame = OfflineFrame.newBuilder()
            .setVersion(OfflineVersion.V1)
            .setV1(
                ConnV1Frame.newBuilder()
                    .setType(ConnFrameType.KEEP_ALIVE)
                    .setKeepAlive(KeepAliveFrame.newBuilder().setAck(ack))
            )
            .build()
        runCatching {
            if (encryptionDone) encryptAndSendOfflineFrame(frame) else sendFrame(frame.toByteArray())
        }
    }

    protected fun sendDisconnectionAndDisconnect() {
        val frame = OfflineFrame.newBuilder()
            .setVersion(OfflineVersion.V1)
            .setV1(
                ConnV1Frame.newBuilder()
                    .setType(ConnFrameType.DISCONNECTION)
                    .setDisconnection(DisconnectionFrame.newBuilder())
            )
            .build()
        runCatching {
            if (encryptionDone) encryptAndSendOfflineFrame(frame) else sendFrame(frame.toByteArray())
        }
        close()
    }
}
