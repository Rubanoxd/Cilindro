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
