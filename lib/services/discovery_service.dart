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

      // Anuncio inmediato y luego cada 3 segundos.
      _announce();
      _announceTimer =
          Timer.periodic(const Duration(seconds: 3), (_) => _announce());

      // Expira dispositivos que llevan >10 s sin anunciarse.
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

  void _announce() {
    final socket = _socket;
    if (socket == null) return;
    final data = utf8.encode(jsonEncode(self.toAnnouncement()));
    try {
      socket.send(data, InternetAddress('255.255.255.255'), discoveryPort);
    } on Object catch (e) {
      debugPrint('Error al anunciar: $e');
    }
  }

  void _cleanup() {
    final now = DateTime.now();
    final before = _devices.length;
    _devices.removeWhere((_, d) =>
        d.lastSeen == null ||
        now.difference(d.lastSeen!) > const Duration(seconds: 10));
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
