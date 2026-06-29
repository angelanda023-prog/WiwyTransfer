import Foundation

// Tipos de apoyo para el receptor Quick Share, portados/adaptados de NearDrop
// (The Unlicense, dominio público): https://github.com/grishka/NearDrop

enum NearbyError: Error {
    case protocolError(_ message: String)
    case requiredFieldMissing(_ message: String)
    case ukey2
    case inputInvalid(_ message: String)
}

public struct RemoteDeviceInfo {
    public let name: String
    public let type: DeviceType
    public var id: String?

    init(name: String, type: DeviceType, id: String? = nil) {
        self.name = name
        self.type = type
        self.id = id
    }

    public enum DeviceType: Int32 {
        case unknown = 0
        case phone
        case tablet
        case computer

        public static func fromRawValue(value: Int) -> DeviceType {
            DeviceType(rawValue: Int32(value)) ?? .unknown
        }
    }
}

public struct FileMetadata {
    public let name: String
    public let size: Int64
    public let mimeType: String
}

public struct TransferMetadata {
    public let files: [FileMetadata]
    public let id: String
    public let pinCode: String?
    public var textDescription: String?

    init(files: [FileMetadata], id: String, pinCode: String?, textDescription: String? = nil) {
        self.files = files
        self.id = id
        self.pinCode = pinCode
        self.textDescription = textDescription
    }

    public var totalSize: Int64 { files.reduce(0) { $0 + $1.size } }
}

extension Data {
    /// Alias usado por el código portado de NearDrop.
    static func randomData(length: Int) -> Data { Data.random(length) }
}
