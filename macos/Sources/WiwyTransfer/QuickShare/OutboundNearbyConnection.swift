// Portado/adaptado de NearDrop (The Unlicense): https://github.com/grishka/NearDrop
// Emisor: rol cliente del handshake UKEY2 + envío de archivos por Quick Share.
// (Solo archivos; sin QR ni texto/URL.)

import Foundation
import Network
import CryptoKit
import CommonCrypto
import UniformTypeIdentifiers

import SwiftECC
import BigInt

protocol OutboundNearbyConnectionDelegate: AnyObject {
    func outboundConnectionWasEstablished(_ connection: OutboundNearbyConnection)
    func outboundConnection(_ connection: OutboundNearbyConnection, progress: Double)
    func outboundConnectionTransferAccepted(_ connection: OutboundNearbyConnection)
    func outboundConnection(_ connection: OutboundNearbyConnection, failedWith error: Error)
    func outboundConnectionTransferFinished(_ connection: OutboundNearbyConnection)
}

final class OutboundNearbyConnection: NearbyConnection {
    private var currentState: State = .initial
    private let urlsToSend: [URL]
    private let senderName: String
    private let senderEndpointID: [UInt8]
    private var ukeyClientFinishMsgData: Data?
    private var queue: [OutgoingFileTransfer] = []
    private var currentTransfer: OutgoingFileTransfer?
    weak var delegate: OutboundNearbyConnectionDelegate?
    private var totalBytesToSend: Int64 = 0
    private var totalBytesSent: Int64 = 0
    private var cancelled = false

    enum State {
        case initial, sentUkeyClientInit, sentUkeyClientFinish, sentPairedKeyEncryption,
             sentPairedKeyResult, sentIntroduction, sendingFiles
    }

    init(connection: NWConnection, id: String, urlsToSend: [URL], senderName: String, senderEndpointID: [UInt8]) {
        self.urlsToSend = urlsToSend
        self.senderName = senderName
        self.senderEndpointID = senderEndpointID
        super.init(connection: connection, id: id)
    }

    deinit {
        try? currentTransfer?.handle?.close()
        for t in queue { try? t.handle?.close() }
    }

    func cancel() {
        cancelled = true
        if encryptionDone {
            var cancel = Sharing_Nearby_Frame()
            cancel.version = .v1
            cancel.v1 = Sharing_Nearby_V1Frame()
            cancel.v1.type = .cancel
            try? sendTransferSetupFrame(cancel)
        }
        try? sendDisconnectionAndDisconnect()
    }

    override func connectionReady() {
        print("QS-out: 🤝 conectado, iniciando handshake")
        do {
            try sendConnectionRequest()
            try sendUkey2ClientInit()
        } catch {
            lastError = error
            protocolError()
        }
    }

    override func isServer() -> Bool { false }

    override func processReceivedFrame(frameData: Data) {
        do {
            switch currentState {
            case .initial:
                protocolError()
            case .sentUkeyClientInit:
                try processUkey2ServerInit(frame: try Securegcm_Ukey2Message(serializedData: frameData), raw: frameData)
            case .sentUkeyClientFinish:
                try processConnectionResponse(frame: try Location_Nearby_Connections_OfflineFrame(serializedData: frameData))
            default:
                let smsg = try Securemessage_SecureMessage(serializedData: frameData)
                try decryptAndProcessReceivedSecureMessage(smsg)
            }
        } catch {
            if case NearbyError.ukey2 = error {
            } else if currentState == .sentUkeyClientInit {
                sendUkey2Alert(type: .badMessage)
            }
            lastError = error
            protocolError()
        }
    }

    override func processTransferSetupFrame(_ frame: Sharing_Nearby_Frame) throws {
        if frame.hasV1 && frame.v1.hasType, case .cancel = frame.v1.type {
            try sendDisconnectionAndDisconnect()
            delegate?.outboundConnection(self, failedWith: NearbyError.canceled(reason: "Cancelado por el receptor"))
            return
        }
        switch currentState {
        case .sentPairedKeyEncryption: try processPairedKeyEncryption(frame: frame)
        case .sentPairedKeyResult: try processPairedKeyResult(frame: frame)
        case .sentIntroduction: try processConsent(frame: frame)
        case .sendingFiles: break
        default: print("QS-out: estado inesperado \(currentState)")
        }
    }

