# Cómo hacer que vaya mejor — ajustes, variables y recompilación

Investigación sobre el código de RPCS3 master `105c498` (28-09-2026), el mismo commit que la build 0.0.42-20073 del Mac.
Nada de esto está probado en el Mac todavía. Lo marcado **[código]** está comprobado leyendo el fuente;
lo marcado **[web]** viene de fuentes externas.

## 1. Correcciones a lo que dábamos por hecho

| Creíamos | Realidad | Fuente |
|---|---|---|
| "Too sleepy" es la causa del cuelgue | Solo se escribe **al cerrar** la emulación, esperando hilos que no terminan. Es un síntoma posterior al cuelgue, no la causa. | [código] `Utilities/Thread.cpp:3280-3296` (`join` con `Emu.IsStopped()`) |
| Pantalla completa activa Game Mode y ayuda | RPCS3 **desactivó Game Mode a propósito** porque empeora la emulación; el Info.plist ya no declara la app como juego. No forzarlo. | [web] [@rpcs3 en X](https://twitter.com/rpcs3/status/2045487510533308567) · [código] `rpcs3/rpcs3.plist.in` |
| `Thread Scheduler Mode` puede ayudar | En Apple Silicon el reparto de núcleos es "genérico" → máscara de todos los núcleos. macOS ignora la afinidad en ARM. **No hace nada: descartado.** | [código] `Thread.cpp:3563` `detect_cpu_layout`, `:3897` |
| `Preferred SPU Threads` fija los SPU en núcleos P | No fija núcleos. Limita cuántos SPU ejecutan **a la vez el mismo bloque de código caliente** (el resto espera `SPU delay penalty` ms). Sigue siendo un ajuste útil para probar. | [código] `rpcs3/Emu/Cell/SPUThread.cpp:612` |
| La build oficial usa el MoltenVK del sistema | La app lleva dentro **MoltenVK 1.4.2 "privateapi"** (1.4.0 y 1.4.1 tenían 2 regresiones). Si se compila a mano hay que conservarlo. | [código] `.ci/deploy-mac.sh:8` · [web] @rpcs3 |
| La build oficial usa las instrucciones del M4 | El **JIT** (el código PS3 traducido) sí: LLVM 22 detecta `apple-m4`. El **propio emulador** (RSX, audio, VDEC, etc.) se compila con `-march=armv8.4-a` genérico. | [código] `Utilities/JITLLVM.cpp:562`, `buildfiles/cmake/ConfigureCompiler.cmake:32-40` |

## 2. El cierre en el menú (issue #17961): lo que dice el código

Cadena que vio el Mac: `RsxKick timeouts → ~610× CELL_VDEC_ERROR_BUSY → ASSERTION game/foreground.cpp:960`.

- `RsxKick` y `foreground.cpp` **no existen en RPCS3**: son mensajes y asserts del propio juego (código de Naughty Dog, salen por TTY).
- `CELL_VDEC_ERROR_BUSY` sale cuando ya hay 4 unidades de vídeo pendientes de decodificar (`cellVdec.cpp:1684`).
  El hilo decodificador solo se atasca si **nadie consume los frames ya decodificados**. En ese caso escribe cada 5 s:
  `Video au decode has been waiting for a consumer for 5 seconds` (`cellVdec.cpp:613`).
- Hipótesis: **el RSX (GPU emulada) se para** → el juego no avanza su bucle de render → no recoge frames de vídeo
  → VDEC se llena → el juego salta su propio assert. El origen probable es el RSX/MoltenVK, no el vídeo.
- Cómo confirmarlo: si en el log aparece `waiting for a consumer` **antes** de los BUSY, se confirma. Si no aparece,
  el decodificador es lento (FFmpeg compitiendo por CPU) y la solución sería otra.

Además, en este Mac `Relaxed ZCULL Sync` rompía los shader workers con `Invalid input structure. Some input bindings were not declared!`
(`VKProgramPipeline.cpp:688`). Es un error de construcción del pipeline Vulkan, no de rendimiento. Merece un issue propio si se reproduce en 0.0.42.

## 3. Palancas sin recompilar (config y variables de entorno)

Por orden de potencial. **Una cada vez**, siempre frente al perfil A.

| # | Cambio | Por qué | Riesgo |
|---|---|---|---|
| C1 | `Disable ZCull Occlusion Queries: true` | En MoltenVK, RPCS3 **no usa conditional rendering en GPU** y sincroniza con la CPU para cada consulta de oclusión (`VKGSRender.cpp:674-677`). Quitarlas elimina esas esperas. Además es una alternativa a Relaxed ZCULL, que aquí rompe. | Objetos o efectos que parpadean o desaparecen (lens flare, halos) |
| C2 | `Accurate ZCULL stats: false` | Equivale a "ZCULL Accuracy: Approximate" en la interfaz: estadísticas ZCULL más baratas **sin** activar Relaxed. | Bajo |
| C3 | `Shader Compiler Threads: 2` | Por defecto usa "auto" y compite con PPU/SPU/RSX por solo 4 núcleos P. | Más tirones la primera vez que aparece un shader |
| C4 | `Max LLVM Compile Threads: 4` | Igual, para la compilación de SPU/PPU en marcha | Carga inicial más lenta |
| C5 | `SPU Block Size: Giga` | Bloques SPU más grandes = menos saltos entre bloques | Cuelgues en algunos juegos |
| C6 | `Preferred SPU Threads: 2..4` | Ver tabla 1 | Si es bajo, SPU esperando = menos FPS |
| C7 | `Max SPURS Threads: 5 / 4` | Menos hilos SPURS para 4 núcleos P | Lógica rota o cuelgues |
| C8 | Variable `MVK_CONFIG_SYNCHRONOUS_QUEUE_SUBMITS=0` | MoltenVK por defecto envía cada lote a la GPU y **espera**. Con 0 lo hace en segundo plano. RPCS3 no fija este valor, así que la variable de entorno sí se aplica (`vkutils/instance.cpp:135-136` solo fija `RESUME_LOST_DEVICE` y `FAST_MATH`). | Experimental: posibles fallos de sincronización o gráficos |
| C9 | `SPU XFloat Accuracy: Relaxed` | +~20 % reportado | Capítulos que no cargan |

Cómo lanzar con variables de entorno (el Claude del Mac puede hacerlo):
```bash
MVK_CONFIG_SYNCHRONOUS_QUEUE_SUBMITS=0 /Applications/RPCS3.app/Contents/MacOS/rpcs3
```

### Diagnóstico: ¿el límite es la GPU o la CPU?
- `MTL_HUD_ENABLED=1 /Applications/RPCS3.app/Contents/MacOS/rpcs3` → muestra en pantalla el HUD de Metal de Apple con el tiempo de GPU por frame.
  Si el GPU time es muy inferior a 33 ms y aun así no llega a 30 FPS, **el límite es la CPU (SPU/PPU/RSX)** y subir la resolución sale casi gratis (apoya la fase 3 del plan).
- `Core: Enable Performance Report: true` → escribe en el log las esperas que superan 0.5 ms.
- `Core: SPU Profiler: true` (solo para diagnosticar, cuesta rendimiento) → qué código SPU consume más.

## 4. Recompilar RPCS3 en el Mac

Script: `build-m4.sh`. Reutiliza los scripts oficiales de CI (misma LLVM 22, Qt 6.11.2 y MoltenVK 1.4.2 privateapi) y cambia dos cosas:

1. **`USE_NATIVE_INSTRUCTIONS=ON`** → el propio emulador se compila para el M4 en vez de para armv8.4-a.
   - **Expectativa honesta: poca ganancia (~0-5 %).** El código PS3, que es lo pesado, ya se traduce para el M4.
     Donde puede notarse es en el hilo RSX (decodificación de FIFO, subida de vértices y texturas) y en el audio.
   - El script comprueba que `-march=native` se haya aplicado de verdad.
2. **Parche opcional `0001-macos-qos-workers.patch`** (`PATCH=1`). Es la idea con más fundamento para un chip de solo 4 núcleos P:
   - Hoy **todos** los hilos de RPCS3 se crean con la prioridad (QoS) máxima, `USER_INTERACTIVE` (`Thread.cpp:2772`).
     Eso incluye los compiladores de shaders (`RSX.W*`) y los de LLVM (`SPUW.*`, `PPUW.*`, `SPU Worker`, `SPRX Worker`).
     Cuando compilan, compiten de tú a tú con PPU, SPU y RSX por los 4 núcleos P.
   - El parche les baja la QoS a `UTILITY`: macOS los manda preferentemente a los 6 núcleos E y deja los P para la emulación.
     Se esperan menos tirones al aparecer shaders o código nuevo. En escenas ya cacheadas no debería cambiar nada.
   - Se puede apagar sin recompilar: `RPCS3_MAC_BG_QOS=0` (sirve para comparar A/B con el mismo binario).
   - **Sin compilar todavía** (el código es solo de macOS y aquí no hay Mac). Si no compila, el Claude del Mac
     debe pegar el error y lo corrijo.

Para bisecar la regresión **no hace falta compilar**: cada build oficial está publicada en
https://github.com/RPCS3/rpcs3-binaries-mac-arm64/releases.

### Ideas descartadas o aparcadas
- **KosmicKrisp** (Vulkan sobre Metal de Mesa/LunarG): hoy es más lento que MoltenVK según [LunarG](https://www.lunarg.com/the-state-of-vulkan-on-apple-jan-2026/) y [Phoronix](https://www.phoronix.com/news/KosmicKrisp-Parity).
  Además, RPCS3 en macOS **siempre** trata el driver como MoltenVK (`vkutils/device.cpp:288-291`), así que con KosmicKrisp
  aplicaría workarounds de MoltenVK. Solo sirve como prueba de cuelgue (¿el bug es de MoltenVK?), no para ganar FPS.
- **PGO/LTO**: RPCS3 prohíbe LTO (`CMakeLists.txt:116-118`). PGO no está soportado por los scripts: mucho trabajo para poca ganancia.
- **Afinidad a núcleos P**: macOS en ARM no la permite. La QoS (parche de arriba) es la única palanca real.

## 5. Orden recomendado (se integra en las próximas ÓRDENES)
1. Terminar P1-1 (¿sigue el cierre en 0.0.42?) + grep de `waiting for a consumer`.
2. Si hay cierre: C1 (Disable ZCull Queries) como primera prueba, porque ataca la sincronización RSX con MoltenVK. Después C8.
3. Si no hay cierre: medir el perfil A, luego C1 → C2 → C3/C4 → C5 → C6/C7, con el HUD de Metal para saber si el límite es la CPU.
4. En paralelo, cuando haya tiempo: `build-m4.sh` con `PATCH=1` y comparación A/B con `RPCS3_MAC_BG_QOS=0`.

---

## 6. Análisis a fondo del M4 (ronda 2, tras los INFORMES #8–#10)

Lo que ya está resuelto con parches propios:
- **0002 — ZCull falso-visible:** con las consultas desactivadas, RPCS3 respondía «0 píxeles visibles» (`RSXThread.cpp`, `get_zcull_stats`) y el juego no dibujaba el 3D. Ahora responde N píxeles: la geometría vuelve y se eliminan todas las esperas a la GPU por oclusión. **Verificado en el Mac.**
- **0003 — semáforos back-end relajados:** sin *host GPU labels* (MoltenVK no tiene `VK_EXT_external_memory_host`), cada escritura de etiqueta hacía un `sync()` completo de la GPU. Hizo desaparecer el mensaje `[SPU-PM] too many flags`, pero **no** arregló el cuelgue.

### Hallazgos del código específicos de ARM64/macOS

| # | Hallazgo | Dónde | Efecto en el M4 | Qué hacer |
|---|---|---|---|---|
| A | **Reservas SPU precisas** (valor por defecto) → cada PUTLLC toma `vm::writer_lock` (bloqueo pesado). Con la opción desactivada, SPURS usa una ruta rápida: copia directa más `compare_exchange` de 128 bits, que el M4 hace con CASP (LSE) en una sola instrucción. | `SPUThread.cpp:3460-3500` | Es exactamente la contención que se ve: 27 000 GETLLAR lentos y 5 SPU atascados en el mismo sitio | **K4** de la ORDEN #11 (`Accurate SPU Reservations: false`). Es el ajuste con más fundamento. |
| B | GETLLAR espera con `busy_wait(300)` y, pasadas 24 vueltas, `std::this_thread::yield()`. En macOS, `yield` es `swtch_pri` y cede el núcleo entero a otro hilo. | `SPUThread.cpp:4571-4580` | Con solo 4 núcleos P, los hilos que ceden el núcleo pueden acabar en los núcleos E | K2/K3 (spin del GETLLAR, espera activa) |
| C | El reloj ARM del M4 va a 24 MHz. `busy_wait` escala con `freq/30 MHz`, que aquí da 0 y se fuerza a 1. Resultado: esperas 1,25 veces más largas que en x86. | `util/asm.hpp:195-216` | Pequeño | No compensa parchearlo |
| D | **Páginas de 16 KB en macOS/ARM.** La caché de texturas y la memoria del PS3 protegen páginas de 4 KB, pero `mprotect` las redondea a 16 KB. | `util/vm_native.cpp:325`, `Memory/vm.cpp:747` | Escribir en datos vecinos de una textura provoca fallos de página falsos: una excepción Mach cara en macOS más la invalidación de la caché. Probablemente cuesta FPS en general. | No se arregla con configuración. Se reduce con `Write Color Buffers: false` (por defecto) y resolución al 100 %. Un parche real sería grande. |
| E | El JIT del PPU y del SPU ya se genera para `apple-m4` (LLVM 22). | `JITLLVM.cpp:562` | — | Nada que ganar ahí |
| F | La **QoS** de los hilos es la única forma de repartir núcleos P/E; macOS ignora la afinidad. | `Thread.cpp:2772` | Hecho en el parche 0001 | Hay que medirlo A/B cuando el juego sea jugable |
| G | La build oficial compila el propio emulador para armv8.4-a; la nuestra, para el M4 nativo. | `ConfigureCompiler.cmake` | Pequeño (0-5 %) | Hecho (`build-m4.sh`) |

### Perfil objetivo "UC2-M4" (cuando deje de colgarse)
Con el build custom (0001+0002+0003):
- Video: `Disable ZCull Occlusion Queries: true`, `ZCull Fake ZPass Value: 4096`, `Relaxed Back-End Semaphores: true`, `Resolution Scale: 100`, `MSAA: Disabled`, `Shader Mode: Async Recompiler with Shader Interpreter`, `Write Color Buffers: false`.
- Core: `Accurate SPU Reservations: false` (si K4 funciona), `RSX FIFO Fetch Accuracy: Fast`, `SPU Block Size: Mega` y `SPU loop detection: true` (probar después), `Preferred SPU Threads: 2-4` (probar).
- Después: v01.09 más el parche de comunidad «Enable GPU Lighting», que pasa la iluminación del SPU a la GPU. Es la mayor rebaja de carga de SPU disponible, y el M4 tiene GPU de sobra.

### Si K1–K7 no arreglan el cuelgue
Hay que ver en qué función exacta del host están los 5 SPU. Para eso la ORDEN #11 pide un `lldb bt` o una build RelWithDebInfo. Con ese dato el parche siguiente irá dirigido a esa función, sin más tanteo.

---

## 7. U2M4 — la build propia (ronda 3)

**U2M4** = RPCS3 `105c4988` + parches 0001–0006, compilado para el M4 con `build-m4.sh` → `/Applications/U2M4.app`.

| Parche | Qué hace | Estado |
|---|---|---|
| 0001 | Baja la QoS de los hilos que compilan (shaders/LLVM) para dejar los 4 núcleos P a la emulación | compila; sin medir A/B |
| 0002 | `ZCull Fake ZPass Value`: con consultas ZCull desactivadas, informa de N píxeles visibles en vez de 0 | recupera el 3D, pero rompe los datos de partículas (assert `particle-u2-cull.cpp:304`) |
| 0003 | `Relaxed Back-End Semaphores`: etiquetas RSX sin sincronizar toda la GPU (MoltenVK no tiene host labels) | quita el mensaje `SPU-PM too many flags` |
| 0004 | `SPU Heuristics Host Thread Count`: las heurísticas de espera SPU asumían ≥12 hilos (M4 base = 10) | sin efecto sobre el cuelgue |
| 0005 | **Bug de RPCS3 corregido:** el intérprete de shaders no declaraba el binding del renderizado condicional emulado ("Invalid input structure") | **verificado** (0 errores) |
| 0006 | `Emulated Conditional Rendering`: renderizado condicional en la GPU sin retrasar los informes ZCULL | pendiente (ORDEN #16) |

### Candidatos de optimización para cuando el juego sea estable (en orden)
1. **`PPU Vector NaN Handling: false`** (Core). Con el valor por defecto (`true`), cada operación vectorial de coma flotante del PPU pasa por `VecHandleNan` (`PPUTranslator.cpp:487`), que añade instrucciones extra en ARM. El hilo principal del juego se pasa el tiempo ejecutando código del PPU, así que es una ganancia directa en la CPU. Riesgo: fallos visuales o de física si el juego depende de los NaN. Obliga a recompilar la caché del PPU (el primer arranque será más largo).
2. **Parche de la comunidad "Enable GPU Lighting"** (v01.09): pasa la iluminación del SPU a la GPU. Es la mayor rebaja de carga del SPU disponible.
3. `SPU Block Size: Mega`, `SPU loop detection: true`, `Preferred SPU Threads: 2-4`: probarlos uno a uno.
4. `SPU XFloat Accuracy: Relaxed`, solo si algo del resto falla por poco. Riesgo: capítulos que no cargan.
5. **`Frame limit: 30`**: el juego ya va a 30. Limitarlo evita gastar energía en picos (las cinemáticas a 60) y retrasa el throttling térmico del Air sin ventilador.
6. **A/B de QoS** (0001): lanzar con `RPCS3_MAC_BG_QOS=0` y sin él.
7. **Parche grande (último recurso):** agrupar la protección de páginas de la caché de texturas en bloques de 16 KB para reducir fallos de página falsos en macOS/ARM (`util/vm_native.cpp:325`). Solo si el perfil de CPU muestra mucho tiempo en el manejador de fallos (`sample` → `signal_handler`/`handle_access_violation`).

### Cómo medir en el Mac
- FPS: el overlay de RPCS3, media y mínimo en 60 s en el tren y en un combate.
- Dónde se va la CPU: `sample <pid> 10`. Si domina el JIT del PPU, aplicar el candidato 1. Si dominan los SPU, el 2 y el 3. Si domina `handle_access_violation`, el 7. Si dominan las esperas del RSX o de MoltenVK, más trabajo en la GPU.

---

## 8. Ideas específicas de ARM / Apple Silicon (ronda 4)

### Corrección importante
En la sección 6 dije que MoltenVK no tiene `VK_EXT_external_memory_host`. **Es falso**: lo tiene desde MoltenVK 1.2.3 (2023), y nosotros usamos la 1.4.2. Eso significa que **"Allow Host GPU Labels" podría funcionar de forma nativa en el Mac**. RPCS3 solo lo desactiva si el driver no tiene esa extensión (`VKGSRender.cpp:767-783`). Si funciona:
- las etiquetas del RSX las escribe la propia GPU, sin que el emulador se pare a esperarla;
- sustituye con ventaja al parche 0003 (semáforos relajados), que es un apaño menos seguro.
→ **Primera prueba de la ORDEN #21.**

### SVE / SME: no nos sirven (y por qué)
- El M4 **no tiene SVE normal**. Tiene **SME2** (ARMv9), pero solo funciona en "modo streaming": una unidad aparte, orientada a matrices, con un coste alto de entrar y salir del modo. Las instrucciones SPU son operaciones sueltas de 128 bits, así que ese coste se comería cualquier ganancia.
- La buena noticia: **NEON mide exactamente 128 bits, igual que los registros del SPU**, así que la traducción ya es uno a uno. `SHUFB`, la instrucción de mezcla estrella del SPU, se traduce a `TBL`/`TBX` de NEON (RPCS3 ya lo hace; `SPUCommonRecompiler.cpp` tiene la variante TBL2/TBX2).

### Lo que sí aprovecha el hardware de Apple
| Idea | Qué es | Beneficio para UC2 | Coste |
|---|---|---|---|
| **Host GPU Labels vía memoria unificada** | La memoria del M4 es compartida entre CPU y GPU. Con `external_memory_host`, la GPU escribe las etiquetas directamente en la memoria del PS3 emulado | Quita las esperas de sincronización por etiqueta (la causa de fondo del `RsxKick`) | Configuración (`Allow Host GPU Labels: true`) |
| **PGO solo con Uncharted 2** | Compilar U2M4 con `-fprofile-generate`, jugar 10 min y recompilar con `-fprofile-use`. El compilador optimiza para el camino real del juego | Típicamente +5-15 % en el código C++ del emulador (FIFO del RSX, caché de texturas, DMA) | 2 compilaciones + 1 partida. Ideal porque solo nos importa un juego |
| **`-mcpu=apple-m4`** en vez de `-march=native` | Además de las instrucciones, ajusta el código a la microarquitectura del M4 | Pequeño | Una línea en `build-m4.sh` |
| **FPCR.FZ (flush-to-zero)** | El SPU real no tiene denormales. Con FZ activo en los hilos SPU, ARM los trata igual, gratis | Exactitud; evita la ruta lenta de denormales | Un parche pequeño en el arranque del hilo SPU. Hay que comprobar qué hace ya RPCS3 |
| **Atómicos de 128 bits (LSE2 / CASP)** | El M4 hace atómicamente cargas de 16 bytes y compare-and-swap de 128 bits | Ya usado por la ruta rápida de reservas SPU (`Accurate SPU Reservations: false`) | Probar K4 cuando el juego sea estable |
| **QoS por hilo** (hecho, 0001) | La única forma de separar núcleos P y E en macOS | Deja los 4 núcleos P a SPU, PPU y RSX | — |

### Diagnóstico ingenioso: el modo TSO de Apple
Los chips Apple tienen un **modo TSO por hardware**: el mismo orden de memoria que x86, el que usa Rosetta. En macOS no hay API pública para activarlo, pero **cualquier binario x86 ejecutado con Rosetta corre en TSO**.
→ Prueba barata para confirmar la hipótesis del parche 0008: la **build oficial de RPCS3 para Intel (x86_64) con Rosetta**. Irá lenta, pero si no se cuelga al empezar a jugar, confirma que el cuelgue era de orden de memoria ARM.

### Ideas grandes (si falta rendimiento al final)
- **Renderizador Metal nativo:** fuera de alcance. Es un backend entero nuevo.
- **Tamaño de página de 16 KB:** agrupar las protecciones de página de la caché de texturas (sección 6, D).
- **Quitar comprobaciones que UC2 no necesita:** con el perfil U2M4 fijo, eliminar las rutas de precisión que el juego no usa, guiándonos por `sample`.
