// Pruebas de WiwyTransfer (sin abrir sockets de red).

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:wiwytransfer/models/device.dart';
import 'package:wiwytransfer/widgets/wiwy_header.dart';

void main() {
  group('Device', () {
    test('serializa y deserializa un anuncio', () {
      const device = Device(
        alias: 'Mi Pixel',
        fingerprint: 'abc123',
        ip: '',
        port: 53318,
        deviceType: DeviceType.mobile,
      );

      final parsed =
          Device.fromAnnouncement(device.toAnnouncement(), '192.168.1.50');

      expect(parsed, isNotNull);
      expect(parsed!.alias, 'Mi Pixel');
      expect(parsed.fingerprint, 'abc123');
      expect(parsed.ip, '192.168.1.50');
      expect(parsed.port, 53318);
      expect(parsed.deviceType, DeviceType.mobile);
    });

    test('ignora anuncios de otras apps', () {
      final foreign = {
        'alias': 'X',
        'fingerprint': 'y',
        'port': 1,
        'deviceType': 'mobile',
        'app': 'otra-app',
      };
      expect(Device.fromAnnouncement(foreign, '10.0.0.1'), isNull);
    });
  });

  testWidgets('WiwyHeader muestra la marca y el título', (tester) async {
    await tester.pumpWidget(const MaterialApp(
      home: Scaffold(body: WiwyHeader(title: 'Enviar archivos')),
    ));

    expect(find.text('WiwyTransfer'), findsOneWidget);
    expect(find.text('Enviar archivos'), findsOneWidget);
  });
}
