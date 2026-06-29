// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "WiwyTransfer",
    platforms: [.macOS(.v13)],
    targets: [
        .executableTarget(
            name: "WiwyTransfer",
            path: "Sources/WiwyTransfer"
        )
    ]
)
