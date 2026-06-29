# WiwyTransfer

Transferencia de archivos en red WiFi local entre **Android** y **macOS**, estilo QuickShare / Nearby Share.
Sin servidores ni internet: los dispositivos se descubren por **mDNS/Bonjour** y transfieren **directo por TCP**.

Apps **nativas**: Android en Kotlin (Jetpack Compose), macOS en Swift (SwiftUI).

```
WiwyTransfer/
├── PROTOCOL.md        ← protocolo compartido (descubrimiento + transferencia)
├── assets/icon/       ← icono de la app
├── android/           ← app Android (Kotlin + Compose)
└── macos/             ← app macOS (Swift + SwiftUI, sin Xcode)
```

## Requisitos

- Ambos dispositivos en la **misma red WiFi**.
- Android 8.0 (API 26) o superior.
- macOS 13 (Ventura) o superior.

---

## Android

**Compilar el APK de depuración:**

```bash
cd android
./gradlew :app:assembleDebug
# APK en: app/build/outputs/apk/debug/app-debug.apk
```

**Instalar en un dispositivo conectado por USB (con depuración activada):**

```bash
./gradlew :app:installDebug
# o:  adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> El proyecto trae `local.properties` apuntando al SDK de Android. Si lo clonas en otra
> máquina, crea ese archivo con `sdk.dir=/ruta/al/Android/sdk`.

---

## macOS

No necesita Xcode, solo las **Command Line Tools** (Swift 5.9+).

```bash
cd macos
./build-app.sh          # compila en release y crea WiwyTransfer.app
open WiwyTransfer.app    # ejecuta
```

Para desarrollo rápido sin empaquetar:

```bash
swift run
```

### Instalar fuera de la App Store

macOS permite instalar apps fuera de la tienda. `build-app.sh` firma el `.app` con firma
**ad-hoc**, suficiente para tu propio Mac. Para usarlo:

1. Copia `WiwyTransfer.app` a `/Applications`.
2. La primera vez: clic derecho → **Abrir** (o Ajustes → Privacidad y seguridad → *Abrir igualmente*).
3. Acepta el permiso de **red local** cuando lo pida (necesario para descubrir y recibir).

Para distribuir a otros Macs sin avisos hace falta firma + notarización con una cuenta del
Apple Developer Program.

---

## Cómo se usa

1. Abre la app en ambos dispositivos (en Mac, deja la pestaña **Recibir** visible para poder recibir).
2. En **Ajustes** puedes cambiar el nombre con el que te ven los demás.
3. Para enviar: pestaña **Enviar** → *Elegir archivos* → toca el dispositivo destino.
   - En Android también puedes usar **Compartir → WiwyTransfer** desde cualquier app.
4. El receptor acepta la solicitud; los archivos llegan a:
   - **Android:** `Descargas/WiwyTransfer`
   - **macOS:** `~/Descargas/WiwyTransfer`

---

## Quick Share nativo (interop, en desarrollo)

La app de macOS también interopera con el **Quick Share nativo de Android** (sin app
extra en el móvil), basándose en el protocolo de [NearDrop](https://github.com/grishka/NearDrop):

- El Mac se anuncia por Bonjour (`_FC9F5ED42C8A._tcp`) y aparece en *Compartir → Quick Share* del Android.
- Handshake **UKEY2** (ECDH P-256 + HKDF), canal **AES-256-CBC + HMAC-SHA256**.
- La app **permanece en la barra de menús** para recibir aunque cierres la ventana;
  aceptas cada transferencia desde una **notificación** (botón *Aceptar*).
- Los archivos llegan a `~/Descargas/WiwyTransfer`.

Estado: **recibir** implementado (Android → Mac). **Enviar** (Mac → Android) en progreso;
funcionará con el móvil en su pantalla de *Recibir* (límite de macOS: no puede "despertar"
por BLE a un móvil que no esté en modo recepción).

## Detalles técnicos

- **Descubrimiento:** servicio Bonjour `_wiwytransfer._tcp` con TXT `name`/`os`/`v`.
  Android usa `NsdManager`; macOS usa `NWBrowser`/`NWListener`.
- **Transferencia:** una conexión TCP por lote. Cabecera JSON con la lista de archivos y
  tamaños, confirmación del receptor, y luego los bytes crudos en orden. Ver [PROTOCOL.md](PROTOCOL.md).
- **Sin cifrado** en esta versión (pensado para redes locales de confianza).

## Limitaciones conocidas

- En Android la recepción funciona con la app en primer plano (modelo "abre para recibir").
- Sin reanudación de transferencias interrumpidas.
- Algunas redes WiFi (con *AP/client isolation*) bloquean el tráfico entre dispositivos.
