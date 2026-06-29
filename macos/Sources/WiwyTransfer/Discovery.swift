import Foundation
import Network

/// Un par descubierto en la red local.
struct Peer: Identifiable, Equatable {
    let id: String
    let displayName: String
    let os: String
    let endpoint: NWEndpoint

    static func == (lhs: Peer, rhs: Peer) -> Bool { lhs.id == rhs.id }
}

/// Descubre receptores WiwyTransfer en la red vía Bonjour (NWBrowser).
final class Discovery {
    private var browser: NWBrowser?
    private let onChange: ([Peer]) -> Void
    private var ownServiceName: String?

    init(onChange: @escaping ([Peer]) -> Void) {
        self.onChange = onChange
    }

    func setOwnServiceName(_ name: String) {
        ownServiceName = name
    }

    func start() {
        stop()
        let params = NWParameters()
        params.includePeerToPeer = true
        let descriptor = NWBrowser.Descriptor.bonjourWithTXTRecord(type: Proto.serviceType, domain: nil)
        let browser = NWBrowser(for: descriptor, using: params)
        self.browser = browser

        browser.browseResultsChangedHandler = { [weak self] results, _ in
            guard let self = self else { return }
            let peers: [Peer] = results.compactMap { result in
                guard case let .service(name, _, _, _) = result.endpoint else { return nil }
                if name == self.ownServiceName { return nil }

                var display = name.replacingOccurrences(of: "WiwyTransfer-", with: "")
                var os = "?"
                if case let .bonjour(txt) = result.metadata {
                    if let n = txt["name"] { display = n }
                    if let o = txt["os"] { os = o }
                }
                return Peer(id: name, displayName: display, os: os, endpoint: result.endpoint)
            }
            DispatchQueue.main.async { self.onChange(peers) }
        }

        browser.start(queue: .global(qos: .userInitiated))
    }

    func stop() {
        browser?.cancel()
        browser = nil
    }
}
