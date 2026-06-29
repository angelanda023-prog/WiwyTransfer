package com.wiwy.wiwytransfer.qs

import com.google.protobuf.ByteString

// ---- Alias cortos para los tipos protobuf generados ----

// securemessage
typealias SecureMessage = com.google.security.cryptauth.lib.securemessage.SecureMessageProto.SecureMessage
typealias HeaderAndBody = com.google.security.cryptauth.lib.securemessage.SecureMessageProto.HeaderAndBody
typealias SmHeader = com.google.security.cryptauth.lib.securemessage.SecureMessageProto.Header
typealias GenericPublicKey = com.google.security.cryptauth.lib.securemessage.SecureMessageProto.GenericPublicKey
typealias EcP256PublicKey = com.google.security.cryptauth.lib.securemessage.SecureMessageProto.EcP256PublicKey
typealias EncScheme = com.google.security.cryptauth.lib.securemessage.SecureMessageProto.EncScheme
typealias SigScheme = com.google.security.cryptauth.lib.securemessage.SecureMessageProto.SigScheme
typealias PublicKeyType = com.google.security.cryptauth.lib.securemessage.SecureMessageProto.PublicKeyType

// securegcm
typealias Ukey2Message = com.google.security.cryptauth.lib.securegcm.UkeyProto.Ukey2Message
typealias Ukey2ClientInit = com.google.security.cryptauth.lib.securegcm.UkeyProto.Ukey2ClientInit
typealias Ukey2ServerInit = com.google.security.cryptauth.lib.securegcm.UkeyProto.Ukey2ServerInit
typealias Ukey2ClientFinished = com.google.security.cryptauth.lib.securegcm.UkeyProto.Ukey2ClientFinished
typealias Ukey2Alert = com.google.security.cryptauth.lib.securegcm.UkeyProto.Ukey2Alert
typealias DeviceToDeviceMessage = com.google.security.cryptauth.lib.securegcm.DeviceToDeviceMessagesProto.DeviceToDeviceMessage
typealias GcmMetadata = com.google.security.cryptauth.lib.securegcm.SecureGcmProto.GcmMetadata

// connections (offline_wire_formats)
typealias OfflineFrame = com.google.location.nearby.connections.proto.OfflineWireFormatsProto.OfflineFrame
typealias ConnV1Frame = com.google.location.nearby.connections.proto.OfflineWireFormatsProto.V1Frame
typealias PayloadTransferFrame = com.google.location.nearby.connections.proto.OfflineWireFormatsProto.PayloadTransferFrame
typealias ConnectionRequestFrame = com.google.location.nearby.connections.proto.OfflineWireFormatsProto.ConnectionRequestFrame
typealias ConnectionResponseFrame = com.google.location.nearby.connections.proto.OfflineWireFormatsProto.ConnectionResponseFrame
typealias OsInfo = com.google.location.nearby.connections.proto.OfflineWireFormatsProto.OsInfo
typealias KeepAliveFrame = com.google.location.nearby.connections.proto.OfflineWireFormatsProto.KeepAliveFrame
typealias DisconnectionFrame = com.google.location.nearby.connections.proto.OfflineWireFormatsProto.DisconnectionFrame

// sharing (wire_format)
typealias SharingFrame = com.google.android.gms.nearby.sharing.Protocol.Frame
typealias SharingV1Frame = com.google.android.gms.nearby.sharing.Protocol.V1Frame
typealias IntroductionFrame = com.google.android.gms.nearby.sharing.Protocol.IntroductionFrame
typealias SharingFileMetadata = com.google.android.gms.nearby.sharing.Protocol.FileMetadata
typealias PairedKeyEncryptionFrame = com.google.android.gms.nearby.sharing.Protocol.PairedKeyEncryptionFrame
typealias PairedKeyResultFrame = com.google.android.gms.nearby.sharing.Protocol.PairedKeyResultFrame
typealias SharingConnectionResponseFrame = com.google.android.gms.nearby.sharing.Protocol.ConnectionResponseFrame

// ---- Alias para tipos/enums anidados (Kotlin no los expone vía typealias del padre) ----
typealias GcmType = com.google.security.cryptauth.lib.securegcm.SecureGcmProto.Type
typealias Ukey2Type = com.google.security.cryptauth.lib.securegcm.UkeyProto.Ukey2Message.Type
typealias Ukey2AlertType = com.google.security.cryptauth.lib.securegcm.UkeyProto.Ukey2Alert.AlertType
typealias Ukey2HandshakeCipher = com.google.security.cryptauth.lib.securegcm.UkeyProto.Ukey2HandshakeCipher
typealias CipherCommitment = com.google.security.cryptauth.lib.securegcm.UkeyProto.Ukey2ClientInit.CipherCommitment

typealias OfflineVersion = com.google.location.nearby.connections.proto.OfflineWireFormatsProto.OfflineFrame.Version
typealias ConnFrameType = com.google.location.nearby.connections.proto.OfflineWireFormatsProto.V1Frame.FrameType
typealias PacketType = com.google.location.nearby.connections.proto.OfflineWireFormatsProto.PayloadTransferFrame.PacketType
typealias PayloadHeader = com.google.location.nearby.connections.proto.OfflineWireFormatsProto.PayloadTransferFrame.PayloadHeader
typealias PayloadType = com.google.location.nearby.connections.proto.OfflineWireFormatsProto.PayloadTransferFrame.PayloadHeader.PayloadType
typealias PayloadChunk = com.google.location.nearby.connections.proto.OfflineWireFormatsProto.PayloadTransferFrame.PayloadChunk
typealias OsType = com.google.location.nearby.connections.proto.OfflineWireFormatsProto.OsInfo.OsType

typealias SharingVersion = com.google.android.gms.nearby.sharing.Protocol.Frame.Version
typealias SharingFrameType = com.google.android.gms.nearby.sharing.Protocol.V1Frame.FrameType
typealias SharingResponseStatus = com.google.android.gms.nearby.sharing.Protocol.ConnectionResponseFrame.Status
typealias PairedKeyResultStatus = com.google.android.gms.nearby.sharing.Protocol.PairedKeyResultFrame.Status
typealias FileMetadataType = com.google.android.gms.nearby.sharing.Protocol.FileMetadata.Type
typealias PublicKeyTypeEnum = com.google.security.cryptauth.lib.securemessage.SecureMessageProto.PublicKeyType

fun ByteArray.toByteString(): ByteString = ByteString.copyFrom(this)
fun ByteString.bytes(): ByteArray = this.toByteArray()
