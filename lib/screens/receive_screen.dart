import 'package:flutter/material.dart';

import '../theme.dart';
import '../services/app_services.dart';
import '../widgets/wiwy_header.dart';

class ReceiveScreen extends StatelessWidget {
  const ReceiveScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final services = AppServices.instance;
    final self = services.self;

    return ListView(
      children: [
        const WiwyHeader(
          title: 'Recibir',
          subtitle:
              'Tu dispositivo es visible en la red local. Otros pueden enviarte '
              'archivos directamente.',
        ),
        const SizedBox(height: 8),
        Center(
          child: ValueListenableBuilder<double?>(
            valueListenable: services.receiver.progress,
            builder: (context, progress, _) {
              return Container(
                width: 160,
                height: 160,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  gradient: LinearGradient(
                    colors: [
                      WiwyTheme.brand.withValues(alpha: 0.15),
                      WiwyTheme.accent.withValues(alpha: 0.15),
                    ],
                  ),
                ),
                child: progress == null
                    ? const Icon(Icons.wifi_tethering,
                        size: 72, color: WiwyTheme.brand)
                    : Center(
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            SizedBox(
                              width: 56,
                              height: 56,
                              child: CircularProgressIndicator(value: progress),
                            ),
                            const SizedBox(height: 12),
                            Text('${(progress * 100).toStringAsFixed(0)}%'),
                          ],
                        ),
                      ),
              );
            },
          ),
        ),
        const SizedBox(height: 16),
        Center(child: Text(self.alias, style: theme.textTheme.titleMedium)),
        Center(
          child: Text(
            '${self.ip}:${self.port}',
            style: theme.textTheme.bodySmall?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
        ),
        const SizedBox(height: 24),
        ListenableBuilder(
          listenable: services.receiver,
          builder: (context, _) {
            final files = services.receiver.received;
            final dirPath = services.receiver.saveDirPath;
            return Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (dirPath != null)
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 20),
                    child: Text(
                      'Los archivos se guardan en:\n$dirPath',
                      style: theme.textTheme.bodySmall?.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                  ),
                if (files.isNotEmpty) ...[
                  const SizedBox(height: 16),
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 20),
                    child: Text('Recibidos', style: theme.textTheme.titleSmall),
                  ),
                  for (final f in files)
                    ListTile(
                      leading: const CircleAvatar(
                          child: Icon(Icons.insert_drive_file)),
                      title: Text(f.path.split('/').last),
                      subtitle: Text('de ${f.from}'),
                    ),
                ],
              ],
            );
          },
        ),
        const SizedBox(height: 24),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 20),
          child: Card(
            color: theme.colorScheme.surfaceContainerHighest,
            child: const Padding(
              padding: EdgeInsets.all(16),
              child: Row(
                children: [
                  Icon(Icons.lock_outline),
                  SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      'Las transferencias en la misma red van directas entre '
                      'dispositivos y nunca pasan por la nube.',
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ],
    );
  }
}
