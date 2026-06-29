import Foundation

/// Tipo de dispositivo anunciado en el endpoint info de Quick Share.
enum QSDeviceType: Int {
    case unknown = 0
    case phone = 1
    case tablet = 2
    case computer = 3

    static func from(_ raw: Int) -> QSDeviceType { QSDeviceType(rawValue: raw) ?? .unknown }
}

/// Endpoint info que va en el registro TXT "n" (base64 url-safe).
///
/// Formato (ver PROTOCOL de NearDrop):
///  - 1 byte bitfield: Version(3) | Visibility(1) | DeviceType(3) | Reserved(1)
///  - 16 bytes aleatorios
///  - nombre UTF-8 con prefijo de 1 byte de longitud (si es visible)
struct QSEndpointInfo {
    var name: String?
    var deviceType: QSDeviceType

    init(name: String, deviceType: QSDeviceType) {
        self.name = name
        self.deviceType = deviceType
    }

    func serialize() -> Data {
        var bytes: [UInt8] = [UInt8(deviceType.rawValue << 1)]
        bytes.append(contentsOf: [UInt8](Data.random(16)))
        var nameChars = Array(name?.utf8 ?? "".utf8)
        if nameChars.count > 255 { nameChars = Array(nameChars[0..<255]) }
        bytes.append(UInt8(nameChars.count))
        bytes.append(contentsOf: nameChars)
        return Data(bytes)
    }

    init?(data: Data) {
        let b = [UInt8](data)
        guard b.count > 17 else { return nil }
        let hasName = (b[0] & 0x10) == 0
        self.deviceType = QSDeviceType.from(Int(b[0] & 0b111) >> 1)
        if hasName {
            let len = Int(b[17])
            guard b.count >= 18 + len else { return nil }
            self.name = String(bytes: b[18..<(18 + len)], encoding: .utf8)
        } else {
            self.name = nil
        }
    }
}

/// Codifica/decodifica el nombre de servicio mDNS de Quick Share (10 bytes, base64 url-safe).
///
/// Bytes: 0x23 (PCP) | endpointID[4] | 0xFC 0x9F 0x5E (service ID) | 0x00 0x00
enum QSServiceName {
    static func generateEndpointID() -> [UInt8] {
        let alphabet = Array("0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".utf8)
        return (0..<4).map { _ in alphabet[Int.random(in: 0..<alphabet.count)] }
    }

    static func build(endpointID: [UInt8]) -> String {
        let bytes: [UInt8] = [
            0x23,
            endpointID[0], endpointID[1], endpointID[2], endpointID[3],
            0xFC, 0x9F, 0x5E,
            0x00, 0x00,
        ]
        return Data(bytes).urlSafeBase64()
    }

    /// Devuelve el endpoint ID (4 chars) si el nombre es un servicio Quick Share válido.
    static func endpointID(fromServiceName name: String) -> String? {
        guard let data = Data.fromUrlSafeBase64(name) else { return nil }
        let b = [UInt8](data)
        guard b.count >= 5, b[0] == 0x23 else { return nil }
        return String(bytes: b[1..<5], encoding: .utf8)
    }
}
