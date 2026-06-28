# WiwyTransfer

Transferencia de archivos rápida y segura entre tus dispositivos — estilo LocalSend.

- ⚡ **Transferencia directa (P2P)** cuando ambos dispositivos están en la misma red: máxima velocidad y sin consumir almacenamiento en la nube.
- ☁️ **Cloudflare R2** solo cuando los dispositivos están en redes diferentes o cuando quieres compartir un archivo mediante un enlace.
- 📱 Compatible con **Android, Android TV, Windows, macOS y Linux**.
- 🔒 Transferencias seguras mediante HTTPS y enlaces temporales.
- 📂 Historial de archivos y posibilidad de compartir mediante URL.

## Arquitectura

```
        ┌──────────────────────────────────────┐
        │            Panel Web                 │
        │  • Subir archivos  • Historial       │
        │  • Compartir enlaces • Usuarios      │
        └─────────────────┬────────────────────┘
                          ▼
              ┌────────────────────────┐
              │     Cloudflare R2      │
              └───────────┬────────────┘
        ┌─────────────────┼──────────────────┐
        ▼        ▼         ▼         ▼        ▼
    Android  Android TV  Windows   macOS    Linux

  Misma red  → Transferencia P2P directa (sin R2)
  Redes      → Subir a R2 → Descargar desde R2
  distintas
```

## Estructura del proyecto

- `lib/` — aplicación Flutter (Android, Android TV, Windows, macOS, Linux).
  - `screens/` — pantallas (Enviar, Recibir, Historial, Ajustes).
  - `models/` — modelos de datos.
  - `widgets/` — componentes reutilizables.
- *(próximamente)* `web/` — panel web (Next.js) y `worker/` — API en Cloudflare Workers + R2.

## Desarrollo

Requisitos: [Flutter](https://docs.flutter.dev/get-started/install) 3.44+.

```bash
flutter pub get        # instalar dependencias
flutter run            # ejecutar en el dispositivo conectado
flutter test           # ejecutar pruebas
flutter analyze        # análisis estático
```

### Compilar

```bash
flutter build apk --release       # Android
flutter build macos --release     # macOS
flutter build windows --release   # Windows
flutter build linux --release     # Linux
```

## Estado

🚧 En desarrollo. Actualmente: interfaz base multiplataforma. Próximos pasos:
descubrimiento de dispositivos en red local (UDP multicast), transferencia P2P,
integración con Cloudflare R2 y panel web.

## Licencia

MIT
