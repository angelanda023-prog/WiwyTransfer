import 'dart:convert';
import 'dart:io';

import '../models/device.dart';

/// Resultado de un intento de envío.
enum SendResult { success, rejected, error }

/// Envía archivos a otro dispositivo de la red local por HTTP.
class SendService {
  const SendService(this.self);

  final Device self;

  /// Envía [file] a [target]. [onProgress] recibe el avance (0..1).
  Future<SendResult> sendFile(
    Device target,
    File file, {
    void Function(double progress)? onProgress,
  }) async {
    final client = HttpClient();
    try {
      final fileName = file.uri.pathSegments.last;
      final fileSize = await file.length();

      // 1) Pedir permiso al receptor.
      final prepareReq = await client.postUrl(
        Uri.parse('http://${target.ip}:${target.port}/api/prepare'),
      );
      prepareReq.headers.contentType = ContentType.json;
      prepareReq.write(jsonEncode({
        'alias': self.alias,
        'fileName': fileName,
        'fileSize': fileSize,
      }));
      final prepareRes = await prepareReq.close();

      if (prepareRes.statusCode == HttpStatus.forbidden) {
        return SendResult.rejected;
      }
      if (prepareRes.statusCode != HttpStatus.ok) {
        return SendResult.error;
      }

      final body = await utf8.decoder.bind(prepareRes).join();
      final sessionId = (jsonDecode(body) as Map<String, dynamic>)['sessionId'];
      if (sessionId is! String) return SendResult.error;

      // 2) Subir los bytes con progreso.
      final uploadReq = await client.postUrl(
        Uri.parse(
          'http://${target.ip}:${target.port}/api/upload?session=$sessionId',
        ),
      );
      uploadReq.headers.contentType = ContentType.binary;
      uploadReq.contentLength = fileSize;

      var sent = 0;
      await for (final chunk in file.openRead()) {
        uploadReq.add(chunk);
        sent += chunk.length;
        if (fileSize > 0) onProgress?.call(sent / fileSize);
      }
      final uploadRes = await uploadReq.close();
      await uploadRes.drain<void>();

      return uploadRes.statusCode == HttpStatus.ok
          ? SendResult.success
          : SendResult.error;
    } on Object {
      return SendResult.error;
    } finally {
      client.close();
    }
  }
}
