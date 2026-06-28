import 'package:file_selector/file_selector.dart';
import 'package:flutter/material.dart';

import '../services/app_services.dart';
import '../widgets/wiwy_header.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  Future<void> _pickFolder() async {
    final String? path = await getDirectoryPath();
    if (path == null) return;
    await AppServices.instance.setSaveDirectory(path);
    if (!mounted) return;
    setState(() {});
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text('Carpeta de recepción: $path')),
    );
  }

  Future<void> _resetFolder() async {
    await AppServices.instance.setSaveDirectory(null);
    if (!mounted) return;
    setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    final services = AppServices.instance;
    final self = services.self;

    return ListView(
      children: [
        const WiwyHeader(title: 'Ajustes'),
        ListTile(
          leading: const Icon(Icons.devices),
          title: const Text('Nombre del dispositivo'),
          subtitle: Text(self.alias),
        ),
        ListenableBuilder(
          listenable: services.receiver,
          builder: (context, _) {
            final custom = services.receiver.customSaveDir;
            return ListTile(
              leading: const Icon(Icons.folder_outlined),
              title: const Text('Carpeta de recepción'),
              subtitle: Text(
                services.receiver.saveDirPath ?? 'Carpeta de la app',
              ),
              trailing: custom != null
                  ? IconButton(
                      icon: const Icon(Icons.restore),
                      tooltip: 'Restablecer',
                      onPressed: _resetFolder,
                    )
                  : null,
              onTap: _pickFolder,
            );
          },
        ),
        const ListTile(
          leading: Icon(Icons.cloud_outlined),
          title: Text('Cloudflare R2'),
          subtitle: Text('No configurado'),
        ),
        const Divider(),
        const AboutListTile(
          icon: Icon(Icons.info_outline),
          applicationName: 'WiwyTransfer',
          applicationVersion: '1.1.2',
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
