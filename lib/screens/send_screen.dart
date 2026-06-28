import 'package:flutter/material.dart';

import '../widgets/wiwy_header.dart';

/// Dispositivo descubierto en la red local (mock por ahora).
class _Peer {
  const _Peer(this.name, this.platform, this.icon);
  final String name;
  final String platform;
  final IconData icon;
}

class SendScreen extends StatelessWidget {
  const SendScreen({super.key});

  static const _nearby = [
    _Peer('Galaxy A54', 'Android', Icons.smartphone),
    _Peer('Sala TV', 'Android TV', Icons.tv),
    _Peer('PC-Oficina', 'Windows', Icons.desktop_windows),
  ];

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return ListView(
      children: [
        const WiwyHeader(
          title: 'Enviar archivos',
          subtitle:
              'Selecciona archivos y elige un dispositivo cercano para enviar '
              'directo (P2P) o comparte por enlace en la nube.',
        ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 20),
          child: Column(
            children: [
              FilledButton.icon(
                onPressed: () => _notImplemented(context),
                icon: const Icon(Icons.attach_file),
                label: const Text('Seleccionar archivos'),
              ),
              const SizedBox(height: 12),
              OutlinedButton.icon(
                onPressed: () => _notImplemented(context),
                icon: const Icon(Icons.link),
                label: const Text('Crear enlace para compartir'),
                style: OutlinedButton.styleFrom(
                  minimumSize: const Size.fromHeight(52),
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 24),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 20),
          child: Row(
            children: [
              const Icon(Icons.wifi_tethering, size: 18),
              const SizedBox(width: 8),
              Text(
                'Dispositivos cercanos',
                style: theme.textTheme.titleSmall,
              ),
            ],
          ),
        ),
        const SizedBox(height: 8),
        for (final peer in _nearby)
          ListTile(
            leading: CircleAvatar(child: Icon(peer.icon)),
            title: Text(peer.name),
            subtitle: Text('${peer.platform} · misma red'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => _notImplemented(context),
          ),
        const SizedBox(height: 80),
      ],
    );
  }

  void _notImplemented(BuildContext context) {
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Función en construcción 🛠️')),
    );
  }
}
