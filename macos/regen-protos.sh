#!/usr/bin/env bash
# Regenera el código Swift de los protobufs de Quick Share.
# Requiere: protoc y protoc-gen-swift en el PATH (o ajusta las rutas abajo).
#   - protoc:           https://github.com/protocolbuffers/protobuf/releases
#   - protoc-gen-swift: swift build -c release --product protoc-gen-swift (en apple/swift-protobuf)
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"

PROTOC="${PROTOC:-protoc}"
PLUGIN="${PROTOC_GEN_SWIFT:-protoc-gen-swift}"

"$PROTOC" --plugin=protoc-gen-swift="$PLUGIN" \
    --proto_path="$HERE/proto" \
    --swift_out="$HERE/Sources/WiwyTransfer/Generated" \
    --swift_opt=Visibility=Public \
    "$HERE"/proto/*.proto

echo "Protos regenerados en Sources/WiwyTransfer/Generated/"
