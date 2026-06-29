import Foundation
import Network

/// Cliente TCP que envía un lote de archivos a un par descubierto.
final class TransferClient {
    private let senderName: String
    private let os: String

    init(senderName: String, os: String) {
        self.senderName = senderName
        self.os = os
    }

    /// Envía [urls] al [peer]. Lanza WiwyError.declined si el receptor rechaza.
    func send(
        to peer: Peer,
        files urls: [URL],
        onProgress: @escaping (Int64, Int64) -> Void
    ) async throws -> Int {
        let metas = try urls.map { url -> (URL, FileMeta) in
            let size = (try url.resourceValues(forKeys: [.fileSizeKey]).fileSize).map(Int64.init) ?? 0
            return (url, FileMeta(name: url.lastPathComponent, size: size))
        }
        let total = metas.reduce(Int64(0)) { $0 + $1.1.size }

        let conn = WiwyConnection(NWConnection(to: peer.endpoint, using: .tcp))
        try await conn.start()
        defer { conn.cancel() }

        let header = TransferHeader(v: Proto.version, sender: senderName, os: os, files: metas.map { $0.1 })
        try await conn.send(try header.jsonLine())

        let decisionLine = try await conn.readLine()
        let decision = try JSONDecoder().decode(Decision.self, from: Data(decisionLine.utf8))
        guard decision.accept else { throw WiwyError.declined(decision.reason) }

        var sent: Int64 = 0
        for (url, _) in metas {
            let handle = try FileHandle(forReadingFrom: url)
            defer { try? handle.close() }
            while true {
                let chunk = try handle.read(upToCount: Proto.bufferSize) ?? Data()
                if chunk.isEmpty { break }
                try await conn.send(chunk)
                sent += Int64(chunk.count)
                onProgress(sent, total)
            }
        }

        let resultLine = try await conn.readLine()
        let result = try JSONDecoder().decode(TransferResult.self, from: Data(resultLine.utf8))
        guard result.ok else { throw WiwyError.declined(result.error) }
        return result.received
    }
}
