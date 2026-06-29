import Foundation
import Network

let kQSServiceType = "_FC9F5ED42C8A._tcp."

/// Un dispositivo Quick Share descubierto en la red.
struct QSDevice: Identifiable, Equatable {
    let id: String          // endpoint ID (4 chars)
    let name: String
    let type: QSDeviceType
    let endpoint: NWEndpoint

    static func == (a: QSDevice, b: QSDevice) -> Bool { a.id == b.id }
}

/// Recibe la publicación de NetService (éxito/fallo).
private final class QSNetServiceDelegate: NSObject, NetServiceDelegate {
    var onPublished: (() -> Void)?
    var onError: ((String) -> Void)?

    func netServiceDidPublish(_ sender: NetService) { onPublished?() }
    func netService(_ sender: NetService, didNotPublish errorDict: [String: NSNumber]) {
        onError?("No se pudo publicar el servicio Quick Share: \(errorDict)")
    }
}

/// Gestiona la interoperabilidad con el Quick Share nativo:
/// anuncia este Mac como receptor y descubre otros dispositivos.
final class QuickShareManager {
    private var tcpListener: NWListener?
    private var netService: NetService?
    private let netServiceDelegate = QSNetServiceDelegate()
    private var browser: NWBrowser?

    let endpointID = QSServiceName.generateEndpointID()
    private(set) var deviceName = Host.current().localizedName ?? "Mac"

    /// Dispositivos Quick Share descubiertos (para enviar). En main.
    var onDevices: ([QSDevice]) -> Void = { _ in }
    /// Mensajes de estado legibles.
    var onStatus: (String) -> Void = { _ in }
    /// Conexión TCP entrante de un emisor (la maneja la capa de recepción).
    var onIncoming: (NWConnection) -> Void = { _ in }

    func start(deviceName: String) {
        self.deviceName = deviceName
        startListener()
        startBrowser()
    }

    func restart(deviceName: String) {
        stop()
        start(deviceName: deviceName)
    }

    func stop() {
        netService?.stop()
        netService = nil
        tcpListener?.cancel()
        tcpListener = nil
        browser?.cancel()
        browser = nil
    }

    // MARK: - Anuncio (ser visible como receptor)

    private func startListener() {
        do {
            let listener = try NWListener(using: .tcp)
            listener.newConnectionHandler = { [weak self] conn in
                print("QS: ⬇️ conexión TCP entrante de \(conn.endpoint)")
                // No arrancamos la conexión aquí: lo hace InboundNearbyConnection.start().
                self?.onIncoming(conn)
            }
            listener.stateUpdateHandler = { [weak self] state in
                guard let self = self else { return }
                switch state {
                case .ready:
                    if let port = listener.port?.rawValue {
                        print("QS: 🟢 listener TCP escuchando en puerto \(port)")
                        DispatchQueue.main.async { self.publishService(port: Int(port)) }
                    }
                case .failed(let err):
                    self.onStatus("Listener Quick Share falló: \(err)")
                default:
                    break
                }
            }
            self.tcpListener = listener
            listener.start(queue: .global(qos: .userInitiated))
        } catch {
            onStatus("No se pudo abrir el listener Quick Share: \(error)")
        }
    }

    private func publishService(port: Int) {
        let name = QSServiceName.build(endpointID: endpointID)
        let info = QSEndpointInfo(name: deviceName, deviceType: .computer)
        let txt = NetService.data(fromTXTRecord: [
            "n": info.serialize().urlSafeBase64().data(using: .utf8)!
        ])
        let service = NetService(domain: "", type: kQSServiceType, name: name, port: Int32(port))
        netServiceDelegate.onPublished = { [weak self] in
            self?.onStatus("Visible en Quick Share como “\(self?.deviceName ?? "")”.")
        }
        netServiceDelegate.onError = { [weak self] msg in self?.onStatus(msg) }
        service.delegate = netServiceDelegate
        service.setTXTRecord(txt)
        service.publish()
        self.netService = service
    }

    // MARK: - Descubrimiento (encontrar receptores Quick Share)

    private func startBrowser() {
        let params = NWParameters()
        params.includePeerToPeer = true
        let descriptor = NWBrowser.Descriptor.bonjourWithTXTRecord(
            type: kQSServiceType, domain: nil)
        let browser = NWBrowser(for: descriptor, using: params)
        browser.browseResultsChangedHandler = { [weak self] results, _ in
            guard let self = self else { return }
            let myName = QSServiceName.build(endpointID: self.endpointID)
            let devices: [QSDevice] = results.compactMap { result in
                guard case let .service(name, _, _, _) = result.endpoint else { return nil }
                if name == myName { return nil }
                guard let epid = QSServiceName.endpointID(fromServiceName: name) else { return nil }

                var display = "Dispositivo Quick Share"
                var type = QSDeviceType.unknown
                if case let .bonjour(txt) = result.metadata,
                   let n = txt["n"], let raw = Data.fromUrlSafeBase64(n),
                   let info = QSEndpointInfo(data: raw) {
                    if let nm = info.name { display = nm }
                    type = info.deviceType
                }
                return QSDevice(id: epid, name: display, type: type, endpoint: result.endpoint)
            }
            DispatchQueue.main.async { self.onDevices(devices) }
        }
        self.browser = browser
        browser.start(queue: .global(qos: .userInitiated))
    }
}
