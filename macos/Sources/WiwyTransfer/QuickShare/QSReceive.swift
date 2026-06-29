import Foundation
import UserNotifications

/// Delegado del receptor: reenvía los eventos como closures (se invocan en el hilo principal).
final class QSReceiver: NSObject, InboundNearbyConnectionDelegate {
    var onConsent: ((TransferMetadata, RemoteDeviceInfo, InboundNearbyConnection) -> Void)?
    var onProgress: ((Int64, Int64, String) -> Void)?
    var onFinished: (([String], RemoteDeviceInfo, String) -> Void)?
    var onTerminated: ((String, Error?) -> Void)?

    func obtainUserConsent(for transfer: TransferMetadata, from device: RemoteDeviceInfo, connection: InboundNearbyConnection) {
        onConsent?(transfer, device, connection)
    }
    func connectionProgress(_ connection: InboundNearbyConnection, received: Int64, total: Int64, currentFile: String) {
        onProgress?(received, total, currentFile)
    }
    func connectionFinished(_ connection: InboundNearbyConnection, savedPaths: [String], from device: RemoteDeviceInfo) {
        onFinished?(savedPaths, device, connection.id)
    }
    func connectionWasTerminated(connection: InboundNearbyConnection, error: Error?) {
        onTerminated?(connection.id, error)
    }
}

/// Notificaciones del sistema con acciones Aceptar/Rechazar para transferencias entrantes.
/// Permite aceptar sin tener la ventana en primer plano.
final class QSNotificationCenter: NSObject, UNUserNotificationCenterDelegate {
    static let shared = QSNotificationCenter()

    /// (idConexión, aceptada)
    var onResponse: ((String, Bool) -> Void)?

    private let categoryID = "QS_TRANSFER"
    private var available: Bool { Bundle.main.bundleIdentifier != nil }

    func setup() {
        guard available else { return } // sin bundle (p. ej. `swift run`) no hay notificaciones
        let center = UNUserNotificationCenter.current()
        center.delegate = self
        center.requestAuthorization(options: [.alert, .sound]) { _, _ in }
        let accept = UNNotificationAction(identifier: "QS_ACCEPT", title: "Aceptar", options: [.foreground])
        let decline = UNNotificationAction(identifier: "QS_DECLINE", title: "Rechazar", options: [.destructive])
        let category = UNNotificationCategory(identifier: categoryID, actions: [accept, decline],
                                              intentIdentifiers: [], options: [])
        center.setNotificationCategories([category])
    }

    func postTransferRequest(id: String, sender: String, fileCount: Int, totalBytes: Int64) {
        guard available else { return }
        let content = UNMutableNotificationContent()
        content.title = "Quick Share"
        content.body = "\(sender) quiere enviarte \(fileCount) archivo(s) (\(formatBytes(totalBytes))). Toca para aceptar."
        content.categoryIdentifier = categoryID
        content.userInfo = ["connId": id]
        content.sound = .default
        let req = UNNotificationRequest(identifier: "qs-\(id)", content: content, trigger: nil)
        UNUserNotificationCenter.current().add(req)
    }

    func clear(id: String) {
        guard available else { return }
        UNUserNotificationCenter.current().removeDeliveredNotifications(withIdentifiers: ["qs-\(id)"])
    }

    // Mostrar el banner aunque la app esté en primer plano.
    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .sound])
    }

    // Respuesta del usuario al tocar la notificación o sus botones.
    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                didReceive response: UNNotificationResponse,
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        let id = response.notification.request.content.userInfo["connId"] as? String ?? ""
        switch response.actionIdentifier {
        case "QS_ACCEPT", UNNotificationDefaultActionIdentifier:
            onResponse?(id, true)
        default:
            onResponse?(id, false)
        }
        completionHandler()
    }
}
