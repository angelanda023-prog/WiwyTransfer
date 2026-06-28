import 'package:flutter/material.dart';

import '../models/transfer_item.dart';
import '../widgets/wiwy_header.dart';

class HistoryScreen extends StatelessWidget {
  const HistoryScreen({super.key});

  static final _items = [
    TransferItem(
      fileName: 'vacaciones.zip',
      sizeBytes: 845 * 1024 * 1024,
      mode: TransferMode.p2p,
      direction: TransferDirection.sent,
      peerName: 'Sala TV',
      date: DateTime(2026, 6, 28, 14, 30),
    ),
    TransferItem(
      fileName: 'presentacion.pdf',
      sizeBytes: 3 * 1024 * 1024,
      mode: TransferMode.cloud,
      direction: TransferDirection.received,
      peerName: 'PC-Oficina',
      date: DateTime(2026, 6, 27, 9, 12),
    ),
    TransferItem(
      fileName: 'foto.jpg',
      sizeBytes: 420 * 1024,
      mode: TransferMode.p2p,
      direction: TransferDirection.received,
      peerName: 'Galaxy A54',
      date: DateTime(2026, 6, 26, 20, 5),
    ),
  ];

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return ListView(
      children: [
        const WiwyHeader(
          title: 'Historial',
          subtitle: 'Archivos enviados y recibidos.',
        ),
        for (final item in _items)
          ListTile(
            leading: CircleAvatar(
              backgroundColor: item.mode.color.withValues(alpha: 0.15),
              child: Icon(item.mode.icon, color: item.mode.color),
            ),
            title: Text(item.fileName),
            subtitle: Text(
              '${item.direction == TransferDirection.sent ? 'Enviado a' : 'Recibido de'} '
              '${item.peerName} · ${item.readableSize}',
            ),
            trailing: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                Text(
                  item.mode.label,
                  style: theme.textTheme.labelSmall
                      ?.copyWith(color: item.mode.color),
                ),
                const SizedBox(height: 2),
                Text(
                  '${item.date.day}/${item.date.month}',
                  style: theme.textTheme.labelSmall,
                ),
              ],
            ),
          ),
      ],
    );
  }
}
