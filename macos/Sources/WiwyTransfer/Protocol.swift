import Foundation

/// Constantes y modelos del protocolo WiwyTransfer (ver PROTOCOL.md).
enum Proto {
    static let serviceType = "_wiwytransfer._tcp"
    static let version = 1
    static let bufferSize = 64 * 1024
}

struct FileMeta: Codable {
    let name: String
    let size: Int64
}

struct TransferHeader: Codable {
    let v: Int
    let sender: String
    let os: String
    let files: [FileMeta]

    var totalBytes: Int64 { files.reduce(0) { $0 + $1.size } }
}

struct Decision: Codable {
    let accept: Bool
    let reason: String?
}

struct TransferResult: Codable {
    let ok: Bool
    let received: Int
    let error: String?
}

enum WiwyError: Error, LocalizedError {
    case connectionClosed
    case badResponse
    case declined(String?)

    var errorDescription: String? {
        switch self {
        case .connectionClosed: return "La conexión se cerró"
        case .badResponse: return "Respuesta inválida del otro dispositivo"
        case .declined(let r): return r ?? "Transferencia rechazada"
        }
    }
}

extension TransferHeader {
    /// Solo el nombre de archivo, sin componentes de ruta.
    static func sanitize(_ raw: String) -> String {
        let base = (raw as NSString).lastPathComponent
        let trimmed = base.trimmingCharacters(in: .whitespaces)
        if trimmed.isEmpty || trimmed == "." || trimmed == ".." { return "archivo" }
        return trimmed
    }
}
