import 'package:flutter/material.dart';

import 'models/transfer_item.dart';
import 'screens/home_shell.dart';
import 'services/app_services.dart';
import 'services/receive_server.dart';
import 'theme.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final services = await AppServices.init();

  // Cuando llega una petición de envío, mostramos un diálogo para aceptar.
  services.receiver.onIncomingRequest = _confirmIncoming;

  runApp(const WiwyTransferApp());
}

/// Muestra un diálogo preguntando si se acepta una transferencia entrante.
Future<bool> _confirmIncoming(IncomingRequest request) async {
  final context = AppServices.navigatorKey.currentContext;
  if (context == null) return false;

  final accepted = await showDialog<bool>(
    context: context,
    barrierDismissible: false,
    builder: (ctx) => AlertDialog(
      icon: const Icon(Icons.download),
      title: const Text('Archivo entrante'),
      content: Text(
        '${request.senderAlias} quiere enviarte:\n\n'
        '${request.fileName}\n'
        '${_readableSize(request.fileSize)}',
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(ctx, false),
          child: const Text('Rechazar'),
        ),
        FilledButton(
          onPressed: () => Navigator.pop(ctx, true),
          child: const Text('Aceptar'),
        ),
      ],
    ),
  );
  return accepted ?? false;
}

String _readableSize(int bytes) => TransferItem(
      fileName: '',
      sizeBytes: bytes,
      mode: TransferMode.p2p,
      direction: TransferDirection.received,
      peerName: '',
      date: DateTime.now(),
    ).readableSize;

class WiwyTransferApp extends StatelessWidget {
  const WiwyTransferApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'WiwyTransfer',
      debugShowCheckedModeBanner: false,
      navigatorKey: AppServices.navigatorKey,
      theme: WiwyTheme.light(),
      darkTheme: WiwyTheme.dark(),
      themeMode: ThemeMode.system,
      home: const HomeShell(),
    );
  }
}
