# Instrucciones para añadir el logo de Koda

## Paso 1: Descargar el logo
El logo está en: https://i.imgur.com/LH92PwU.jpeg

Descargalo y guardalo como:
- `app/src/main/res/drawable/ic_koda_logo.png`

## Paso 2: Crear versiones para diferentes densidades

Necesitas crear el logo en estos tamaños:

| Carpeta | Tamaño | Archivo |
|---------|--------|---------|
| mipmap-mdpi | 48x48px | ic_launcher.png |
| mipmap-hdpi | 72x72px | ic_launcher.png |
| mipmap-xhdpi | 96x96px | ic_launcher.png |
| mipmap-xxhdpi | 144x144px | ic_launcher.png |
| mipmap-xxxhdpi | 192x192px | ic_launcher.png |
| mipmap-anydpi-v26 | adaptive | ic_launcher.xml (ya creado) |

## Paso 3: Opcional - Redimensionar automáticamente

Si tenés ImageMagick instalado:

```bash
cd app/src/main/res

# Crear versiones redimensionadas
convert ic_koda_logo.png -resize 48x48 mipmap-mdpi/ic_launcher.png
convert ic_koda_logo.png -resize 72x72 mipmap-hdpi/ic_launcher.png
convert ic_koda_logo.png -resize 96x96 mipmap-xhdpi/ic_launcher.png
convert ic_koda_logo.png -resize 144x144 mipmap-xxhdpi/ic_launcher.png
convert ic_koda_logo.png -resize 192x192 mipmap-xxxhdpi/ic_launcher.png
```

## Cambios realizados

Ya actualicé:
- `ic_splash.xml` - Usa el nuevo logo
- `activity_launcher.xml` - Logo más grande (64dp) con fondo
- `ic_launcher_background.xml` - Fondo con gradiente navy
- `ic_launcher_foreground.xml` - Referencia al logo
- `logo_background.xml` - Fondo para el logo en welcome screen
- `mipmap-anydpi-v26/ic_launcher.xml` - Adaptive icon

