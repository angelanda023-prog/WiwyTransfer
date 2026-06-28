import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:path_provider/path_provider.dart';

import '../models/device.dart';

/// Datos de una petición entrante de envío.
class IncomingRequest {
  IncomingRequest({
    required this.senderAlias,
    required this.fileName,
    required this.fileSize,
  });

  final String senderAlias;
  final String fileName;
  final int fileSize;
}

/// Un archivo ya recibido y guardado.
class ReceivedFile {
  ReceivedFile({required this.path, required this.size, required this.from});
  final String path;
  final int size;
  final String from;
}

/// Servidor HTTP que recibe archivos de otros dispositivos en la red local.
///
/// Protocolo (simplificado, estilo LocalSend):
///   POST /api/prepare  {alias, fileName, fileSize}  -> {sessionId} | 403
///   POST /api/upload?session=..&name=..  (cuerpo = bytes) -> 200
class ReceiveServer extends ChangeNotifier {
  ReceiveServer(this.self, {this.port = defaultPort, this.saveDirectoryOverride});

  static const int defaultPort = 53318;

  /// Puerto donde escucha (use 0 para uno efímero, p. ej. en pruebas).
  final int port;

  final Device self;

  /// Carpeta de guardado alternativa (se usa en pruebas).
  final Directory? saveDirectoryOverride;

  HttpServer? _server;

  /// Puerto realmente asignado tras arrancar.
  int get boundPort => _server?.port ?? port;

  /// Pregunta a la UI si se acepta una transferencia. Lo asigna la app.
  Future<bool> Function(IncomingRequest request)? onIncomingRequest;

  /// Progreso de la recepción en curso (0..1), o null si no hay ninguna.
  final ValueNotifier<double?> progress = ValueNotifier(null);

  final List<ReceivedFile> received = [];

  /// Sesiones aceptadas pendientes de subida: sessionId -> metadata.
  final Map<String, IncomingRequest> _sessions = {};

  bool get isRunning => _server != null;

  Future<void> start() async {
    if (_server != null) return;
    try {
      _server = await HttpServer.bind(InternetAddress.anyIPv4, port,
          shared: true);
      _server!.listen(_handle);
      // Prepara la carpeta de guardado para poder mostrar su ruta.
      unawaited(_saveDirectory());
    } on Object catch (e) {
      debugPrint('ReceiveServer no pudo iniciar: $e');
    }
  }

  Future<void> _handle(HttpRequest req) async {
    try {
      switch (req.uri.path) {
        case '/api/info':
          _json(req, self.toAnnouncement());
        case '/api/prepare':
          await _handlePrepare(req);
        case '/api/upload':
          await _handleUpload(req);
        default:
          req.response.statusCode = HttpStatus.notFound;
          await req.response.close();
      }
    } on Object catch (e) {
      debugPrint('Error atendiendo ${req.uri}: $e');
      try {
        req.response.statusCode = HttpStatus.internalServerError;
        await req.response.close();
      } on Object {
        // respuesta ya cerrada
      }
    }
  }

  Future<void> _handlePrepare(HttpRequest req) async {
    final body = await utf8.decoder.bind(req).join();
    final json = jsonDecode(body) as Map<String, dynamic>;
    final request = IncomingRequest(
      senderAlias: json['alias'] as String? ?? 'Desconocido',
      fileName: json['fileName'] as String? ?? 'archivo',
      fileSize: json['fileSize'] as int? ?? 0,
    );

    final accept = await onIncomingRequest?.call(request) ?? false;
    if (!accept) {
      req.response.statusCode = HttpStatus.forbidden;
      await req.response.close();
      return;
    }

    final sessionId = DateTime.now().microsecondsSinceEpoch.toString();
    _sessions[sessionId] = request;
    _json(req, {'sessionId': sessionId});
  }

  Future<void> _handleUpload(HttpRequest req) async {
    final sessionId = req.uri.queryParameters['session'];
    final request = sessionId == null ? null : _sessions.remove(sessionId);
    if (request == null) {
      req.response.statusCode = HttpStatus.forbidden;
      await req.response.close();
      return;
    }

    final Directory dir;
    final File file;
    final IOSink sink;
    try {
      dir = await _saveDirectory();
      file = File('${dir.path}/${_safeName(request.fileName)}');
      sink = file.openWrite();
    } on Object catch (e) {
      debugPrint('No se pudo abrir el archivo para guardar: $e');
      req.response
        ..statusCode = HttpStatus.internalServerError
        ..write('No se pudo guardar: $e');
      await req.response.close();
      return;
    }

    var receivedBytes = 0;
    final total = request.fileSize;
    progress.value = 0;

    try {
      await for (final chunk in req) {
        sink.add(chunk);
        receivedBytes += chunk.length;
        if (total > 0) progress.value = receivedBytes / total;
      }
      await sink.flush();
      await sink.close();

      received.insert(
        0,
        ReceivedFile(
          path: file.path,
          size: receivedBytes,
          from: request.senderAlias,
        ),
      );
      notifyListeners();

      _json(req, {'ok': true});
    } on Object catch (e) {
      debugPrint('Error guardando la subida: $e');
      try {
        await sink.close();
      } on Object {/* ya cerrado */}
      try {
        req.response
          ..statusCode = HttpStatus.internalServerError
          ..write('Error al recibir: $e');
        await req.response.close();
      } on Object {/* respuesta ya enviada */}
    } finally {
      progress.value = null;
    }
  }

  /// Ruta donde se guardan los archivos recibidos (para mostrarla en la UI).
  String? saveDirPath;

  /// Carpeta elegida por el usuario. Si es null, se usa la de la app.
  String? customSaveDir;

  /// Cambia la carpeta de recepción y refresca la ruta mostrada.
  Future<void> setCustomSaveDir(String? path) async {
    customSaveDir = path;
    await _saveDirectory();
  }

  /// Carpeta donde se guardan los archivos recibidos.
  ///
  /// Si el usuario eligió una carpeta, se usa esa. Si no (o si no es
  /// escribible), se cae al directorio de documentos de la app, que en macOS
  /// está dentro del contenedor del sandbox y siempre se puede escribir.
  Future<Directory> _saveDirectory() async {
    if (saveDirectoryOverride != null) return saveDirectoryOverride!;

    final custom = customSaveDir;
    if (custom != null && custom.isNotEmpty) {
      try {
        final dir = Directory(custom);
        if (!await dir.exists()) await dir.create(recursive: true);
        // Comprueba que se puede escribir realmente.
        final probe = File('${dir.path}/.wiwy_write_test');
        await probe.writeAsString('ok');
        await probe.delete();
        saveDirPath = dir.path;
        notifyListeners();
        return dir;
      } on Object catch (e) {
        debugPrint('Carpeta elegida no escribible ($custom): $e');
        // cae al directorio de la app
      }
    }

    final base = await getApplicationDocumentsDirectory();
    final dir = Directory('${base.path}/WiwyTransfer');
    if (!await dir.exists()) await dir.create(recursive: true);
    saveDirPath = dir.path;
    notifyListeners();
    return dir;
  }

  String _safeName(String name) =>
      name.replaceAll(RegExp(r'[\\/:*?"<>|]'), '_');

  void _json(HttpRequest req, Map<String, dynamic> data) {
    req.response
      ..headers.contentType = ContentType.json
      ..write(jsonEncode(data));
    req.response.close();
  }

  @override
  void dispose() {
    _server?.close(force: true);
    _server = null;
    progress.dispose();
    super.dispose();
  }
}
