# 🚗 SpeedMonitor - Control de Velocidad en Tiempo Real

## Características
- 📍 **GPS en tiempo real** — Velocidad actualizada cada 500ms
- 🪟 **Miniventana flotante** — Visible sobre cualquier app
- 🔔 **Alerta sonora** — Doble bip cuando superas el límite
- 🎚️ **Límite configurable** — De 20 a 200 km/h
- 🌙 **Diseño oscuro** — Fácil de leer al volante

## Cómo compilar el APK

### Opción 1: Android Studio (recomendado)
1. Abre Android Studio
2. Selecciona **File > Open** y abre esta carpeta
3. Espera que sincronice Gradle
4. Ve a **Build > Build Bundle(s)/APK(s) > Build APK(s)**
5. El APK estará en `app/build/outputs/apk/debug/app-debug.apk`

### Opción 2: Línea de comandos
```bash
# Copia local.properties.template a local.properties y ajusta la ruta del SDK
cp local.properties.template local.properties

# Compila el APK debug
./gradlew assembleDebug

# El APK se genera en:
# app/build/outputs/apk/debug/app-debug.apk
```

### Opción 3: GitHub + GitHub Actions
Sube este proyecto a GitHub y usa las GitHub Actions de Android para compilar automáticamente.

## Instalación en el dispositivo

### Desde Android Studio:
- Conecta el teléfono por USB
- Activa **Depuración USB** en el teléfono
- Clic en **Run ▶**

### Manual (APK instalado):
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Sin computadora:
1. Copia el APK al teléfono
2. En el teléfono ve a **Ajustes > Seguridad > Instalar apps desconocidas**
3. Activa el permiso para tu gestor de archivos
4. Navega al APK y tócalo para instalar

## Permisos requeridos

| Permiso | Por qué |
|---------|---------|
| `ACCESS_FINE_LOCATION` | Leer velocidad del GPS |
| `SYSTEM_ALERT_WINDOW` | Mostrar miniventana flotante |
| `FOREGROUND_SERVICE` | Mantener el servicio activo |
| `POST_NOTIFICATIONS` | Notificación en barra de estado |

## Uso
1. Abre SpeedMonitor
2. Ajusta el límite de velocidad con el deslizador
3. Toca **"Guardar Límite"**
4. Toca **"Iniciar Monitoreo"**
5. Concede los permisos solicitados
6. ¡Listo! Puedes usar otras apps. La miniventana flotará sobre todo.

## Comportamiento de la alerta
- ✅ **Bajo el límite** — Velocímetro verde, sin sonido
- ⚠️ **Cerca del límite (85%)** — Velocímetro naranja, sin sonido
- 🚨 **Sobre el límite** — Velocímetro rojo, doble bip constante

## Estructura del proyecto
```
SpeedMonitor/
├── app/src/main/
│   ├── java/com/speedmonitor/
│   │   ├── MainActivity.kt     # Pantalla principal
│   │   └── SpeedService.kt     # Servicio GPS + overlay + audio
│   ├── res/
│   │   ├── layout/
│   │   │   ├── activity_main.xml    # Layout pantalla principal
│   │   │   └── overlay_speed.xml   # Layout miniventana
│   │   ├── drawable/
│   │   │   ├── card_background.xml
│   │   │   └── overlay_background.xml
│   │   └── values/
│   │       ├── strings.xml
│   │       └── themes.xml
│   └── AndroidManifest.xml
└── README.md
```
