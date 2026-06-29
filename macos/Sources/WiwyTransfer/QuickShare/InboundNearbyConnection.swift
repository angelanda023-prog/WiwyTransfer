// Portado/adaptado de NearDrop (The Unlicense): https://github.com/grishka/NearDrop
// Receptor: maneja el handshake UKEY2 (rol servidor), el consentimiento y la
// recepción de archivos, guardándolos en Descargas/WiwyTransfer.

import Foundation
import Network
import CryptoKit
import CommonCrypto

import SwiftECC
import BigInt

protocol InboundNearbyConnectionDelegate: AnyObject {
    func obtainUserConsent(for transfer: TransferMetadata, from device: RemoteDeviceInfo, connection: InboundNearbyConnection)
    func connectionProgress(_ connection: InboundNearbyConnection, received: Int64, total: Int64, currentFile: String)
    func connectionFinished(_ connection: InboundNearbyConnection, savedPaths: [String], from device: RemoteDeviceInfo)
    func connectionWasTerminated(connection: InboundNearbyConnection, error: Error?)
}

final class InboundNearbyConnection: NearbyConnection {

    private var currentState: State = .initial
    weak var delegate: InboundNearbyConnectionDelegate?
    private var cipherCommitment: Data?

    private let saveDirectory: URL
    private var savedPaths: [String] = []
    private var totalBytes: Int64 = 0
    private var receivedBytes: Int64 = 0
    private var currentFileName: String = ""

    enum State {
        case initial, receivedConnectionRequest, sentUkeyServerInit, receivedUkeyClientFinish,
             sentConnectionResponse, sentPairedKeyResult, receivedPairedKeyResult,
             waitingForUserConsent, receivingFiles, disconnected
    }

    init(connection: NWConnection, id: String, saveDirectory: URL) {
        self.saveDirectory = saveDirectory
        super.init(connection: connection, id: id)
    }

    override func handleConnectionClosure() {
        super.handleConnectionClosure()
        let wasReceiving = currentState == .receivingFiles
        currentState = .disconnected
        try? deletePartiallyReceivedFiles()
        DispatchQueue.main.async {
            self.delegate?.connectionWasTerminated(connection: self, error: wasReceiving ? self.lastError : self.lastError)
        }
    }

    override internal func processReceivedFrame(frameData: Data) {
        do {
            switch currentState {
            case .initial:
                let frame = try Location_Nearby_Connections_OfflineFrame(serializedData: frameData)
                try processConnectionRequestFrame(frame)
            case .receivedConnectionRequest:
                let msg = try Securegcm_Ukey2Message(serializedData: frameData)
                ukeyClientInitMsgData = frameData
                try processUkey2ClientInit(msg)
            case .sentUkeyServerInit:
                let msg = try Securegcm_Ukey2Message(serializedData: frameData)
                try processUkey2ClientFinish(msg, raw: frameData)
            case .receivedUkeyClientFinish:
                let frame = try Location_Nearby_Connections_OfflineFrame(serializedData: frameData)
                try processConnectionResponseFrame(frame)
            default:
                let smsg = try Securemessage_SecureMessage(serializedData: frameData)
                try decryptAndProcessReceivedSecureMessage(smsg)
            }
        } catch {
            lastError = error
            print("QS: error de deserialización: \(error) en estado \(currentState)")
            protocolError()
        }
    }

    override internal func processTransferSetupFrame(_ frame: Sharing_Nearby_Frame) throws {
        if frame.hasV1 && frame.v1.hasType, case .cancel = frame.v1.type {
            print("QS: transferencia cancelada")
            try sendDisconnectionAndDisconnect()
            return
        }
        switch currentState {
        case .sentConnectionResponse:
            try processPairedKeyEncryptionFrame(frame)
        case .sentPairedKeyResult:
            try processPairedKeyResultFrame(frame)
        case .receivedPairedKeyResult:
            try processIntroductionFrame(frame)
        default:
            print("QS: estado inesperado en processTransferSetupFrame: \(currentState)")
        }
    }

    override func isServer() -> Bool { true }

