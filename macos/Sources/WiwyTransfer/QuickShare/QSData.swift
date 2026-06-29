import Foundation
import Security

/// Helpers de base64 URL-safe usados por el protocolo Quick Share (Nearby Share).
extension Data {
    func urlSafeBase64() -> String {
        base64EncodedString()
            .replacingOccurrences(of: "=", with: "")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "+", with: "-")
    }

    static func fromUrlSafeBase64(_ str: String) -> Data? {
        var s = str
            .replacingOccurrences(of: "_", with: "/")
            .replacingOccurrences(of: "-", with: "+")
        while s.count % 4 != 0 { s += "=" }
        return Data(base64Encoded: s, options: .ignoreUnknownCharacters)
    }

    static func random(_ length: Int) -> Data {
        var data = Data(count: length)
        let ok = data.withUnsafeMutableBytes {
            SecRandomCopyBytes(kSecRandomDefault, length, $0.baseAddress!)
        }
        precondition(ok == errSecSuccess)
        return data
    }
}
