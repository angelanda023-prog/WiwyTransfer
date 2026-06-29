import Foundation

/// Delegado del emisor: reenvía los eventos como closures (se invocan fuera del hilo
/// principal; quien los consume debe saltar a @MainActor para tocar la UI).
final class QSSender: NSObject, OutboundNearbyConnectionDelegate {
    var onEstablished: (() -> Void)?
    var onAccepted: (() -> Void)?
    var onProgress: ((Double) -> Void)?
    var onFinished: (() -> Void)?
    var onFailed: ((Error) -> Void)?

    func outboundConnectionWasEstablished(_ connection: OutboundNearbyConnection) { onEstablished?() }
    func outboundConnection(_ connection: OutboundNearbyConnection, progress: Double) { onProgress?(progress) }
    func outboundConnectionTransferAccepted(_ connection: OutboundNearbyConnection) { onAccepted?() }
    func outboundConnection(_ connection: OutboundNearbyConnection, failedWith error: Error) { onFailed?(error) }
    func outboundConnectionTransferFinished(_ connection: OutboundNearbyConnection) { onFinished?() }
}
