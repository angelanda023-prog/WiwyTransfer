package com.wiwy.wiwytransfer.qs

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.net.Socket

/** Metadatos de un archivo entrante por Quick Share. */
data class QsFileMeta(val name: String, val size: Long, val mimeType: String)

/** Eventos del receptor (se invocan en hilo de IO; el consumidor salta a main para UI). */
interface InboundDelegate {
    fun onConsent(connection: InboundNearbyConnection, sender: String, pin: String?, files: List<QsFileMeta>)
    fun onProgress(received: Long, total: Long, currentFile: String)
    fun onFinished(savedPaths: List<String>, sender: String)
    fun onClosed(error: String?)
}

/** Receptor Quick Share (rol servidor del UKEY2). Port de InboundNearbyConnection.swift. */
class InboundNearbyConnection(
    socket: Socket,
    private val saveDir: File,
    private val delegate: InboundDelegate,
) : NearbyConnection(socket) {

    private enum class State {
        INITIAL, RECEIVED_CONNECTION_REQUEST, SENT_UKEY_SERVER_INIT, RECEIVED_UKEY_CLIENT_FINISH,
        SENT_CONNECTION_RESPONSE, SENT_PAIRED_KEY_RESULT, RECEIVED_PAIRED_KEY_RESULT,
        WAITING_FOR_CONSENT, RECEIVING_FILES, DISCONNECTED
    }

    private class FileState(
        val meta: QsFileMeta, val dest: File,
        var out: FileOutputStream? = null, var bytes: Long = 0, var created: Boolean = false,
    )

    private var state = State.INITIAL
    private var cipherCommitment: ByteArray? = null
    private var senderName = "Quick Share"
    private val files = LinkedHashMap<Long, FileState>()
    private val savedPaths = ArrayList<String>()
    private var totalBytes = 0L
    private var receivedBytes = 0L

    override fun isServer() = true
    override fun connectionReady() { /* el cliente inicia */ }

    override fun onClosed() {
        // borra archivos a medio recibir
        for (f in files.values) if (f.created) runCatching { f.out?.close(); if (state != State.DISCONNECTED) f.dest.delete() }
        delegate.onClosed(null)
    }

    override fun processReceivedFrame(frameData: ByteArray) {
        try {
            when (state) {
                State.INITIAL -> processConnectionRequest(OfflineFrame.parseFrom(frameData))
                State.RECEIVED_CONNECTION_REQUEST -> {
                    ukeyClientInitMsgData = frameData
                    processUkey2ClientInit(Ukey2Message.parseFrom(frameData))
                }
                State.SENT_UKEY_SERVER_INIT -> processUkey2ClientFinish(Ukey2Message.parseFrom(frameData), frameData)
                State.RECEIVED_UKEY_CLIENT_FINISH -> processConnectionResponse(OfflineFrame.parseFrom(frameData))
                else -> decryptAndProcessReceivedSecureMessage(SecureMessage.parseFrom(frameData))
            }
        } catch (e: Exception) {
            Log.w("WiwyQS", "Error en estado $state: ${e.message}")
            close()
        }
    }

    override fun processTransferSetupFrame(frame: SharingFrame) {
        if (frame.hasV1() && frame.v1.type == SharingFrameType.CANCEL) { sendDisconnectionAndDisconnect(); return }
        when (state) {
            State.SENT_CONNECTION_RESPONSE -> processPairedKeyEncryption()
            State.SENT_PAIRED_KEY_RESULT -> state = State.RECEIVED_PAIRED_KEY_RESULT
            State.RECEIVED_PAIRED_KEY_RESULT -> processIntroduction(frame)
            else -> {}
        }
    }

    private fun processConnectionRequest(frame: OfflineFrame) {
        val info = frame.v1.connectionRequest.endpointInfo.toByteArray()
        if (info.size > 17) {
            val nameLen = info[17].toInt() and 0xFF
            if (info.size >= 18 + nameLen) {
                senderName = String(info, 18, nameLen, Charsets.UTF_8)
            }
        }
        Log.i("WiwyQS", "ConnectionRequest de $senderName")
        state = State.RECEIVED_CONNECTION_REQUEST
    }

    private fun processUkey2ClientInit(msg: Ukey2Message) {
        if (msg.messageType != Ukey2Type.CLIENT_INIT) { sendUkey2Alert(Ukey2AlertType.BAD_MESSAGE_TYPE); return }
        val clientInit = Ukey2ClientInit.parseFrom(msg.messageData)
        var found = false
        for (c in clientInit.cipherCommitmentsList) {
            if (c.handshakeCipher == Ukey2HandshakeCipher.P256_SHA512) { cipherCommitment = c.commitment.toByteArray(); found = true; break }
        }
        if (!found) { sendUkey2Alert(Ukey2AlertType.BAD_HANDSHAKE_CIPHER); return }

        keyPair = Crypto.generateKeyPair()
        val (x, y) = Crypto.publicKeyXY(keyPair!!.public)
        val pkey = GenericPublicKey.newBuilder()
            .setType(PublicKeyTypeEnum.EC_P256)
            .setEcP256PublicKey(EcP256PublicKey.newBuilder().setX(x.toByteString()).setY(y.toByteString()))
            .build()
        val serverInit = Ukey2ServerInit.newBuilder()
            .setVersion(1)
            .setRandom(Crypto.randomBytes(32).toByteString())
            .setHandshakeCipher(Ukey2HandshakeCipher.P256_SHA512)
            .setPublicKey(pkey.toByteString())
            .build()
        val serverInitMsg = Ukey2Message.newBuilder()
            .setMessageType(Ukey2Type.SERVER_INIT)
            .setMessageData(serverInit.toByteString())
            .build()
        ukeyServerInitMsgData = serverInitMsg.toByteArray()
        sendFrame(ukeyServerInitMsgData!!)
        state = State.SENT_UKEY_SERVER_INIT
    }

    private fun processUkey2ClientFinish(msg: Ukey2Message, raw: ByteArray) {
        if (msg.messageType != Ukey2Type.CLIENT_FINISH) { close(); return }
        if (!Crypto.sha512(raw).contentEquals(cipherCommitment)) throw NearbyException("commitment no coincide")
        val finished = Ukey2ClientFinished.parseFrom(msg.messageData)
        val clientKey = GenericPublicKey.parseFrom(finished.publicKey)
        finalizeKeyExchange(clientKey)
        Log.i("WiwyQS", "UKEY2 completado (PIN $pinCode)")
        state = State.RECEIVED_UKEY_CLIENT_FINISH
    }

    private fun processConnectionResponse(frame: OfflineFrame) {
        if (frame.v1.type != ConnFrameType.CONNECTION_RESPONSE) return
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
        state = State.SENT_CONNECTION_RESPONSE
    }

    private fun processPairedKeyEncryption() {
        val pkr = SharingFrame.newBuilder()
            .setVersion(SharingVersion.V1)
            .setV1(
                SharingV1Frame.newBuilder()
                    .setType(SharingFrameType.PAIRED_KEY_RESULT)
                    .setPairedKeyResult(PairedKeyResultFrame.newBuilder().setStatus(PairedKeyResultStatus.UNABLE))
            )
            .build()
        sendTransferSetupFrame(pkr)
        state = State.SENT_PAIRED_KEY_RESULT
    }

    private fun processIntroduction(frame: SharingFrame) {
        val intro = frame.v1.introduction
        if (intro.fileMetadataCount == 0) { rejectTransfer(SharingResponseStatus.UNSUPPORTED_ATTACHMENT_TYPE); return }
        saveDir.mkdirs()
        for (fm in intro.fileMetadataList) {
            val name = sanitize(fm.name)
            val dest = uniqueFile(saveDir, name)
            files[fm.payloadId] = FileState(QsFileMeta(name, fm.size, fm.mimeType), dest)
            totalBytes += fm.size
        }
        state = State.WAITING_FOR_CONSENT
        delegate.onConsent(this, senderName, pinCode, files.values.map { it.meta })
    }

    /** El usuario aceptó/rechazó (llamar desde la UI). */
    fun submitConsent(accept: Boolean) {
        if (!accept) { rejectTransfer(SharingResponseStatus.REJECT); return }
        try {
            for (f in files.values) {
                f.out = FileOutputStream(f.dest)
                f.created = true
            }
            val resp = SharingFrame.newBuilder()
                .setVersion(SharingVersion.V1)
                .setV1(
                    SharingV1Frame.newBuilder()
                        .setType(SharingFrameType.RESPONSE)
                        .setConnectionResponse(SharingConnectionResponseFrame.newBuilder().setStatus(SharingResponseStatus.ACCEPT))
                )
                .build()
            state = State.RECEIVING_FILES
            sendTransferSetupFrame(resp)
        } catch (e: Exception) {
            Log.w("WiwyQS", "Error aceptando: ${e.message}"); close()
        }
    }

    override fun processFileChunk(frame: PayloadTransferFrame) {
        val id = frame.payloadHeader.id
        val f = files[id] ?: return
        val chunk = frame.payloadChunk
        if (!chunk.body.isEmpty) {
            val b = chunk.body.toByteArray()
            f.out?.write(b)
            f.bytes += b.size
            receivedBytes += b.size
            delegate.onProgress(receivedBytes, totalBytes, f.meta.name)
        } else if ((chunk.flags.toInt() and 1) == 1) {
            runCatching { f.out?.close() }
            f.out = null
            savedPaths.add(f.dest.absolutePath)
            files.remove(id)
            if (files.isEmpty()) {
                state = State.DISCONNECTED
                delegate.onFinished(savedPaths.toList(), senderName)
                sendDisconnectionAndDisconnect()
            }
        }
    }

    private fun rejectTransfer(status: SharingResponseStatus) {
        val resp = SharingFrame.newBuilder()
            .setVersion(SharingVersion.V1)
            .setV1(
                SharingV1Frame.newBuilder()
                    .setType(SharingFrameType.RESPONSE)
                    .setConnectionResponse(SharingConnectionResponseFrame.newBuilder().setStatus(status))
            )
            .build()
        runCatching { sendTransferSetupFrame(resp); sendDisconnectionAndDisconnect() }
    }

    private fun sanitize(raw: String): String {
        val base = raw.substringAfterLast('/').substringAfterLast('\\').trim()
        return if (base.isEmpty() || base == "." || base == "..") "archivo" else base
    }

    private fun uniqueFile(dir: File, name: String): File {
        var dest = File(dir, name)
        if (!dest.exists()) return dest
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (dest.exists()) { dest = File(dir, "$base ($i)$ext"); i++ }
        return dest
    }
}
