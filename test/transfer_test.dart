// Prueba de integración del P2P: envía un archivo real por loopback y verifica
// que el receptor lo guarda con el contenido correcto.

import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:wiwytransfer/models/device.dart';
import 'package:wiwytransfer/services/receive_server.dart';
import 'package:wiwytransfer/services/send_service.dart';

void main() {
  test('transferencia P2P completa: prepare + upload', () async {
    final tempDir = await Directory.systemTemp.createTemp('wiwy_test_');

    const receiverSelf = Device(
      alias: 'Receptor',
      fingerprint: 'recv',
      ip: '127.0.0.1',
      port: ReceiveServer.port,
      deviceType: DeviceType.desktop,
    );
    final server = ReceiveServer(receiverSelf, saveDirectoryOverride: tempDir)
      ..onIncomingRequest = (_) async => true; // auto-acepta
    await server.start();

    // Archivo a enviar.
    final srcDir = await Directory.systemTemp.createTemp('wiwy_src_');
    final srcFile = File('${srcDir.path}/saludo.txt');
    const content = 'Hola desde WiwyTransfer P2P 🚀';
    await srcFile.writeAsString(content);

    const senderSelf = Device(
      alias: 'Emisor',
      fingerprint: 'send',
      ip: '127.0.0.1',
      port: 0,
      deviceType: DeviceType.mobile,
    );
    const target = Device(
      alias: 'Receptor',
      fingerprint: 'recv',
      ip: '127.0.0.1',
      port: ReceiveServer.port,
      deviceType: DeviceType.desktop,
    );

    const sender = SendService(senderSelf);
    final result = await sender.sendFile(target, srcFile);

    expect(result, SendResult.success);

    // El archivo debe existir en la carpeta del receptor con el mismo contenido.
    final receivedFile = File('${tempDir.path}/saludo.txt');
    expect(await receivedFile.exists(), isTrue);
    expect(await receivedFile.readAsString(), content);
    expect(server.received.length, 1);
    expect(server.received.first.from, 'Emisor');

    server.dispose();
    await tempDir.delete(recursive: true);
    await srcDir.delete(recursive: true);
  });

  test('transferencia rechazada devuelve SendResult.rejected', () async {
    final tempDir = await Directory.systemTemp.createTemp('wiwy_test2_');

    const receiverSelf = Device(
      alias: 'Receptor',
      fingerprint: 'recv2',
      ip: '127.0.0.1',
      port: ReceiveServer.port,
      deviceType: DeviceType.desktop,
    );
    final server = ReceiveServer(receiverSelf, saveDirectoryOverride: tempDir)
      ..onIncomingRequest = (_) async => false; // rechaza
    await server.start();

    final srcDir = await Directory.systemTemp.createTemp('wiwy_src2_');
    final srcFile = File('${srcDir.path}/x.txt');
    await srcFile.writeAsString('contenido');

    const senderSelf = Device(
      alias: 'Emisor',
      fingerprint: 'send2',
      ip: '127.0.0.1',
      port: 0,
      deviceType: DeviceType.mobile,
    );
    const target = Device(
      alias: 'Receptor',
      fingerprint: 'recv2',
      ip: '127.0.0.1',
      port: ReceiveServer.port,
      deviceType: DeviceType.desktop,
    );

    final result = await const SendService(senderSelf).sendFile(target, srcFile);
    expect(result, SendResult.rejected);

    server.dispose();
    await tempDir.delete(recursive: true);
    await srcDir.delete(recursive: true);
  });
}
