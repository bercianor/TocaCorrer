# TocaCorrer

> Entrenador de running nativo para Android con guía por voz, integración con calendario y cero dependencia de Google.

[![Licencia: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-7.0%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.x-purple.svg)](https://kotlinlang.org)

**[English version](README.md)**

---

## ¿Qué es TocaCorrer?

TocaCorrer es una app Android completamente offline y sin Google que lee tus entrenamientos de running desde tu calendario local y te guía por cada fase con anuncios de voz y feedback háptico.

Sin Google Play Services. Sin rastreo. Sin nube. Solo tú y la carretera.

---

## Características

- **Entrenamientos desde el calendario** — lee las sesiones del día desde tu calendario del sistema (compatible con Nextcloud / DAVx⁵)
- **DSL de entrenamiento** — define intervalos en un formato sencillo: `D5 - 4x(R3 - D1) - D5`
- **8 tipos de fase** — Descanso (D), Trote (T), Rodaje (R), Rodaje alegre (RA), Rodaje fuerte (RF), Fartlek (F), Progresivos (P), Ritmo de competición (RC)
- **Fases por distancia** — mezcla tiempo y distancia: `D5 - 3x(R1k - D1) - D5` muestra `13 min + 3 km`
- **Guía por voz** — TTS nativo de Android anuncia cambios de fase, puntos de control cada 500 m y cuentas atrás
- **Seguimiento GPS** — `LocationManager` nativo, sin Google Maps SDK
- **Auto-pausa / Auto-reanudación** — pausa cuando la velocidad baja de 0,5 m/s durante un tiempo configurable
- **Modo cinta** — entrena sin GPS con avance manual de fase
- **Servicio en primer plano** — sigue corriendo con la pantalla apagada
- **Exportación GPX 1.1** — comparte o guarda tus rutas
- **Backup y restauración** — exportación / importación JSON, totalmente local
- **Estadísticas** — gráficos de barras semanales y mensuales
- **Bilingüe** — inglés y español, configurable en ajustes
- **Tema claro / oscuro / sistema**

---

## DSL de entrenamiento

| Token | Significado | Ejemplo |
|-------|-------------|---------|
| `D` | Descanso / Caminar | `D5` = descansar 5 min |
| `T` | Trote | `T10` = trotar 10 min |
| `R` | Rodaje | `R1k` = rodaje 1 km |
| `RA` | Rodaje alegre | `RA20` = 20 min |
| `RF` | Rodaje fuerte | `RF5` = 5 min |
| `F` | Fartlek | `F30` = 30 min |
| `P` | Progresivos | `P20` = 20 min |
| `RC` | Ritmo de competición | `RC5` = 5 min |
| `Nx(...)` | Series | `4x(R3 - D1)` = 4 repeticiones |

**Ejemplo:** `D5 - 4x(R3 - D1) - D5` → calentamiento 5 min, 4 × (rodaje 3 min + descanso 1 min), vuelta a la calma 5 min

Añade esto a la descripción de tu evento de calendario y TocaCorrer lo detecta automáticamente.

---

## Requisitos

- Android 7.0+ (API 24)
- Calendario sincronizado con [DAVx⁵](https://www.davx5.com/) o cualquier cliente CalDAV (Nextcloud, etc.)
- Permiso de GPS para seguimiento en exterior
- Motor TTS instalado (el TTS por defecto de Android funciona perfectamente)

---

## Instalación

TocaCorrer **no se distribuye por Google Play**. Descarga el último APK desde la página de [Releases](https://github.com/bercianor/releases):

1. Descarga `tocacorrer-vX.Y.Z.apk`
2. Verifica el checksum: `sha256sum -c tocacorrer-vX.Y.Z.apk.sha256`
3. Activa _Instalar desde fuentes desconocidas_ en los ajustes de Android
4. Instala el APK

---

## Compilar desde el código fuente

```bash
# Clonar el repositorio
git clone https://github.com/bercianor/TocaCorrer.git
cd TocaCorrer

# Build debug
./gradlew assembleDebug

# Ejecutar tests
./gradlew test

# Build release (requiere configuración de firma — ver abajo)
./gradlew assembleRelease
```

### Firmar un build release en local

```bash
# Generar un keystore (configuración única)
keytool -genkey -v -keystore tocacorrer.jks -alias tocacorrer \
  -keyalg RSA -keysize 2048 -validity 10000

# Exportar variables de entorno
export KEYSTORE_PATH=/path/to/tocacorrer.jks
export KEYSTORE_PASSWORD=tu_contraseña_del_store
export KEY_ALIAS=tocacorrer
export KEY_PASSWORD=tu_contraseña_de_la_clave

./gradlew assembleRelease
```

---

## Stack tecnológico

| Capa | Tecnología |
|------|-----------|
| Lenguaje | Kotlin |
| UI | Jetpack Compose |
| Base de datos | Room (SQLite) |
| Localización | `android.location.LocationManager` (GPS nativo) |
| Background | Foreground Service |
| Voz | Android `TextToSpeech` |
| Haptics | Android `Vibrator` |
| Serialización | Moshi (backup / restore) |
| Build | Gradle 8 + Kotlin DSL |
| SDK mínimo | API 24 (Android 7.0) |

**Sin Google Play Services. Sin Firebase. Sin analíticas.**

---

## Permisos

| Permiso | Propósito |
|---------|-----------|
| `ACCESS_FINE_LOCATION` | Seguimiento GPS |
| `ACCESS_COARSE_LOCATION` | Ubicación aproximada |
| `FOREGROUND_SERVICE` | Ejecución en segundo plano |
| `FOREGROUND_SERVICE_LOCATION` | GPS con pantalla apagada |
| `POST_NOTIFICATIONS` | Notificación de entrenamiento (Android 13+) |
| `VIBRATE` | Haptics en cambio de fase |
| `READ_CALENDAR` | Leer entrenamiento del calendario |
| `WAKE_LOCK` | Mantener CPU activa durante el entrenamiento |

---

## Privacidad

- Todos los datos permanecen en tu dispositivo
- No se declara permiso de internet
- Sin analíticas ni informes de errores
- Los datos GPS se almacenan localmente en SQLite y solo se exportan cuando tú lo solicitas

---

## Licencia

[MIT](LICENSE) © 2026 Rubén (bercianor)
