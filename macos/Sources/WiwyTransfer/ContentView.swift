import SwiftUI
import AppKit

struct ContentView: View {
    @EnvironmentObject var model: AppModel
    @State private var tab = 0
    @State private var showSettings = false

    var body: some View {
        VStack(spacing: 0) {
            header
            Picker("", selection: $tab) {
                Text("Enviar").tag(0)
                Text("Recibir").tag(1)
            }
            .pickerStyle(.segmented)
            .labelsHidden()
            .padding()

            Divider()

            if tab == 0 { SendView() } else { ReceiveView() }
            Spacer(minLength: 0)
        }
        .sheet(isPresented: $showSettings) { SettingsView() }
        .alert(item: $model.incoming) { req in
            Alert(
                title: Text("Solicitud de transferencia"),
                message: Text("\(req.header.sender) quiere enviarte \(req.header.files.count) archivo(s) (\(formatBytes(req.header.totalBytes)))."),
                primaryButton: .default(Text("Aceptar")) { req.respond(true) },
                secondaryButton: .cancel(Text("Rechazar")) { req.respond(false) }
            )
        }
        .onChange(of: model.selectedFiles) { files in
            if !files.isEmpty { tab = 0 }
        }
    }

    private var header: some View {
        HStack {
            Image(systemName: "paperplane.fill")
                .foregroundColor(.accentColor)
            Text("WiwyTransfer").font(.headline)
            Spacer()
            Button { showSettings = true } label: {
                Image(systemName: "gearshape")
            }
            .buttonStyle(.borderless)
        }
        .padding(.horizontal)
        .padding(.top, 12)
    }
}

struct SendView: View {
    @EnvironmentObject var model: AppModel

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Button {
                    pickFiles()
                } label: {
                    Label("Elegir archivos", systemImage: "paperclip")
                        .frame(maxWidth: .infinity)
                }
                .controlSize(.large)

                if !model.selectedFiles.isEmpty {
                    GroupBox {
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text("\(model.selectedFiles.count) archivo(s)")
                                    .font(.headline)
                                Spacer()
                                Button("Quitar") { model.clearSelection() }
                                    .buttonStyle(.borderless)
                            }
                            ForEach(model.selectedFiles.prefix(5), id: \.self) { url in
                                Text("• \(url.lastPathComponent)")
                                    .font(.caption)
                                    .lineLimit(1)
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
                    Text("Buscando dispositivos en la red WiFi…")
                        .foregroundColor(.secondary).font(.callout)
                } else {
                    ForEach(model.peers) { peer in
                        PeerRow(peer: peer)
                    }
                }

                if !model.qsDevices.isEmpty {
                    Divider()
                    Text("Quick Share cercanos").font(.headline)
                    ForEach(model.qsDevices) { dev in
                        HStack {
                            Image(systemName: dev.type == .phone ? "iphone" : "questionmark.circle")
                            VStack(alignment: .leading) {
                                Text(dev.name)
                                Text("Quick Share").font(.caption).foregroundColor(.secondary)
                            }
                            Spacer()
                            Text("envío en paso 3").font(.caption2).foregroundColor(.secondary)
                        }
                        .padding(.vertical, 4)
                    }
                }
            }
            .padding()
        }
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
            Label("Enviado (\(received) archivo/s)", systemImage: "checkmark.circle.fill")
                .foregroundColor(.green)
        case let .declined(reason):
            Label("Rechazado\(reason.map { ": \($0)" } ?? "")", systemImage: "xmark.circle")
                .foregroundColor(.orange)
        case let .error(msg):
            Label("Error: \(msg)", systemImage: "exclamationmark.triangle")
                .foregroundColor(.red)
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
        Button {
            model.send(to: peer)
        } label: {
            HStack {
                Image(systemName: peer.os == "android" ? "iphone" : "laptopcomputer")
                VStack(alignment: .leading) {
                    Text(peer.displayName).font(.body)
                    Text(peer.os).font(.caption).foregroundColor(.secondary)
                }
                Spacer()
                Image(systemName: "paperplane.fill")
                    .foregroundColor(enabled ? .accentColor : .secondary)
            }
            .contentShape(Rectangle())
            .padding(.vertical, 6)
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

struct ReceiveView: View {
    @EnvironmentObject var model: AppModel

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
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
                        Label("Quick Share (interop, experimental)", systemImage: "dot.radiowaves.left.and.right")
                            .font(.caption)
                        Text(model.qsStatus).font(.callout)
                        Text("Abre Compartir → Quick Share en tu Android (misma WiFi) y busca este Mac.")
                            .font(.caption2).foregroundColor(.secondary)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }

                receiveStatus
            }
            .padding()
        }
    }

    @ViewBuilder private var receiveStatus: some View {
        switch model.receiveState {
        case .listening:
            Label("Esperando archivos…", systemImage: "hourglass")
                .foregroundColor(.secondary)
        case let .receiving(p):
            GroupBox {
                VStack(alignment: .leading) {
                    Text("Recibiendo de \(p.sender)")
                    if !p.fileName.isEmpty {
                        Text("\(p.fileName) (\(p.fileIndex)/\(p.fileCount))")
                            .font(.caption).foregroundColor(.secondary)
                    }
                    ProgressView(value: p.overallTotal > 0 ? Double(p.overallReceived) / Double(p.overallTotal) : 0)
                    Text("\(formatBytes(p.overallReceived)) / \(formatBytes(p.overallTotal))")
                        .font(.caption)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        case let .done(paths, sender):
            GroupBox {
                VStack(alignment: .leading, spacing: 6) {
                    Label("Recibido de \(sender)", systemImage: "checkmark.circle.fill")
                        .foregroundColor(.green)
                    ForEach(paths, id: \.self) { p in
                        Text("• \((p as NSString).lastPathComponent)").font(.caption)
                    }
                    HStack {
                        Button("Abrir carpeta") {
                            NSWorkspace.shared.open(model.saveDirectory)
                        }
                        Button("Listo") { model.resetReceive() }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        case let .error(msg):
            Label("Error: \(msg)", systemImage: "exclamationmark.triangle")
                .foregroundColor(.red)
        }
    }
}

struct SettingsView: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) var dismiss
    @State private var name = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Nombre del dispositivo").font(.headline)
            TextField("Visible para otros", text: $name)
                .textFieldStyle(.roundedBorder)
                .frame(width: 280)
            HStack {
                Spacer()
                Button("Cancelar") { dismiss() }
                Button("Guardar") {
                    model.setDeviceName(name)
                    dismiss()
                }
                .keyboardShortcut(.defaultAction)
            }
        }
        .padding()
        .onAppear { name = model.deviceName }
    }
}
