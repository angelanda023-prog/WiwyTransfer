import Foundation
import Network

/// Envoltorio async/await sobre NWConnection con lectura de líneas JSON y de bloques de bytes.
final class WiwyConnection {
    private let connection: NWConnection
    private let queue = DispatchQueue(label: "wiwy.connection")
    private var buffer = Data()

    init(_ connection: NWConnection) {
        self.connection = connection
    }

    func start() async throws {
        try await withCheckedThrowingContinuation { (cont: CheckedContinuation<Void, Error>) in
            connection.stateUpdateHandler = { state in
                switch state {
                case .ready:
                    cont.resume()
                    self.connection.stateUpdateHandler = nil
                case .failed(let error):
                    cont.resume(throwing: error)
                    self.connection.stateUpdateHandler = nil
                case .cancelled:
                    cont.resume(throwing: WiwyError.connectionClosed)
                    self.connection.stateUpdateHandler = nil
                default:
                    break
                }
            }
            connection.start(queue: queue)
        }
    }

    func cancel() {
        connection.cancel()
    }

    func send(_ data: Data) async throws {
        try await withCheckedThrowingContinuation { (cont: CheckedContinuation<Void, Error>) in
            connection.send(content: data, completion: .contentProcessed { error in
                if let error = error { cont.resume(throwing: error) }
                else { cont.resume() }
            })
        }
    }

    /// Lee bytes crudos de la red hasta llenar el buffer interno al menos una vez.
    private func fill() async throws {
        let chunk: Data = try await withCheckedThrowingContinuation { cont in
            connection.receive(minimumIncompleteLength: 1, maximumLength: Proto.bufferSize) { data, _, isComplete, error in
                if let error = error { cont.resume(throwing: error); return }
                if let data = data, !data.isEmpty { cont.resume(returning: data); return }
                if isComplete { cont.resume(throwing: WiwyError.connectionClosed); return }
                cont.resume(returning: Data())
            }
        }
        buffer.append(chunk)
    }

    /// Lee una línea (terminada en '\n') y la decodifica como UTF-8 sin el salto.
    func readLine() async throws -> String {
        while true {
            if let idx = buffer.firstIndex(of: 0x0A) {
                let lineData = buffer.subdata(in: buffer.startIndex..<idx)
                buffer.removeSubrange(buffer.startIndex...idx)
                return String(data: lineData, encoding: .utf8) ?? ""
            }
            try await fill()
        }
    }

    /// Lee exactamente [count] bytes, escribiéndolos en [handle] por bloques. Reporta avance.
    func readBytes(count: Int64, to handle: FileHandle, onChunk: (Int) -> Void) async throws {
        var remaining = count
        while remaining > 0 {
            if buffer.isEmpty { try await fill() }
            let take = Int(min(Int64(buffer.count), remaining))
            let slice = buffer.prefix(take)
            try handle.write(contentsOf: slice)
            buffer.removeFirst(take)
            remaining -= Int64(take)
            onChunk(take)
        }
    }
}

extension Encodable {
    /// Codifica a JSON en una sola línea terminada en '\n'.
    func jsonLine() throws -> Data {
        var data = try JSONEncoder().encode(self)
        data.append(0x0A)
        return data
    }
}
