# Cilindro

**Los efectos de Cylinder (iOS) en el escritorio de Android.**

Cilindro es un módulo de Xposed (LSPosed / Vector) que ejecuta los **scripts Lua originales de
[Cylinder](https://github.com/rweichler/cylinder)** —el tweak de jailbreak que animaba el paso entre
páginas del escritorio del iPhone— sobre el **Pixel Launcher**. Los scripts se ejecutan **sin
modificar**: Cilindro reimplementa en Android la API de Lua que ofrecía Cylinder.

Si venías de iOS con jailbreak y echabas de menos Vortex, Cube o Ant Lines, esto es para ti.

> *English summary at the bottom.*

---

## Qué hace

- **Ejecuta scripts de Cylinder tal cual**, con sus `dofile()` e `include/`, en Lua 5.2
  ([LuaJ](https://github.com/luaj/luaj)), que es la versión que documenta Cylinder.
- **3D de verdad**: rotaciones con perspectiva y profundidad (`pitch`, `yaw`, eje Z) mediante
  `android.graphics.Camera` y `View.setAnimationMatrix`, de modo que efectos como *Cube (inside)*
  se ven como en el iPhone.
- **App para elegir efecto**, separada en *probados* y *sin probar*. Tocas uno, vuelves al
  escritorio y ya está puesto: sin reiniciar nada.
- **Coste mínimo**: medido en un Pixel 6a, un efecto ocupa entre el **0,06 % y el 0,18 %** del
  tiempo de un fotograma (de 5 a 15 µs por llamada, según los iconos de la página).

## Requisitos

| | |
|---|---|
| Teléfono | Android rooteado con un framework compatible con Xposed (**LSPosed** o **Vector**) |
| Launcher | **Pixel Launcher** (`com.google.android.apps.nexuslauncher`) |
| Probado en | Pixel 6a, Android 17 (`CP2A.260705.006`), KernelSU Next + ReZygisk + Vector 2.2 |

Otros launchers basados en Launcher3 probablemente funcionarían cambiando el nombre del paquete,
pero no está probado.

## Instalación

1. Instala `Cilindro.apk`.
2. En LSPosed/Vector, **activa el módulo** y márcale como ámbito el **Pixel Launcher**.
3. Fuerza la detención del Pixel Launcher (o reinicia).
4. Abre **Cilindro**, elige un efecto y vuelve al escritorio.

## Efectos

### Incluidos (12, GPL v2)

Los 12 scripts de [supermamon](https://github.com/supermamon/cylinder-scripts), que se publican
bajo GPL v2 y van **sin modificar** en [`efectos/supermamon/`](efectos/supermamon/):

Alternate Spin · **Ant Lines (Horizontal)** ✅ · Ant Lines (Vertical) · Black Hole · Explosion ·
Flip icons (horizontal) · Flip icons (vertical) · Gather Around · **Icon Roll** ✅ · Page Twist ·
Page flip · Page spin

✅ = probado y funcionando en el Pixel 6a. El resto no se ha probado aún.

### No incluidos (50): añádelos tú

El repositorio original de Cylinder **no tiene licencia**, así que los scripts que hay en él
(de rweichler y de otros ocho autores) **no se pueden redistribuir aquí**, aunque sean públicos.
Entre ellos están los más populares, como Vortex y Cube.

Pero Cilindro los ejecuta. Si los tienes o los encuentras, se añaden en un momento:

```sh
./efectos.sh --rweichler          # descarga todos los del repositorio original de Cylinder
./efectos.sh "Vortex" "Cube (inside)"     # o solo los que quieras
./construye.sh
```

Cualquier script de Cylinder que tengas guardado sirve también: ponlo en
`efectos/<origen>/<Nombre>/efecto.lua`, con su carpeta `include/` al lado si la usa, y vuelve a
compilar. Si encuentras alguno que funcione (o que falle), **abre un issue**: así la lista de
probados crece.

| Efecto | Autor | En el Pixel 6a |
|---|---|---|
| Beta382's personal Rubik's Cube | Beta382 |  |
| Cube (inside) (no zoom) | Beta382 |  |
| Cube (outside) (no zoom) | Beta382 |  |
| Curl and Roll Away | Beta382 | ✅ funciona |
| Curl and Roll Away Alternate | Beta382 |  |
| Page Squeeze | Beta382 |  |
| Rubik's Cube | Beta382 |  |
| Rubik's Cube (complex) | Beta382 |  |
| Snake | Beta382 |  |
| Vertical Scrolling | Beta382 |  |
| Vortex | Beta382 | ✅ funciona |
| Wave | Beta382 |  |
| Hyperspace | cylgom |  |
| Psychospiral | cylgom | ✅ funciona |
| Suck | cylgom |  |
| Backwards | gertab |  |
| Double Door | gertab |  |
| Cube overlap | JGTweaks |  |
| Burst | KnifeOfPi |  |
| Focus | KnifeOfPi |  |
| Helix | KnifeOfPi |  |
| Radar | KnifeOfPi |  |
| Dominoes | Qaanol |  |
| Foosball | Qaanol |  |
| Roadrunner | Qaanol |  |
| Spinners | Qaanol |  |
| Bubble | r_idn |  |
| Carousel (left) | r_idn |  |
| Carousel (right) | r_idn |  |
| Checkerboard scatter | r_idn | ✅ funciona |
| Zoom and Fade | r_idn |  |
| Zoom and Fade (alt) | r_idn |  |
| Blinds | rweichler |  |
| Card (horizontal) | rweichler |  |
| Card (vertical) | rweichler |  |
| Chomp | rweichler |  |
| Cube (inside) | rweichler | ✅ funciona |
| Cube (outside) | rweichler |  |
| Hella Far | rweichler |  |
| Hinge | rweichler |  |
| Icon Collection | rweichler |  |
| Page Fade | rweichler |  |
| Shrink | rweichler |  |
| Shrink completely | rweichler |  |
| Spin | rweichler |  |
| Stairs (left down) | rweichler |  |
| Stairs (right down) | rweichler |  |
| Stay put (combine with others) | rweichler |  |
| EmotionUI | ViktorX11 |  |
| Tornado | supermamon | necesita `cube.lua` de rweichler |

## Compilar

Sin Gradle: `aapt2` + `javac` + `d8` + `apksigner`.

Necesitas un **JDK 17** o posterior y el **Android SDK** con *build-tools* y una plataforma de nivel
**29 o superior**. El script los busca solo; si no los encuentra:

```sh
./efectos.sh --gpl                     # los 12 efectos incluidos (ya vienen en el repo)
ANDROID_HOME=/ruta/al/sdk JAVA_HOME=/ruta/al/jdk ./construye.sh
```

El resultado es `build/Cilindro.apk`. La primera vez se crea una clave de firma en
`cilindro.keystore`; **es tuya, no la subas** (el `.gitignore` ya la excluye). LuaJ no va en el
repositorio: se descarga de Maven Central y se comprueba su huella SHA-1.

## Cómo funciona

Lo interesante, por si quieres llevarlo a otro launcher o mejorarlo:

- **No engancha ningún método del launcher.** Google ofusca y cambia las clases del Pixel Launcher en
  cada versión, así que solo se engancha `Activity.onResume`, que es del sistema. El escritorio se
  localiza por el **nombre de recurso** `workspace`, que tampoco se ofusca.
- **Corre en cada fotograma** con un `ViewTreeObserver.OnPreDrawListener`. Ojo: el
  `OnScrollChangedListener` **no** salta al pasar de página en Launcher3.
- **Las vistas se reinician en cada fotograma** antes de llamar al script, o las transformaciones se
  acumularían.
- **El desplazamiento de cada página** es `scroll − left + paddingLeft`: sin el margen interno del
  escritorio, en reposo sale −15 y el efecto tuerce todo un poco.
- **`page.width` es el paso entre páginas**, no el ancho de la vista: el script lo usa como arista del
  cubo, y con el ancho de la vista las caras no llegan a tocarse.
- **El orden de las transformaciones va al revés.** Core Animation concatena por la izquierda, así que
  la *última* operación que pide el script es la *primera* que se aplica. Cilindro las reproduce en
  orden inverso sobre la `Camera`.
- **`Camera.setLocation` no va en píxeles**, sino en pulgadas de 72 px. Pasarle píxeles pone la
  cámara a kilómetros y todo se ve diminuto.
- **La app se comunica con el módulo por un `ContentProvider`**: el módulo corre dentro del proceso
  del launcher y no puede leer las preferencias de otra app (SELinux lo impide hasta con root).

### API de Cylinder soportada

| Cylinder | Android |
|---|---|
| `view:translate(x, y, z)` | `setTranslationX/Y`; con Z, matriz 3D con `Camera` |
| `view:rotate(ángulo, pitch, yaw, roll)` | `setRotation`; con pitch/yaw, matriz 3D con `Camera` |
| `view:scale(x, y)` | `setScaleX/Y` |
| `view.alpha` | `setAlpha` |
| `view.x`, `.y`, `.width`, `.height` | posición y tamaño de la vista |
| `page.layer.x`, `.y` | anclaje de la página, independiente de la matriz |
| `page.subviews`, `subviews(page)` | los iconos de la página |
| `dofile("include/…")` | se resuelve dentro de la carpeta del efecto |
| `print`, `popup` | al registro de Xposed |
| `PERSPECTIVE_DISTANCE` | 1000 |

### Limitaciones conocidas

- **`view.layer.transform`** (matrices 4×4 en crudo) no está implementado: los efectos tipo
  *Rubik's Cube* no funcionarán.
- **Los widgets del escritorio** (reloj, *De un vistazo*) cuentan como iconos y el efecto también
  los mueve.
- Solo **Pixel Launcher**.

`herramientas/Banco.java` es el banco de pruebas con el que se midió el rendimiento: ejecuta un
script real con LuaJ en el propio teléfono (`app_process`).

## Créditos y licencia

- **Cylinder** y su API de Lua: **Reed Weichler** ([rweichler](https://github.com/rweichler)).
- Los 12 efectos incluidos: **supermamon**, GPL v2.
- **LuaJ**, licencia MIT.

Cilindro **no contiene código de Cylinder**: es una implementación nueva de su API. Se publica bajo la
**GNU GPL v2 o posterior**. Detalles en [CREDITOS.md](CREDITOS.md). No está afiliado con Cylinder ni
con sus autores.

---

## English summary

**Cilindro** is an Xposed module (LSPosed/Vector) that runs the **original Lua scripts of
[Cylinder](https://github.com/rweichler/cylinder)**, the iOS jailbreak tweak for home-screen page
transitions, on Android's **Pixel Launcher**, unmodified. It reimplements Cylinder's Lua API on top of
Android views, including real 3D (`Camera` + `View.setAnimationMatrix`), and ships a small picker app.
Tested on a Pixel 6a running Android 17 with KernelSU Next + Vector.

Only the 12 **GPL v2** scripts by supermamon are bundled. The original Cylinder repository has **no
license**, so its scripts (Vortex, Cube…) are not redistributed here. Run `./efectos.sh --rweichler`
to fetch them from their source, or drop any Cylinder script into
`efectos/<origin>/<Name>/efecto.lua` and rebuild. Build with `./construye.sh` (JDK 17+ and the Android
SDK, no Gradle). License: GPL v2 or later.
