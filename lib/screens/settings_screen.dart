import 'package:flutter/material.dart';

import '../widgets/wiwy_header.dart';

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return ListView(
      children: [
        const WiwyHeader(title: 'Ajustes'),
        const ListTile(
          leading: Icon(Icons.devices),
          title: Text('Nombre del dispositivo'),
          subtitle: Text('WiwyTransfer'),
        ),
        const ListTile(
          leading: Icon(Icons.cloud_outlined),
          title: Text('Cloudflare R2'),
          subtitle: Text('No configurado'),
        ),
        SwitchListTile(
          secondary: const Icon(Icons.wifi_tethering),
          title: const Text('Preferir transferencia directa (P2P)'),
          subtitle: const Text('Usa la nube solo si no hay conexión local'),
          value: true,
          onChanged: (_) {},
        ),
        const Divider(),
        const AboutListTile(
          icon: Icon(Icons.info_outline),
          applicationName: 'WiwyTransfer',
          applicationVersion: '1.0.0',
          aboutBoxChildren: [
            Text(
              'Transferencia de archivos P2P en red local y por la nube '
              '(Cloudflare R2) entre redes distintas.',
            ),
          ],
          child: Text('Acerca de WiwyTransfer'),
        ),
      ],
    );
  }
}
