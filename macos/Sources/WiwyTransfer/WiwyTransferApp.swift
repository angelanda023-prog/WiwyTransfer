import SwiftUI

@main
struct WiwyTransferApp: App {
    @StateObject private var model = AppModel()

    var body: some Scene {
        WindowGroup("WiwyTransfer") {
            ContentView()
                .environmentObject(model)
                .frame(minWidth: 440, minHeight: 560)
                .onAppear { model.start() }
        }
        .windowResizability(.contentSize)
    }
}
