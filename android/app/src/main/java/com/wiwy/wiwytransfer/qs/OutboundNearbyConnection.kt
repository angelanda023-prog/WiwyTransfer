package com.wiwy.wiwytransfer.qs

import java.io.InputStream
import java.net.Socket

/** Un archivo a enviar por Quick Share. */
class QsOutgoingFile(
    val name: String,
    val size: Long,
    val mimeType: String,
    val open: () -> InputStream,
)

interface OutboundDelegate {
    fun onEstablished()
    fun onAccepted()
    fun onProgress(fraction: Double)
    fun onFinished()
    fun onFailed(message: String)
}

/** Emisor Quick Share (rol cliente del UKEY2). Port de OutboundNearbyConnection.swift. */
class OutboundNearbyConnection(
    socket: Socket,
    private val senderName: String,
    private val senderEndpointId: ByteArray,
    private val files: List<QsOutgoingFile>,
    private val delegate: OutboundDelegate,
) : NearbyConnection(socket) {

    private enum class State {
        INITIAL, SENT_UKEY_CLIENT_INIT, SENT_UKEY_CLIENT_FINISH, SENT_PAIRED_KEY_ENCRYPTION,
        SENT_PAIRED_KEY_RESULT, SENT_INTRODUCTION, SENDING_FILES
    }

    private class Outgoing(val meta: QsOutgoingFile, val payloadId: Long)

    private var state = State.INITIAL
    private var ukeyClientFinishMsgData: ByteArray? = null
    private val queue = ArrayList<Outgoing>()
    private var totalBytes = 0L
    private var sentBytes = 0L

    override fun isServer() = false

    override fun connectionReady() {
        QsDebug.log("📤 Conectado, iniciando handshake")
        try {
            sendConnectionRequest()
            sendUkey2ClientInit()
        } catch (e: Exception) {
            fail(e.message ?: "error iniciando")
        }
    }

    override fun onClosed() { /* delegate.onFailed/onFinished ya señalizados */ }

    override fun processReceivedFrame(frameData: ByteArray) {
        try {
            when (state) {
                State.SENT_UKEY_CLIENT_INIT -> processServerInit(Ukey2Message.parseFrom(frameData), frameData)
                State.SENT_UKEY_CLIENT_FINISH -> processConnectionResponse(OfflineFrame.parseFrom(frameData))
                else -> decryptAndProcessReceivedSecureMessage(SecureMessage.parseFrom(frameData))
            }
        } catch (e: Exception) {
            fail(e.message ?: "error de protocolo")
        }
    }

    override fun processTransferSetupFrame(frame: SharingFrame) {
        if (frame.hasV1() && frame.v1.type == SharingFrameType.CANCEL) {
            fail("Cancelado por el receptor"); return
        }
        when (state) {
            State.SENT_PAIRED_KEY_ENCRYPTION -> { sendPairedKeyResult(); state = State.SENT_PAIRED_KEY_RESULT }
            State.SENT_PAIRED_KEY_RESULT -> { sendIntroduction(); state = State.SENT_INTRODUCTION }
            State.SENT_INTRODUCTION -> processConsent(frame)
            else -> {}
        }
    }

    private fun sendConnectionRequest() {
        val frame = OfflineFrame.newBuilder()
            .setVersion(OfflineVersion.V1)
            .setV1(
                ConnV1Frame.newBuilder()
                    .setType(ConnFrameType.CONNECTION_REQUEST)
                    .setConnectionRequest(
                        ConnectionRequestFrame.newBuilder()
                            .setEndpointId(String(senderEndpointId, Charsets.US_ASCII))
                            .setEndpointName(senderName)
                            .setEndpointInfo(
                                QsEndpoint.b64urlDecode(QsEndpoint.endpointInfo(senderName)).toByteString()
                            )
                            .addMediums(ConnMedium.WIFI_LAN)
                    )
            )
            .build()
        sendFrame(frame.toByteArray())
    }

    private fun sendUkey2ClientInit() {
        keyPair = Crypto.generateKeyPair()
        val (x, y) = Crypto.publicKeyXY(keyPair!!.public)
        val pkey = GenericPublicKey.newBuilder()
            .setType(PublicKeyTypeEnum.EC_P256)
            .setEcP256PublicKey(EcP256PublicKey.newBuilder().setX(x.toByteString()).setY(y.toByteString()))
            .build()
        val finished = Ukey2ClientFinished.newBuilder().setPublicKey(pkey.toByteString()).build()
        val finishMsg = Ukey2Message.newBuilder()
            .setMessageType(Ukey2Type.CLIENT_FINISH)
            .setMessageData(finished.toByteString())
            .build()
        ukeyClientFinishMsgData = finishMsg.toByteArray()

        val commitment = CipherCommitment.newBuilder()
            .setHandshakeCipher(Ukey2HandshakeCipher.P256_SHA512)
            .setCommitment(Crypto.sha512(ukeyClientFinishMsgData!!).toByteString())
            .build()
        val clientInit = Ukey2ClientInit.newBuilder()
            .setVersion(1)
            .setRandom(Crypto.randomBytes(32).toByteString())
            .setNextProtocol("AES_256_CBC-HMAC_SHA256")
            .addCipherCommitments(commitment)
            .build()
        val initMsg = Ukey2Message.newBuilder()
            .setMessageType(Ukey2Type.CLIENT_INIT)
            .setMessageData(clientInit.toByteString())
            .build()
        ukeyClientInitMsgData = initMsg.toByteArray()
        sendFrame(ukeyClientInitMsgData!!)
        state = State.SENT_UKEY_CLIENT_INIT
    }

    private fun processServerInit(msg: Ukey2Message, raw: ByteArray) {
        if (msg.messageType != Ukey2Type.SERVER_INIT) { sendUkey2Alert(Ukey2AlertType.BAD_MESSAGE_TYPE); throw NearbyException("no server init") }
        ukeyServerInitMsgData = raw
        val serverInit = Ukey2ServerInit.parseFrom(msg.messageData)
        val serverKey = GenericPublicKey.parseFrom(serverInit.publicKey)
        finalizeKeyExchange(serverKey)
        sendFrame(ukeyClientFinishMsgData!!)
        state = State.SENT_UKEY_CLIENT_FINISH

        val resp = OfflineFrame.newBuilder()
            .setVersion(OfflineVersion.V1)
            .setV1(
                ConnV1Frame.newBuilder()
                    .setType(ConnFrameType.CONNECTION_RESPONSE)
                    .setConnectionResponse(
                        ConnectionResponseFrame.newBuilder()
                            .setResponse(ConnResponseStatus.ACCEPT)
                            .setStatus(0)
                            .setOsInfo(OsInfo.newBuilder().setType(OsType.ANDROID))
                    )
            )
            .build()
        sendFrame(resp.toByteArray())
        encryptionDone = true
        QsDebug.log("🔐 UKEY2 cliente completado")
        delegate.onEstablished()
    }

    private fun processConnectionResponse(frame: OfflineFrame) {
        if (frame.v1.type != ConnFrameType.CONNECTION_RESPONSE) throw NearbyException("esperaba connection response")
        if (frame.v1.connectionResponse.response != ConnResponseStatus.ACCEPT) throw NearbyException("rechazado")
        val pke = SharingFrame.newBuilder()
            .setVersion(SharingVersion.V1)
            .setV1(
                SharingV1Frame.newBuilder()
                    .setType(SharingFrameType.PAIRED_KEY_ENCRYPTION)
                    .setPairedKeyEncryption(
                        PairedKeyEncryptionFrame.newBuilder()
                            .setSecretIdHash(Crypto.randomBytes(6).toByteString())
                            .setSignedData(Crypto.randomBytes(72).toByteString())
                    )
            )
            .build()
        sendTransferSetupFrame(pke)
        state = State.SENT_PAIRED_KEY_ENCRYPTION
    }

    private fun sendPairedKeyResult() {
        val pkr = SharingFrame.newBuilder()
            .setVersion(SharingVersion.V1)
            .setV1(
                SharingV1Frame.newBuilder()
                    .setType(SharingFrameType.PAIRED_KEY_RESULT)
                    .setPairedKeyResult(PairedKeyResultFrame.newBuilder().setStatus(PairedKeyResultStatus.UNABLE))
            )
            .build()
        sendTransferSetupFrame(pkr)
    }

    private fun sendIntroduction() {
        val intro = IntroductionFrame.newBuilder()
        for (f in files) {
            val payloadId = kotlin.random.Random.nextLong()
            val type = when {
                f.mimeType.startsWith("image/") -> FileMetadataType.IMAGE
                f.mimeType.startsWith("video/") -> FileMetadataType.VIDEO
                f.mimeType.startsWith("audio/") -> FileMetadataType.AUDIO
                f.name.endsWith(".apk", true) -> FileMetadataType.ANDROID_APP
                else -> FileMetadataType.UNKNOWN
            }
            intro.addFileMetadata(
                SharingFileMetadata.newBuilder()
                    .setName(f.name).setSize(f.size).setMimeType(f.mimeType)
                    .setType(type).setPayloadId(payloadId)
            )
            queue.add(Outgoing(f, payloadId))
            totalBytes += f.size
        }
        val frame = SharingFrame.newBuilder()
            .setVersion(SharingVersion.V1)
            .setV1(SharingV1Frame.newBuilder().setType(SharingFrameType.INTRODUCTION).setIntroduction(intro))
            .build()
        sendTransferSetupFrame(frame)
    }

    private fun processConsent(frame: SharingFrame) {
        if (!frame.hasV1() || frame.v1.type != SharingFrameType.RESPONSE) throw NearbyException("esperaba response")
        QsDebug.log("📥 Respuesta del receptor: ${frame.v1.connectionResponse.status}")
        when (frame.v1.connectionResponse.status) {
            SharingResponseStatus.ACCEPT -> {
                state = State.SENDING_FILES
                delegate.onAccepted()
                sendAllFiles()
            }
            else -> fail("Rechazado por el receptor")
        }
    }

    private fun sendAllFiles() {
        try {
            val buf = ByteArray(512 * 1024)
            for (out in queue) {
                QsDebug.log("⬆️ Enviando ${out.meta.name} (${out.meta.size}b)")
                out.meta.open().use { input ->
                    var offset = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        val chunk = PayloadTransferFrame.newBuilder()
                            .setPacketType(PacketType.DATA)
                            .setPayloadHeader(
                                PayloadHeader.newBuilder()
                                    .setId(out.payloadId).setType(PayloadType.FILE)
                                    .setTotalSize(out.meta.size).setIsSensitive(false)
                            )
                            .setPayloadChunk(
                                PayloadChunk.newBuilder().setOffset(offset).setFlags(0)
                                    .setBody(buf.copyOf(n).toByteString())
                            )
                            .build()
                        encryptAndSendOfflineFrame(wrapFile(chunk))
                        offset += n
                        sentBytes += n
                        delegate.onProgress(if (totalBytes > 0) sentBytes.toDouble() / totalBytes else 0.0)
                    }
                    // EOF chunk
                    val eof = PayloadTransferFrame.newBuilder()
                        .setPacketType(PacketType.DATA)
                        .setPayloadHeader(
                            PayloadHeader.newBuilder()
                                .setId(out.payloadId).setType(PayloadType.FILE)
                                .setTotalSize(out.meta.size).setIsSensitive(false)
                        )
                        .setPayloadChunk(PayloadChunk.newBuilder().setOffset(offset).setFlags(1))
                        .build()
                    encryptAndSendOfflineFrame(wrapFile(eof))
                }
            }
            QsDebug.log("✅ Todos enviados, esperando que el receptor cierre…")
            delegate.onFinished()
            finishSendingGracefully()
        } catch (e: Exception) {
            fail("${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun wrapFile(transfer: PayloadTransferFrame): OfflineFrame =
        OfflineFrame.newBuilder()
            .setVersion(OfflineVersion.V1)
            .setV1(ConnV1Frame.newBuilder().setType(ConnFrameType.PAYLOAD_TRANSFER).setPayloadTransfer(transfer))
            .build()

    private fun fail(message: String) {
        QsDebug.log("❌ Envío falló: $message")
        delegate.onFailed(message)
        close()
    }
}
