import 'dart:io';
import 'dart:math';

import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../models/device.dart';
import 'discovery_service.dart';
import 'receive_server.dart';
import 'send_service.dart';

/// Punto único de acceso a los servicios de red de WiwyTransfer.
class AppServices {
  AppServices._(this.self)
      : discovery = DiscoveryService(self),
        receiver = ReceiveServer(self),
        sender = SendService(self);

  /// Clave global del navegador para mostrar diálogos desde los servicios.
  static final GlobalKey<NavigatorState> navigatorKey =
      GlobalKey<NavigatorState>();

  static AppServices? _instance;
  static AppServices get instance => _instance!;

  static const String _kSaveDir = 'save_dir';

  final Device self;
  final DiscoveryService discovery;
  final ReceiveServer receiver;
  final SendService sender;

  /// Inicializa los servicios: detecta IP/nombre, arranca servidor y descubrimiento.
  static Future<AppServices> init() async {
    final ip = await _localIp();
    final self = Device(
      alias: _defaultAlias(),
      fingerprint: _randomFingerprint(),
      ip: ip,
      port: ReceiveServer.defaultPort,
      deviceType: Device.currentPlatformType(),
    );

    final services = AppServices._(self);

    // Carga la carpeta de recepción elegida previamente, si existe.
    final prefs = await SharedPreferences.getInstance();
    services.receiver.customSaveDir = prefs.getString(_kSaveDir);

    await services.receiver.start();
    await services.discovery.start();
    _instance = services;
    return services;
  }

  /// Cambia la carpeta de recepción y la recuerda para próximas sesiones.
  Future<void> setSaveDirectory(String? path) async {
    await receiver.setCustomSaveDir(path);
    final prefs = await SharedPreferences.getInstance();
    if (path == null || path.isEmpty) {
      await prefs.remove(_kSaveDir);
    } else {
      await prefs.setString(_kSaveDir, path);
    }
  }

  /// Nombre amigable por defecto para este dispositivo.
  static String _defaultAlias() {
    final host = Platform.localHostname;
    if (host.isNotEmpty && host.toLowerCase() != 'localhost') {
      return host.split('.').first;
    }
    if (Platform.isAndroid) return 'Android';
    if (Platform.isMacOS) return 'Mac';
    if (Platform.isWindows) return 'Windows';
    if (Platform.isLinux) return 'Linux';
    return 'WiwyTransfer';
  }

  static String _randomFingerprint() {
    final r = Random();
    return List.generate(16, (_) => r.nextInt(16).toRadixString(16)).join();
  }

  /// Busca la IPv4 privada de la red local.
  static Future<String> _localIp() async {
    try {
      final interfaces = await NetworkInterface.list(
        type: InternetAddressType.IPv4,
        includeLoopback: false,
      );
      for (final iface in interfaces) {
        for (final addr in iface.addresses) {
          final ip = addr.address;
          if (ip.startsWith('192.168.') ||
              ip.startsWith('10.') ||
              _is172Private(ip)) {
            return ip;
          }
        }
      }
      // Si no hay rango privado típico, usa la primera no-loopback.
      for (final iface in interfaces) {
        if (iface.addresses.isNotEmpty) return iface.addresses.first.address;
      }
    } on Object {
      // sin red
    }
    return '0.0.0.0';
  }

  static bool _is172Private(String ip) {
    if (!ip.startsWith('172.')) return false;
    final second = int.tryParse(ip.split('.')[1]) ?? 0;
    return second >= 16 && second <= 31;
  }
}
