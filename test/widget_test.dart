// Pruebas básicas de WiwyTransfer.

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:wiwytransfer/main.dart';

void main() {
  testWidgets('La app arranca y muestra la marca y navegación',
      (WidgetTester tester) async {
    await tester.pumpWidget(const WiwyTransferApp());
    await tester.pumpAndSettle();

    // El nombre de la app aparece en la cabecera.
    expect(find.text('WiwyTransfer'), findsWidgets);

    // La navegación inferior tiene sus cuatro secciones.
    expect(find.text('Enviar'), findsWidgets);
    expect(find.text('Recibir'), findsWidgets);
    expect(find.text('Historial'), findsWidgets);
    expect(find.text('Ajustes'), findsWidgets);
  });

  testWidgets('Se puede navegar a la sección Recibir',
      (WidgetTester tester) async {
    await tester.pumpWidget(const WiwyTransferApp());
    await tester.pumpAndSettle();

    await tester.tap(find.byIcon(Icons.download_outlined));
    await tester.pumpAndSettle();

    expect(find.text('Esperando archivos…'), findsOneWidget);
  });
}
