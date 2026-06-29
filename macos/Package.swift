// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "WiwyTransfer",
    platforms: [.macOS(.v13)],
    dependencies: [
        .package(url: "https://github.com/apple/swift-protobuf.git", from: "1.28.0"),
        .package(url: "https://github.com/leif-ibsen/SwiftECC", from: "3.5.3"),
        .package(url: "https://github.com/leif-ibsen/BigInt", from: "1.9.0"),
    ],
    targets: [
        .executableTarget(
            name: "WiwyTransfer",
            dependencies: [
                .product(name: "SwiftProtobuf", package: "swift-protobuf"),
                .product(name: "SwiftECC", package: "SwiftECC"),
                .product(name: "BigInt", package: "BigInt"),
            ],
            path: "Sources/WiwyTransfer"
        )
    ]
)
