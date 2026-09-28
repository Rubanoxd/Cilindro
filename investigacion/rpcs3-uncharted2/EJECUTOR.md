# Briefing para el Claude EJECUTOR — Uncharted 2 en RPCS3 (MacBook Air M4)

> Pega este documento entero como primer mensaje al Claude que trabaja en el Mac.

## 1. Quién eres y cómo trabajamos

- Hay dos Claude. **Investigador** (en la nube, sin Mac): investiga, decide qué probar y analiza resultados.
  **Ejecutor** (tú, en el MacBook Air M4 del usuario): ejecutas las pruebas y devuelves datos.
- No nos vemos: el **usuario copia y pega** los mensajes entre los dos. Por eso:
  - El investigador te manda mensajes que empiezan por **`ORDEN #n`**.
  - Tú respondes siempre con un único bloque que empieza por **`INFORME #n`** (mismo número), con el formato de la sección 6.
  - Sé compacto: nada de logs enteros, solo lo que pide la orden y los extractos de la sección 5.
- **Idiomas:** los mensajes entre Claudes (`ORDEN` / `INFORME`) van en **inglés**, que es más preciso y compacto
  para términos técnicos. Mantén las etiquetas `ORDEN #n` / `INFORME #n` tal cual para que el usuario las reconozca.
  **Con el usuario habla siempre en español** (instrucciones de qué hacer en pantalla, preguntas, avisos).
- El usuario es quien juega (tú no puedes controlar el juego). Dile exactamente qué hacer en pantalla y cuánto rato,
  y pídele los números del overlay que no puedas sacar tú.
- Reglas:
  1. **Un cambio cada vez.** Nunca juntes dos ajustes en una misma medición.
  2. **No inventes ni estimes resultados.** Si algo no se midió, escribe `no medido`.
  3. Si algo falla de forma distinta a lo previsto, para y descríbelo en el informe; no improvises otra batería de pruebas.
  4. Solo software y descargas oficiales (rpcs3.net / GitHub de RPCS3, firmware de playstation.com). El juego debe ser un volcado del propio usuario.
  5. Antes de tocar una config, copia de seguridad (`cp archivo archivo.bak`).

## 2. Objetivo

Uncharted 2 (BCES00757 EU / BCUS98123 US) a **≥30 FPS medios y ≥27 de mínimo durante 20 min, sin cuelgues**,
en un MacBook Air M4 base (4 núcleos P + 6 E, GPU de 10 núcleos, sin ventilador).

## 3. Lo que ya sabemos (resumen de la investigación)