    override func protocolError() {
        super.protocolError()
        if let err = lastError {
            delegate?.outboundConnection(self, failedWith: err)
        }
    }

    private func sendConnectionRequest() throws {
        var frame = Location_Nearby_Connections_OfflineFrame()
        frame.version = .v1
        frame.v1 = Location_Nearby_Connections_V1Frame()
        frame.v1.type = .connectionRequest
        frame.v1.connectionRequest = Location_Nearby_Connections_ConnectionRequestFrame()
        frame.v1.connectionRequest.endpointID = String(bytes: senderEndpointID, encoding: .ascii) ?? "0000"
        frame.v1.connectionRequest.endpointName = senderName
        frame.v1.connectionRequest.endpointInfo = QSEndpointInfo(name: senderName, deviceType: .computer).serialize()
        frame.v1.connectionRequest.mediums = [.wifiLan]
        sendFrameAsync(try frame.serializedData())
    }

    private func sendUkey2ClientInit() throws {
        let domain = Domain.instance(curve: .EC256r1)
        let (pubKey, privKey) = domain.makeKeyPair()
        publicKey = pubKey
        privateKey = privKey

        var finishFrame = Securegcm_Ukey2Message()
        finishFrame.messageType = .clientFinish
        var finish = Securegcm_Ukey2ClientFinished()
        var pkey = Securemessage_GenericPublicKey()
        pkey.type = .ecP256
        pkey.ecP256PublicKey = Securemessage_EcP256PublicKey()
        pkey.ecP256PublicKey.x = Data(pubKey.w.x.asSignedBytes())
        pkey.ecP256PublicKey.y = Data(pubKey.w.y.asSignedBytes())
        finish.publicKey = try pkey.serializedData()
        finishFrame.messageData = try finish.serializedData()
        ukeyClientFinishMsgData = try finishFrame.serializedData()

        var frame = Securegcm_Ukey2Message()
        frame.messageType = .clientInit
        var clientInit = Securegcm_Ukey2ClientInit()
        clientInit.version = 1
        clientInit.random = Data.randomData(length: 32)
        clientInit.nextProtocol = "AES_256_CBC-HMAC_SHA256"
        var sha = SHA512()
        sha.update(data: ukeyClientFinishMsgData!)
        var commitment = Securegcm_Ukey2ClientInit.CipherCommitment()
        commitment.commitment = Data(sha.finalize())
        commitment.handshakeCipher = .p256Sha512
        clientInit.cipherCommitments.append(commitment)
        frame.messageData = try clientInit.serializedData()

        ukeyClientInitMsgData = try frame.serializedData()
        sendFrameAsync(ukeyClientInitMsgData!)
        currentState = .sentUkeyClientInit
    }

    private func processUkey2ServerInit(frame: Securegcm_Ukey2Message, raw: Data) throws {
        ukeyServerInitMsgData = raw
        guard frame.messageType == .serverInit else { sendUkey2Alert(type: .badMessageType); throw NearbyError.ukey2 }
        let serverInit = try Securegcm_Ukey2ServerInit(serializedData: frame.messageData)
        guard serverInit.version == 1 else { sendUkey2Alert(type: .badVersion); throw NearbyError.ukey2 }
        guard serverInit.random.count == 32 else { sendUkey2Alert(type: .badRandom); throw NearbyError.ukey2 }
        guard serverInit.handshakeCipher == .p256Sha512 else { sendUkey2Alert(type: .badHandshakeCipher); throw NearbyError.ukey2 }

        let serverKey = try Securemessage_GenericPublicKey(serializedData: serverInit.publicKey)
        try finalizeKeyExchange(peerKey: serverKey)
        sendFrameAsync(ukeyClientFinishMsgData!)
        currentState = .sentUkeyClientFinish

        var resp = Location_Nearby_Connections_OfflineFrame()
        resp.version = .v1
        resp.v1 = Location_Nearby_Connections_V1Frame()
        resp.v1.type = .connectionResponse
        resp.v1.connectionResponse = Location_Nearby_Connections_ConnectionResponseFrame()
        resp.v1.connectionResponse.response = .accept
        resp.v1.connectionResponse.status = 0
        resp.v1.connectionResponse.osInfo = Location_Nearby_Connections_OsInfo()
        resp.v1.connectionResponse.osInfo.type = .apple
        sendFrameAsync(try resp.serializedData())

        encryptionDone = true
        print("QS-out: 🔐 UKEY2 completado")
        delegate?.outboundConnectionWasEstablished(self)
    }

