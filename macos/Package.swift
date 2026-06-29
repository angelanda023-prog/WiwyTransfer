// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "WiwyTransfer",
    platforms: [.macOS(.v13)],
    dependencies: [
        .package(url: "https://github.com/apple/swift-protobuf.git", from: "1.28.0")
    ],
    targets: [
        .executableTarget(
            name: "WiwyTransfer",
            dependencies: [
                .product(name: "SwiftProtobuf", package: "swift-protobuf")
            ],
            path: "Sources/WiwyTransfer"
        )
    ]
)