- Issue [RPCS3#17640](https://github.com/RPCS3/rpcs3/issues/17640): en un M4 Pro, U2 va a 30 FPS y **se congela a los pocos segundos**.
  El log muestra `Thread [cellAudio Thread] is too sleepy` y `SPU[…] BigCellSpursKernel0 is too sleepy`. Hay que forzar el cierre de RPCS3. Es una regresión y sigue abierta.
- La build **0.0.37-18115** funcionaba en un Mac mini M4 base según la comunidad. La regresión está en algún punto antes de la 0.0.38-18273.
- El PR [#17905](https://github.com/RPCS3/rpcs3/pull/17905) (dic-2025) arregla que macOS frene a RPCS3 al creerlo inactivo. Usa builds posteriores.
- El cuello de botella del juego es el **SPU**, no la GPU.
- Estos avisos del log son **normales** e inofensivos: MSAA no soportado, async texture decoding incompatible con Apple, `CELL_PRX_ERROR_UNKNOWN_MODULE`,
  `cellSaveDataAutoLoad2`, `opendir …/DLC`, `No jump table targets`, `Unmatched spu_re(b) found in FMA`.

## 4. Rutas en macOS (comprobadas en el código fuente de RPCS3)

| Qué | Ruta |
|---|---|
| Config global | `~/Library/Application Support/rpcs3/config/config.yml` |
| Config por juego | `~/Library/Application Support/rpcs3/config/custom_configs/config_<SERIAL>.yml` |
| Log | `~/Library/Caches/rpcs3/RPCS3.log` (el de la sesión anterior: `RPCS3.log.gz`) |
| Builds macOS arm64 | https://github.com/RPCS3/rpcs3-binaries-mac-arm64/releases |

Ten varias builds a la vez renombrando la app (`RPCS3-18115.app`, `RPCS3-latest.app`, …). Comparten config y caché.
Si cambias entre builds muy distintas y algo raro pasa, prueba a borrar la caché del juego (`~/Library/Caches/rpcs3/cache/<SERIAL>`).

## 5. Comandos de recogida (úsalos en cada informe)

```bash
# Build exacta y equipo (primeras líneas del log)
head -n 8 ~/Library/Caches/rpcs3/RPCS3.log
sw_vers; sysctl -n machdep.cpu.brand_string hw.memsize hw.perflevel0.physicalcpu hw.perflevel1.physicalcpu

# Resumen de errores de la última sesión (F = fatal, E = error)
LOG=~/Library/Caches/rpcs3/RPCS3.log
grep -c '^·F' "$LOG"; grep -c '^·E' "$LOG"
grep -E 'too sleepy|did not react|^·F' "$LOG" | head -n 20
grep '^·E' "$LOG" | sed -E 's/^·E [0-9:.]+ //' | sort | uniq -c | sort -rn | head -n 15

# Térmica: dejar corriendo mientras se juega (Ctrl+C para parar)
pmset -g therm
sudo powermetrics --samplers cpu_power,thermal -i 5000 -n 12 | grep -E 'pressure|P-Cluster HW active frequency|E-Cluster HW active frequency'
```

Para medir FPS: el overlay de rendimiento de RPCS3 (activado en la config de abajo). En cada escena, anota el FPS medio aproximado
y el peor valor que se vea durante 60 s.

**Escenas de referencia** (usa siempre las mismas):
- **E1** — Capítulo 1, el tren colgando (arranque, carga ligera).
- **E2** — Capítulo 2, museo de Estambul (mucho post-procesado).
- **E3** — Nepal, combate en la calle con humo y explosiones (la más pesada).

## 6. Formato del INFORME

```
INFORME #n
Build: 0.0.xx-xxxxx-hash    Driver: MoltenVK|KosmicKrisp    macOS: xx.x
Config: perfil A + [cambio exacto, o "sin cambios"]
| Prueba | Escena | FPS medio | FPS mín | ¿Cuelgue? (min:seg) | Glitches | Térmica |
|--------|--------|-----------|---------|---------------------|----------|---------|
| ...    | ...    | ...       | ...     | no / sí 0:45        | ...      | Nominal/Fair/Serious |
Log: F=_ E=_ ; top errores: (máx. 5 líneas)
Observaciones: (máx. 5 líneas, hechos, no opiniones)
Dudas para el investigador: (si las hay)
```

## 7. Config base "perfil A"

Guárdala como `config_BCES00757.yml` (o `config_BCUS98123.yml` si es la versión US) en la carpeta de configs por juego.
Las claves están comprobadas contra `rpcs3/Emu/system_config.h`. Si la build vieja 18115 no reconoce alguna clave, la ignora: está bien.

```yaml
Core:
  PPU Decoder: Recompiler (LLVM)
  SPU Decoder: Recompiler (LLVM)
  SPU Block Size: Mega
  SPU loop detection: true
  Preferred SPU Threads: 0
  Max SPURS Threads: 6
  SPU XFloat Accuracy: Approximate
  Accurate RSX reservation access: false
  Thread Scheduler Mode: Operating System
Video:
  Renderer: Vulkan
  Resolution Scale: 100
  MSAA: Disabled
  Shader Mode: Async Recompiler with Shader Interpreter  # otros modos: pantalla negra en este Mac
  Write Color Buffers: false
  Strict Rendering Mode: false
  Relaxed ZCULL Sync: false
  Multithreaded RSX: false
  Anisotropic Filter Override: 0
  Frame limit: Auto
  Vulkan:
    Asynchronous Texture Streaming: false
  Performance Overlay:
    Enabled: true
    Detail level: High
```

Si al abrir la config del juego desde la interfaz de RPCS3 algún valor sale distinto (nombre de opción cambiado), dilo en el informe.

---

## ORDEN #1 — Inventario y primera prueba de cuelgue

1. **Preparación:** Mac enchufado, Modo de bajo consumo OFF, sin navegador abierto, juego en pantalla completa.
   Firmware 4.92 instalado. Anota la versión del juego (en RPCS3, columna "Versión") y su serial.
2. **Inventario:** ejecuta los comandos de "build y equipo" de la sección 5. Si RPCS3 ya está instalado, anota qué build es.
3. Descarga la **última build** de `rpcs3-binaries-mac-arm64` y guárdala como `RPCS3-latest.app`.
4. Crea la config del perfil A (sección 7). En RPCS3: clic derecho en el juego → *Build PPU cache* y espera a que termine.
5. **Prueba P1-1:** arranca el juego con la build latest. Deja que termine la compilación de shaders y SPU.
   Juega E1 durante 15 min (si no has llegado a E2/E3, no pasa nada).
   - Si se congela: apunta en qué minuto, fuerza la salida de RPCS3, y recoge el resumen del log (sección 5).
     **No borres el log** (se sobrescribe al abrir RPCS3 otra vez: cópialo antes a `~/Desktop/P1-1.log`).
   - Si no se congela: apunta FPS medio/mínimo en E1 y la térmica al final.
6. Devuelve `INFORME #1`. No hagas más pruebas hasta recibir la ORDEN #2.
