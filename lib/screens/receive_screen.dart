import 'package:flutter/material.dart';

import '../theme.dart';
import '../widgets/wiwy_header.dart';

class ReceiveScreen extends StatelessWidget {
  const ReceiveScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return ListView(
      children: [
        const WiwyHeader(
          title: 'Recibir',
          subtitle:
              'Tu dispositivo es visible en la red local. Otros pueden enviarte '
              'archivos directamente.',
        ),
        const SizedBox(height: 16),
        Center(
          child: Container(
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
            child: const Icon(
              Icons.wifi_tethering,
              size: 72,
              color: WiwyTheme.brand,
            ),
          ),
        ),
        const SizedBox(height: 16),
        Center(
          child: Text(
            'Esperando archivos…',
            style: theme.textTheme.titleMedium,
          ),
        ),
        const SizedBox(height: 4),
        Center(
          child: Text(
            'Este dispositivo: WiwyTransfer',
            style: theme.textTheme.bodySmall?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
        ),
        const SizedBox(height: 28),
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
                      'Las transferencias en la misma red van cifradas y nunca '
                      'pasan por la nube.',
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
