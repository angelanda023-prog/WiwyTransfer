"""Hace transparente el fondo blanco EXTERIOR del icono.

Rellena desde las 4 esquinas hacia dentro: solo el blanco conectado al borde
se vuelve transparente, así los elementos blancos interiores (avión, texto,
logos) se conservan intactos.
"""
from PIL import Image, ImageDraw
import sys

src = sys.argv[1] if len(sys.argv) > 1 else "icon.png"
img = Image.open(src).convert("RGBA")
w, h = img.size

# Relleno por inundación desde cada esquina con tolerancia para el antialias.
for corner in [(0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)]:
    ImageDraw.floodfill(img, corner, (0, 0, 0, 0), thresh=90)

img.save(src)
print(f"Fondo eliminado: {src} ({w}x{h}) con alpha")
