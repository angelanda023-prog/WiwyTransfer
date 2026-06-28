import 'dart:io';

import 'package:file_selector/file_selector.dart';
import 'package:flutter/material.dart';

import '../models/device.dart';
import '../services/app_services.dart';
import '../services/send_service.dart';
import '../widgets/wiwy_header.dart';

class SendScreen extends StatefulWidget {
  const SendScreen({super.key});

  @override
  State<SendScreen> createState() => _SendScreenState();
}

class _SendScreenState extends State<SendScreen> {
  File? _selectedFile;
  double? _sendProgress;

  IconData _iconFor(DeviceType type) => switch (type) {
        DeviceType.mobile => Icons.smartphone,
        DeviceType.tv => Icons.tv,
        DeviceType.web => Icons.public,
        DeviceType.desktop => Icons.computer,
      };

  Future<void> _pickFile() async {
    final XFile? file = await openFile();
    if (file != null) {
      setState(() => _selectedFile = File(file.path));
    }
  }

  Future<void> _sendToManualIp() async {
    if (_selectedFile == null) {
      _snack('Primero selecciona un archivo');
      return;
    }
    final controller = TextEditingController();
    final ip = await showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Enviar a una IP'),
        content: TextField(
          controller: controller,
          autofocus: true,
          keyboardType: TextInputType.url,
          decoration: const InputDecoration(
            labelText: 'IP del receptor',
            hintText: 'ej. 192.168.1.50',
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Cancelar'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, controller.text.trim()),
            child: const Text('Enviar'),
          ),
        ],
      ),
    );
    if (ip == null || ip.isEmpty) return;

    final target = Device(
      alias: ip,
      fingerprint: 'manual-$ip',
      ip: ip,
      port: 53318,
      deviceType: DeviceType.desktop,
    );
    await _sendTo(target);
  }

  Future<void> _sendTo(Device target) async {
    final file = _selectedFile;
    if (file == null) {
      _snack('Primero selecciona un archivo');
      return;
    }
    setState(() => _sendProgress = 0);
    final result = await AppServices.instance.sender.sendFile(
      target,
      file,
      onProgress: (p) => setState(() => _sendProgress = p),
    );
    if (!mounted) return;
    setState(() => _sendProgress = null);
    switch (result) {
      case SendResult.success:
        _snack('Enviado a ${target.alias} ✅');
      case SendResult.rejected:
        _snack('${target.alias} rechazó la transferencia');
      case SendResult.error:
        _snack('Error al enviar a ${target.alias}');
    }
  }

  void _snack(String msg) {
    ScaffoldMessenger.of(context)
        .showSnackBar(SnackBar(content: Text(msg)));
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final discovery = AppServices.instance.discovery;

    return ListView(
      children: [
        const WiwyHeader(
          title: 'Enviar archivos',
          subtitle:
              'Selecciona un archivo y toca un dispositivo cercano para '
              'enviarlo directo (P2P), sin pasar por la nube.',
        ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 20),
          child: Column(
            children: [
              FilledButton.icon(
                onPressed: _sendProgress != null ? null : _pickFile,
                icon: const Icon(Icons.attach_file),
                label: Text(
                  _selectedFile == null
                      ? 'Seleccionar archivo'
                      : _selectedFile!.uri.pathSegments.last,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              const SizedBox(height: 12),
              OutlinedButton.icon(
                onPressed: _sendProgress != null ? null : _sendToManualIp,
                icon: const Icon(Icons.lan),
                label: const Text('Enviar a una IP'),
                style: OutlinedButton.styleFrom(
                  minimumSize: const Size.fromHeight(52),
                ),
              ),
              if (_sendProgress != null) ...[
                const SizedBox(height: 16),
                LinearProgressIndicator(value: _sendProgress),
                const SizedBox(height: 6),
                Text('Enviando… ${(_sendProgress! * 100).toStringAsFixed(0)}%'),
              ],
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
              Text('Dispositivos cercanos', style: theme.textTheme.titleSmall),
            ],
          ),
        ),
        const SizedBox(height: 8),
        ListenableBuilder(
          listenable: discovery,
          builder: (context, _) {
            final devices = discovery.devices;
            if (devices.isEmpty) {
              return const Padding(
                padding: EdgeInsets.all(32),
                child: Center(
                  child: Column(
                    children: [
                      SizedBox(
                        width: 28,
                        height: 28,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      ),
                      SizedBox(height: 16),
                      Text('Buscando dispositivos en la red…'),
                    ],
                  ),
                ),
              );
            }
            return Column(
              children: [
                for (final device in devices)
                  ListTile(
                    leading: CircleAvatar(
                      child: Icon(_iconFor(device.deviceType)),
                    ),
                    title: Text(device.alias),
                    subtitle: Text('${device.ip} · misma red'),
                    trailing: const Icon(Icons.send),
                    onTap: _sendProgress != null
                        ? null
                        : () => _sendTo(device),
                  ),
              ],
            );
          },
        ),
        const SizedBox(height: 80),
      ],
    );
  }
}
