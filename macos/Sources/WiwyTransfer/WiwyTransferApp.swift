import SwiftUI
import AppKit

@main
struct WiwyTransferApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    var body: some Scene {
        // Solo barra de menús: no hay escena de ventana, así que NADA se abre al iniciar.
        MenuBarExtra("WiwyTransfer", systemImage: "paperplane.fill") {
            MenuBarView()
                .environmentObject(AppModel.shared)
        }
    }
}

/// Arranca los servicios, configura la app como accesorio (sin Dock ni ventana)
/// y crea la ventana principal solo bajo demanda.
final class AppDelegate: NSObject, NSApplicationDelegate {
    private var window: NSWindow?

    func applicationDidFinishLaunching(_ notification: Notification) {
        NSApp.setActivationPolicy(.accessory) // sin Dock; vive en la barra de menús
        MainActor.assumeIsolated {
            AppModel.shared.start()
        }
    }

    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool {
        false
    }

    @MainActor func showMainWindow() {
        if window == nil {
            let root = ContentView()
                .environmentObject(AppModel.shared)
                .frame(minWidth: 440, minHeight: 560)
            let w = NSWindow(contentViewController: NSHostingController(rootView: root))
            w.title = "WiwyTransfer"
            w.styleMask = [.titled, .closable, .miniaturizable, .resizable]
            w.setContentSize(NSSize(width: 470, height: 620))
            w.isReleasedWhenClosed = false
            window = w
        }
        NSApp.setActivationPolicy(.regular)
        window?.center()
        window?.makeKeyAndOrderFront(nil)
        NSApp.activate(ignoringOtherApps: true)
    }
}

struct MenuBarView: View {
    @EnvironmentObject var model: AppModel

    var body: some View {
        Text("WiwyTransfer").font(.headline)
        Divider()

        // Solicitud entrante pendiente: aceptar/rechazar desde el propio menú.
        if let req = model.qsIncoming {
            Text("\(req.sender) quiere enviarte \(req.fileCount) archivo(s)")
            Button("✅ Aceptar") { model.respondQuickShare(id: req.id, accepted: true) }
            Button("❌ Rechazar") { model.respondQuickShare(id: req.id, accepted: false) }
            Divider()
        }

        Text(model.qsStatus)
        if model.qsReceiving {
            Text(model.qsProgressText)
        }
        Divider()

        Button("Abrir ventana…") {
            (NSApp.delegate as? AppDelegate)?.showMainWindow()
        }
        Button("Salir") { NSApplication.shared.terminate(nil) }
            .keyboardShortcut("q")
    }
}
