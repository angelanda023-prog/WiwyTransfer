import SwiftUI
import AppKit

enum SidebarItem: String, CaseIterable, Identifiable {
    case receive = "Recibir archivos"
    case send = "Enviar archivos"
    case settings = "Configuraciones"

    var id: String { rawValue }
    var icon: String {
        switch self {
        case .receive: return "tray.and.arrow.down"
        case .send: return "paperplane"
        case .settings: return "gearshape"
        }
    }
}

struct ContentView: View {
    @EnvironmentObject var model: AppModel
    @State private var selection: SidebarItem? = .receive

    var body: some View {
        NavigationSplitView {
            List(SidebarItem.allCases, selection: $selection) { item in
                Label(item.rawValue, systemImage: item.icon).tag(item)
            }
            .navigationSplitViewColumnWidth(min: 210, ideal: 230, max: 280)
        } detail: {
            Group {
                switch selection ?? .receive {
                case .receive: ReceivePane()
                case .send: SendView()
                case .settings: SettingsPane()
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .alert(item: $model.incoming) { req in
            Alert(
                title: Text("Solicitud de transferencia"),
                message: Text("\(req.header.sender) quiere enviarte \(req.header.files.count) archivo(s) (\(formatBytes(req.header.totalBytes)))."),
                primaryButton: .default(Text("Aceptar")) { req.respond(true) },
                secondaryButton: .cancel(Text("Rechazar")) { req.respond(false) }
            )
        }
        .onChange(of: model.selectedFiles) { files in
            if !files.isEmpty { selection = .send }
        }
    }
}

// MARK: - Recibir

struct ReceivePane: View {
    @EnvironmentObject var model: AppModel

    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                Image(nsImage: NSApp.applicationIconImage ?? NSImage())
                    .resizable()
                    .interpolation(.high)
                    .frame(width: 120, height: 120)
                    .shadow(radius: 8)

                Text("Recibir archivos").font(.largeTitle).bold()

                Divider()

                GroupBox {
                    VStack(alignment: .leading, spacing: 4) {
                        Label("Visible como", systemImage: "wifi").font(.caption)
                        Text(model.deviceName).font(.title3).bold()
                        Text("Se guardará en Descargas/WiwyTransfer.")
                            .font(.caption).foregroundColor(.secondary)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }

                GroupBox {
                    VStack(alignment: .leading, spacing: 4) {
                        Label("Quick Share", systemImage: "dot.radiowaves.left.and.right").font(.caption)
                        Text(model.qsStatus).font(.callout)
                        if model.qsReceiving {
                            ProgressView(value: model.qsProgress)
                            Text(model.qsProgressText).font(.caption2).foregroundColor(.secondary)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }

                Text("""
                Cuando WiwyTransfer está en funcionamiento aparece en la barra de menús, \
                arriba a la derecha. Para enviar desde tu Android, toca Compartir → Quick Share \
                y selecciona este Mac. Aceptas cada transferencia desde la ventana flotante.

                Importante: el Android y el Mac deben estar en la misma red Wi-Fi.
                """)
                .font(.callout)
                .foregroundColor(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)

                ourProtocolStatus
            }
            .padding(28)
            .frame(maxWidth: 640)
            .frame(maxWidth: .infinity)
        }
        .navigationTitle("Recibir archivos")
    }

    @ViewBuilder private var ourProtocolStatus: some View {
        switch model.receiveState {
        case .listening:
            EmptyView()
        case let .receiving(p):
            GroupBox {
                VStack(alignment: .leading) {
                    Text("Recibiendo de \(p.sender) (WiwyTransfer)")
                    ProgressView(value: p.overallTotal > 0 ? Double(p.overallReceived) / Double(p.overallTotal) : 0)
                }.frame(maxWidth: .infinity, alignment: .leading)
            }
        case let .done(paths, sender):
            GroupBox {
                VStack(alignment: .leading, spacing: 6) {
                    Label("Recibido de \(sender)", systemImage: "checkmark.circle.fill").foregroundColor(.green)
                    ForEach(paths, id: \.self) { Text("• \(($0 as NSString).lastPathComponent)").font(.caption) }
                    Button("Listo") { model.resetReceive() }
                }.frame(maxWidth: .infinity, alignment: .leading)
            }
        case let .error(msg):
            Label("Error: \(msg)", systemImage: "exclamationmark.triangle").foregroundColor(.red)
        }
    }
}

// MARK: - Enviar

struct SendView: View {
    @EnvironmentObject var model: AppModel

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("Enviar archivos").font(.largeTitle).bold()

                Button { pickFiles() } label: {
                    Label("Elegir archivos", systemImage: "paperclip").frame(maxWidth: .infinity)
                }
                .controlSize(.large)

                if !model.selectedFiles.isEmpty {
                    GroupBox {
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text("\(model.selectedFiles.count) archivo(s)").font(.headline)
                                Spacer()
                                Button("Quitar") { model.clearSelection() }.buttonStyle(.borderless)
                            }
                            ForEach(model.selectedFiles.prefix(5), id: \.self) { url in
                                Text("• \(url.lastPathComponent)").font(.caption).lineLimit(1)
                            }
                            if model.selectedFiles.count > 5 {
                                Text("…y \(model.selectedFiles.count - 5) más").font(.caption)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }

                sendStatus

                HStack {
                    Text("Dispositivos cercanos").font(.headline)
                    Spacer()
                    Button { model.refresh() } label: { Image(systemName: "arrow.clockwise") }
                        .buttonStyle(.borderless)
                }

                if model.peers.isEmpty {
                    Text("Buscando dispositivos WiwyTransfer en la red…")
                        .foregroundColor(.secondary).font(.callout)
                } else {
                    ForEach(model.peers) { PeerRow(peer: $0) }
                }

                Divider()
                HStack {
                    Text("Quick Share cercanos").font(.headline)
                    Spacer()
                }
                Text("El móvil debe tener abierta su pantalla de “Recibir” de Quick Share.")
                    .font(.caption).foregroundColor(.secondary)

                if model.qsSending || !model.qsSendStatus.isEmpty {
                    GroupBox {
                        VStack(alignment: .leading) {
                            Text(model.qsSendStatus).font(.callout)
                            if model.qsSending { ProgressView(value: model.qsSendProgress) }
                        }.frame(maxWidth: .infinity, alignment: .leading)
                    }
                }

                if model.qsDevices.isEmpty {
                    Text("Buscando dispositivos Quick Share…")
                        .font(.callout).foregroundColor(.secondary)
                } else {
                    ForEach(model.qsDevices) { dev in
                        Button { model.sendQuickShare(to: dev) } label: {
                            HStack {
                                Image(systemName: dev.type == .phone ? "iphone" : "questionmark.circle")
                                VStack(alignment: .leading) {
                                    Text(dev.name)
                                    Text("Quick Share").font(.caption).foregroundColor(.secondary)
                                }
                                Spacer()
                                Image(systemName: "paperplane.fill")
                                    .foregroundColor((!model.selectedFiles.isEmpty && !model.qsSending) ? .accentColor : .secondary)
                            }
                            .contentShape(Rectangle())
                            .padding(.vertical, 6)
                        }
                        .buttonStyle(.plain)
                        .disabled(model.selectedFiles.isEmpty || model.qsSending)
                    }
                }
            }
            .padding(28)
            .frame(maxWidth: 640)
            .frame(maxWidth: .infinity)
        }
        .navigationTitle("Enviar archivos")
    }

    @ViewBuilder private var sendStatus: some View {
        switch model.sendState {
        case .idle:
            EmptyView()
        case let .sending(sent, total):
            GroupBox {
                VStack(alignment: .leading) {
                    Text("Enviando… \(formatBytes(sent)) / \(formatBytes(total))").font(.callout)
                    ProgressView(value: total > 0 ? Double(sent) / Double(total) : 0)
                }
            }
        case let .done(received):
            Label("Enviado (\(received) archivo/s)", systemImage: "checkmark.circle.fill").foregroundColor(.green)
        case let .declined(reason):
            Label("Rechazado\(reason.map { ": \($0)" } ?? "")", systemImage: "xmark.circle").foregroundColor(.orange)
        case let .error(msg):
            Label("Error: \(msg)", systemImage: "exclamationmark.triangle").foregroundColor(.red)
        }
    }

    private func pickFiles() {
        let panel = NSOpenPanel()
        panel.allowsMultipleSelection = true
        panel.canChooseDirectories = false
        panel.canChooseFiles = true
        if panel.runModal() == .OK {
            model.selectedFiles = panel.urls
            model.sendState = .idle
        }
    }
}

struct PeerRow: View {
    @EnvironmentObject var model: AppModel
    let peer: Peer

