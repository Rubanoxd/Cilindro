# Uncharted 2 en RPCS3 a ≥30 FPS en un MacBook Air M4 base — investigación y plan

Fecha: 28-09-2026 · Rol: investigador/planificador. Esto no se ha probado en hardware real:
son hipótesis ordenadas por coste/beneficio con criterios de medición para quien ejecute.

## 1. Resumen

- **Es alcanzable pero ajustado.** En el issue [RPCS3#17640](https://github.com/RPCS3/rpcs3/issues/17640)
  un M4 **Pro** (8P+4E) llegaba a los **30 FPS** en Uncharted 2 (BCES00757)… y se congelaba a los pocos segundos.
  El M4 base del Air tiene 4P+6E, GPU de 10 núcleos y **no tiene ventilador**, así que parte con menos margen
  y con throttling térmico a los 10-15 min.
- **El problema nº 1 no es el FPS, es el cuelgue** (regresión en arm64/macOS, abierta y asignada a kd-11,
  sin fix publicado que la cite). Sin resolver eso, ajustar settings no sirve.
- **El cuello de botella del juego es el SPU** (Naughty Dog carga en SPURS el post-procesado, animación, etc.).
  Con solo 4 núcleos P, cada hilo de SPU que cae en un núcleo E cuesta FPS. Las palancas buenas son las que
  reducen trabajo SPU o lo mantienen en núcleos P.

## 2. Qué dice el issue #17640 (y fallos relacionados)

| Dato | Detalle |
|---|---|
| Equipo | MacBook Pro M4 Pro, 24 GB, macOS Tahoe 26.0.1, FW 4.92 |
| Build | 0.0.38-18273-3f797b2d (master) |
| Síntoma | Compila shaders/PPU, corre a 30 FPS (U2) / 60 (GoW3), y se congela en segundos. Parar exige forzar salida. |
| Log clave | `Thread [cellAudio Thread] is too sleepy… 21340us`, `SPU[…] BigCellSpursKernel0 is too sleepy`, "game did not react to the exit request" |
| Avisos GPU (no son la causa) | Sin soporte MSAA completo, sin *async texture decoding* en Apple, sin doubles/depth bounds/wide lines/logic ops |
| Ruido (inofensivo) | `CELL_PRX_ERROR_UNKNOWN_MODULE`, `cellSaveDataAutoLoad2`, `opendir …/DLC`, "No jump table targets", "Unmatched spu_re(b) in FMA" |
| Estado | Abierto, etiquetas Bug + Regression, asignado a kd-11 |

Lectura: "too sleepy" en `cellAudio` y en el kernel SPURS a la vez apunta a **hilos que no despiertan / no se
planifican** (deadlock de sincronización o hilos degradados por el SO), no a falta de potencia.

Contexto relacionado:
- [#17597](https://github.com/RPCS3/rpcs3/issues/17597): las builds oficiales compilaban PPU para M3 en vez de M4 (LLVM 19 vs 21). Arreglado en [#17630](https://github.com/RPCS3/rpcs3/pull/17630).
- [#17905](https://github.com/RPCS3/rpcs3/pull/17905) (merge 21-12-2025): fuerza prioridad pthread máxima en macOS porque el SO "estrangulaba" RPCS3 al creerlo inactivo (FPS que caían hasta mover el ratón). **Muy relevante**: mismo síntoma de fondo que "too sleepy". Hay que usar una build posterior.
- [#18175](https://github.com/RPCS3/rpcs3/issues/18175) / [#18176](https://github.com/RPCS3/rpcs3/pull/18176): crash al arrancar en arm64 ("Neon support not present"), feb-2026, arreglado.
- [#18423](https://github.com/RPCS3/rpcs3/pull/18423) → revertido en [#18656](https://github.com/RPCS3/rpcs3/pull/18656) (29-04-2026) por crashes en macOS arm64 (carrera en memory_decommit).
- [#17735](https://github.com/RPCS3/rpcs3/pull/17735) (dic-2025): RPCS3 macOS carga ICDs de Vulkan vía loader → se puede probar **KosmicKrisp** en lugar de MoltenVK.
- Informes de la comunidad: build **0.0.37-18115** funcionaba en Mac mini M4 base (U2 jugable) antes de la regresión.

En el código actual (master 105c498) Apple por MoltenVK se clasifica como `chip_class::APPLE_MVK` sin distinguir M1-M4 (`rpcs3/Emu/RSX/VK/vkutils/chip_class.cpp:108`).

## 3. Plan por fases

### Fase 0 — Preparación (una vez)
1. Enchufado, **Modo bajo consumo OFF**, pantalla completa (activa Game Mode de macOS), cerrar navegador.
2. Mac en superficie dura; para sesiones largas, base con ventilador (el Air sin ventilador baja relojes P tras ~10 min).
3. Firmware 4.92, juego actualizado a la última versión del parche (los parches de la comunidad dependen de la versión).
4. Precompilar: clic derecho en el juego → *Build PPU cache*. Primera partida: dejar que termine la compilación de SPU/shaders antes de medir.

### Fase 1 — Resolver el cuelgue (bloqueante)
1. Probar **la última build master** (incluye #17905 y el revert #18656). Si no se cuelga en 15 min → Fase 2.
2. Si se cuelga, probar en orden (uno a uno, anotando resultado):
   - `SPU loop detection: true` / `false`
   - `Max SPURS Threads: 4`
   - `Thread Scheduler Mode: RPCS3 Scheduler` (alt)
   - `SPU Block Size: Safe`
   - `Sleep Timers Accuracy` en el valor más preciso
   - Driver Vulkan: KosmicKrisp en lugar de MoltenVK
3. Si nada funciona: usar **0.0.37-18115** (conocida buena) y, en paralelo, **bisecar** entre 18115 y 18273
   (≈160 builds → ~8 pruebas) para localizar el commit. Aportar el commit exacto a #17640 es la contribución de más valor.
4. Al comentar en el issue: log completo (`RPCS3.log.gz`), build, config, y el resultado de cada prueba.

### Fase 2 — Llegar a 30 FPS (config base: `config_BCES00757.yml`)
Medir **cada cambio por separado** en las escenas de referencia (sección 4). Orden sugerido:
1. Base: Approximate XFloat, Relaxed ZCULL, sin MSAA, 720p (100%), Async shaders, async textures OFF.
2. `SPU Block Size: Mega` → `Giga`.
3. `Preferred SPU Threads`: 0 → 2 → 3 → 4. Idea: agrupar la carga SPU pesada en los 4 núcleos P.
4. `Max SPURS Threads`: 6 → 5 → 4. En 4P+6E puede **ganar** FPS (menos migraciones a núcleos E); si hay cuelgues o lógica rota, volver a 6.
5. `SPU XFloat Accuracy: Relaxed` — ~+20 % reportado en este juego, pero **algunos capítulos no cargan**. Usar solo por tramos y volver a Approximate si un nivel no carga.
6. `Multithreaded RSX` ON solo como último experimento.

### Fase 3 — Idea de mayor potencial: pasar trabajo del SPU a la GPU
El parche de juego **"Disable SPU Post-processing"** quita el post-procesado que corre en SPU (muy caro).
Aviso conocido: a resolución 100 % aparecen **cuadros blancos**; solo es correcto con escala superior.
Experimento: `Resolution Scale: 150` + ese parche. La GPU del M4 va sobrada a ~1080p en un juego de PS3,
y a cambio se libera la CPU, que es el cuello. Comparar contra el perfil A.
(No aplicar el parche de 60 FPS: el objetivo es 30 estables.)

### Fase 4 — Sostenibilidad térmica
Correr 20-30 min en la escena más pesada y registrar FPS mínimos. Si cae por temperatura:
limitador a 30 (`Frame limit: 30`) para no gastar en picos inútiles, y base con ventilador.

## 4. Cómo medir
- Overlay de rendimiento (en la config): FPS, frametime, uso CPU/GPU, carga por hilo PPU/SPU.
- Escenas: (a) menú/intro del tren (cap. 1), (b) museo de Estambul (cap. 2, mucho post-procesado), (c) combate en Nepal con humo/explosiones.
- Para cada prueba anotar: build, cambio, FPS medio / mínimo 1 %, ¿cuelgue?, ¿glitch?
- Criterio de éxito: **≥30 FPS medios y ≥27 mínimos en (b) y (c) durante 20 min, sin cuelgues.**

## 5. Riesgos y expectativas
- Si el cuelgue no se resuelve en builds recientes, depender de 0.0.37-18115 limita las mejoras futuras.
- Mejor escenario esperado en Air M4: 30 FPS con caídas a mediados de los 20 en combates intensos; la Fase 3 es la que puede cerrar esa diferencia.
- Glitches gráficos menores en MoltenVK son probables (sin depth bounds / logic ops).

## Fuentes
- https://github.com/RPCS3/rpcs3/issues/17640 · /17597 · /18175
- https://github.com/RPCS3/rpcs3/pull/17630 · /17905 · /17735 · /18656
- Hilo de U2 en foros RPCS3 (XFloat/ZCULL relaxed, parche SPU post-processing): https://forums.rpcs3.net/archive/index.php/thread-199949.html
- Código RPCS3 master 105c498 (`rpcs3/Emu/system_config.h`, `rpcs3/Emu/RSX/VK/vkutils/`)