    private func processConnectionResponse(frame: Location_Nearby_Connections_OfflineFrame) throws {
        guard frame.version == .v1 else { throw NearbyError.protocolError("Versión de frame inesperada") }
        guard frame.v1.type == .connectionResponse else { throw NearbyError.protocolError("Tipo de frame inesperado") }
        guard frame.v1.connectionResponse.response == .accept else { throw NearbyError.protocolError("Conexión rechazada por el receptor") }

        var pairedEncryption = Sharing_Nearby_Frame()
        pairedEncryption.version = .v1
        pairedEncryption.v1 = Sharing_Nearby_V1Frame()
        pairedEncryption.v1.type = .pairedKeyEncryption
        pairedEncryption.v1.pairedKeyEncryption = Sharing_Nearby_PairedKeyEncryptionFrame()
        pairedEncryption.v1.pairedKeyEncryption.secretIDHash = Data.randomData(length: 6)
        pairedEncryption.v1.pairedKeyEncryption.signedData = Data.randomData(length: 72)
        try sendTransferSetupFrame(pairedEncryption)
        currentState = .sentPairedKeyEncryption
    }

    private func processPairedKeyEncryption(frame: Sharing_Nearby_Frame) throws {
        guard frame.hasV1, frame.v1.hasPairedKeyEncryption else { throw NearbyError.requiredFieldMissing("v1.pairedKeyEncryption") }
        var pairedResult = Sharing_Nearby_Frame()
        pairedResult.version = .v1
        pairedResult.v1 = Sharing_Nearby_V1Frame()
        pairedResult.v1.type = .pairedKeyResult
        pairedResult.v1.pairedKeyResult = Sharing_Nearby_PairedKeyResultFrame()
        pairedResult.v1.pairedKeyResult.status = .unable
        try sendTransferSetupFrame(pairedResult)
        currentState = .sentPairedKeyResult
    }

    private func processPairedKeyResult(frame: Sharing_Nearby_Frame) throws {
        guard frame.hasV1, frame.v1.hasPairedKeyResult else { throw NearbyError.requiredFieldMissing("v1.pairedKeyResult") }

        var introduction = Sharing_Nearby_Frame()
        introduction.version = .v1
        introduction.v1.type = .introduction
        for url in urlsToSend where url.isFileURL {
            var meta = Sharing_Nearby_FileMetadata()
            meta.name = Self.sanitizeFileName(url.lastPathComponent)
            let attrs = try FileManager.default.attributesOfItem(atPath: url.path)
            meta.size = (attrs[FileAttributeKey.size] as! NSNumber).int64Value
            meta.mimeType = "application/octet-stream"
            if let type = UTType(filenameExtension: url.pathExtension), let m = type.preferredMIMEType {
                meta.mimeType = m
            }
            if meta.mimeType.hasPrefix("image/") { meta.type = .image }
            else if meta.mimeType.hasPrefix("video/") { meta.type = .video }
            else if meta.mimeType.hasPrefix("audio/") { meta.type = .audio }
            else if url.pathExtension.lowercased() == "apk" { meta.type = .androidApp }
            else { meta.type = .unknown }
            meta.payloadID = Int64.random(in: Int64.min...Int64.max)
            queue.append(OutgoingFileTransfer(url: url, payloadID: meta.payloadID,
                                              handle: try FileHandle(forReadingFrom: url),
                                              totalBytes: meta.size, currentOffset: 0))
            introduction.v1.introduction.fileMetadata.append(meta)
            totalBytesToSend += meta.size
        }
        print("QS-out: 📨 Introduction enviada (\(queue.count) archivo/s)")
        try sendTransferSetupFrame(introduction)
        currentState = .sentIntroduction
    }

