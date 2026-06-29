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
/// y gestiona archivos abiertos con "Abrir con… WiwyTransfer".
final class AppDelegate: NSObject, NSApplicationDelegate {
    func applicationDidFinishLaunching(_ notification: Notification) {
        NSApp.setActivationPolicy(.accessory) // sin Dock; vive en la barra de menús
        MainActor.assumeIsolated {
            AppModel.shared.start()
        }
    }

    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool {
        false
    }

    // Finder → "Abrir con… WiwyTransfer" (o arrastrar archivos sobre el icono).
    func application(_ application: NSApplication, open urls: [URL]) {
        MainActor.assumeIsolated {
            AppModel.shared.selectedFiles = urls
            AppModel.shared.sendState = .idle
            MainWindowController.shared.show(initialTab: .send)
        }
    }
}

/// Controlador de la ventana principal (creada bajo demanda, no al iniciar).
@MainActor
final class MainWindowController: NSObject, NSWindowDelegate {
    static let shared = MainWindowController()
    private var window: NSWindow?

    func show(initialTab: SidebarItem? = nil) {
        if let tab = initialTab { AppModel.shared.requestedTab = tab }

        if window == nil {
            let root = ContentView().environmentObject(AppModel.shared)
            let hosting = NSHostingController(rootView: root)
            let w = NSWindow(contentViewController: hosting)
            w.title = "WiwyTransfer"
            w.styleMask = [.titled, .closable, .miniaturizable, .resizable]
            w.setContentSize(NSSize(width: 720, height: 560))
            w.isReleasedWhenClosed = false
            w.delegate = self
            w.center()
            window = w
        }
        NSApp.setActivationPolicy(.regular) // mostrar en Dock mientras hay ventana
        window?.makeKeyAndOrderFront(nil)
        NSApp.activate(ignoringOtherApps: true)
    }

    func windowWillClose(_ notification: Notification) {
        // Al cerrar la ventana, volver a ser solo barra de menús (sin Dock).
        NSApp.setActivationPolicy(.accessory)
    }
}

struct MenuBarView: View {
    @EnvironmentObject var model: AppModel

    var body: some View {
        Text("WiwyTransfer").font(.headline)
        Divider()

        if let req = model.qsIncoming {
            Text("\(req.sender) quiere enviarte \(req.fileCount) archivo(s)")
            Button("✅ Aceptar") { model.respondQuickShare(id: req.id, accepted: true) }
            Button("❌ Rechazar") { model.respondQuickShare(id: req.id, accepted: false) }
            Divider()
        }

        Text(model.qsStatus)
        if model.qsReceiving { Text(model.qsProgressText) }
        Divider()

        Button("Abrir ventana…") {
            MainWindowController.shared.show()
        }
        Button("Salir") { NSApplication.shared.terminate(nil) }
            .keyboardShortcut("q")
    }
}