    override func processFileChunk(frame: Location_Nearby_Connections_PayloadTransferFrame) throws {
        let id = frame.payloadHeader.id
        guard let fileInfo = transferredFiles[id] else { throw NearbyError.protocolError("ID de payload \(id) desconocido") }
        let currentOffset = fileInfo.bytesTransferred
        guard frame.payloadChunk.offset == currentOffset else { throw NearbyError.protocolError("Offset inválido \(frame.payloadChunk.offset), esperado \(currentOffset)") }
        guard currentOffset + Int64(frame.payloadChunk.body.count) <= fileInfo.meta.size else { throw NearbyError.protocolError("El tamaño del archivo excede lo anunciado") }
        if frame.payloadChunk.body.count > 0 {
            fileInfo.fileHandle?.write(frame.payloadChunk.body)
            transferredFiles[id]!.bytesTransferred += Int64(frame.payloadChunk.body.count)
            receivedBytes += Int64(frame.payloadChunk.body.count)
            reportProgress(fileName: fileInfo.meta.name)
        } else if (frame.payloadChunk.flags & 1) == 1 {
            try fileInfo.fileHandle?.close()
            transferredFiles[id]!.fileHandle = nil
            savedPaths.append(fileInfo.destinationURL.path)
            transferredFiles.removeValue(forKey: id)
            if transferredFiles.isEmpty {
                finishTransfer()
                try sendDisconnectionAndDisconnect()
            }
        }
    }

    override func processBytesPayload(payload: Data, id: Int64) throws -> Bool {
        if let fileInfo = transferredFiles[id] {
            fileInfo.fileHandle?.write(payload)
            transferredFiles[id]!.bytesTransferred += Int64(payload.count)
            receivedBytes += Int64(payload.count)
            reportProgress(fileName: fileInfo.meta.name)
            try fileInfo.fileHandle?.close()
            transferredFiles[id]!.fileHandle = nil
            savedPaths.append(fileInfo.destinationURL.path)
            transferredFiles.removeValue(forKey: id)
            if transferredFiles.isEmpty {
                finishTransfer()
                try sendDisconnectionAndDisconnect()
            }
            return true
        }
        return false
    }

    private func reportProgress(fileName: String) {
        let total = totalBytes
        let received = receivedBytes
        DispatchQueue.main.async {
            self.delegate?.connectionProgress(self, received: received, total: total, currentFile: fileName)
        }
    }

    private func finishTransfer() {
        let paths = savedPaths
        let device = remoteDeviceInfo
        DispatchQueue.main.async {
            if let device = device {
                self.delegate?.connectionFinished(self, savedPaths: paths, from: device)
            }
        }
    }

    private func processConnectionRequestFrame(_ frame: Location_Nearby_Connections_OfflineFrame) throws {
        guard frame.hasV1 && frame.v1.hasConnectionRequest && frame.v1.connectionRequest.hasEndpointInfo else { throw NearbyError.requiredFieldMissing("connectionRequest.endpointInfo") }
        guard case .connectionRequest = frame.v1.type else { throw NearbyError.protocolError("Tipo de frame inesperado \(frame.v1.type)") }
        let endpointInfo = frame.v1.connectionRequest.endpointInfo
        guard endpointInfo.count > 17 else { throw NearbyError.protocolError("Endpoint info demasiado corto") }
        let b = [UInt8](endpointInfo)
        let deviceNameLength = Int(b[17])
        guard b.count >= deviceNameLength + 18 else { throw NearbyError.protocolError("Endpoint info no contiene el nombre") }
        guard let deviceName = String(bytes: b[18..<(18 + deviceNameLength)], encoding: .utf8) else { throw NearbyError.protocolError("Nombre de dispositivo no es UTF-8 válido") }
        let rawDeviceType = Int(b[0] & 7) >> 1
        remoteDeviceInfo = RemoteDeviceInfo(name: deviceName, type: RemoteDeviceInfo.DeviceType.fromRawValue(value: rawDeviceType))
        currentState = .receivedConnectionRequest
    }