    private func processConsent(frame: Sharing_Nearby_Frame) throws {
        guard frame.version == .v1, frame.v1.type == .response else { throw NearbyError.requiredFieldMissing("v1.type==response") }
        switch frame.v1.connectionResponse.status {
        case .accept:
            currentState = .sendingFiles
            print("QS-out: ✅ receptor aceptó, enviando archivos")
            delegate?.outboundConnectionTransferAccepted(self)
            try sendNextFileChunk()
        case .reject, .unknown:
            delegate?.outboundConnection(self, failedWith: NearbyError.canceled(reason: "Rechazado por el receptor"))
            try sendDisconnectionAndDisconnect()
        case .notEnoughSpace:
            delegate?.outboundConnection(self, failedWith: NearbyError.canceled(reason: "Sin espacio en el receptor"))
            try sendDisconnectionAndDisconnect()
        case .timedOut:
            delegate?.outboundConnection(self, failedWith: NearbyError.canceled(reason: "Tiempo agotado"))
            try sendDisconnectionAndDisconnect()
        case .unsupportedAttachmentType:
            delegate?.outboundConnection(self, failedWith: NearbyError.canceled(reason: "Tipo no soportado"))
            try sendDisconnectionAndDisconnect()
        }
    }

    private func sendNextFileChunk() throws {
        if cancelled { return }
        if currentTransfer == nil || currentTransfer?.currentOffset == currentTransfer?.totalBytes {
            if currentTransfer?.handle != nil { try currentTransfer?.handle?.close() }
            if queue.isEmpty {
                try sendDisconnectionAndDisconnect()
                delegate?.outboundConnectionTransferFinished(self)
                return
            }
            currentTransfer = queue.removeFirst()
        }

        let fileBuffer = (try currentTransfer!.handle!.read(upToCount: 512 * 1024)) ?? Data()

        var transfer = Location_Nearby_Connections_PayloadTransferFrame()
        transfer.packetType = .data
        transfer.payloadChunk.offset = currentTransfer!.currentOffset
        transfer.payloadChunk.flags = 0
        transfer.payloadChunk.body = fileBuffer
        transfer.payloadHeader.id = currentTransfer!.payloadID
        transfer.payloadHeader.type = .file
        transfer.payloadHeader.totalSize = Int64(currentTransfer!.totalBytes)
        transfer.payloadHeader.isSensitive = false
        currentTransfer!.currentOffset += Int64(fileBuffer.count)

        var wrapper = Location_Nearby_Connections_OfflineFrame()
        wrapper.version = .v1
        wrapper.v1 = Location_Nearby_Connections_V1Frame()
        wrapper.v1.type = .payloadTransfer
        wrapper.v1.payloadTransfer = transfer
        try encryptAndSendOfflineFrame(wrapper, completion: {
            do { try self.sendNextFileChunk() }
            catch { self.lastError = error; self.protocolError() }
        })
        totalBytesSent += Int64(fileBuffer.count)
        delegate?.outboundConnection(self, progress: totalBytesToSend > 0 ? Double(totalBytesSent) / Double(totalBytesToSend) : 0)

        if currentTransfer!.currentOffset == currentTransfer!.totalBytes {
            // Señal de fin de archivo (chunk vacío con flag LAST_CHUNK).
            var eof = Location_Nearby_Connections_PayloadTransferFrame()
            eof.packetType = .data
            eof.payloadChunk.offset = currentTransfer!.currentOffset
            eof.payloadChunk.flags = 1
            eof.payloadHeader.id = currentTransfer!.payloadID
            eof.payloadHeader.type = .file
            eof.payloadHeader.totalSize = Int64(currentTransfer!.totalBytes)
            eof.payloadHeader.isSensitive = false
            var w = Location_Nearby_Connections_OfflineFrame()
            w.version = .v1
            w.v1 = Location_Nearby_Connections_V1Frame()
            w.v1.type = .payloadTransfer
            w.v1.payloadTransfer = eof
            try encryptAndSendOfflineFrame(w)
        }
    }

    private static func sanitizeFileName(_ name: String) -> String {
        name.replacingOccurrences(of: "[\\/\\\\?%\\*:\\|\"<>=]", with: "_", options: .regularExpression)
    }
}

private struct OutgoingFileTransfer {
    let url: URL
    let payloadID: Int64
    let handle: FileHandle?
    let totalBytes: Int64
    var currentOffset: Int64
}
