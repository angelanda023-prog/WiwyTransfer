import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';

import '../models/device.dart';

/// Descubrimiento de dispositivos en la red local mediante UDP broadcast.
///
/// Cada dispositivo anuncia su presencia periódicamente a `255.255.255.255` y
/// escucha los anuncios de los demás. Se usa broadcast (no multicast) para
/// evitar tener que adquirir un MulticastLock en Android.
class DiscoveryService extends ChangeNotifier {
  DiscoveryService(this.self);

  /// Puerto fijo donde se emiten/escuchan los anuncios.
  static const int discoveryPort = 53321;

  /// Este dispositivo.
  final Device self;

  RawDatagramSocket? _socket;
  Timer? _announceTimer;
  Timer? _cleanupTimer;

  final Map<String, Device> _devices = {};

  /// Dispositivos descubiertos (excluye este).
  List<Device> get devices => _devices.values.toList()
    ..sort((a, b) => a.alias.toLowerCase().compareTo(b.alias.toLowerCase()));

  bool get isRunning => _socket != null;

  Future<void> start() async {
    if (_socket != null) return;
    try {
      _socket = await RawDatagramSocket.bind(
        InternetAddress.anyIPv4,
        discoveryPort,
        reuseAddress: true,
        reusePort: true,
      );
      _socket!.broadcastEnabled = true;
      _socket!.listen(_onEvent);

      // Anuncio inmediato y luego cada 2 segundos.
      _announce();
      _announceTimer =
          Timer.periodic(const Duration(seconds: 2), (_) => _announce());

      // Expira dispositivos que llevan >20 s sin anunciarse (tolerante a la
      // pérdida de paquetes UDP para evitar parpadeo en la lista).
      _cleanupTimer =
          Timer.periodic(const Duration(seconds: 5), (_) => _cleanup());
    } on Object catch (e) {
      debugPrint('DiscoveryService no pudo iniciar: $e');
    }
  }

  void _onEvent(RawSocketEvent event) {
    if (event != RawSocketEvent.read) return;
    final dg = _socket?.receive();
    if (dg == null) return;
    try {
      final json = jsonDecode(utf8.decode(dg.data)) as Map<String, dynamic>;
      final device = Device.fromAnnouncement(json, dg.address.address);
      if (device == null || device.fingerprint == self.fingerprint) return;

      final isNew = !_devices.containsKey(device.fingerprint);
      _devices[device.fingerprint] = device;
      if (isNew) {
        // Responde de inmediato para que el nuevo dispositivo nos vea ya.
        _announce();
      }
      notifyListeners();
    } on Object {
      // Paquete no válido: se ignora.
    }
  }

  /// Fuerza un anuncio inmediato (p. ej. al volver la app al primer plano).
  void announceNow() => _announce();

  void _announce() {
    final socket = _socket;
    if (socket == null) return;
    final data = utf8.encode(jsonEncode(self.toAnnouncement()));
    for (final target in _broadcastTargets()) {
      try {
        socket.send(data, InternetAddress(target), discoveryPort);
      } on Object catch (e) {
        debugPrint('Error al anunciar a $target: $e');
      }
    }
  }

  /// Direcciones a las que enviar el anuncio: broadcast global + broadcast
  /// dirigido de la subred (más fiable en muchos routers).
  List<String> _broadcastTargets() {
    final targets = <String>{'255.255.255.255'};
    final ip = self.ip;
    final parts = ip.split('.');
    if (parts.length == 4 && ip != '0.0.0.0') {
      targets.add('${parts[0]}.${parts[1]}.${parts[2]}.255');
    }
    return targets.toList();
  }

  void _cleanup() {
    final now = DateTime.now();
    final before = _devices.length;
    _devices.removeWhere((_, d) =>
        d.lastSeen == null ||
        now.difference(d.lastSeen!) > const Duration(seconds: 20));
    if (_devices.length != before) notifyListeners();
  }

  @override
  void dispose() {
    _announceTimer?.cancel();
    _cleanupTimer?.cancel();
    _socket?.close();
    _socket = null;
    super.dispose();
  }
}
