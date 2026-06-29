import Foundation
import Network

struct ReceiveProgress {
    let sender: String
    let fileIndex: Int
    let fileCount: Int
    let fileName: String
    let overallReceived: Int64
    let overallTotal: Int64
}

/// Servidor TCP que anuncia el servicio Bonjour y recibe lotes de archivos.
final class TransferServer {
    private var listener: NWListener?
    private(set) var serviceName = ""

    private let askAccept: (TransferHeader, String) async -> Bool
    private let saveDirectory: () -> URL
    private let onProgress: (ReceiveProgress) -> Void
    private let onComplete: ([String], TransferHeader) -> Void
    private let onError: (String) -> Void

    init(
        askAccept: @escaping (TransferHeader, String) async -> Bool,
        saveDirectory: @escaping () -> URL,
        onProgress: @escaping (ReceiveProgress) -> Void,
        onComplete: @escaping ([String], TransferHeader) -> Void,
        onError: @escaping (String) -> Void
    ) {
        self.askAccept = askAccept
        self.saveDirectory = saveDirectory
        self.onProgress = onProgress
        self.onComplete = onComplete
        self.onError = onError
    }

    func start(displayName: String, os: String) throws {
        stop()
        let listener = try NWListener(using: .tcp)
        let txt = NWTXTRecord(["v": "\(Proto.version)", "name": displayName, "os": os])
        let svcName = "WiwyTransfer-\(displayName)"
        listener.service = NWListener.Service(name: svcName, type: Proto.serviceType, txtRecord: txt)
        serviceName = svcName

        listener.newConnectionHandler = { [weak self] conn in
            guard let self = self else { conn.cancel(); return }
            Task { await self.handle(conn) }
        }
        self.listener = listener
        listener.start(queue: .global(qos: .userInitiated))
    }

    func restart(displayName: String, os: String) {
        try? start(displayName: displayName, os: os)
    }

    func stop() {
        listener?.cancel()
        listener = nil
    }

    private func handle(_ nwConn: NWConnection) async {
        let conn = WiwyConnection(nwConn)
        do {
            try await conn.start()
            let headerLine = try await conn.readLine()
            let header = try JSONDecoder().decode(TransferHeader.self, from: Data(headerLine.utf8))
            let peerAddr = describe(nwConn.endpoint)

            let accepted = await askAccept(header, peerAddr)
            try await conn.send(try Decision(accept: accepted, reason: nil).jsonLine())
            guard accepted else { conn.cancel(); return }

            let dir = saveDirectory()
            try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)

            var saved: [String] = []
            var overall: Int64 = 0
            let total = header.totalBytes

            for (idx, meta) in header.files.enumerated() {
                let name = TransferHeader.sanitize(meta.name)
                let url = uniqueURL(in: dir, name: name)
                FileManager.default.createFile(atPath: url.path, contents: nil)
                let handle = try FileHandle(forWritingTo: url)
                do {
                    try await conn.readBytes(count: meta.size, to: handle) { n in
                        overall += Int64(n)
                        self.onProgress(ReceiveProgress(
                            sender: header.sender,
                            fileIndex: idx + 1,
                            fileCount: header.files.count,
                            fileName: name,
                            overallReceived: overall,
                            overallTotal: total
                        ))
                    }
                    try handle.close()
                    saved.append(url.path)
                } catch {
                    try? handle.close()
                    try? FileManager.default.removeItem(at: url)
                    throw error
                }
            }

            try await conn.send(try TransferResult(ok: true, received: saved.count, error: nil).jsonLine())
            conn.cancel()
            onComplete(saved, header)
        } catch {
            let msg = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
            if let line = try? TransferResult(ok: false, received: 0, error: msg).jsonLine() {
                try? await conn.send(line)
            }
            conn.cancel()
            onError(msg)
        }
    }

    private func uniqueURL(in dir: URL, name: String) -> URL {
        let fm = FileManager.default
        var candidate = dir.appendingPathComponent(name)
        guard fm.fileExists(atPath: candidate.path) else { return candidate }
        let ext = (name as NSString).pathExtension
        let base = (name as NSString).deletingPathExtension
        var i = 1
        repeat {
            let newName = ext.isEmpty ? "\(base) (\(i))" : "\(base) (\(i)).\(ext)"
            candidate = dir.appendingPathComponent(newName)
            i += 1
        } while fm.fileExists(atPath: candidate.path)
        return candidate
    }

    private func describe(_ endpoint: NWEndpoint) -> String {
        switch endpoint {
        case let .hostPort(host, _): return "\(host)"
        default: return "\(endpoint)"
        }
    }
}
