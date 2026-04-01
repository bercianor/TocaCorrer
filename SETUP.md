# Guía de inicialización: Git + Keystore + GitHub

> Documento temporal — bórralo cuando hayas completado la configuración.

---

## Paso 1 — Crear el keystore de firma

Ejecuta esto **una sola vez** desde cualquier directorio. Guarda las contraseñas en tu gestor (Bitwarden, etc.).

```bash
keytool -genkey -v \
  -keystore ~/tocacorrer.jks \
  -alias tocacorrer \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

Te pedirá:

- **Contraseña del keystore** → ponla fuerte, guárdala como `KEYSTORE_PASSWORD`
- Nombre, organización, ciudad, país → puedes poner lo que quieras
- **Contraseña de la key** → puede ser la misma que la del keystore, guárdala como `KEY_PASSWORD`

Guarda el fichero `~/tocacorrer.jks` en un sitio seguro (Nextcloud, etc.).
**Nunca lo subas a Git.**

---

## Paso 2 — Inicializar el repositorio Git local

```bash
cd /home/ruben/dev/TocaCorrer

# Inicializar git
git init

# Añadir todo y hacer el primer commit
git add .
git commit -m "feat: initial commit"
```

---

## Paso 3 — Crear el repositorio en GitHub

1. Ve a [github.com/new](https://github.com/new)
2. Nombre: `TocaCorrer`
3. Descripción: `Native Android running trainer — De-Googled, calendar-driven, voice-guided`
4. Visibilidad: **Public** (para que las releases sean accesibles)
5. **No** inicialices con README ni .gitignore (ya los tenemos)
6. Pulsa **Create repository**

---

## Paso 4 — Conectar el repo local con GitHub y subir

```bash
cd /home/ruben/dev/TocaCorrer

# Añadir el remote (sustituye 'bercianor' por tu usuario si es diferente)
git remote add origin https://github.com/bercianor/TocaCorrer.git

# Subir
git push -u origin master
```

---

## Paso 5 — Configurar los secrets en GitHub

Ve a: `github.com/bercianor/TocaCorrer → Settings → Secrets and variables → Actions → New repository secret`

Necesitas crear **4 secrets**:

### Secret 1: `KEYSTORE_BASE64`

```bash
# Ejecuta esto y copia el resultado completo
base64 -w 0 ~/tocacorrer.jks
```

Pega la salida como valor del secret.

### Secret 2: `KEYSTORE_PASSWORD`

La contraseña del keystore que pusiste en el Paso 1.

### Secret 3: `KEY_ALIAS`

```
tocacorrer
```

### Secret 4: `KEY_PASSWORD`

La contraseña de la key que pusiste en el Paso 1.

---

## Paso 6 — Verificar que el workflow funciona

Crea y sube tu primera tag para disparar la release:

```bash
cd /home/ruben/dev/TocaCorrer

# Primera release
git tag v1.0.0
git push origin v1.0.0
```

Ve a `github.com/bercianor/TocaCorrer/actions` para ver el workflow ejecutándose.

Cuando termine, en `github.com/bercianor/TocaCorrer/releases` aparecerá la release con:

- `tocacorrer-v1.0.0.apk`
- `tocacorrer-v1.0.0.apk.sha256`
- El changelog generado automáticamente

---

## Referencia rápida — tags

```bash
# Release estable
git tag v1.0.0 && git push origin v1.0.0

# Beta (pre-release)
git tag v1.1.0-beta.1 && git push origin v1.1.0-beta.1

# Release candidate (pre-release)
git tag v1.1.0-rc.1 && git push origin v1.1.0-rc.1

# Ver todas las tags
git tag --sort=-version:refname

# Borrar una tag (local + remote) si te equivocas
git tag -d v1.0.0
git push origin --delete v1.0.0
```

---

## Referencia rápida — conventional commits

El changelog agrupa los commits por prefijo. Úsalos desde hoy:

```bash
git commit -m "feat: add interval pace display"
git commit -m "feat(parser): support RC token for race pace"
git commit -m "fix: distance phase skipping on treadmill mode"
git commit -m "fix(timer): gate not cancelled on pause"
git commit -m "perf: reduce GPS update frequency"
git commit -m "refactor: extract formatRoutineSummary helper"
git commit -m "docs: update README with new phase types"
git commit -m "test: add WorkoutServiceDistanceTest"
git commit -m "chore: bump versionCode to 2"
git commit -m "build: enable minify in release builds"

# Breaking change (añade ! antes de :)
git commit -m "feat!: replace PhaseType RUN/REST with 8 specific types"
```

---

## Checklist final

- [ ] Keystore creado en `~/tocacorrer.jks`
- [ ] Contraseñas guardadas en el gestor de contraseñas
- [ ] `.gitignore` incluye `*.jks`
- [ ] `git init` + primer commit hecho
- [ ] Repositorio creado en GitHub
- [ ] `git push -u origin main` hecho
- [ ] 4 secrets configurados en GitHub Actions
- [ ] Primera tag `v1.0.0` creada y subida
- [ ] Workflow ejecutado correctamente en GitHub Actions
- [ ] Release visible en GitHub con APK y checksum
