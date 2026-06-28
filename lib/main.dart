import 'package:flutter/material.dart';

import 'screens/home_shell.dart';
import 'theme.dart';

void main() {
  runApp(const WiwyTransferApp());
}

class WiwyTransferApp extends StatelessWidget {
  const WiwyTransferApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'WiwyTransfer',
      debugShowCheckedModeBanner: false,
      theme: WiwyTheme.light(),
      darkTheme: WiwyTheme.dark(),
      themeMode: ThemeMode.system,
      home: const HomeShell(),
    );
  }
}