    private var enabled: Bool {
        if case .sending = model.sendState { return false }
        return !model.selectedFiles.isEmpty
    }

    var body: some View {
        Button { model.send(to: peer) } label: {
            HStack {
                Image(systemName: peer.os == "android" ? "iphone" : "laptopcomputer")
                VStack(alignment: .leading) {
                    Text(peer.displayName)
                    Text(peer.os).font(.caption).foregroundColor(.secondary)
                }
                Spacer()
                Image(systemName: "paperplane.fill").foregroundColor(enabled ? .accentColor : .secondary)
            }
            .contentShape(Rectangle())
            .padding(.vertical, 6)
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

// MARK: - Configuraciones

struct SettingsPane: View {
    @EnvironmentObject var model: AppModel
    @State private var name = ""

    var body: some View {
        Form {
            Section("Dispositivo") {
                TextField("Nombre visible para otros", text: $name)
                Button("Guardar") { model.setDeviceName(name) }
            }
            Section("Recepción") {
                LabeledContent("Carpeta", value: "Descargas/WiwyTransfer")
                Button("Abrir carpeta de descargas") { NSWorkspace.shared.open(model.saveDirectory) }
            }
        }
        .formStyle(.grouped)
        .onAppear { name = model.deviceName }
        .navigationTitle("Configuraciones")
    }
}
