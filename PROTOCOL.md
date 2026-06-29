# Protocolo WiwyTransfer

Transferencia de archivos en red WiFi local entre Android y macOS, estilo QuickShare.
Sin servidores externos: descubrimiento por mDNS/Bonjour + transferencia directa por TCP.

## 1. Descubrimiento (mDNS / Bonjour)

- **Tipo de servicio:** `_wiwytransfer._tcp.`
- Cada dispositivo que puede **recibir** anuncia el servicio en su puerto TCP de recepción.
- Cada dispositivo **busca** el mismo tipo para listar pares disponibles.
- Registros TXT:
  | clave | valor | ejemplo |
  |-------|-------|---------|
  | `v`   | versión de protocolo | `1` |
  | `name`| nombre visible del dispositivo | `Pixel de Ángel` |
  | `os`  | `android` \| `macos` | `macos` |

El nombre mostrado al usuario es el del TXT `name`; si falta, se usa el nombre del servicio Bonjour.

## 2. Transferencia (TCP)

Una conexión TCP por lote de envío. Todo el control va en **líneas JSON UTF-8 terminadas en `\n`**;
los bytes de archivo van crudos, sin codificar.

### Secuencia

```
Emisor (cliente)                         Receptor (servidor)
  | --- conecta al puerto TCP ----------->|
  | --- HEADER (1 línea JSON) ----------->|
  |                                       | (muestra diálogo aceptar/rechazar)
  | <----------- DECISION (1 línea JSON) -|
  | --- bytes archivo 1 ----------------->|
  | --- bytes archivo 2 ----------------->|
  |     ...                               |
  | <----------- RESULT (1 línea JSON) ---|
  |                (cierran)              |
```

### HEADER (emisor → receptor)

```json
{"v":1,"sender":"Pixel de Ángel","os":"android","files":[{"name":"foto.jpg","size":238411},{"name":"doc.pdf","size":91020}]}
```

- `size` en bytes. El receptor leerá **exactamente** `size` bytes por archivo, en orden.
- `name` es solo el nombre de archivo (sin ruta). El receptor sanea separadores.

### DECISION (receptor → emisor)

```json
{"accept":true}
```
o
```json
{"accept":false,"reason":"rechazado por el usuario"}
```

Si `accept` es `false`, ambos cierran sin transferir bytes.

### Bytes de archivo

Tras un `accept:true`, el emisor envía los archivos en el mismo orden del HEADER,
concatenando los bytes crudos. El receptor sabe cuántos bytes corresponden a cada
archivo por el `size` del HEADER. Se recomienda buffer de 64 KiB.

### RESULT (receptor → emisor)

```json
{"ok":true,"received":2}
```
o en error:
```json
{"ok":false,"error":"escritura fallida","received":1}
```

## 3. Notas

- Versión de protocolo actual: **1**. Si `v` no coincide, el receptor puede rechazar.
- Sin cifrado en v1 (red local de confianza). Mejora futura: TLS con PSK por código corto.
- Puerto TCP: efímero asignado por el SO y anunciado por mDNS (no fijo).
