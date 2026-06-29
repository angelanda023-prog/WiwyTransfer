import AppKit
import SwiftUI

/// Ventana flotante propia con el aspecto de una notificación de macOS
/// (icono + título con PIN + descripción + botones Rechazar/Aceptar), en la
/// esquina superior derecha. No requiere permiso de notificaciones.
@MainActor
final class QSApprovalPanel {
    static let shared = QSApprovalPanel()
    private var panel: NSPanel?

    func show(title: String, subtitle: String,
              onAccept: @escaping () -> Void, onReject: @escaping () -> Void) {
        close()
        let view = ApprovalView(
            title: title, subtitle: subtitle,
            onAccept: { [weak self] in self?.close(); onAccept() },
            onReject: { [weak self] in self?.close(); onReject() })

        let hosting = NSHostingView(rootView: view)
        let panel = NSPanel(
            contentRect: NSRect(x: 0, y: 0, width: 380, height: 92),
            styleMask: [.borderless, .nonactivatingPanel],
            backing: .buffered, defer: false)
        panel.level = .floating
        panel.isFloatingPanel = true
        panel.hidesOnDeactivate = false
        panel.worksWhenModal = true
        panel.isOpaque = false
        panel.backgroundColor = .clear
        panel.hasShadow = true
        panel.contentView = hosting

        if let screen = NSScreen.main {
            let vf = screen.visibleFrame
            panel.setFrameTopLeftPoint(NSPoint(x: vf.maxX - 400, y: vf.maxY - 12))
        }
        panel.orderFrontRegardless()
        self.panel = panel
    }

    func close() {
        panel?.close()
        panel = nil
    }
}

private struct ApprovalView: View {
    let title: String
    let subtitle: String
    let onAccept: () -> Void
    let onReject: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Image(nsImage: NSApp.applicationIconImage ?? NSImage())
                .resizable()
                .frame(width: 46, height: 46)

            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 13, weight: .bold))
                    .lineLimit(1)
                Text(subtitle)
                    .font(.system(size: 12))
                    .foregroundColor(.secondary)
                    .lineLimit(2)
                    .fixedSize(horizontal: false, vertical: true)
            }

            Spacer(minLength: 6)

            VStack(spacing: 6) {
                Button(action: onReject) {
                    Text("Rechazar").frame(width: 78)
                }
                Button(action: onAccept) {
                    Text("Aceptar").frame(width: 78)
                }
                .keyboardShortcut(.defaultAction)
            }
            .controlSize(.large)
            .buttonStyle(.bordered)
        }
        .padding(14)
        .frame(width: 380)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 18))
    }
}