    private func processUkey2ClientInit(_ msg: Securegcm_Ukey2Message) throws {
        guard msg.hasMessageType, msg.hasMessageData else { throw NearbyError.requiredFieldMissing("clientInit ukey2message.type|data") }
        guard case .clientInit = msg.messageType else { sendUkey2Alert(type: .badMessageType); throw NearbyError.ukey2 }
        let clientInit: Securegcm_Ukey2ClientInit
        do {
            clientInit = try Securegcm_Ukey2ClientInit(serializedData: msg.messageData)
        } catch {
            sendUkey2Alert(type: .badMessageData); throw NearbyError.ukey2
        }
        guard clientInit.version == 1 else { sendUkey2Alert(type: .badVersion); throw NearbyError.ukey2 }
        guard clientInit.random.count == 32 else { sendUkey2Alert(type: .badRandom); throw NearbyError.ukey2 }
        var found = false
        for commitment in clientInit.cipherCommitments {
            if case .p256Sha512 = commitment.handshakeCipher {
                found = true
                cipherCommitment = commitment.commitment
                break
            }
        }
        guard found else { sendUkey2Alert(type: .badHandshakeCipher); throw NearbyError.ukey2 }
        guard clientInit.nextProtocol == "AES_256_CBC-HMAC_SHA256" else { sendUkey2Alert(type: .badNextProtocol); throw NearbyError.ukey2 }

        let domain = Domain.instance(curve: .EC256r1)
        let (pubKey, privKey) = domain.makeKeyPair()
        publicKey = pubKey
        privateKey = privKey

        var serverInit = Securegcm_Ukey2ServerInit()
        serverInit.version = 1
        serverInit.random = Data.randomData(length: 32)
        serverInit.handshakeCipher = .p256Sha512

        var pkey = Securemessage_GenericPublicKey()
        pkey.type = .ecP256
        pkey.ecP256PublicKey = Securemessage_EcP256PublicKey()
        pkey.ecP256PublicKey.x = Data(pubKey.w.x.asSignedBytes())
        pkey.ecP256PublicKey.y = Data(pubKey.w.y.asSignedBytes())
        serverInit.publicKey = try pkey.serializedData()

        var serverInitMsg = Securegcm_Ukey2Message()
        serverInitMsg.messageType = .serverInit
        serverInitMsg.messageData = try serverInit.serializedData()
        let serverInitData = try serverInitMsg.serializedData()
        ukeyServerInitMsgData = serverInitData
        sendFrameAsync(serverInitData)
        currentState = .sentUkeyServerInit
    }

    private func processUkey2ClientFinish(_ msg: Securegcm_Ukey2Message, raw: Data) throws {
        guard msg.hasMessageType, msg.hasMessageData else { throw NearbyError.requiredFieldMissing("clientFinish ukey2message.type|data") }
        guard case .clientFinish = msg.messageType else { throw NearbyError.ukey2 }

        var sha = SHA512()
        sha.update(data: raw)
        guard cipherCommitment == Data(sha.finalize()) else { throw NearbyError.ukey2 }

        let clientFinish = try Securegcm_Ukey2ClientFinished(serializedData: msg.messageData)
        guard clientFinish.hasPublicKey else { throw NearbyError.requiredFieldMissing("ukey2clientFinish.publicKey") }
        let clientKey = try Securemessage_GenericPublicKey(serializedData: clientFinish.publicKey)

        try finalizeKeyExchange(peerKey: clientKey)
        currentState = .receivedUkeyClientFinish
    }

    private func processConnectionResponseFrame(_ frame: Location_Nearby_Connections_OfflineFrame) throws {
        guard frame.hasV1, frame.v1.hasType else { throw NearbyError.requiredFieldMissing("offlineFrame.v1.type") }
        if case .connectionResponse = frame.v1.type {
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

            var pairedEncryption = Sharing_Nearby_Frame()
            pairedEncryption.version = .v1
            pairedEncryption.v1 = Sharing_Nearby_V1Frame()
            pairedEncryption.v1.type = .pairedKeyEncryption
            pairedEncryption.v1.pairedKeyEncryption = Sharing_Nearby_PairedKeyEncryptionFrame()
            pairedEncryption.v1.pairedKeyEncryption.secretIDHash = Data.randomData(length: 6)
            pairedEncryption.v1.pairedKeyEncryption.signedData = Data.randomData(length: 72)
            try sendTransferSetupFrame(pairedEncryption)
            currentState = .sentConnectionResponse
        } else {
            print("QS: offline frame plano no manejado: \(frame)")
        }
    }

