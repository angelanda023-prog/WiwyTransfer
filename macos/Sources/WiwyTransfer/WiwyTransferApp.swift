import SwiftUI

@main
struct WiwyTransferApp: App {
    @StateObject private var model = AppModel()

    var body: some Scene {
        WindowGroup("WiwyTransfer", id: "main") {
            ContentView()
                .environmentObject(model)
                .frame(minWidth: 440, minHeight: 560)
                .onAppear { model.start() }
        }
        .windowResizability(.contentSize)

        // La app permanece en la barra de menús para poder recibir aunque la
        // ventana esté cerrada. Aceptas las transferencias desde la notificación.
        MenuBarExtra("WiwyTransfer", systemImage: "paperplane.fill") {
            MenuBarView()
                .environmentObject(model)
        }
    }
}

struct MenuBarView: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.openWindow) private var openWindow

    var body: some View {
        Text("WiwyTransfer").font(.headline)
        Divider()
        Text(model.qsStatus)
        if model.qsReceiving {
            Text(model.qsProgressText)
        }
        Divider()
        Button("Abrir ventana") {
            openWindow(id: "main")
            NSApplication.shared.activate(ignoringOtherApps: true)
        }
        Button("Salir") { NSApplication.shared.terminate(nil) }
            .keyboardShortcut("q")
    }
}
