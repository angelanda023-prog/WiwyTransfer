import 'package:flutter/material.dart';

/// Cómo se realizó (o se realizará) una transferencia.
enum TransferMode {
  /// Directo en la misma red (sin nube). Máxima velocidad.
  p2p,

  /// A través de Cloudflare R2 (redes distintas o enlace compartido).
  cloud,
}

enum TransferDirection { sent, received }

extension TransferModeX on TransferMode {
  String get label => switch (this) {
        TransferMode.p2p => 'Directo (P2P)',
        TransferMode.cloud => 'Nube (R2)',
      };

  IconData get icon => switch (this) {
        TransferMode.p2p => Icons.wifi_tethering,
        TransferMode.cloud => Icons.cloud_outlined,
      };

  Color get color => switch (this) {
        TransferMode.p2p => const Color(0xFF06B6D4),
        TransferMode.cloud => const Color(0xFF8B5CF6),
      };
}

/// Una entrada del historial de transferencias.
class TransferItem {
  const TransferItem({
    required this.fileName,
    required this.sizeBytes,
    required this.mode,
    required this.direction,
    required this.peerName,
    required this.date,
  });

  final String fileName;
  final int sizeBytes;
  final TransferMode mode;
  final TransferDirection direction;
  final String peerName;
  final DateTime date;

  String get readableSize {
    if (sizeBytes < 1024) return '$sizeBytes B';
    if (sizeBytes < 1024 * 1024) {
      return '${(sizeBytes / 1024).toStringAsFixed(1)} KB';
    }
    if (sizeBytes < 1024 * 1024 * 1024) {
      return '${(sizeBytes / (1024 * 1024)).toStringAsFixed(1)} MB';
    }
    return '${(sizeBytes / (1024 * 1024 * 1024)).toStringAsFixed(2)} GB';
  }
}