    private func processPairedKeyEncryptionFrame(_ frame: Sharing_Nearby_Frame) throws {
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

    private func processPairedKeyResultFrame(_ frame: Sharing_Nearby_Frame) throws {
        guard frame.hasV1, frame.v1.hasPairedKeyResult else { throw NearbyError.requiredFieldMissing("v1.pairedKeyResult") }
        currentState = .receivedPairedKeyResult
    }

    private func makeFileDestinationURL(_ initialDest: URL) -> URL {
        var dest = initialDest
        if FileManager.default.fileExists(atPath: dest.path) {
            var counter = 1
            var path: String
            let ext = dest.pathExtension
            let baseUrl = dest.deletingPathExtension()
            repeat {
                path = "\(baseUrl.path) (\(counter))"
                if !ext.isEmpty { path += ".\(ext)" }
                counter += 1
            } while FileManager.default.fileExists(atPath: path)
            dest = URL(fileURLWithPath: path)
        }
        return dest
    }

    private func processIntroductionFrame(_ frame: Sharing_Nearby_Frame) throws {
        guard frame.hasV1, frame.v1.hasIntroduction else { throw NearbyError.requiredFieldMissing("v1.introduction") }
        currentState = .waitingForUserConsent
        guard frame.v1.introduction.fileMetadata.count > 0, frame.v1.introduction.textMetadata.isEmpty else {
            rejectTransfer(with: .unsupportedAttachmentType)
            return
        }
        try FileManager.default.createDirectory(at: saveDirectory, withIntermediateDirectories: true)
        for file in frame.v1.introduction.fileMetadata {
            let dest = makeFileDestinationURL(saveDirectory.appendingPathComponent(file.name))
            let info = InternalFileInfo(
                meta: FileMetadata(name: file.name, size: file.size, mimeType: file.mimeType),
                payloadID: file.payloadID,
                destinationURL: dest)
            transferredFiles[file.payloadID] = info
        }
        totalBytes = transferredFiles.values.reduce(0) { $0 + $1.meta.size }
        receivedBytes = 0
        let metadata = TransferMetadata(files: transferredFiles.map { $0.value.meta }, id: id, pinCode: pinCode)
        let device = remoteDeviceInfo!
        DispatchQueue.main.async {
            self.delegate?.obtainUserConsent(for: metadata, from: device, connection: self)
        }
    }

    func submitUserConsent(accepted: Bool) {
        DispatchQueue.global(qos: .utility).async {
            if accepted { self.acceptTransfer() } else { self.rejectTransfer() }
        }
    }

    private func acceptTransfer() {
        do {
            for (id, file) in transferredFiles {
                FileManager.default.createFile(atPath: file.destinationURL.path, contents: nil)
                let handle = try FileHandle(forWritingTo: file.destinationURL)
                transferredFiles[id]!.fileHandle = handle
                transferredFiles[id]!.created = true
            }
            var frame = Sharing_Nearby_Frame()
            frame.version = .v1
            frame.v1.type = .response
            frame.v1.connectionResponse.status = .accept
            currentState = .receivingFiles
            try sendTransferSetupFrame(frame)
        } catch {
            lastError = error
            protocolError()
        }
    }

    private func rejectTransfer(with reason: Sharing_Nearby_ConnectionResponseFrame.Status = .reject) {
        var frame = Sharing_Nearby_Frame()
        frame.version = .v1
        frame.v1.type = .response
        frame.v1.connectionResponse.status = reason
        do {
            try sendTransferSetupFrame(frame)
            try sendDisconnectionAndDisconnect()
        } catch {
            print("QS: error rechazando: \(error)")
            protocolError()
        }
    }

    private func deletePartiallyReceivedFiles() throws {
        for (_, file) in transferredFiles where file.created {
            try? FileManager.default.removeItem(at: file.destinationURL)
        }
    }
}
