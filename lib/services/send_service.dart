import 'dart:convert';
import 'dart:io';

import '../models/device.dart';

/// Resultado de un intento de envío.
enum SendResult { success, rejected, error }

/// Resultado detallado: estado + mensaje legible cuando hay error.
typedef SendOutcome = ({SendResult result, String? message});

/// Envía archivos a otro dispositivo de la red local por HTTP.
class SendService {
  const SendService(this.self);

  final Device self;

  /// Envía [file] a [target]. [onProgress] recibe el avance (0..1).
  Future<SendOutcome> sendFile(
    Device target,
    File file, {
    void Function(double progress)? onProgress,
  }) async {
    final client = HttpClient()..connectionTimeout = const Duration(seconds: 8);
    try {
      final fileName = file.uri.pathSegments.last;
      final fileSize = await file.length();
      final base = 'http://${target.ip}:${target.port}';

      // 1) Pedir permiso al receptor.
      final String sessionId;
      try {
        final prepareReq = await client.postUrl(Uri.parse('$base/api/prepare'));
        prepareReq.headers.contentType = ContentType.json;
        prepareReq.write(jsonEncode({
          'alias': self.alias,
          'fileName': fileName,
          'fileSize': fileSize,
        }));
        final prepareRes = await prepareReq.close();

        if (prepareRes.statusCode == HttpStatus.forbidden) {
          await prepareRes.drain<void>();
          return (result: SendResult.rejected, message: null);
        }
        if (prepareRes.statusCode != HttpStatus.ok) {
          await prepareRes.drain<void>();
          return (
            result: SendResult.error,
            message: 'El receptor respondió ${prepareRes.statusCode} al preparar',
          );
        }
        final body = await utf8.decoder.bind(prepareRes).join();
        final id = (jsonDecode(body) as Map<String, dynamic>)['sessionId'];
        if (id is! String) {
          return (result: SendResult.error, message: 'Sesión inválida');
        }
        sessionId = id;
      } on SocketException catch (e) {
        return (
          result: SendResult.error,
          message: 'No se pudo conectar con ${target.ip}: ${e.osError?.message ?? e.message}',
        );
      }

      // 2) Subir los bytes con progreso.
      try {
        final uploadReq =
            await client.postUrl(Uri.parse('$base/api/upload?session=$sessionId'));
        uploadReq.headers.contentType = ContentType.binary;
        uploadReq.contentLength = fileSize;

        var sent = 0;
        await uploadReq.addStream(file.openRead().map((chunk) {
          sent += chunk.length;
          if (fileSize > 0) onProgress?.call(sent / fileSize);
          return chunk;
        }));
        final uploadRes = await uploadReq.close();
        final status = uploadRes.statusCode;
        await uploadRes.drain<void>();

        if (status == HttpStatus.ok) {
          return (result: SendResult.success, message: null);
        }
        return (
          result: SendResult.error,
          message: 'El receptor respondió $status al recibir',
        );
      } on SocketException catch (e) {
        return (
          result: SendResult.error,
          message: 'Conexión interrumpida durante la subida: ${e.osError?.message ?? e.message}',
        );
      }
    } on Object catch (e) {
      return (result: SendResult.error, message: e.toString());
    } finally {
      client.close();
    }
  }
}
