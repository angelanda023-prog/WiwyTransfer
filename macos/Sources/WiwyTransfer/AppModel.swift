import Foundation
import SwiftUI

struct IncomingRequest: Identifiable {
    let id = UUID()
    let header: TransferHeader
    let peerAddress: String
    let respond: (Bool) -> Void
}

enum SendState {
    case idle
    case sending(sent: Int64, total: Int64)
    case done(received: Int)
    case declined(reason: String?)
    case error(String)
}

enum ReceiveState {
    case listening
    case receiving(ReceiveProgress)
    case done(paths: [String], sender: String)
    case error(String)
}

@MainActor
final class AppModel: ObservableObject {
    @Published var peers: [Peer] = []
    @Published var deviceName: String
    @Published var sendState: SendState = .idle
    @Published var receiveState: ReceiveState = .listening
    @Published var incoming: IncomingRequest?
    @Published var selectedFiles: [URL] = []

    private var server: TransferServer?
    private var discovery: Discovery?

    private let osName = "macos"

    var saveDirectory: URL {
        let downloads = FileManager.default.urls(for: .downloadsDirectory, in: .userDomainMask).first
            ?? FileManager.default.homeDirectoryForCurrentUser
        return downloads.appendingPathComponent("WiwyTransfer", isDirectory: true)
    }

    init() {
        self.deviceName = UserDefaults.standard.string(forKey: "device_name")
            ?? (Host.current().localizedName ?? "Mac")
    }

    func start() {
        let server = TransferServer(
            askAccept: { [weak self] header, addr in
                await self?.requestAccept(header: header, peerAddress: addr) ?? false
            },
            saveDirectory: { [weak self] in
                self?.saveDirectory ?? FileManager.default.homeDirectoryForCurrentUser
            },
            onProgress: { [weak self] p in
                Task { @MainActor in self?.receiveState = .receiving(p) }
            },
            onComplete: { [weak self] paths, header in
                Task { @MainActor in self?.receiveState = .done(paths: paths, sender: header.sender) }
            },
            onError: { [weak self] msg in
                Task { @MainActor in self?.receiveState = .error(msg) }
            }
        )
        try? server.start(displayName: deviceName, os: osName)
        self.server = server

        let discovery = Discovery { [weak self] peers in
            self?.peers = peers
        }
        discovery.setOwnServiceName(server.serviceName)
        discovery.start()
        self.discovery = discovery
    }

    private func requestAccept(header: TransferHeader, peerAddress: String) async -> Bool {
        await withCheckedContinuation { cont in
            Task { @MainActor in
                self.incoming = IncomingRequest(header: header, peerAddress: peerAddress) { accept in
                    self.incoming = nil
                    if accept {
                        self.receiveState = .receiving(ReceiveProgress(
                            sender: header.sender, fileIndex: 0, fileCount: header.files.count,
                            fileName: "", overallReceived: 0, overallTotal: header.totalBytes))
                    }
                    cont.resume(returning: accept)
                }
            }
        }
    }

    func setDeviceName(_ name: String) {
        let clean = name.trimmingCharacters(in: .whitespaces)
        let final = clean.isEmpty ? (Host.current().localizedName ?? "Mac") : clean
        deviceName = final
        UserDefaults.standard.set(final, forKey: "device_name")
        server?.restart(displayName: final, os: osName)
        if let server = server { discovery?.setOwnServiceName(server.serviceName) }
    }

    func refresh() {
        discovery?.start()
    }

    func resetReceive() {
        receiveState = .listening
    }

    func clearSelection() {
        selectedFiles = []
        sendState = .idle
    }

    func send(to peer: Peer) {
        guard !selectedFiles.isEmpty else { return }
        let files = selectedFiles
        sendState = .sending(sent: 0, total: files.reduce(Int64(0)) {
            $0 + ((try? $1.resourceValues(forKeys: [.fileSizeKey]).fileSize).flatMap { $0 }.map(Int64.init) ?? 0)
        })
        let client = TransferClient(senderName: deviceName, os: osName)
        Task {
            do {
                let received = try await client.send(to: peer, files: files) { sent, total in
                    Task { @MainActor in self.sendState = .sending(sent: sent, total: total) }
                }
                await MainActor.run { self.sendState = .done(received: received) }
            } catch let WiwyError.declined(reason) {
                await MainActor.run { self.sendState = .declined(reason: reason) }
            } catch {
                let msg = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
                await MainActor.run { self.sendState = .error(msg) }
            }
        }
    }
}

func formatBytes(_ bytes: Int64) -> String {
    if bytes < 1024 { return "\(bytes) B" }
    let units = ["KB", "MB", "GB", "TB"]
    var value = Double(bytes) / 1024
    var i = 0
    while value >= 1024 && i < units.count - 1 { value /= 1024; i += 1 }
    return String(format: "%.1f %@", value, units[i])
}
