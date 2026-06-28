import 'package:flutter/material.dart';

/// Paleta y temas de WiwyTransfer.
class WiwyTheme {
  WiwyTheme._();

  /// Color de marca principal.
  static const Color brand = Color(0xFF2563EB); // azul WiwyTransfer
  static const Color brandDark = Color(0xFF1E3A8A);
  static const Color accent = Color(0xFF06B6D4); // cian para acentos P2P

  static ThemeData light() => _build(Brightness.light);
  static ThemeData dark() => _build(Brightness.dark);

  static ThemeData _build(Brightness brightness) {
    final scheme = ColorScheme.fromSeed(
      seedColor: brand,
      brightness: brightness,
    );
    return ThemeData(
      useMaterial3: true,
      colorScheme: scheme,
      appBarTheme: const AppBarTheme(centerTitle: false),
      cardTheme: CardThemeData(
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
        ),
        clipBehavior: Clip.antiAlias,
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          minimumSize: const Size.fromHeight(52),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(14),
          ),
        ),
      ),
    );
  }
}
