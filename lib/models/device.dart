import 'dart:io' show Platform;

/// Tipo de dispositivo, para mostrar el icono adecuado.
enum DeviceType { mobile, desktop, tv, web }

/// Un dispositivo descubierto en la red local (o este mismo dispositivo).
class Device {
  const Device({
    required this.alias,
    required this.fingerprint,
    required this.ip,
    required this.port,
    required this.deviceType,
    this.lastSeen,
  });

  /// Nombre visible (editable por el usuario).
  final String alias;

  /// Identificador único y estable del dispositivo.
  final String fingerprint;

  /// IP en la red local. Vacío para "este dispositivo" hasta conocerla.
  final String ip;

  /// Puerto del servidor HTTP de recepción.
  final int port;

  final DeviceType deviceType;

  /// Última vez que se recibió un anuncio suyo (para expirar inactivos).
  final DateTime? lastSeen;

  Device copyWith({String? ip, DateTime? lastSeen, String? alias}) => Device(
        alias: alias ?? this.alias,
        fingerprint: fingerprint,
        ip: ip ?? this.ip,
        port: port,
        deviceType: deviceType,
        lastSeen: lastSeen ?? this.lastSeen,
      );

  /// Mensaje de anuncio que se envía por UDP broadcast.
  Map<String, dynamic> toAnnouncement() => {
        'alias': alias,
        'fingerprint': fingerprint,
        'port': port,
        'deviceType': deviceType.name,
        'app': 'wiwytransfer',
      };

  static Device? fromAnnouncement(Map<String, dynamic> json, String senderIp) {
    if (json['app'] != 'wiwytransfer') return null;
    final fp = json['fingerprint'];
    final alias = json['alias'];
    final port = json['port'];
    if (fp is! String || alias is! String || port is! int) return null;
    return Device(
      alias: alias,
      fingerprint: fp,
      ip: senderIp,
      port: port,
      deviceType: DeviceType.values.firstWhere(
        (t) => t.name == json['deviceType'],
        orElse: () => DeviceType.desktop,
      ),
      lastSeen: DateTime.now(),
    );
  }

  /// Tipo de dispositivo de la plataforma actual.
  static DeviceType currentPlatformType() {
    if (Platform.isAndroid || Platform.isIOS) return DeviceType.mobile;
    return DeviceType.desktop;
  }
}
