# Wiintosh GPU (Wii U) — investigación completa en un solo archivo

> Documento único para Claude Code. Cópialo a tu fork de `Wiintosh/osx-drivers`
> (p. ej. `docs/WIIU_GPU.md`) y añade a `CLAUDE.md`:
> `Lee docs/WIIU_GPU.md antes de tocar WiiGraphics o de pedir acciones físicas.`
>
> - **Parte 1** — Plan por fases (qué hacer).
> - **Parte 2** — Referencia técnica (registros, PM4, código).
> - **Parte 3** — Depuración y avisos al humano.
> - **Parte 4** — Pixel 6a con Vanilla en lugar de GamePad (cambia la fase 1: sin GamePad físico).
> - **Parte 5** — Ejecutarlo todo desde Claude Code en el MacBook (SSH vía Redmi).
> - **Partes 6–11** — Datos reales de la consola y su análisis.
> - **Parte 12** — Proyectos similares (⭐ NetBSD Wii U) y qué aprovechar.
> - **Parte 13** — Fe de erratas.
> - **Parte 14** — Resultados medidos en la Wii U (anillo, CP_DMA, plugin GA funcionando).
> - **Parte 15** — Por qué el WindowServer no usa el plugin (faltan superficies CGS).
> - **Parte 16** — Resultados tras la 15 (no hay write-through en xnu PPC; `sample` roto).
> - **Parte 17** — Parche del bit W, interfaz de superficies, muestreador.
> - **Parte 18** — El WindowServer pide superficies (experimento IOAccelTypes ✅).
> - **Parte 19** — Tabla de métodos exacta y diseño con backing en RAM.
> - **Parte 20** — ✅ Superficies CGS funcionando (el WindowServer compone por la GPU).
> - **Parte 21** — Qué más delegar, plan de SMP, cómo hacerlo permanente.
> - **Parte 22** — ✅ Instalación permanente verificada, Read, GetBeamPosition.
> - **Parte 23** — MEM1 para la GPU, vblank, PR antes que SMP.
> - **Parte 24** — ✅ MEM1 verificado; PR preparado.
> - **Parte 25** — Revisión del PR y pasos para publicar.
> - **Parte 25b** — ✅ PR publicado: Wiintosh/osx-drivers#1.
> - **Parte 26** — SMP: investigación y texto del issue (26.6).
> - **Parte 27** — SMP paso 0: registros del núcleo 0 y MEM0.
> - **Parte 28** — ⚠️ El controlador de CPU bloquea hasta arrancar todos los núcleos; prueba de IPI en caliente.
> - **Parte 29** — Issue #24 y prueba A (el bit 20 no se latchea).
> - **Parte 30** — MEM0 = vector alto de reset, ⚠️ errata `stwcx.` de Espresso (la lectura de los bits de ICI de 30.1 era errónea: ver 31).
> - **Parte 31** — A': NetBSD tenía razón (IPI núcleo n = bit 20−n); rutina de Nintendo; recuento de `stwcx.`.
> - **Parte 32** — Entrada de IPIs, valores de Nintendo, trampolín, diseño del parche de `stwcx.`.
> - **Parte 33** — `__HIB,__data` es la pila de interrupciones; hueco en la commpage.
> - **Parte 34** — La commpage MP se elige al arrancar (`ml_get_max_cpus`) → SMP y parche en `WiiPE`/`WiiCPU` con `-wiismp`.
> - **Parte 35** — ✅ Parche de `stwcx.` en caliente (142 sitios) probado en UP.
> - **Parte 36** — Escaneo con símbolos en el arranque, qué entra en `-wiismp cpus=1`, kexts.
> - **Parte 37** — ✅ Parche de `stwcx.` en el arranque (142); no hay boot‑args (claves `WiiSMP` en el plist); recuento en espacio de usuario.
> - **Parte 38** — PIR, espacio de usuario, orden para despertar el núcleo 1 y plan (38.4).
> - **Parte 39** — `WAKE(1)` → excepción 0x1700 en el núcleo 0.
> - **Parte 40** — **0x1700 es la IPI de Espresso** (NetBSD `EXC_IPI`): redirigir el vector de XNU con 1 instrucción; `sync_cache64` para memoria baja; **orden actualizado (40.4)**.
> - **Partes 41–42** — el núcleo 1 entra en XNU pero se cuelga en `cpu_sync_timebase`: **handshake por memoria sin coherencia**; valores HID de Nintendo en el trampolín, sonda de coherencia barata, orden 42.6.
> - **Parte 43** — rutina de Nintendo 0x240–0x330 confirmada: HID por núcleo = los de 42.6; no hay init de L2 en ese tramo.
> - **Partes 43b–44** — la coherencia la activan **CAR/BCR**; el núcleo 1 corrompe o se atasca porque `init750nb` le enciende la L1 **sin invalidar** y `cacheInit` escribe esa basura a memoria. Solución: `pfHID0` y `pfl2cr` del núcleo 1 parcheados (44.3).
> - **Partes 45–46** — el núcleo 1 ya arranca; las interrupciones externas se congelan. Causa principal: **read-modify-write de las máscaras de Latte/PI sin spinlock** (carrera SMP → vector enmascarado para siempre). Ack del IPI en bucle como NetBSD.
> - **Partes 47–48** — vectores de Latte soft-disabled porque su workloop quedó asignado al **núcleo 1 en reposo (doze)** y el IPI SIGPwake no lo despierta; prueba: quitar el doze (48.3).
> - **Partes 49–50** — sin doze el workloop sigue atascado ⇒ el núcleo 1 **no está en su bucle ocioso**. No-determinación: **carrera de wakeup perdido** en `IOCPUInterruptController::registerInterrupt` + `enabledCPUs++` (50.2). Diagnóstico con los contadores del per_proc 1 independiente de processor_start.
> - **Partes 51–52** — IOCPU arreglado; los dos núcleos viven; solo queda Latte 5 (probablemente **OHCI0**, USB) soft-disabled. xnu no necesita IPI para recoger el hilo ⇒ el workloop está **bloqueado**, no pendiente. Diagnóstico decisivo en 52.3.
> - **Partes 53–54** — sin WiiUSB/IPC sigue igual. El bucle ocioso **no necesita IPI**; que el núcleo 1 reciba solo ~3 externas indica que **no está ocioso** (ocupado en un hilo o con la expulsión desactivada). Offsets de hwCtr/SIGP y de `struct processor` para verlo sin código nuevo (54.2). Plan B: arrancar el núcleo 1 **después** del escritorio (54.5).
> - **Partes 55–56** — el núcleo 1 se queda en su **hilo de arranque después de SignalReady** (antes del primer cambio de contexto) y el núcleo 0 se congela a la vez **sin panic visible**. Muy probable: un panic/timeout de lock cuyo camino (`Debugger` → `cpu_signal(SIGPdebug)` → tu `signalCPU`) se bloquea. Balizas exactas y gancho en `_panic` (56.4).
> - **Partes 57–58** — con 56.6 el núcleo 1 ya llega a `idle_thread` sin congelación; vuelve a faltar Latte 5 (OHCI0). La baliza 13 **no prueba** que esté ocioso: `idle_thread` salta al hilo recogido **sin pasar por más balizas**. Si `processor[1]->active_thread ≠ idle_thread`, el núcleo 1 está **ejecutando sin fin la action de OHCI** (58.2). PC del núcleo 1 con un stub en `_interrupt` (58.3).
> - **Partes 59–60** — ¡escritorio con `hw.ncpu 2`! Pero el núcleo 1 está **muerto en un panic silencioso** (muy probablemente `panic("thread_terminate")`: `ast_taken` no vio AST_APC). Por eso el sistema funciona como UP y arranca; en smp33, con el núcleo 1 vivo, se colgaba OHCI. Cómo confirmarlo por SSH (60.3).
> - Si algo se contradice, vale la parte **más reciente** (60 > 59 > 58 > 57 > 56 > 55 > 54 > 53 > 52 > 51 > 50 > 49 > 48 > 47 > 46 > 45 > 44 > 43b > 43 > 42 > 41 > 40 > 39 > 38 > …).
>
> Todo lo marcado **[NO VERIFICADO]** debe comprobarse en la consola antes de usarlo.


---

# EMPIEZA AQUÍ (para Claude Code en el MacBook)

**Contexto:** el humano (Rubén) tiene una Wii U con Wiintosh (Mac OS X **10.4.11**, Darwin 8.11.0) y quiere acelerar su GPU (GX2/Latte, familia AMD R7xx). Este archivo contiene toda la investigación previa. Tú corres en su MacBook.

**Acceso ya funcionando:**
- `ssh wiiu` (vía `ProxyJump redmi`; Redmi Miatoll con Fedora 44 hace de adaptador USB CDC‑ECM; red `172.16.42.0/24`, Redmi = `172.16.42.1`). Usuario Tiger: `rubano1421`.
- Pixel 6a (Vanilla, root) por **cable USB al Mac** con `adb`.
- En la Wii U: gcc 4.0.1 (Xcode 2.4.x), cabeceras en `Kernel.framework`, 17 GB libres, partición BOOT = `disk0s2` (no montada), kexts Wiintosh 0.5.2 cargados.

**Reglas:**
1. Lee primero las **Partes 60, 59, 58, 57, 56, 55, 54, 53, 52, 51, 50, 49, 48, 47, 46, 45, 44, 43b, 43, 42, 41, 40, 39, 38, 37, 36, 35, 34, 33, 32, 31, 30, 29, 28, 27, 26, 25b, 25, 24, 23, 22, 21, 20, 19, 18, 17, 16, 15, 14 y 13** (lo más reciente), luego las Partes 1–12. Las Partes 6–13 son datos reales/correcciones y prevalecen sobre las 1–5.
2. Solo lecturas hasta que el humano diga "adelante". Toda escritura de registros, `kextload`, instalación de mkext o reinicio → pedir confirmación.
3. `sudo` en Tiger es NOPASSWD ALL (Parte 9.3): **no** ejecutar `sudo` sin confirmación.
4. Cuando haga falta acción física, avisar (Parte 3/5.5) y decir exactamente qué hacer.
5. Lo marcado **[NO VERIFICADO]** se comprueba antes de construir encima.
6. Anotar cada prueba en `docs/BITACORA.md` del fork.

**Estado actual (Parte 14):** fases 0, 2 y 3 hechas; el plugin GA carga pero el WindowServer no lo usa. **Siguiente trabajo: 60.6** (leer panicstr/debug_buf y thread->ast por SSH; revisar stubs de hw_atomic_*).

**Primeras tareas originales (ya hechas, se dejan como referencia):**
1. Clonar `Wiintosh/osx-drivers` (o el fork del humano) y `Goldfish64/MacPPCKernelSDK` en el Mac.
2. `ssh wiiu 'cat /System/Library/Frameworks/IOKit.framework/Headers/graphics/IOGraphicsInterface.h'` y guardar copia (vtable del plugin GA).
3. `ssh wiiu 'sysctl hw.physmem hw.usermem'` (Parte 11.1).
3b. Clonar NetBSD `sys/arch/evbppc/nintendo` + `include/wiiu.h` como referencia (Parte 12.1).
4. Compilar `osx-drivers` **sin cambios** en la Wii U (Parte 9.2) y comparar con la release 0.5.2.
5. Preparar la sonda de la fase 0 (Parte 2 §3) y pedir permiso para cargarla.

---


# PARTE 1 — PLAN

> Documento de trabajo para Claude Code. Cópialo a la raíz de tu fork de
> `Wiintosh/osx-drivers` (por ejemplo como `docs/PLAN_GPU_WIIU.md`) y añade a
> `CLAUDE.md` la línea: `Lee docs/PLAN_GPU_WIIU.md antes de tocar WiiGraphics.`
>
> Estado de la investigación: septiembre 2026. Todo lo marcado **[NO VERIFICADO]**
> es hipótesis que hay que confirmar en hardware antes de construir encima.

---

## 0. Resumen ejecutivo

| Fase | Objetivo | Viabilidad | Riesgo |
|---|---|---|---|
| 0 | Sonda de diagnóstico: leer el estado real de la GPU tras arrancar Wiintosh | Alta | Muy bajo (solo lecturas) |
| 1 | Framebuffer mejorado: GamePad como 2ª pantalla, VBL, doble búfer | Alta | Bajo |
| 2 | Arrancar el Command Processor (anillo PM4) desde el kernel | Media | Cuelgues de GPU |
| 3 | Aceleración 2D (plugin "GA": relleno y copia) con el motor 3D | Media | Corrupción de pantalla |
| 4 | Quartz Extreme / OpenGL / Core Image | **No realista** | — |

Regla de oro: **no avanzar de fase sin validar la anterior en la consola real.**

---

## 1. Contexto: qué existe hoy

### 1.1 Repositorios de Wiintosh
- `Wiintosh/osx-drivers` — kexts. El de gráficos es `WiiGraphics/`:
  - `src/Cafe/WiiCafeFB.{hpp,cpp}` — framebuffer Wii U (clase `WiiCafeFB : IOFramebuffer`).
  - `src/Cafe/GX2Regs.hpp` — registros de pantalla (bloques D1GRPH `0x6100`, cursor `0x6400`, LUT `0x6480`).
  - `Info.plist` — hace match con `IONameMatch = NTDOY,gx2`, `IOProviderClass = IOPlatformDevice`.
- `Wiintosh/openbios` — crea el nodo `/gx2` (`drivers/wii_gx2.fs`, `arch/ppc/wii/wii.fs`):
  - `reg` = `{0x0C200000, 0x80000}` (MMIO) + `{framebuffer, 0x384000}`.
  - `interrupts = 2`, `interrupt-parent = /interrupt-controller@0c000000` (PI de Espresso).
  - Lee el framebuffer de TV desde `D1GRPH_PRIMARY_SURFACE_ADDRESS` (`0x6110`).
  - Comentario literal: *"Gamepad framebuffer will remain with Starbuck for logging purposes."*
- `Wiintosh/wiiu-loader` (fork de linux-loader, corre en Starbuck/ARM) — `arm/system/abif.c`:
  - TV (D1) en `0x8F000000`, GamePad (D2) en `0x8FE00000`.
  - 32 bpp ARGB8888, `ARRAY_LINEAR_ALIGNED`, crossbar de canales según `LT_GPU_ENDIANNESS`.
  - GamePad: 896×504 de pitch, 854×480 visibles (`arm/video/gfx.c`).
  - Acceso a GPU desde ARM vía ABIF: `offs | 0xC0000000` en `LT_ABIF_CPLTL_OFFSET/DATA`.

### 1.2 Qué hace `WiiCafeFB` hoy
- Un solo modo: 1280×720 @60, profundidades 32/16/8 bpp.
- CLUT + gamma cargados en LUT A (`loadHardwareLUT`).
- Cursor hardware 32×32 ARGB, buffer físicamente contiguo, filas de 64 px, little-endian.
- MMIO con `OSReadBigInt32/OSWriteBigInt32` (el puente Latte ya presenta los registros en big-endian).
- **Cero aceleración**: no toca el Command Processor, ni interrupciones de GPU, ni el D2.

### 1.3 Problemas abiertos relacionados (Wiintosh/Wiintosh)
- #13 Panic con carga de red alta vía USB (relevante para el ciclo de pruebas por SSH).
- #15 Cursor de espera permanente en 10.0–10.1.
- #17 System Profiler no rellena la info de pantalla en Tiger.
- #19 WiiGraphics/WiiAudio no cargan por dependencias (hay que poner `OSBundleRequired=Root` en IOGraphicsFamily/IOAudioFamily, ver README de Wiintosh).

---

## 2. El hardware: GPU7 / Latte

- Familia **AMD R7xx** (TeraScale 1). WiiUBrew la relaciona con la Radeon HD 4330 (→ RV710) **[NO VERIFICADO el chip exacto]**. 550 MHz.
- MMIO en `0x0C200000`, longitud `0x80000`, accesos de 32 bits, **big-endian desde la CPU**.
- **No está en un bus PCI** → el driver `radeon` de Linux no se puede usar tal cual (por eso Linux en Wii U sigue siendo solo framebuffer: linux-wiiu issue #19).
- Memoria:
  - MEM1: 32 MB eDRAM (render targets en Cafe OS).
  - MEM2: 2 GB DDR3 compartida CPU/GPU. Framebuffers de Wiintosh al final de MEM2.
  - **La GPU usa direcciones físicas** (Cafe OS/TCL pasa `OSEffectiveToPhysical` a la GPU). En IOKit: usar `getPhysicalSegment()` directamente.
- Las cachés de Espresso **no son coherentes** con la GPU: flush explícito (`flushDataCache`) o mapear sin caché.
- Pantallas: D1 = TV (HDMI), D2 = GamePad (el hardware/firmware DRH lo codifica y envía).
- Interrupciones: 2 líneas hacia el PI de Espresso. En Cafe OS, TCL lee un anillo IH (Interrupt Handler) con eventos `CP_RB`, `CP_EOP_EVENT`, `SCRATCH`, `DMA_*`, etc.

### 2.1 Registros confirmados (coinciden con Linux `r600d.h` y con decaf-emu)

| Registro | Offset | Uso |
|---|---|---|
| `IH_RB_BASE` / `RPTR` / `WPTR` | `0x3E04` / `0x3E08` / `0x3E0C` | Anillo de interrupciones |
| `GRBM_STATUS` | `0x8010` | ¿GPU ocupada? (bit 31 GUI_ACTIVE) |
| `GRBM_SOFT_RESET` | `0x8020` | Reset de bloques (`SOFT_RESET_CP`) |
| `SCRATCH_REG0..7` | `0x8500..` | Fences simples |
| `CP_ME_CNTL` | `0x86D8` | bit 28 `ME_HALT`, bit 26 `PFP_HALT` |
| `CP_RB_RPTR` | `0x8700` | Puntero de lectura del anillo |
| `CP_RB_BASE` | `0xC100` | Dirección del anillo `>> 8` |
| `CP_RB_CNTL` | `0xC104` | Tamaño, `BUF_SWAP_32BIT = 2<<16` |
| `CP_RB_RPTR_ADDR` | `0xC10C` | Write-back del RPTR |
| `CP_RB_WPTR` | `0xC114` | Puntero de escritura |
| `CP_INT_CNTL` / `STATUS` | `0xC124` / `0xC128` | Interrupciones del CP |
| `CP_PFP_UCODE_ADDR` / `DATA` | `0xC150` / `0xC154` | Microcódigo PFP |
| `CP_ME_RAM_RADDR` / `WADDR` / `DATA` | `0xC158` / `0xC15C` / `0xC160` | Microcódigo ME (leer/escribir) |
| `D1GRPH_*` | `0x6100..0x6138` | Superficie TV |
| `D2GRPH_*` | `0x6900..0x6938` | Superficie GamePad |
| `D1MODE_VBLANK_STATUS` | `0x6534` | Estado de VBLANK (polling) |
| Context regs (CB/DB/PA/SQ/SPI…) | `0x28000+` | Estado 3D (vía `SET_CONTEXT_REG`) |

Opcodes PM4 tipo 3 (idénticos a AMD): `NOP 0x10`, `INDIRECT_BUFFER 0x3F`, `CP_DMA 0x41`, `SURFACE_SYNC 0x43`, `ME_INITIALIZE 0x44`, `EVENT_WRITE 0x46`, `EVENT_WRITE_EOP 0x47`, `SET_CONFIG_REG 0x68`, `SET_CONTEXT_REG 0x69`, `SET_ALU_CONST 0x6A`, `SET_RESOURCE 0x6D`, `SET_SAMPLER 0x6E`, `DRAW_INDEX_AUTO 0x2D`… (los `DECAF_*` de decaf-emu son inventados por el emulador, **no existen en hardware**).

---

## 3. Qué pide Mac OS X 10.4 (arquitectura gráfica)

1. **`IOFramebuffer`** (kernel) — ya existe (`WiiCafeFB`).
2. **Aceleración 2D "GA"** — plugin CFPlugIn en espacio de usuario:
   - Interfaz **pública**: `IOGraphicsInterface.h` / `IOGraphicsInterfaceTypes.h`
     (están en `MacPPCKernelSDK/Headers/IOKit/graphics/IOGraphicsInterfaceTypes.h`).
   - `kIOGraphicsAcceleratorTypeID` = `ACCF0000-0000-0000-0000-000A2789904E`.
   - `kIOGraphicsAcceleratorInterfaceID` = `6766E94A-0000-0000-0000-000A2789904E`.
   - IOGraphicsLib (`IOPSAllocateBlitEngine`) lo carga con `IOCreatePlugInInterfaceForService(framebuffer, kIOGraphicsAcceleratorTypeID, …)` y pide tres blitters vía `GetBlitter`:
     - copia: `kIOBlitTypeCopyRects | kIOBlitCopyOperation`, `kIOBlitSourceDefault`
     - relleno: `kIOBlitTypeRects | kIOBlitCopyOperation`, `kIOBlitSourceSolid`
     - subida: `kIOBlitTypeCopyRects | kIOBlitCopyOperation`, `kIOBlitSourceMemory`
   - El framebuffer anuncia el plugin con la propiedad `IOCFPlugInTypes` = `{ "ACCF0000-…" = "WiiGX2GA.plugin" }`.
   - Precedente: Apple (`ATIRadeon9700GA.plugin`, `GeForceGA.plugin`) y VMsvga2 (driver VMware de terceros que implementó su GA plugin).
3. **Quartz Extreme / OpenGL / Core Image** — necesitan un bundle GLD (interfaz **privada**, sin documentar, del GLEngine de Apple) + `IOAccelSurface` en el kernel. Nadie ha hecho uno de terceros para 10.4 PPC; VMsvga2 nunca lo implementó. Requisitos QE en Tiger: GPU Radeon/GeForce2MX+, 16 MB VRAM, AGP. **Fuera de alcance.** Tiger funciona sin QE (como en los G3 con Rage 128).

---

## 4. Entorno de desarrollo

### 4.1 Topología
```
Wii U (Tiger, sshd) ──CDC-ECM usb0── Miatoll (Fedora 44 + claude remote-control) ──Wi-Fi──► Claude
```
- Claude Code corre en el Miatoll como usuario `claude` (sin sudo).
- Acceso a Tiger: `ssh wiiu` (ver config en §4.3).

### 4.2 Compilación
- El CI oficial usa Darling + compiladores de Xcode 3 de un repo **privado** (`Wiintosh/powerpc-kext-ci`) → un fork **no** compila en CI.
- Darling no corre en ARM (Miatoll).
- **Opción recomendada:** instalar Xcode Tools del DVD de Tiger en la propia Wii U (gcc 4.0) y compilar por SSH. Adaptar `common/kext.mk`: `CC=gcc-4.0`, `CXX=g++-4.0`, `LD=ld`, quitar `$(DARLING_SHELL)`, flag `-arch ppc`.
- Alternativa: un Mac PPC real con Xcode 2.5/3.1.
- **Comprobación rápida de sintaxis en el Miatoll** (no es una compilación real, el ABI difiere):
  ```bash
  clang++ -target powerpc-unknown-linux-gnu -fsyntax-only -x c++ -std=gnu++98 \
    -fno-rtti -fno-exceptions -nostdinc -D__ppc__ -D__BIG_ENDIAN__ -D__APPLE__ \
    -D__MACH__ -D__KERNEL__ -DKERNEL -DDEBUG -D__MAC_OS_X_VERSION_MIN_REQUIRED=1020 \
    -I../MacPPCKernelSDK/Headers -Iinclude -IWiiGraphics/src/Cafe \
    WiiGraphics/src/Cafe/WiiCafeFB.cpp
  ```
  (Clonar `Goldfish64/MacPPCKernelSDK` junto al repo. El `WiiCafeFB.cpp` actual pasa esta comprobación.)
- El `.mkext` se puede generar en el Miatoll con `python3 make-mkext.py` (necesita `pip install pylzss`) y copiarlo a la partición FAT `BOOT` (en Tiger: `/Volumes/BOOT`).

### 4.3 SSH a Tiger (OpenSSH antiguo)
En el Miatoll (una vez, como admin): `sudo update-crypto-policies --set DEFAULT:SHA1`.
`~/.ssh/config` del usuario `claude`:
```
Host wiiu
    HostName <IP de la Wii U en usb0>
    User <usuario de Tiger>
    IdentityFile ~/.ssh/id_rsa_wiiu
    KexAlgorithms +diffie-hellman-group-exchange-sha1,diffie-hellman-group14-sha1
    HostKeyAlgorithms +ssh-rsa
    PubkeyAcceptedAlgorithms +ssh-rsa
    MACs +hmac-sha1
```
Clave RSA (Tiger no soporta ed25519). Copias: `scp -O` o `rsync` (limitar con `--bwlimit=200` por el issue #13).

### 4.4 Permisos en Tiger
Solo un script propiedad de root, `/usr/local/sbin/wiiu-kext` (755, root:wheel), con sudoers:
`<usuario> ALL=(root) NOPASSWD: /usr/local/sbin/wiiu-kext`
El script debe: validar que la ruta esté bajo `~/wiiu-test/`, hacer `chown -R root:wheel`, y `kextload`/`kextunload` (fase 0) o instalar el mkext en `/Volumes/BOOT` (fases 1+).

### 4.5 Bucle de prueba
1. Editar en el Miatoll → comprobar sintaxis con clang.
2. `rsync` a Tiger → compilar en Tiger por SSH.
3. Kext cargable en caliente (sonda): `sudo wiiu-kext load …`, leer `/var/log/system.log`.
4. Cambios en `WiiGraphics` (se carga desde el mkext al arrancar): instalar mkext → reiniciar → **el humano** relanza Wiintosh desde Aroma → leer logs.
5. Panic/cuelgue: la conexión muere; el humano manda foto de la pantalla. Anotar en `docs/BITACORA.md`.

**Nunca** tocar: PLLs/relojes, SMC, NAND, registros de voltaje, `LT_*` de Starbuck salvo lectura.

---

## 5. Fase 0 — Sonda de diagnóstico (`WiiGX2Probe.kext`)

Kext independiente, `IOService` que hace match con `NTDOY,gx2` con `IOMatchCategory` propio (p. ej. `WiiGX2Probe`) para no competir con `WiiCafeFB`. Solo **lecturas** (excepto el punto opcional 4).

1. Mapear `provider->mapDeviceMemoryWithIndex(0)`.
2. Volcar a `IOLog`:
   `GRBM_STATUS 0x8010`, `CP_ME_CNTL 0x86D8`, `CP_RB_BASE 0xC100`, `CP_RB_CNTL 0xC104`,
   `CP_RB_RPTR 0x8700`, `CP_RB_WPTR 0xC114`, `CP_INT_CNTL 0xC124`, `IH_RB_BASE 0x3E04`,
   `SCRATCH_REG0..7`, `D1GRPH_*` y `D2GRPH_*` (`0x6100..0x6138`, `0x6900..0x6938`),
   `D1MODE_VBLANK_STATUS 0x6534`.
3. Microcódigo presente: con el CP **ya detenido** (`CP_ME_CNTL` bit 28 = 1), escribir `CP_ME_RAM_RADDR = 0` y leer 16 palabras de `CP_ME_RAM_DATA`; idem PFP con `CP_PFP_UCODE_ADDR`/`DATA`. Si todo es 0 o 0xFFFFFFFF → no hay microcódigo. Si el CP está corriendo, **no** tocar (solo anotarlo).
4. (Opcional, separado) Volcar el microcódigo completo (PFP 848 palabras, ME 1360 palabras en R700) a un fichero **local** del usuario. Es código de Nintendo: **no se sube al repo**.

**Pregunta que responde:** ¿el CP de Cafe OS sigue vivo/cargado tras el arranque vía Aroma → fw.img → OpenBIOS? Esto decide la fase 2.

---

## 6. Fase 1 — Framebuffer mejorado

### 6.1 GamePad como segunda pantalla
- Nuevo nodo en OpenBIOS (`/gx2-drc` o un segundo `reg` en `/gx2`) o, más simple, una segunda personalidad/instancia de `WiiCafeFB` parametrizada por `D1`/`D2` (base de registros `0x6100` vs `0x6900`, cursor `0x6400` vs `0x6C00` **[NO VERIFICADO el offset del cursor D2; en AVIVO es +0x800]**).
- Modo: 854×480 visibles, `bytesPerRow = 896*4`, memoria en `0x8FE00000`.
- Starbuck (wiiu-loader) escribe logs en el D2 → hay que desactivar esos logs (cambio en wiiu-loader o comando IPC) antes de que Mac OS X lo use.
- Resultado: Tiger con dos monitores (TV + GamePad).

### 6.2 Sincronía vertical
- Primero por polling de `D1MODE_VBLANK_STATUS (0x6534)`: implementar `IOFramebuffer::getAttribute(kIOVRAMSaveAttribute …)`/beam position si hace falta.
- Después, interrupción real: `registerInterrupt` en el proveedor (línea 2 del nodo `/gx2`) + máscaras `D1MODE_INT_MASK`. En Linux las interrupciones de R600 pasan por el anillo IH y requieren el RLC cargado → posponer a la fase 2 si el polling basta.

### 6.3 Doble búfer / page flip
- Dos superficies en MEM2; cambiar `D1GRPH_PRIMARY_SURFACE_ADDRESS` durante VBLANK usando `D1GRPH_UPDATE` (lock) **[NO VERIFICADO offset en Latte; en AVIVO `0x6144`]**.

### 6.4 Pequeñas mejoras
- Rellenar propiedades para System Profiler (issue #17): `model`, `AAPL,...`, `IOFBMemorySize`.
- Resoluciones adicionales vía el escalador D1SCL (sin cambiar el timing HDMI) — investigar al final.

---

## 7. Fase 2 — Command Processor (anillo PM4)

Nueva clase en `WiiGraphics`, p. ej. `WiiGX2Engine : IOService` (o dentro de un `IOAccelerator`), dueña del anillo.

### 7.1 Microcódigo — 3 caminos (decidir con la fase 0)
1. **Ya cargado** por Cafe OS y sobrevive (el reset `SOFT_RESET_CP` no borra la RAM de microcódigo en R6xx/R7xx): no cargar nada.
2. **Volcado de la propia consola** (fase 0.4) → fichero en la tarjeta SD, cargado en tiempo de arranque. No redistribuir.
3. **linux-firmware** `radeon/RV710_pfp.bin` + `RV710_me.bin` (big-endian en disco, `be32_to_cpup`) **[NO VERIFICADO que GPU7 lo acepte]**.

Carga (de `rv770_cp_load_microcode` en Linux):
```
CP_ME_CNTL = ME_HALT | PFP_HALT
CP_RB_CNTL = BUF_SWAP_32BIT | RB_NO_UPDATE | RB_BLKSZ(15) | RB_BUFSZ(3)
GRBM_SOFT_RESET = SOFT_RESET_CP; leer; esperar 15 ms; GRBM_SOFT_RESET = 0
CP_PFP_UCODE_ADDR = 0; 848×  CP_PFP_UCODE_DATA = w; CP_PFP_UCODE_ADDR = 0
CP_ME_RAM_WADDR  = 0; 1360× CP_ME_RAM_DATA   = w
CP_PFP_UCODE_ADDR = 0; CP_ME_RAM_WADDR = 0; CP_ME_RAM_RADDR = 0
```

### 7.2 Arranque del anillo (de `r600_cp_resume` / `r600_cp_start`)
1. Anillo: `IOBufferMemoryDescriptor::withOptions(kIOMemoryPhysicallyContiguous, 64 KB, 4096)`; página de write-back (RPTR + scratch) igual.
2. Reset CP como arriba.
3. `CP_RB_CNTL = log2(size/8) | (log2(4096/8) << 8) | BUF_SWAP_32BIT` (**BUF_SWAP obligatorio: la CPU es big-endian**).
4. `CP_RB_CNTL |= RB_RPTR_WR_ENA`; `CP_RB_RPTR_WR = 0`; `CP_RB_WPTR = 0`.
5. `CP_RB_RPTR_ADDR = phys(wb)`; `SCRATCH_ADDR = phys(wb+scratch) >> 8`; `SCRATCH_UMSK = 0xff`.
6. `CP_RB_CNTL` sin `RB_RPTR_WR_ENA`; `CP_RB_BASE = phys(ring) >> 8`.
7. `ME_INITIALIZE` (PM4 tipo 3, 0x44) con los parámetros de R7xx de Linux; luego `CP_ME_CNTL = 0xff` (quitar halt).
8. **Prueba**: `SCRATCH_REG0 = 0xCAFEDEAD`; escribir en el anillo `SET_CONFIG_REG(SCRATCH_REG0, 0xDEADBEEF)`; avanzar `WPTR`; poll hasta 100 ms. Si cambia → CP vivo.

### 7.3 Envío de trabajo
- Buffers indirectos (`INDIRECT_BUFFER 0x3F`) en MEM2, flush de caché antes de enviar.
- Fences: `EVENT_WRITE_EOP` con `CACHE_FLUSH_AND_INV_TS_EVENT` escribiendo un contador de 64 bits en memoria de write-back (así lo hace Cafe OS/TCL, con `ENDIAN_SWAP = SWAP_8IN64`), o `SCRATCH_REG`.
- Espera: polling al principio; interrupción `CP_EOP_EVENT` vía anillo IH más adelante (puede requerir RLC).
- Recuperación: timeout → `GRBM_SOFT_RESET` y re-init; nunca colgar el kernel esperando.

### 7.4 Interfaz con el espacio de usuario
- `IOUserClient` (`WiiGX2UserClient`) con métodos: `submitIB(addr,len)`, `waitFence(n)`, `allocSurface`, `mapFramebuffer`.
- Validar todo lo que venga de usuario: el CP puede escribir en cualquier dirección física.

---

## 8. Fase 3 — Aceleración 2D (`WiiGX2GA.plugin`)

R6xx/R7xx **no tienen motor 2D**: relleno y copia se hacen con el motor 3D (o `CP_DMA` para copias lineales).

### 8.1 Código a portar (licencia MIT, AMD 2008)
De `xf86-video-ati` (gitlab.freedesktop.org/xorg/driver/xf86-video-ati, `src/`):
- `r6xx_accel.c` (~1.260 líneas): emisión de estado (CB, SQ, SPI, VGT, recursos, samplers, draws).
- `r600_exa.c` (~2.100): `R600PrepareSolid/Solid`, `R600PrepareCopy/Copy` (+ copia con búfer temporal para solapes), composite (opcional).
- `r600_shader.c` (~2.900): **shaders ya ensamblados** (solid VS/PS, copy VS/PS).
- `r600_state.h`, `r600_reg*.h`.
Ya contemplan big-endian: `cb_conf.endian = ENDIAN_8IN32`, `SQ_ENDIAN_8IN32`, `IT_INDEX_TYPE_SWAP_MODE(ENDIAN_8IN32)`.

Sustituir las llamadas de X.Org (`ScrnInfoPtr`, `PixmapPtr`, `BEGIN_BATCH/E32`) por un pequeño escritor de IB propio.

### 8.2 Plugin CFPlugIn
- Bundle `WiiGX2GA.plugin` en `/System/Library/Extensions`, factoría que devuelve `IOGraphicsAcceleratorInterface`.
- Implementar: `Reset`, `CopyCapabilities`, `Flush`, `Synchronize`, `GetBeamPosition`, `SetDestination`, `GetBlitter`, `WaitComplete` (las superficies `AllocateSurface/...` pueden devolver `kIOReturnUnsupported` al inicio).
- `GetBlitter` devuelve funciones para: rectángulos sólidos, copia pantalla→pantalla y subida desde memoria (esta última puede ser `memcpy` por CPU al principio).
- `WiiCafeFB` publica `IOCFPlugInTypes`.
- Compilar con gcc 4.0 de Xcode en Tiger (CoreFoundation + IOKit.framework).

### 8.3 Validación
- Arrastrar ventanas, scroll en Terminal/Safari, comparar con el driver sin plugin (renombrar el plugin para desactivarlo).
- Coherencia: el WindowServer también escribe en el framebuffer por CPU → `Synchronize`/`WaitComplete` deben esperar al fence antes de que la CPU toque la zona.

---

## 9. Fase 4 — Por qué no Quartz Extreme/OpenGL

- Requiere un GLD bundle con la ABI privada del GLEngine de 10.4 PPC (ingeniería inversa de `ATIRadeon9700GLDriver.bundle`) + clases `IOAccelSurface/IOAccelGLContext` en el kernel + una implementación GL entera (p. ej. portar el driver clásico `r600` de Mesa 7.x) + compilador de shaders R700.
- Nadie lo ha logrado para una GPU de terceros en 10.4; en 2026 VMQemuVGA apenas despacha las dos primeras funciones GL en x86.
- Revisitar solo si las fases 0–3 funcionan de forma estable.

---

## 10. Instrucciones para Claude Code

- Trabajar en una rama del fork de `osx-drivers`; commits pequeños, uno por paso.
- Antes de cada cambio en hardware: explicar qué registros se escriben y por qué.
- Primero lecturas, luego escrituras reversibles; guardar y restaurar los valores originales de los registros que se toquen.
- Toda espera activa con timeout.
- Mantener `docs/BITACORA.md` con: fecha, commit, qué se probó, resultado (log o foto), conclusión.
- Marcar como **[NO VERIFICADO]** cualquier offset que no venga de: Linux `r600d.h`, decaf-emu `latte_registers*.h`, wiiu-loader o WiiUBrew.
- No subir microcódigo ni ningún binario de Nintendo al repositorio.
- Pedir al humano: reinicios, relanzar Wiintosh desde Aroma, fotos de panics.

---

## 11. Referencias
- Wiintosh: https://github.com/Wiintosh/Wiintosh · https://github.com/Wiintosh/osx-drivers · https://github.com/Wiintosh/openbios · https://github.com/Wiintosh/wiiu-loader
- SDK de cabeceras PPC: https://github.com/Goldfish64/MacPPCKernelSDK
- WiiUBrew GX2: https://wiiubrew.org/wiki/Hardware/GX2
- Arquitectura Wii U: https://www.copetti.org/writings/consoles/wiiu/
- fail0verflow (identificación R7xx): https://fail0verflow.com/blog/2014/console-hacking-2013-omake/
- linux-wiiu, aceleración GX2: https://gitlab.com/linux-wiiu/linux-wiiu/-/work_items/19
- Linux radeon: `drivers/gpu/drm/radeon/r600.c`, `rv770.c`, `r600d.h` (https://github.com/torvalds/linux)
- decaf-emu (registros, PM4, TCL): https://github.com/decaf-emu/decaf-emu (`src/libgpu/latte/`, `src/libdecaf/src/cafe/libraries/tcl/`)
- xf86-video-ati: https://gitlab.freedesktop.org/xorg/driver/xf86-video-ati
- Mesa r600 big-endian (RV730): https://lists.freedesktop.org/archives/dri-devel/2011-April/010170.html
- IOGraphicsInterface.h: https://github.com/jollyjinx/IOGraphicsGit/blob/master/IOGraphics/IOGraphicsFamily/IOKit/graphics/IOGraphicsInterface.h
- IOGraphicsLib.c: http://web.mit.edu/darwin/src/modules/IOKitUser/graphics.subproj/IOGraphicsLib.c
- VMsvga2: https://sourceforge.net/projects/vmsvga2/
- AMD R6xx/R7xx 3D register reference y R700 ISA (documentación abierta de AMD en x.org)

---

# PARTE 2 — REFERENCIA TÉCNICA

> Segundo documento para Claude Code. `PLAN_GPU_WIIU.md` dice **qué** hacer por
> fases; este dice **cómo**, con secuencias de registros, paquetes PM4 y esqueletos
> de código. Todo sale del driver `radeon` de Linux (`r600.c`, `rv770.c`,
> `r600d.h`, `r500_reg.h`), de decaf-emu y de xf86-video-ati, salvo lo marcado
> **[NO VERIFICADO]**, que hay que confirmar leyendo el registro en la Wii U antes
> de escribir en él.

---

## 1. Convenciones

- MMIO: base física `0x0C200000`, tamaño `0x80000`. En el kext: `provider->mapDeviceMemoryWithIndex(0)`.
- Acceso siempre con `OSReadBigInt32 / OSWriteBigInt32` (el puente de Latte ya da los registros en big-endian a la CPU). Tras escrituras críticas: `eieio()` o leer de vuelta.
- Las direcciones que ve la GPU son **físicas**. Nunca pasar direcciones virtuales del kernel.
- Memoria compartida con la GPU:
  ```cpp
  IOBufferMemoryDescriptor *md = IOBufferMemoryDescriptor::withOptions(
      kIOMemoryPhysicallyContiguous, size, PAGE_SIZE);
  void *cpu = md->getBytesNoCopy();
  IOByteCount len; IOPhysicalAddress gpu = md->getPhysicalSegment(0, &len);
  ```
  Después de escribir desde la CPU: `flushDataCache(cpu, size)` (ya se usa en `WiiCafeFB::setCursorImage`). Antes de leer lo que escribió la GPU: invalidar/flush de nuevo o mapear sin caché.
- Macros PM4 (de `r600d.h`):
  ```c
  #define PACKET0(reg, n)  ((0u << 30) | (((reg) >> 2) & 0xFFFF) | (((n) & 0x3FFF) << 16))
  #define PACKET3(op, n)   ((3u << 30) | (((op) & 0xFF) << 8) | (((n) & 0x3FFF) << 16))
  #define PACKET2          (2u << 30)                        /* relleno / NOP de 1 palabra */
  #define SET_CONFIG_REG_OFFSET  0x00008000   /* índice = (reg - 0x8000) >> 2 */
  #define SET_CONTEXT_REG_OFFSET 0x00028000   /* índice = (reg - 0x28000) >> 2 */
  ```
  `n` = número de palabras de datos − 1.

---

## 2. Mapa de registros ampliado

### 2.1 Control del motor (config)
| Registro | Offset | Bits útiles |
|---|---|---|
| `GRBM_CNTL` | `0x8000` | timeout de lectura |
| `GRBM_STATUS` | `0x8010` | bit 31 `GUI_ACTIVE`, bit 29 `CP_BUSY` **[NO VERIFICADO bit exacto; ver r600d.h]** |
| `GRBM_SOFT_RESET` | `0x8020` | bit 0 `SOFT_RESET_CP` |
| `WAIT_UNTIL` | `0x8040` | `WAIT_3D_IDLE`, `WAIT_3D_IDLECLEAN` |
| `SCRATCH_REG0..7` | `0x8500..0x851C` | valores de fence |
| `SCRATCH_UMSK` | `0x8540` | qué scratch se copian a memoria (0xFF) |
| `SCRATCH_ADDR` | `0x8544` | dirección física de copia `>> 8` |
| `CP_SEM_WAIT_TIMER` | `0x85BC` | 0 |
| `CP_ME_CNTL` | `0x86D8` | bit 28 `ME_HALT`, bit 26 `PFP_HALT` |
| `CP_RB_RPTR` | `0x8700` | RPTR (solo lectura) |
| `CP_RB_WPTR_DELAY` | `0x8704` | 0 |
| `SRBM_SOFT_RESET` | `0x0E60` | reset de bloques del sistema (IH, DMA…) |

### 2.2 Anillo del CP
| Registro | Offset | Nota |
|---|---|---|
| `CP_RB_BASE` | `0xC100` | físico `>> 8` |
| `CP_RB_CNTL` | `0xC104` | `RB_BUFSZ(x)` bits 0-5, `RB_BLKSZ(x)` bits 8-13, `BUF_SWAP_32BIT` = `2<<16`, `RB_NO_UPDATE` = `1<<27`, `RB_RPTR_WR_ENA` = `1<<31` |
| `CP_RB_RPTR_WR` | `0xC108` | forzar RPTR |
| `CP_RB_RPTR_ADDR` | `0xC10C` | write-back de RPTR (físico, alineado a 4) |
| `CP_RB_WPTR` | `0xC114` | en palabras de 32 bits |
| `CP_INT_CNTL` | `0xC124` | habilitar interrupciones CP |
| `CP_INT_STATUS` | `0xC128` | `RB_INT_STAT` = `1<<31` |
| `CP_PFP_UCODE_ADDR/DATA` | `0xC150/0xC154` | |
| `CP_ME_RAM_RADDR/WADDR/DATA` | `0xC158/0xC15C/0xC160` | |
| `CP_DEBUG` | `0xC1FC` | Linux escribe `(1<<27)|(1<<28)` |
| `CP_IB1_BASE_LO/HI/SIZE` | `0x8730/0x8734/0x8738` | solo lectura, útil al depurar cuelgues |

### 2.3 Interrupciones
| Registro | Offset |
|---|---|
| `IH_RB_CNTL` | `0x3E00` |
| `IH_RB_BASE` | `0x3E04` |
| `IH_RB_RPTR` | `0x3E08` |
| `IH_RB_WPTR` | `0x3E0C` |
| `IH_RB_WPTR_ADDR_LO` | `0x3E14` |
| `IH_CNTL` | `0x3E18` |
| `RLC_CNTL` | `0x3F00` |
| `INTERRUPT_CNTL` | `0x5468` |
| `DxMODE_INT_MASK` | `0x6540` (D1 vblank bit 0, D1 vline bit 4, D2 vblank bit 8, D2 vline bit 12) |
| `DISP_INTERRUPT_STATUS` | `0x7EDC` (`LB_D1_VBLANK` bit 4, `LB_D2_VBLANK` bit 5) |
| `D1MODE_VBLANK_STATUS` / `D2…` | `0x6534` / `0x6D34` (`OCCURRED` bit 0, `ACK` bit 4, `STAT` bit 12) |

**IDs de fuente del anillo IH** (de `r600_irq_process`): `1` D1 vblank/vline, `5` D2 vblank/vline, `9` D1 page-flip, `11` D2 page-flip, `19` hotplug, `176` CP_INT en anillo, `177/178` CP_INT en IB1/IB2, `181` CP EOP, `224` DMA trap, `233` GUI idle. Cada entrada IH = 4 palabras (src_id en bits 0-7 de la palabra 0, src_data en bits 0-27 de la palabra 1) **[NO VERIFICADO endianness de las entradas en Latte; Cafe OS/TCL las lee, mirar decaf `tcl_interrupthandler.cpp`]**.

### 2.4 Pantalla (bloque AVIVO/DCE; D2 = D1 + `0x800`)
| Registro | D1 (TV) | D2 (GamePad) |
|---|---|---|
| `DxCRTC_H_TOTAL` | `0x6000` | `0x6800` |
| `DxCRTC_CONTROL` | `0x6080` | `0x6880` |
| `DxCRTC_STATUS_POSITION` | `0x60A0` | `0x68A0` |
| `DxGRPH_ENABLE` | `0x6100` | `0x6900` |
| `DxGRPH_CONTROL` | `0x6104` | `0x6904` |
| `DxGRPH_SWAP_CNTL` | `0x610C` | `0x690C` |
| `DxGRPH_PRIMARY_SURFACE_ADDRESS` | `0x6110` | `0x6910` |
| `DxGRPH_PITCH` | `0x6120` | `0x6920` |
| `DxGRPH_X/Y_START/END` | `0x612C–0x6138` | `0x692C–0x6938` |
| `DxGRPH_UPDATE` | `0x6144` (`UPDATE_LOCK` bit 16, `SURFACE_UPDATE_PENDING` bit 2) | `0x6944` |
| `DxGRPH_FLIP_CONTROL` | `0x6148` | `0x6948` |
| `DxGRPH_INTERRUPT_STATUS/CONTROL` | `0x6158/0x615C` | `0x6958/0x695C` |
| `DxCUR_CONTROL` | `0x6400` | `0x6C00` |
| `DxMODE_DESKTOP_HEIGHT` | `0x652C` | `0x6D2C` |
| `DxMODE_VIEWPORT_SIZE` | `0x6584` | `0x6D84` |
| `DxSCL_SCALER_ENABLE` | `0x6590` | `0x6D90` |

Todo el bloque D2 sale de sumar `0x800` (así en AVIVO y confirmado para `D2GRPH=0x6900` por wiiu-loader); **[NO VERIFICADO en Latte para cursor/CRTC/escalador]**.

### 2.5 Motor DMA (R7xx; Cafe OS lo llama DMAE)
`DMA_RB_CNTL 0xD000`, `DMA_RB_BASE 0xD004`, `DMA_CNTL 0xD02C`. Útil más adelante para copias/rellenos lineales sin shaders **[NO VERIFICADO su microcódigo/estado en Latte]**.

---

## 3. Fase 0 en código: la sonda

`WiiGraphics/src/Probe/WiiGX2Probe.{hpp,cpp}` (o kext aparte). Personalidad en `Info.plist`:
```xml
<key>WiiGX2Probe</key>
<dict>
  <key>IOClass</key><string>WiiGX2Probe</string>
  <key>IOProviderClass</key><string>IOPlatformDevice</string>
  <key>IONameMatch</key><string>NTDOY,gx2</string>
  <key>IOMatchCategory</key><string>WiiGX2Probe</string>
  <key>IOProbeScore</key><integer>1000</integer>
</dict>
```
Esqueleto:
```cpp
static const struct { const char *name; UInt32 off; } kRegs[] = {
  {"GRBM_STATUS",0x8010},{"CP_ME_CNTL",0x86D8},{"CP_RB_BASE",0xC100},
  {"CP_RB_CNTL",0xC104},{"CP_RB_RPTR",0x8700},{"CP_RB_WPTR",0xC114},
  {"CP_RB_RPTR_ADDR",0xC10C},{"CP_INT_CNTL",0xC124},{"CP_INT_STATUS",0xC128},
  {"SCRATCH_UMSK",0x8540},{"SCRATCH_ADDR",0x8544},{"SCRATCH_REG0",0x8500},
  {"IH_RB_CNTL",0x3E00},{"IH_RB_BASE",0x3E04},{"IH_CNTL",0x3E18},{"RLC_CNTL",0x3F00},
  {"DMA_RB_CNTL",0xD000},{"DMA_CNTL",0xD02C},
  {"D1GRPH_CONTROL",0x6104},{"D1GRPH_SURF",0x6110},{"D1GRPH_PITCH",0x6120},
  {"D1GRPH_UPDATE",0x6144},{"D1CRTC_CONTROL",0x6080},{"D1CRTC_POS",0x60A0},
  {"D1MODE_VBLANK_STATUS",0x6534},{"DxMODE_INT_MASK",0x6540},
  {"D2GRPH_CONTROL",0x6904},{"D2GRPH_SURF",0x6910},{"D2GRPH_PITCH",0x6920},
  {"D2CRTC_CONTROL",0x6880},{"DISP_INTERRUPT_STATUS",0x7EDC},
};
for (unsigned i = 0; i < sizeof(kRegs)/sizeof(kRegs[0]); i++)
  IOLog("GX2Probe: %-22s [%05X] = %08X\n", kRegs[i].name, kRegs[i].off, rd(kRegs[i].off));

// Muestreo de CRTC: si la posición cambia, la pantalla corre y sabemos el ritmo.
for (int i = 0; i < 4; i++) { IOLog("GX2Probe: CRTC pos %08X\n", rd(0x60A0)); IODelay(4000); }

// ¿Hay microcódigo? SOLO si CP_ME_CNTL indica ME detenido (bit 28).
if (rd(0x86D8) & (1u << 28)) {
  wr(0xC158, 0);                       // CP_ME_RAM_RADDR
  for (int i = 0; i < 8; i++) IOLog("GX2Probe: ME[%d] = %08X\n", i, rd(0xC160));
  wr(0xC150, 0);                       // CP_PFP_UCODE_ADDR (lectura por DATA)
  for (int i = 0; i < 8; i++) IOLog("GX2Probe: PFP[%d] = %08X\n", i, rd(0xC154));
  wr(0xC150, 0); wr(0xC158, 0);
} else {
  IOLog("GX2Probe: CP en marcha, no se toca la RAM de microcódigo\n");
}
```
Interpretar:
- `CP_ME_CNTL` con bit 28 = 0 y `CP_RB_RPTR == CP_RB_WPTR` → el CP de Cafe OS sigue vivo e inactivo: el microcódigo **está** cargado.
- `CP_RB_BASE` distinto de 0 → apunta al anillo de Cafe OS (probablemente memoria que ahora usa Mac OS X: hay que detener el CP antes de que lea basura).
- `CRTC pos` fijo → el D1 no refresca; algo raro con el modo.
- La lectura de PFP por `CP_PFP_UCODE_DATA` puede no devolver datos en R7xx **[NO VERIFICADO]**; si todo sale 0, confiar en el ME.

⚠️ Riesgo detectado: si el CP de Cafe OS sigue **corriendo** y su anillo está en memoria que Mac OS X reutiliza, puede ejecutar basura en cualquier momento. La sonda debe reportarlo; la fase 2 empezará deteniéndolo (`CP_ME_CNTL = ME_HALT|PFP_HALT`).

---

## 4. Fase 1 en código

### 4.1 Vblank por polling
```cpp
bool WiiCafeFB::waitVBlank(UInt32 base /*0x0 para D1, 0x800 para D2*/) {
  wr(0x6534 + base, 1u << 4);                        // ACK de la anterior
  for (int t = 0; t < 20000; t++) {                  // ~20 ms
    if (rd(0x6534 + base) & 1u) return true;         // VBLANK_OCCURRED
    IODelay(1);
  }
  return false;
}
```
Úsalo en `setDisplayMode` y al cambiar de superficie para evitar tearing.

### 4.2 Page flip sin tearing
```cpp
wr(0x6144 + base, rd(0x6144 + base) | (1u << 16));   // UPDATE_LOCK
wr(0x6110 + base, newSurfacePhys);                   // PRIMARY_SURFACE_ADDRESS
wr(0x6144 + base, rd(0x6144 + base) & ~(1u << 16));  // se aplica en el próximo vblank
while (rd(0x6144 + base) & (1u << 2)) IODelay(10);   // SURFACE_UPDATE_PENDING (con timeout)
```

### 4.3 GamePad (D2) como IOFramebuffer
- Refactor: `WiiCafeFB` recibe `_regBase` (`0x0` ó `0x800`), ancho/alto visibles, pitch y dirección de memoria desde propiedades.
- OpenBIOS: añadir un nodo `gx2-drc` con `reg = {0x0C200000,0x80000, 0x8FE00000, 896*504*4}` y propiedad `wiintosh,display = 1`; o crear el nub desde `WiiCafeFB::start` con `IOPlatformDevice` hijo.
- Modo único: `nominalWidth 854`, `nominalHeight 480`, `bytesPerRow 3584` (896×4), 32 bpp.
- Starbuck escribe logs en el D2: parchear `wiiu-loader` (`arm/video/gfx.c`) para dejar de pintar en `GFX_DRC` cuando arranca la PPC, o limpiar y pedirle por IPC que pare.
- Cursor D2: registros en `0x6C00+` **[NO VERIFICADO]**.

### 4.4 Interrupción vblank real (cuando haga falta)
En DCE3 (R7xx) las interrupciones de pantalla llegan por el anillo IH. Linux (`r600_irq_init`) necesita el **RLC** cargado para que el IH funcione. Opciones:
1. Mantener polling (suficiente para Tiger).
2. Probar si la línea `interrupts = 2` del nodo `/gx2` dispara con solo `DxMODE_INT_MASK` + `DxGRPH_INTERRUPT_CONTROL` **[NO VERIFICADO]**.
3. Montar IH completo tras la fase 2.

---

## 5. Fase 2 en código: el anillo PM4

### 5.1 Detener y reiniciar el CP (de `rv770_cp_load_microcode` / `r600_cp_resume`)
```cpp
wr(0x86D8, (1u<<28) | (1u<<26));                 // ME_HALT | PFP_HALT
wr(0x8540, 0);                                    // SCRATCH_UMSK
wr(0x8020, 1u);  (void)rd(0x8020); IOSleep(15);   // SOFT_RESET_CP
wr(0x8020, 0);
```
(Si hay que cargar microcódigo, hacerlo aquí: ver PLAN §7.1. Tamaños R7xx: PFP 848, ME 1360 palabras.)

### 5.2 Configurar el anillo (64 KB)
```cpp
UInt32 bufsz = log2(64*1024 / 8);                 // = 13
UInt32 cntl  = bufsz | (log2(4096/8) << 8) | (2u << 16);   // BUF_SWAP_32BIT
wr(0xC104, cntl);                                 // CP_RB_CNTL
wr(0x85BC, 0);                                    // CP_SEM_WAIT_TIMER
wr(0x8704, 0);                                    // CP_RB_WPTR_DELAY
wr(0xC104, cntl | (1u << 31));                    // RB_RPTR_WR_ENA
wr(0xC108, 0);  wptr = 0;  wr(0xC114, 0);         // RPTR_WR, WPTR
wr(0xC10C, wbPhys + 0);                           // CP_RB_RPTR_ADDR
wr(0x8544, (wbPhys + 0x100) >> 8);                // SCRATCH_ADDR
wr(0x8540, 0xFF);                                 // SCRATCH_UMSK
IOSleep(1);
wr(0xC104, cntl);
wr(0xC100, ringPhys >> 8);                        // CP_RB_BASE
wr(0xC1FC, (1u << 27) | (1u << 28));              // CP_DEBUG
```

### 5.3 ME_INITIALIZE y arranque (de `r600_cp_start`, rama RV770+)
```cpp
ring(PACKET3(0x44, 5));   // ME_INITIALIZE
ring(0x1);
ring(0x0);
ring(maxHwContexts - 1);  // RV710 en Linux: 4 → escribir 3  [NO VERIFICADO para GPU7]
ring(1u << 16);           // PACKET3_ME_INITIALIZE_DEVICE_ID(1)
ring(0); ring(0);
commit();                 // flushDataCache + wr(CP_RB_WPTR, wptr)
wr(0x86D8, 0xFF);         // quitar HALT (valor que usa Linux)
```

### 5.4 Prueba del anillo (de `r600_ring_test`)
```cpp
wr(0x8500, 0xCAFEDEAD);                           // SCRATCH_REG0
ring(PACKET3(0x68, 1));                           // SET_CONFIG_REG
ring((0x8500 - 0x8000) >> 2);
ring(0xDEADBEEF);
commit();
for (int i = 0; i < 100000; i++) { if (rd(0x8500) == 0xDEADBEEF) break; IODelay(1); }
```
Si no llega: volcar `GRBM_STATUS`, `CP_RB_RPTR`, `CP_RB_WPTR`, `CP_ME_CNTL` y detener el CP.

### 5.5 Commit y relleno
- El WPTR se cuenta en palabras. Alinear el final de cada envío a 16 palabras con `PACKET2` (Linux usa `align_mask = 15`).
- `flushDataCache(ring + oldWptr, bytes)` antes de escribir `CP_RB_WPTR`; ojo con el wrap-around.
- Espacio libre = `(rptr - wptr - 1) & mask`, leyendo `rptr` del write-back (flush/invalidar antes).

### 5.6 Fence (de `r600_fence_ring_emit`, rama con eventos)
```cpp
UInt32 coher = (1u<<23) | (1u<<24) | (1u<<27) | (1u<<20);
// TC_ACTION_ENA | VC_ACTION_ENA | SH_ACTION_ENA | FULL_CACHE_ENA  [bits: copiar de r600d.h PACKET3_*_ACTION_ENA]
ring(PACKET3(0x43, 3)); ring(coher); ring(0xFFFFFFFF); ring(0); ring(10);   // SURFACE_SYNC
ring(PACKET3(0x47, 4));                                                     // EVENT_WRITE_EOP
ring((0x14 /*CACHE_FLUSH_AND_INV_EVENT_TS*/) | (5u << 8));                  // EVENT_TYPE | EVENT_INDEX(5)
ring(fenceAddrLo);
ring((fenceAddrHi & 0xFF) | (1u << 29) /*DATA_SEL(1)*/ | (0u << 24) /*INT_SEL(0): sin irq aún*/);
ring(seq); ring(0);
```
Los números de bit y el código de evento hay que copiarlos de `r600d.h` (`PACKET3_TC_ACTION_ENA`, `EVENT_TYPE`, `DATA_SEL`, `INT_SEL`) — **no** fiarse de los de arriba sin comprobarlos. Cafe OS usa además `ENDIAN_SWAP = SWAP_8IN64` en la dirección del EOP para escribir el contador en big-endian (decaf `tcl_ring.cpp`).

Alternativa más simple para empezar (sin EOP): `EVENT_WRITE(CACHE_FLUSH_AND_INV_EVENT)` + `SET_CONFIG_REG(WAIT_UNTIL, WAIT_3D_IDLE|WAIT_3D_IDLECLEAN)` + `SET_CONFIG_REG(SCRATCH_REG1, seq)` y poll de `SCRATCH_REG1`.

### 5.7 Estado global del motor 3D
Linux ejecuta `rv770_gpu_init` (tiling, pipes, `SQ_*_RESOURCE_MGMT`, `SX/SPI/VGT` defaults) y `rv770_mc_program` (controlador de memoria). **En la Wii U no hay que reprogramar el controlador de memoria** (lo configura Nintendo; direcciones físicas). Para el resto:
1. Leer y registrar con la sonda los valores que dejó Cafe OS (`GB_TILING_CONFIG`, `SQ_CONFIG 0x8C00`, `SQ_GPR_RESOURCE_MGMT_1/2 0x8C04/0x8C08`, `SQ_THREAD_RESOURCE_MGMT 0x8C0C`, `SQ_STACK_RESOURCE_MGMT_1/2`, `VGT_*`, `SPI_CONFIG_CNTL_1 0x913C`, `TA_CNTL_AUX 0x9508`).
2. Reutilizarlos tal cual. Solo si fallan los dibujos, copiar valores de `rv770_gpu_init` para RV710.

---

## 6. Fase 3: portar la aceleración 2D de xf86-video-ati

### 6.1 Qué se porta (MIT, AMD 2008)
- `r600_shader.c`: `R600_solid_vs/ps` y `R600_copy_vs/ps` (palabras de shader ya ensambladas, parametrizadas por familia; usar la rama R7xx). No hace falta ensamblador.
- `r6xx_accel.c`: `r600_set_default_state`, `r600_set_render_target`, `r600_vs_setup`, `r600_ps_setup`, `r600_set_alu_consts`, `r600_set_vtx_resource`, `r600_set_tex_resource`, `r600_set_tex_sampler`, scissors, `r600_draw_auto`, `r600_cp_set_surface_sync`, `r600_wait_3d_idle(_clean)`.
- `r600_exa.c`: `R600PrepareSolid/R600Solid/R600DoneSolid`, `R600PrepareCopy/R600Copy/R600DoneCopy`, y `R600OverlapCopy` / copia con búfer temporal para solapes (scroll).

### 6.2 Cómo lo hace (relleno)
1. `r600_set_default_state` (una vez por lote).
2. Scissors al tamaño del destino.
3. VS con 2 GPRs, PS con 1 GPR (`export_mode = 2`).
4. Render target `CB_COLOR0`: dirección física del framebuffer, pitch en píxeles, formato `COLOR_8_8_8_8`, `ENDIAN_8IN32` en big-endian (ya está en el código con `#if X_BYTE_ORDER == X_BIG_ENDIAN`).
5. Color en constantes ALU del PS (`R600SetSolidConsts`).
6. Cada rectángulo = 3 vértices (rect list) en un VBO → `r600_draw_auto`.
7. Al terminar: `SURFACE_SYNC` sobre el destino + fence.

Copia: igual, pero con textura (`SET_RESOURCE` + `SET_SAMPLER`) apuntando al origen y 4 floats por vértice (posición + coordenada de textura).

### 6.3 Adaptación
- Sustituir `ScrnInfoPtr`, `PixmapPtr`, `BEGIN_BATCH/E32/END_BATCH`, `radeon_vbo_*` por:
  ```c
  typedef struct { uint32_t *buf; uint32_t n, cap; uint64_t phys; } gx2_ib_t;
  static inline void E32(gx2_ib_t *ib, uint32_t v) { ib->buf[ib->n++] = v; }
  ```
- Shaders y VBO en un búfer físico contiguo; en `r600_vs_setup/ps_setup` las direcciones van `>> 8`.
- Los IB se envían con `INDIRECT_BUFFER (0x3F)`: `PACKET3(0x3F,2)`, `phys & ~3`, `(phys>>32)&0xFF`, `ndw`.
- Todo en espacio de usuario (plugin) salvo el envío, que pasa por el `IOUserClient` del kernel, que debe validar que las direcciones del IB caen dentro de búferes que le pertenecen.

### 6.4 El plugin GA (esqueleto)
```c
// Info.plist del plugin: CFPlugInFactories { UUID-factoria = "WiiGX2GAFactory" },
// CFPlugInTypes { "ACCF0000-0000-0000-0000-000A2789904E" = [UUID-factoria] }
static IOGraphicsAcceleratorInterface gVtbl = {
  NULL, QueryInterface, AddRef, Release,          // IUNKNOWN_C_GUTS
  kCurrentGraphicsInterfaceVersion, kCurrentGraphicsInterfaceRevision,
  Probe, Start, Stop,                              // IOCFPlugInInterface
  Reset, CopyCapabilities, NULL /*GetBlitProc compat*/, Flush,
  NULL, Synchronize, GetBeamPosition,
  AllocateSurface, FreeSurface, LockSurface, UnlockSurface, SwapSurface,
  SetDestination, GetBlitter, WaitComplete, /* reservados */
};
```
**[NO VERIFICADO el orden exacto de campos]**: copiarlo de `IOGraphicsInterface.h` de IOGraphics (versión de Tiger) antes de compilar. `Start` abre el user client del framebuffer (`IOServiceOpen`) y mapea el framebuffer. `GetBlitter` devuelve funciones para relleno sólido, copia pantalla→pantalla y (al principio con `memcpy`) memoria→pantalla. `WaitComplete` espera al último fence.

### 6.5 Coherencia con el WindowServer
El WindowServer pinta con la CPU en el mismo framebuffer. Reglas:
- Antes de devolver de `Synchronize`/`WaitComplete`, esperar al fence.
- Tras cada lote GPU, `SURFACE_SYNC` del framebuffer.
- La CPU escribe en el framebuffer a través de un mapeo sin caché (el de `getApertureRange`), así que no hace falta flush de su lado **[NO VERIFICADO el tipo de mapeo que usa IOGraphics en Wiintosh]**.

---

## 7. Depuración

- **Logs**: `IOLog` → `/var/log/system.log`. Los kexts de Wiintosh ya pueden enviar logs por IPC a Starbuck en Wii U (`include/WiiCommon.hpp`, macros `WIIDBGLOG/WIISYSLOG`, activar con boot-args de depuración) → aparecen en la pantalla que controla Starbuck aunque se cuelgue la PPC.
- **Cuelgue de GPU**: `GRBM_STATUS` con `GUI_ACTIVE` fijo y `CP_RB_RPTR` parado. Volcar `CP_IB1_BASE_LO/SIZE` y la zona del anillo en `RPTR`. Recuperar con `GRBM_SOFT_RESET = SOFT_RESET_CP` y reinicio del anillo.
- **Paquete inválido**: interrupción `CP_BAD_OPCODE` en Cafe OS; aquí, anillo parado. Comprobar el contador `n` de cada `PACKET3`.
- **Colores cambiados** (rojo↔azul): swap/endian del render target (`ENDIAN_8IN32`) o crossbar del D1 (`DGRPH_CROSSBAR`, ver wiiu-loader `abif.c`).
- **Dibujo en diagonal/roto**: pitch (en píxeles para CB, en bytes para D1GRPH) o modo de array (usar `ARRAY_LINEAR_ALIGNED`; alineación de pitch a 64 píxeles **[NO VERIFICADO requisito exacto]**).
- **Decodificar PM4**: decaf-emu tiene un desensamblador (`latte_pm4_reader.h`, `latte_disassembler.h`). Se puede compilar una herramienta en el Miatoll que lea un volcado del anillo.

---

## 8. Material de referencia a descargar al Miatoll

```bash
mkdir -p ~/ref && cd ~/ref
for f in r600.c rv770.c r600d.h rv770d.h r500_reg.h avivod.h r600_cs.c; do
  curl -fsSLO https://raw.githubusercontent.com/torvalds/linux/master/drivers/gpu/drm/radeon/$f; done
git clone --depth 1 https://gitlab.freedesktop.org/xorg/driver/xf86-video-ati.git
git clone --depth 1 --filter=blob:none --sparse https://github.com/decaf-emu/decaf-emu.git && \
  (cd decaf-emu && git sparse-checkout set src/libgpu/latte src/libdecaf/src/cafe/libraries/tcl src/libdecaf/src/cafe/libraries/gx2)
git clone --depth 1 https://github.com/Goldfish64/MacPPCKernelSDK.git
git clone --depth 1 https://github.com/Wiintosh/openbios.git
git clone --depth 1 https://github.com/Wiintosh/wiiu-loader.git
```
Documentación AMD abierta (x.org, "AMD developer docs"): *R6xx/R7xx 3D Register Reference*, *R600/R700 ISA*, *RV630/RV770 Register Reference (display)*. `r600_cs.c` (verificador de comandos de Linux) es muy útil para saber qué registros/paquetes toca cada operación.

---

## 9. Dudas abiertas (resolver en este orden)

1. ¿El CP de Cafe OS está detenido o corriendo al arrancar Mac OS X? (sonda)
2. ¿El microcódigo sigue en la RAM del CP? (sonda)
3. ¿Qué `max_hw_contexts` y configuración SQ dejó Cafe OS? (sonda §5.7)
4. ¿Las lecturas/escrituras de D2 (GamePad) funcionan igual que D1? (fase 1)
5. ¿La línea de interrupción 2 del nodo `/gx2` dispara sin RLC? (fase 1/2)
6. ¿Formato de las entradas del anillo IH en Latte? (decaf `tcl_interrupthandler.cpp`)
7. ¿Orden exacto de `IOGraphicsAcceleratorInterface` en Tiger? (cabecera de IOGraphics-179.x)

---

# PARTE 3 — DEPURACIÓN Y AVISOS

> Tercer documento para Claude Code (junto a `PLAN_GPU_WIIU.md` y
> `REFERENCIA_TECNICA_GX2.md`). Cubre: **cómo ver qué pasa** en la Wii U, incluso
> cuando se cuelga, y **cómo avisar al humano** cuando hace falta una acción
> física (reiniciar, relanzar Wiintosh desde Aroma, hacer una foto…).
> Lo marcado **[NO VERIFICADO]** hay que probarlo antes de depender de ello.

---

## Parte A — Depuración

### A.1 Capas de observación (de más cómoda a más robusta)

| Capa | Sobrevive a… | Cómo |
|---|---|---|
| `IOLog` → `/var/log/system.log` | nada (se pierde si hay panic antes de que syslogd escriba) | `ssh wiiu tail -f /var/log/system.log` |
| `dmesg` de Tiger | nada | `ssh wiiu sudo dmesg` |
| Logs por IPC a Starbuck | cuelgue/panic de la PPC | Ya existe en los kexts de Wiintosh (`include/WiiCommon.hpp`: en Wii U los logs de depuración se envían por IPC). Se ven en la pantalla que controla Starbuck (GamePad). |
| Buffer de log en memoria reservada | reinicio en caliente **[NO VERIFICADO]** | Ver A.4 |
| Foto de la pantalla | todo | El humano la manda (Parte B) |

### A.2 Reglas para el código del driver
- Cada paso de hardware con `IOLog("GX2: <fase>.<paso> ...")` **antes** de escribir el registro: si se cuelga, el último log dice dónde.
- Tras cada `IOLog` importante en fases de riesgo: `IOSleep(50)` para dar tiempo a que llegue a syslog (solo en builds de depuración).
- Esperas siempre con timeout; al vencer: volcar registros (`GRBM_STATUS 0x8010`, `CP_RB_RPTR 0x8700`, `CP_RB_WPTR 0xC114`, `CP_ME_CNTL 0x86D8`, `CP_IB1_BASE_LO/SIZE 0x8730/0x8738`), detener CP (`CP_ME_CNTL = ME_HALT|PFP_HALT`) y devolver error. **Nunca** bucles infinitos.
- Guardar los valores originales de cada registro que se modifique y restaurarlos en `stop()`.
- Interruptor de seguridad por boot-arg: si `gx2=0` en `boot-args`, el driver no toca nada salvo el framebuffer básico. Así, si un cambio impide arrancar, el humano escribe en OpenBIOS `setenv boot-args "-v gx2=0"` y vuelve a arrancar.
  ```cpp
  UInt32 gx2 = 1; PE_parse_boot_arg("gx2", &gx2); if (!gx2) { /* modo seguro */ }
  ```
- Niveles: `gx2dbg=0..3` por boot-arg para controlar verbosidad sin recompilar.

### A.3 Arranque verbose y modo seguro
- En OpenBIOS: `setenv boot-args "-v"` (verbose) — los logs de arranque salen en pantalla.
- `-s` = single user (para arreglar kexts rotos desde consola).
- Crear `disable-autoboot` en la partición `BOOT` para que OpenBIOS se pare y deje cambiar boot-args.
- Mantener siempre en la SD **dos mkext**: el bueno conocido y el de prueba. Documentar en la bitácora cuál está activo. Volver al bueno = copiar/renombrar desde el Miatoll o desde otro ordenador.

### A.4 Log que sobrevive a un panic (idea) **[NO VERIFICADO]**
1. Reservar al final de MEM2 una zona fija (p. ej. 1 MB justo antes de `0x8F000000`, fuera de lo que usa Mac OS X; hay que excluirla en OpenBIOS `ofmem.c`).
2. El driver escribe ahí un anillo de texto con cabecera mágica (`'GX2L'`, contador, posición) y hace `flushDataCache`.
3. Tras reiniciar (Aroma → fw.img → OpenBIOS), la sonda lee la zona: si la cabecera es válida, vuelca el log anterior a `system.log`.
4. Hay que comprobar primero si MEM2 conserva el contenido en un reinicio en caliente y que ni IOSU ni el loader la pisan.

### A.5 Diagnóstico de la GPU (resumen práctico)
| Síntoma | Qué mirar |
|---|---|
| Anillo no avanza | `CP_RB_RPTR` fijo, `CP_ME_CNTL` con HALT, microcódigo ausente |
| GPU colgada | `GRBM_STATUS` con GUI_ACTIVE fijo; volcar IB actual; `GRBM_SOFT_RESET` |
| Colores cambiados | endian del render target (`ENDIAN_8IN32`) / crossbar del D1 |
| Imagen desplazada/diagonal | pitch (píxeles vs bytes), alineación |
| Tearing | no se espera a `D1MODE_VBLANK_STATUS` / `D1GRPH_UPDATE` |
| Panic al transferir ficheros | issue #13 (red USB); limitar `rsync --bwlimit=200` |

- Volcado del anillo: el driver expone por `IOUserClient` (o por un sysctl) la zona del anillo alrededor de `RPTR`; el Miatoll lo decodifica con el desensamblador PM4 de decaf-emu (`latte_pm4_reader.h`).
- Herramienta de usuario en Tiger `gx2ctl` (llamando al user client): `gx2ctl regs`, `gx2ctl ring`, `gx2ctl reset`, `gx2ctl test-scratch`. Evita reiniciar para inspeccionar.

### A.6 Latido (heartbeat) desde el Miatoll
Script en el Miatoll que detecta cuándo la Wii U deja de responder:
```bash
#!/bin/bash
# ~/bin/wiiu-latido.sh — ejecutar en tmux o como servicio de usuario
estado=vivo
while true; do
  if ssh -o ConnectTimeout=5 -o BatchMode=yes wiiu true 2>/dev/null; then
    [ "$estado" = muerto ] && ~/bin/avisar.sh info "La Wii U vuelve a responder"
    estado=vivo
  elif ping -c1 -W2 <IP_WIIU> >/dev/null; then
    : # red viva pero sshd no: posible cuelgue parcial, no avisar aún
  else
    [ "$estado" = vivo ] && ~/bin/avisar.sh urgente "La Wii U no responde: probablemente panic o cuelgue. Haz una FOTO de la pantalla y reinicia."
    estado=muerto
  fi
  sleep 10
done
```
Además, antes de cargar un kext de riesgo, Claude hace `ssh wiiu 'tail -n 200 /var/log/system.log' > ~/bitacora/pre_$(date +%s).log`.

### A.7 Otras vías (a investigar)
- **Serie de Starbuck**: linux-loader/wiiu-loader tiene `arm/system/serial.c`; existe UART de depuración en la placa (requiere soldar; opcional). Daría logs de Starbuck incluso con la PPC colgada.
- **Gadget compuesto en el Miatoll**: añadir una función CDC-ACM (serie) al gadget además de ECM. Tiger tiene driver CDC-ACM; un demonio en Tiger podría reenviar `system.log` por serie (independiente de la red). No sirve para el kernel temprano. **[NO VERIFICADO]**
- **kdp (depurador remoto de Mac OS X)**: necesita un driver Ethernet con soporte de depuración en kernel; el CDC-ECM USB casi seguro no lo tiene. Descartado salvo sorpresa.

---

## Parte B — Avisar al humano

### B.1 Principio
Claude **nunca espera en silencio**. Cuando necesita una acción física:
1. Envía un aviso (B.2) con: qué hacer, por qué, y qué responder.
2. Anota en la bitácora que está esperando.
3. Espera a que la Wii U vuelva (latido) o a que el humano responda en la app.

### B.2 Canal principal: ntfy (push al móvil, gratis, sin cuenta)
1. En el móvil del humano: instalar la app **ntfy** y suscribirse a un tema secreto y largo, p. ej. `wiiu-gx2-<cadena-aleatoria>` (el nombre del tema es la contraseña: no publicarlo).
2. En el Miatoll, `~/bin/avisar.sh`:
```bash
#!/bin/bash
# Uso: avisar.sh <urgente|accion|info> "mensaje"
TEMA="wiiu-gx2-CAMBIAR_POR_CADENA_ALEATORIA"
case "$1" in
  urgente) PRI=5; TAGS=rotating_light ;;
  accion)  PRI=4; TAGS=wrench ;;
  *)       PRI=3; TAGS=information_source ;;
esac
curl -s -H "Title: Wii U / Claude" -H "Priority: $PRI" -H "Tags: $TAGS" \
     -d "$2" "https://ntfy.sh/$TEMA" >/dev/null
# Aviso local extra en el propio Miatoll (ver B.4)
~/bin/aviso-local.sh "$1" 2>/dev/null || true
```
Alternativas equivalentes: bot de Telegram (`curl https://api.telegram.org/bot<TOKEN>/sendMessage -d chat_id=… -d text=…`) o correo. Guardar tokens fuera del repo (`~/.config/wiiu-avisos.env`).

### B.3 Integración con Claude Code (hooks)
En el Miatoll, `~/.claude/settings.json` del usuario `claude` (o `.claude/settings.json` del fork):
```json
{
  "hooks": {
    "Notification": [
      { "hooks": [ { "type": "command",
        "command": "~/bin/avisar.sh accion \"Claude necesita tu atención en la sesión Wii U\"" } ] }
    ],
    "Stop": [
      { "hooks": [ { "type": "command",
        "command": "~/bin/avisar.sh info \"Claude terminó su turno\"" } ] }
    ]
  }
}
```
- `Notification` salta cuando Claude pide permiso o espera respuesta.
- `Stop` salta al acabar cada turno (quitarlo si molesta).
- Además, la app de Claude Code ya muestra la sesión de `claude remote-control`; los avisos de ntfy cubren el caso de que no la estés mirando.

Regla para `CLAUDE.md`:
> Cuando necesites una acción física, ejecuta `~/bin/avisar.sh accion "<instrucciones>"` y escribe en el chat exactamente qué tiene que hacer el humano y qué debe responder. Si la Wii U deja de responder tras cargar algo, usa `urgente`.

### B.4 Aviso local en el Miatoll (el teléfono está al lado de la consola)
El Miatoll es un móvil: puede vibrar, encender la pantalla o hablar.
```bash
#!/bin/bash
# ~/bin/aviso-local.sh <nivel>
# Vibración (el nombre del dispositivo varía; buscar con: ls /sys/class/leds; evtest)
for v in /sys/class/leds/vibrator /sys/class/leds/*vib*; do
  [ -w "$v/brightness" ] && { echo 1 > "$v/brightness"; sleep 1; echo 0 > "$v/brightness"; }
done
# Voz (dnf install espeak-ng) si hay altavoz funcional
command -v espeak-ng >/dev/null && espeak-ng -v es "Atención: la Wii U te necesita" 2>/dev/null
```
En kernels mainline de sm7125 la vibración suele ser un dispositivo `input` con force-feedback (usar `fftest`/`feedbackd`) en lugar de `/sys/class/leds` **[NO VERIFICADO en Miatoll con Fedora 44]**. Requiere permisos: añadir una regla udev o un sudo acotado.

### B.5 Catálogo de peticiones físicas (plantillas)

| Código | Cuándo | Mensaje |
|---|---|---|
| `REINICIAR` | Panic / cuelgue / tras instalar mkext | "Mantén el botón de encendido 5 s. Enciende, entra en Aroma, lanza el loader fw.img. Responde 'arrancado' cuando veas el escritorio." |
| `FOTO` | Panic o pantalla rara | "Haz una foto nítida de la TV (y del GamePad si tiene texto). Súbela al chat." |
| `BOOTARGS` | Modo seguro/verbose | "En OpenBIOS escribe: `setenv boot-args \"-v gx2=0\"` y luego `boot`." |
| `MKEXT_BUENO` | No arranca ni en modo seguro | "Saca la SD, copia `Wii_tiger.mkext.bueno` sobre `Wii_tiger.mkext` en la partición BOOT." |
| `MIRAR` | Verificación visual | "¿Se ve [X] en la TV / GamePad? Responde sí/no y descríbelo." |
| `GAMEPAD` | Pruebas de D2 | "Enciende el GamePad y dime qué muestra." |
| `CABLE` | Red caída | "Comprueba el cable USB entre Miatoll y Wii U." |

Cada aviso incluye: código, paso del plan (p. ej. `Fase 2.4`), y commit probado.

### B.6 Automatizar el reinicio (opcional)
- Enchufe inteligente controlable por HTTP local (Tasmota/Shelly) en el Miatoll: `curl http://enchufe/cm?cmnd=Power%20Off` → espera → `On`.
- Aroma: configurar el autoarranque para lanzar directamente el entorno/loader de Wiintosh **[NO VERIFICADO si el loader fw.img puede ser el autoarranque]**.
- Con ambos, Claude podría reiniciar solo; aun así, **pedir confirmación** antes de cortar la corriente (riesgo si se estaba escribiendo en la SD).

---

## Parte C — Bitácora
`docs/BITACORA.md` en el fork, una entrada por prueba:
```
## 2026-10-01 14:32 — Fase 0.1 — commit abc123
Qué: sonda solo lectura.
Resultado: CP_ME_CNTL=0x00000000 (CP corriendo), CP_RB_BASE=0x..., ...
Avisos enviados: ninguno.
Conclusión: microcódigo presente; siguiente paso 2.1 (detener CP).
```
Guardar fotos de panics en `docs/bitacora/fotos/` (sin datos personales).

---

## Parte D — Lista de preparación
- [ ] App ntfy en el móvil + tema secreto.
- [ ] `~/bin/avisar.sh`, `~/bin/aviso-local.sh`, `~/bin/wiiu-latido.sh` en el Miatoll (chmod +x).
- [ ] Hooks `Notification`/`Stop` en `settings.json`.
- [ ] Regla de avisos en `CLAUDE.md`.
- [ ] `ssh wiiu` funcionando sin contraseña.
- [ ] mkext bueno de respaldo en la SD.
- [ ] Boot-arg `gx2=0` implementado antes de la fase 2.
- [ ] `docs/BITACORA.md` creado.

---

# PARTE 4 — Pixel 6a con Vanilla en lugar de GamePad

> No hay GamePad físico: se usa **Vanilla** (clon de GamePad por software,
> https://github.com/vanilla-wiiu/vanilla, GPL-2.0) en un **Pixel 6a (bluejay)
> rooteado**.

## 4.1 Lo que dice la documentación de Vanilla
- El Pixel 6a aparece como compatible: *"Connection works OOTB, some graphical issues with HW encode (LineageOS 22); Stock Android 17 also works flawlessly"*. No necesita parche de firmware (mismo chip que Pixel 7).
- Requiere **root** (acceso a bajo nivel a la Wi-Fi) y el APK de Releases, en modo **"Local"**.
- La conexión con la Wii U es 802.11n a 5 GHz con una PTK "rotada 3 bytes"; por eso hace falta root y un chip SoftMAC.
- Existe `vanilla-pipe` (solo Linux): hace la conexión Wi-Fi y reenvía por UDP a un frontend en otra máquina ("Via Server").
- El frontend de escritorio tiene **captura (F12)** y **grabación (F5)**.

## 4.2 Limitación clave para Wiintosh **[NO VERIFICADO — prueba nº 1]**
El enlace GamePad↔Wii U lo gestiona Cafe OS/IOSU (chip DRH). Vanilla se empareja y funciona **mientras corre Cafe OS (Aroma)**. Cuando se arranca Wiintosh, wiiu-loader sustituye a IOSU, así que es probable que:
- **En Aroma** Vanilla funcione (menús, lanzar el loader de Wiintosh).
- **En Wiintosh** el enlace se caiga y el D2 (pantalla GamePad) no llegue a ningún sitio.
Prueba: arrancar Wiintosh con Vanilla conectado y ver si sigue mostrando imagen (los logs de Starbuck en D2). Si no:
- La "segunda pantalla GamePad" de la fase 1 queda **aparcada**.
- Los logs por IPC a Starbuck que salen en D2 **no se verán**; habrá que usar la TV o el log persistente (Parte 3, A.4).

## 4.3 Para qué le sirve a la IA
1. **Relanzar Wiintosh sin ti** (junto con el enchufe inteligente): tras cortar y dar corriente, Aroma arranca, Vanilla se reconecta y la IA pulsa botones virtuales para abrir el loader fw.img.
2. **Ver la TV** usando la cámara del Pixel apuntando a la pantalla (panics, colores, tearing).
3. **Ver lo que muestre Vanilla** con capturas de pantalla del Pixel.

## 4.4 Cómo conecta la IA (Miatoll) con el Pixel
Problema: el USB del Miatoll está ocupado (gadget CDC-ECM hacia la Wii U) y la Wi-Fi del Pixel la usa Vanilla para hablar con la Wii U **[NO VERIFICADO si Android mantiene otra red a la vez]**.
Opciones, de mejor a peor:
1. **Bluetooth PAN** Pixel ↔ Miatoll: en el Pixel activar "Compartir conexión por Bluetooth"; en Fedora `bluetoothctl pair/connect` + NetworkManager (`nmcli dev connect <MAC>`). Luego `adb connect <ip-pixel>:5555` (en el Pixel, como root: `setprop service.adb.tcp.port 5555; stop adbd; start adbd`).
2. **Datos móviles del Pixel + túnel saliente**: un script en Termux sube capturas a donde el Miatoll las recoja. Más lento.
3. **Vanilla por pipe**: el Miatoll ejecuta `vanilla-pipe -udp wlan0` y el frontend Linux corre allí con captura. **Descartado** mientras la Wi-Fi del Miatoll sea su salida a internet (y su chip WCN3990 no está en la lista de compatibles **[NO VERIFICADO]**).

## 4.5 Comandos (desde el Miatoll, con adb sobre Bluetooth)
```bash
# Captura de lo que muestra Vanilla
adb exec-out su -c 'screencap -p' > ~/bitacora/vanilla_$(date +%s).png

# Foto de la TV con la cámara trasera (instalar Termux + Termux:API en el Pixel)
adb shell "run-as com.termux files/usr/bin/termux-camera-photo -c 0 /sdcard/tv.jpg" \
  && adb pull /sdcard/tv.jpg ~/bitacora/tv_$(date +%s).jpg
# (si run-as no funciona: su -c + am broadcast a Termux:API [NO VERIFICADO])

# Pulsar un botón virtual de Vanilla (coordenadas a calibrar con una captura)
adb shell su -c 'input tap <x> <y>'
adb shell su -c 'input keyevent KEYCODE_BUTTON_A'   # si Vanilla mapea mandos [NO VERIFICADO]

# Mantener la pantalla del Pixel encendida
adb shell su -c 'svc power stayon true'
```
Script `~/bin/pixel-ver.sh`: captura + foto, las guarda en `~/bitacora/` y las enseña a Claude (Claude Code puede leer imágenes).

## 4.6 Montaje físico recomendado
- Pixel en un soporte fijo con la cámara apuntando a la TV, enchufado a su cargador (no al Miatoll).
- "No bloquear pantalla" y Vanilla en primer plano.
- Calibrar una vez: foto de referencia de la TV con el escritorio de Tiger → Claude guarda las coordenadas de la zona útil.

## 4.7 Nuevas peticiones físicas
| Código | Mensaje |
|---|---|
| `VANILLA` | "Abre Vanilla en el Pixel y conéctalo a la Wii U (estando en Aroma)." |
| `CAMARA` | "Recoloca el Pixel: la TV debe verse entera en la foto." |
| `BT` | "Reconecta el Bluetooth entre el Pixel y el Miatoll." |

## 4.8 Pruebas pendientes, en orden
1. ¿Vanilla sigue mostrando algo tras arrancar Wiintosh?
2. ¿Android mantiene el Bluetooth PAN/adb mientras Vanilla ocupa la Wi-Fi?
3. ¿`screencap` captura el vídeo de Vanilla (decodificado por hardware) o sale negro?
4. ¿Se puede navegar Aroma solo con toques/teclas enviados por adb?

---

# PARTE 5 — Ejecutarlo todo desde Claude Code en el MacBook

> Cambio de topología: Claude Code corre en el **MacBook**; entra por SSH al
> **Redmi (Miatoll, Fedora 44)** y, a través de él, a la **Wii U (Tiger)** y al
> **Pixel 6a** (adb). Esta parte sustituye a "Claude en el Miatoll" de las
> partes anteriores; todo lo demás sigue valiendo.

## 5.1 Topología
```
MacBook (Claude Code) ──Wi‑Fi LAN / SSH──► Redmi Miatoll (Fedora) ──usb0 CDC‑ECM──► Wii U (Tiger, sshd)
                                              │
                                              └─Bluetooth PAN / adb──► Pixel 6a (Vanilla + cámara)
MacBook (opcional) ─► VM QEMU con Mac OS X 10.4 PPC + Xcode 2.5 (compilación)
```
Ventajas: el Mac tiene más CPU, disco y red; el Redmi solo hace de puente. Si el Mac duerme, la sesión se para: `caffeinate -dis` mientras trabaja Claude.

## 5.2 SSH: Mac → Redmi → Wii U
En el Redmi (una vez): `sudo dnf install -y openssh-server && sudo systemctl enable --now sshd`, usuario `claude` con tu clave pública del Mac en `~/.ssh/authorized_keys`, y `PasswordAuthentication no`.

`~/.ssh/config` del **Mac**:
```
Host redmi
    HostName <IP del Redmi en tu Wi‑Fi>      # o redmi.local si hay mDNS (avahi)
    User claude
    IdentityFile ~/.ssh/id_ed25519
    ServerAliveInterval 15
    ControlMaster auto
    ControlPath ~/.ssh/cm-%r@%h:%p
    ControlPersist 10m

Host wiiu
    HostName <IP de la Wii U en usb0>
    User <usuario de Tiger>
    ProxyJump redmi
    IdentityFile ~/.ssh/id_rsa_wiiu
    KexAlgorithms +diffie-hellman-group-exchange-sha1,diffie-hellman-group14-sha1
    HostKeyAlgorithms +ssh-rsa
    PubkeyAcceptedAlgorithms +ssh-rsa
    MACs +hmac-sha1
    ServerAliveInterval 15
```
Clave: con `ProxyJump`, **el que negocia con Tiger es el ssh del Mac**, no el de Fedora. macOS no tiene crypto-policies, así que las opciones `+ssh-rsa` bastan y **no hace falta** relajar SHA‑1 en Fedora (`DEFAULT:SHA1` solo si alguna vez se hace ssh a Tiger desde el Redmi). Generar en el Mac `ssh-keygen -t rsa -b 3072 -f ~/.ssh/id_rsa_wiiu` (Tiger no admite ed25519).

Comprobaciones:
```bash
ssh redmi 'ip -br addr; uname -a'
ssh wiiu 'sw_vers; uname -a'
rsync -av --bwlimit=200 -e ssh ./build/ wiiu:~/wiiu-test/     # límite por el issue #13
```
Si falla la negociación: `ssh -vvv wiiu`. Si el OpenSSH del Mac ya no incluye algún algoritmo antiguo **[NO VERIFICADO para la versión de macOS instalada]**, alternativa: `brew install openssh` o hacer el último salto desde el Redmi con `DEFAULT:SHA1`.

Posibles problemas del Redmi: IP cambiante (reservar DHCP o usar `redmi.local` con `avahi`), firewall (`sudo firewall-cmd --add-service=ssh --permanent`), suspensión del móvil (desactivar suspensión: `sudo systemctl mask sleep.target suspend.target`).

## 5.3 Dónde compilar (todas las opciones)
| Opción | Cómo | Pros | Contras |
|---|---|---|---|
| **A. VM QEMU PPC en el Mac** (recomendada) | `brew install qemu`; `qemu-system-ppc -M mac99,via=pmu -m 1024 -hda tiger.qcow2 ...`; instalar Tiger 10.4.x + **Xcode 2.5** (requiere 10.4.7+, en la VM se puede actualizar a 10.4.11 sin afectar a la Wii U) | Compilador nativo PPC de Apple; rápido de iterar; no se cuelga la Wii U para compilar | Hay que instalar Tiger en QEMU (guías de E-Maculation); el disco de la VM se comparte por SSH/`rsync` (activar "Sesión remota" también en la VM y usar red `-netdev user,hostfwd=tcp::2222-:22`) |
| B. En la propia Wii U | Xcode Tools del DVD de Tiger 10.4.0 (gcc 4.0) | Mismo sistema que el destino | Lento; si la Wii U está colgada no se compila; Xcode 2.5 no instala en 10.4.0 |
| C. Linux x86_64 + Darling + Xcode 3 (lo que usa el CI oficial) | VM Linux en el Mac (en Apple Silicon, emulada) | Es la ruta "oficial" | Los binarios de Xcode 3 del CI están en un repo privado; hay que conseguirlos tú; Darling no corre en ARM ni en macOS |
| D. Mac PPC real con Xcode 2.5/3.1 | — | Fiable | Hardware extra |
| E. Solo comprobación de sintaxis | `clang++ -target powerpc-unknown-linux-gnu -fsyntax-only ...` (ver Parte 2) en el Mac o el Redmi | Instantáneo | No produce binarios; ABI distinto |

Adaptar `common/kext.mk` para A/B/D: `CC=gcc-4.0 CXX=g++-4.0 LD=ld`, quitar `$(DARLING_SHELL)`, añadir `-arch ppc`, SDK: `-isysroot /Developer/SDKs/MacOSX10.4u.sdk` o las cabeceras de `MacPPCKernelSDK`. El `.mkext` se genera con `python3 make-mkext.py` en el Mac (`pip3 install pylzss`).

La VM QEMU también sirve para **probar lógica que no toca hardware** (plugin GA cargando, parsers, `gx2ctl` sin GPU). El kext de la GPU **solo** se prueba en la Wii U.

Flujo completo desde el Mac:
```bash
# 1. editar en el fork (Mac) → 2. compilar en la VM
rsync -a ./ tigervm:~/osx-drivers/ && ssh tigervm 'cd ~/osx-drivers && make OSX_VERSION=tiger'
# 3. traer el kext y empaquetar
rsync -a tigervm:~/osx-drivers/WiiGraphics/build_kext_tiger/ ./out/
python3 make-mkext.py out/Kexts out/Wii_tiger.mkext
# 4. subir a la Wii U (partición BOOT montada en Tiger) y reiniciar
rsync --bwlimit=200 out/Wii_tiger.mkext wiiu:~/wiiu-test/ && ssh wiiu 'sudo /usr/local/sbin/wiiu-kext install ~/wiiu-test/Wii_tiger.mkext'
```

## 5.4 Pixel 6a desde el Mac
- adb en el Mac (`brew install android-platform-tools`) hablando con el adb del Pixel a través del Redmi:
  ```bash
  ssh -N -L 5555:<IP-del-Pixel-en-BT-PAN>:5555 redmi &   # túnel
  adb connect 127.0.0.1:5555
  adb exec-out su -c 'screencap -p' > vanilla.png
  ```
- Alternativa si el Pixel está cerca del Mac: adb por USB directamente al Mac (lo más fiable), o adb por Wi‑Fi si Android mantiene la red normal mientras Vanilla usa la radio **[NO VERIFICADO]**.
- Claude Code en el Mac puede **leer las imágenes** (capturas y fotos de la TV) directamente.

## 5.5 Avisos al humano desde el Mac
En `~/.claude/settings.json` del Mac:
```json
{
  "hooks": {
    "Notification": [ { "hooks": [ { "type": "command",
      "command": "osascript -e 'display notification \"Claude necesita tu atención (Wii U)\" with title \"Wiintosh\" sound name \"Glass\"'; ~/bin/avisar.sh accion 'Claude te necesita'" } ] } ],
    "Stop": [ { "hooks": [ { "type": "command",
      "command": "osascript -e 'display notification \"Turno terminado\" with title \"Wiintosh\"'" } ] } ]
  }
}
```
- `say "La Wii U te necesita"` para aviso por voz en el Mac.
- `~/bin/avisar.sh` (ntfy, Parte 3) funciona igual en macOS (usa `curl`).
- El **latido** (`wiiu-latido.sh`) mejor en el **Redmi** (siempre encendido y pegado a la Wii U), avisando por ntfy; así funciona aunque el Mac duerma.

## 5.6 Permisos de Claude Code en el Mac
`.claude/settings.json` del fork, para no pedir permiso en cada comando rutinario:
```json
{ "permissions": { "allow": [
  "Bash(ssh wiiu tail:*)", "Bash(ssh wiiu cat /var/log/*)", "Bash(ssh redmi ip:*)",
  "Bash(rsync:*)", "Bash(make:*)", "Bash(python3 make-mkext.py:*)",
  "Bash(adb exec-out:*)", "Bash(adb pull:*)"
] } }
```
Mantener **con confirmación**: cualquier `ssh wiiu sudo ...`, cargas de kext, reinicios y cortes de corriente.

## 5.7 Seguridad
- Nada expuesto a internet: todo es LAN (Mac↔Redmi) + USB/Bluetooth. No abrir puertos en el router.
- Tiger (sshd de 2005) solo accesible a través del Redmi. En el Redmi, `firewalld` permitiendo ssh solo desde la LAN.
- En Tiger, sudo limitado al script `wiiu-kext` (Parte 3).
- El tema de ntfy es secreto; no subirlo al repo.
- No subir microcódigo ni firmware de Nintendo.

## 5.8 Todas las posibilidades evaluadas (resumen)
| Posibilidad | Veredicto |
|---|---|
| VPN hacia la sesión de Claude en la nube | No: esa sesión solo sale por HTTPS vía proxy |
| Claude Code en el Redmi + Remote Control | Válido (partes 1–3) |
| **Claude Code en el Mac + SSH vía Redmi** | **Recomendado ahora** |
| Tailscale en el Redmi (subnet router) | Útil para ti fuera de casa; innecesario en LAN |
| Compilar: VM QEMU Tiger / Wii U / Darling / Mac PPC | QEMU recomendado (5.3) |
| Ver la TV: cámara del Pixel | Recomendado |
| Ver la TV: capturadora HDMI USB en el Mac | Alternativa muy buena si tienes una (UVC, `ffmpeg -f avfoundation`); imagen limpia sin cámara |
| Ver GamePad: Vanilla en Pixel | Solo en Aroma probablemente (Parte 4) |
| Logs: syslog por SSH / IPC Starbuck / MEM2 persistente / serie | Por orden de facilidad (Parte 3) |
| kdp (depurador de kernel por red) | Descartado (sin driver Ethernet compatible) |
| Reinicio automático: enchufe inteligente + autoarranque Aroma + Vanilla | Posible, con confirmación humana |

## 5.9 Checklist de arranque (orden)
1. [ ] sshd en el Redmi y `ssh redmi` desde el Mac.
2. [ ] "Sesión remota" en Tiger y `ssh wiiu` (ProxyJump) funcionando.
3. [ ] VM QEMU con Tiger + Xcode 2.5 y compilación del `osx-drivers` sin cambios (debe dar el mismo resultado que la release 0.5.2).
4. [ ] mkext bueno de respaldo en la SD.
5. [ ] ntfy + hooks del Mac + latido en el Redmi.
6. [ ] Pixel: Vanilla en Aroma, adb vía Redmi, foto de la TV.
7. [ ] Fase 0 (sonda) → anotar en `docs/BITACORA.md`.

---

# PARTE 6 — Estado confirmado (2026-09-26)

- ✅ `ssh wiiu` desde el MacBook funciona (vía Redmi). La Wii U ve al Redmi como `172.16.42.1` (red del gadget CDC‑ECM `172.16.42.0/24`).
- ✅ Hostname de Tiger: `wiiu-de-rubano-1421.local`, usuario `rubano1421`.
- ✅ **Darwin 8.11.0 = Mac OS X 10.4.11** (xnu-792.24.17, RELEASE_PPC). Consecuencias:
  - **Xcode 2.5 se puede instalar en la propia Wii U** (requiere 10.4.7+). La opción B de compilación (5.3) pasa a ser viable con Xcode 2.5 y no solo con el Xcode del DVD. La VM QEMU sigue siendo útil para no depender de la consola.
  - Usar las cabeceras/SDK de 10.4u y `-mmacosx-version-min=10.4` en los builds de prueba propios (el repo usa 10.2 como mínimo; mantenerlo para PRs upstream).
  - Las referencias de IOGraphics deben ser las de 10.4.11 (IOGraphics-179.x o posterior de Tiger).
- ✅ Pixel 6a: **por cable USB directamente al MacBook** con adb. Se descarta el Bluetooth PAN y el túnel por el Redmi (Parte 4.4 y 5.4):
  ```bash
  adb devices                                   # en el Mac
  adb exec-out su -c 'screencap -p' > vanilla.png
  adb shell su -c 'input tap <x> <y>'
  ```
  Así Vanilla puede ocupar la Wi‑Fi del Pixel sin afectar a adb.

Siguientes pasos:
1. `ssh wiiu 'sw_vers; ioreg -l -w0 | grep -i -A5 gx2; kextstat | grep -i wii; ls /Volumes'` — ver drivers cargados y si la partición BOOT está montada.
2. Instalar Xcode 2.5 en la Wii U (o en la VM) y compilar `osx-drivers` sin cambios.
3. Fase 0: sonda.

---

# PARTE 7 — Análisis de la segunda salida (2026-09-26)

Salida de `ssh wiiu 'sw_vers; kextstat | grep -i wii; ioreg -l -w0 | grep -i -A5 gx2; ls /Volumes'`:
- `10.4.11 (8S165)`.
- Cargados los 6 kexts de Wiintosh **0.5.2**: WiiPlatform (16), WiiEXI (21), WiiAudio (24), WiiUSB (25), WiiStorage (27), **WiiGraphics (28, 0x7000 bytes)**. El framebuffer `WiiCafeFB` está activo → el problema de dependencias (issue #19) no afecta a esta instalación.
- `ioreg: error: can't obtain properties.` → el `ioreg -l` completo de Tiger falla (registro grande o propiedad no serializable). Usar consultas acotadas:
  ```bash
  ssh wiiu 'ioreg -c WiiCafeFB -l -w0'          # el framebuffer y sus propiedades
  ssh wiiu 'ioreg -n gx2 -l -w0'                # el nodo de OpenBIOS
  ssh wiiu 'ioreg -p IODeviceTree -n gx2 -l -w0' # plano del árbol de dispositivos (reg, interrupts)
  ssh wiiu 'ioreg -c IODisplayConnect -l -w0; ioreg -c IOFramebuffer -r -d 1'
  ```
  Si alguno vuelve a fallar, quitar `-l` y bajar profundidad (`-d 2`), o `ioalloccount`/`ioclasscount WiiCafeFB`.
- `/Volumes`: **Hackintosh HD** (sistema), **Mac OS X Install DVD** (partición instaladora), **Xcode Tools**.
  - La partición FAT **BOOT no está montada** → para instalar mkext nuevos hay que montarla:
    ```bash
    ssh wiiu 'diskutil list'                          # localizar la FAT32 "BOOT" (p. ej. disk0s2)
    ssh wiiu 'sudo diskutil mount /dev/disk0sX'       # o: sudo mkdir /Volumes/BOOT && sudo mount_msdos /dev/disk0sX /Volumes/BOOT
    ```
    Incluir el montaje en el script `wiiu-kext install` y desmontar (`diskutil unmount`) antes de reiniciar para no corromper la FAT.
  - Hay un volumen **Xcode Tools** montado: probablemente las Xcode Tools del DVD de Tiger (Xcode 2.0–2.2). Comprobar e instalar:
    ```bash
    ssh wiiu 'ls "/Volumes/Xcode Tools"; ls /Developer 2>/dev/null | head'
    ssh wiiu 'sudo installer -pkg "/Volumes/Xcode Tools/XcodeTools.mpkg" -target /'
    ssh wiiu 'gcc-4.0 -v; ls /System/Library/Frameworks/Kernel.framework/Headers/IOKit/graphics'
    ```
    - Con Xcode ≥2.0 hay gcc 4.0, `ld`, `kextcache` y las cabeceras del kernel en `Kernel.framework` → suficiente para compilar kexts **en la propia Wii U**.
    - Xcode 2.5 (última para Tiger PPC, 10.4.7+) mejora el SDK `MacOSX10.4u.sdk`; se descarga de developer.apple.com (cuenta gratuita, `xcode25_8m2558_developerdvd.dmg`) y se copia por `rsync --bwlimit` a la Wii U.
    - Espacio: comprobar `df -h /` antes (Xcode ocupa ~1–2 GB).
    - La instalación es lenta en Espresso/SD; hacerla una vez con `caffeinate` en el Mac.

Con esto el flujo de compilación queda:
```bash
rsync -a --bwlimit=200 ./ wiiu:~/osx-drivers/
ssh wiiu 'cd ~/osx-drivers && make OSX_VERSION=tiger CC=gcc-4.0 CXX=g++-4.0 DARLING_SHELL= LD=ld'
```
(requiere el ajuste de `common/kext.mk` descrito en 5.3: rutas de toolchain sin Darling y `-I` a `Kernel.framework/Headers` o a `MacPPCKernelSDK`).

Próximos comandos informativos (solo lectura, seguros):
```bash
ssh wiiu 'df -h; diskutil list; ls "/Volumes/Xcode Tools"'
ssh wiiu 'ioreg -p IODeviceTree -n gx2 -l -w0; ioreg -c WiiCafeFB -l -w0'
ssh wiiu 'grep -i -E "wii|gx2|cafe|fb" /var/log/system.log | tail -50'
ssh wiiu 'sysctl hw.ncpu hw.memsize hw.cpufrequency; nvram boot-args 2>/dev/null'
```
- `hw.ncpu` confirma si Wiintosh usa 1 o 3 núcleos de Espresso.
- `hw.memsize` indica cuánta RAM usa Mac OS X (relevante para reservar zonas de MEM2: ring, log persistente).

---

# PARTE 8 — Análisis de la tercera salida (2026-09-26)

## 8.1 Disco
| Dispositivo | Qué es | Nota |
|---|---|---|
| `disk0s2` | FAT32 953 MB | **Partición BOOT** (OpenBIOS, mkext, loader). No montada: `sudo diskutil mount disk0s2` |
| `disk0s4` | Hackintosh HD, 25 GB, 17 GB libres | Espacio de sobra para Xcode y compilar |
| `disk0s6` | Mac OS X Install DVD, 2.9 GB | Instalador; se puede borrar más adelante |
| `disk1s3` | "Xcode Tools", 963 MB | **Imagen de disco montada** (Apple_Driver_ATAPI = imagen de CD), no el DVD de Tiger |

## 8.2 Xcode ya instalado (según `system.log`)
Entre 19:35 y 21:44 se ejecutó con sudo `installer` de: `XcodeTools.mpkg`, `DevToolsSystem`, `DeveloperToolsCLI`, `DevSDK`, **`MacOSX10.4.Universal.pkg`**, **`gcc4.0.pkg`**, `X11SDK`, `BSDSDK`, `OpenGLSDK`, `DeveloperTools`, `InterfaceBuilderCLI`, y extracción manual con `pax` en `/Developer`.
- La presencia de `MacOSX10.4.Universal.pkg` indica Xcode **2.2 o posterior** (probablemente 2.5). Verificar:
  ```bash
  ssh wiiu 'xcodebuild -version; gcc-4.0 --version; ls /Developer/SDKs; ls /System/Library/Frameworks/Kernel.framework/Headers/IOKit/graphics'
  ```
- Hay también una carpeta `~/darwine/qemu-0.9.0` (y un `X11User.pkg`): restos de otro experimento; no afectan.
- `/etc/sudoers` ya fue modificado (se añadió `/etc/sudoers.d-wiiu`). Revisar con `ssh wiiu 'sudo -l'` que las reglas sean las mínimas (Parte 3: solo el script `wiiu-kext`).

## 8.3 Sistema
- `hw.ncpu: 1` → Wiintosh usa **un solo núcleo** de Espresso (los otros dos, apagados). No hay SMP: todo el trabajo de CPU compite con el WindowServer, lo que hace aún más valiosa la aceleración 2D.
- `hw.memsize: 2147483648` → **2 GB** visibles para Mac OS X.
  - Pero el nodo `memory@0` declara `reg = 0x00000000 / 0x02000000` (32 MB) y `available = {0x4000–0x7FC000, 0x01000000 + 0x01000000}`: OpenBIOS describe solo MEM1; la RAM de MEM2 la añade WiiPE (`"Platform Memory Ranges" = (0, 2^64-1)`).
  - **Consecuencia:** los framebuffers en `0x8F000000`/`0x8FE00000` están **dentro** de los 2 GB que usa el kernel si MEM2 empieza en `0x10000000` (0x10000000 + 2 GB = 0x90000000). Hay que confirmar en `WiiPE` / `WiiPlatform` cómo se reservan esas zonas (y la región "GFX memory" `0x7E000000` que menciona `ofmem.c`) antes de reservar más memoria para anillos o logs. **[NO VERIFICADO — revisar `WiiPlatform/src/PE/WiiPE*.cpp`]**
- `chosen/bootargs` vacío; `bootpath = /sdhc@d070000/disk@0:4,\\:tbxi`.
- `memory-map`: kernel `__TEXT` en `0xE000` (0x353000), `__DATA` `0x361000`, BootArgs `0xA46000`, PRELINK vacío.
- Clases activas relevantes: `WiiCafeFB`=1, `IOFramebufferUserClient`=1, `IODisplayConnect`=1, `AppleDisplay`=1, **`IOAccelerationUserClient`=1 con `IOAccelerator`=0** (el WindowServer abre un cliente de aceleración genérico aunque no haya acelerador; al añadir el nuestro habrá que ver qué pide), `AppleUSBCDC`/`AppleUSBCDCECMControl`/`AppleUSBCDCECMData`=1 (confirma la red CDC‑ECM nativa), `LatteInterruptController`=1, `WiiIPC`=1, `WiiFlipperFB`=0.

## 8.4 Por qué falla `ioreg`
El error aparece siempre al llegar a `options` (`IODTNVRAM`): su serialización falla y `ioreg` aborta. Además `-n gx2` **sin `-r`** imprime el árbol entero. Usar siempre `-r` (solo el subárbol que coincide):
```bash
ssh wiiu 'ioreg -p IODeviceTree -r -n gx2 -l -w0'     # nodo OpenBIOS: reg, interrupts, AAPL,vram-memory
ssh wiiu 'ioreg -r -c WiiCafeFB -l -w0'               # framebuffer y sus hijos (IODisplayConnect, AppleDisplay)
ssh wiiu 'ioreg -r -c IOAccelerationUserClient -l -w0; ioreg -r -c LatteInterruptController -l -w0'
ssh wiiu 'ioreg -p IODeviceTree -r -n "interrupt-controller@0c000000" -l -w0'
```

## 8.5 Datos que aún faltan (pedir al humano)
1. Salida de los `ioreg -r` de 8.4 (sobre todo `reg` e `interrupts` de `gx2`).
2. `xcodebuild -version`, `ls /Developer/SDKs`, cabeceras de `Kernel.framework/.../graphics`.
3. `sudo -l` (reglas sudo actuales).
4. `grep -i -E "WiiCafe|fb:|gx2" /var/log/system.log` tras un arranque con `setenv boot-args "-v -wiifbdbg"`. **Confirmado en `include/WiiCommon.hpp`:** cada clase declara `WiiDeclareLogFunctions("xx")` y se activa con el boot-arg `-wii<xx>dbg` (WiiCafeFB usa `"fb"` → `-wiifbdbg`). Solo en builds con `DEBUG`; en Wii U los mensajes también se envían por IPC a Starbuck (`kWiiFuncIPCCafeLog`). Para el driver nuevo: `WiiDeclareLogFunctions("gx2")` → `-wiigx2dbg`.

---

# PARTE 9 — Cuarta salida (2026-09-26)

## 9.1 `ioreg` de Tiger es limitado
El `ioreg` de 10.4 **no tiene `-r`** y `-c`, `-l`, `-n` son **mutuamente excluyentes** (`usage: ioreg [-b] [-c class | -l | -n name] [-p plane] [-s] [-w width] [-x]`). `-n`/`-c` ya imprimen las propiedades solo de lo que coincide y no fallan en `options` (el fallo venía de `-l`). Comandos correctos:
```bash
ssh wiiu 'ioreg -p IODeviceTree -n gx2 -w0 -x'                 # reg, interrupts, AAPL,vram-memory (hex)
ssh wiiu 'ioreg -c WiiCafeFB -w0 -x'
ssh wiiu 'ioreg -c LatteInterruptController -w0; ioreg -c IOAccelerationUserClient -w0'
ssh wiiu 'ioreg -p IODeviceTree -n interrupt-controller@0c000000 -w0 -x'
```
Si hiciera falta el árbol completo: instalar un `ioreg` más nuevo no es posible; usar `ioreg -w0 | grep -v '^ *|'` (solo la jerarquía, sin propiedades).

## 9.2 Toolchain en la Wii U
- `xcodebuild`: DevToolsCore-798.0 / DevToolsSupport-794.0 → **Xcode 2.4.x** **[versión exacta NO VERIFICADA; 2.5 sería DevToolsCore 9xx]**.
- `gcc-4.0`: `powerpc-apple-darwin8-gcc-4.0.1 (Apple build 5370)` ✅ — basta para kexts (el repo usa gcc 4.2 en CI, pero 4.0.1 compila C++ de kext para 10.4 igual; revisar warnings).
- `/Developer/SDKs` **no existe** → el paquete `MacOSX10.4.Universal.pkg` no terminó de instalarse o se instaló en otra ruta. Para kexts no hace falta SDK: se compila contra el sistema con las cabeceras de `Kernel.framework`. Comprobar:
  ```bash
  ssh wiiu 'ls /System/Library/Frameworks/Kernel.framework/Headers/IOKit/graphics; ls /Developer/Headers 2>/dev/null | head; pkgutil --pkgs 2>/dev/null | grep -i sdk; ls /Library/Receipts | grep -i -E "sdk|gcc|devtools"'
  ```
  Si faltan las cabeceras del kernel, usar `MacPPCKernelSDK` (copiarlo con rsync) — es lo que usa el repo (`INCLUDES := ../MacPPCKernelSDK/Headers`).
- Prueba de compilación del repo sin cambios, en la Wii U:
  ```bash
  rsync -a --bwlimit=200 osx-drivers MacPPCKernelSDK wiiu:~/src/
  ssh wiiu 'cd ~/src/osx-drivers && make OSX_VERSION=tiger DARLING_SHELL= CC=gcc-4.0 CXX=g++-4.0 LD=/usr/bin/ld AS=/usr/bin/as'
  ```
  El Makefile pasa `-mmacosx-version-min=10.2` y `-fapple-kext`; gcc 4.0.1 los admite. Posibles ajustes: `-mlong-branch` y `-force_cpusubtype_ALL` pueden requerir ser pasados al enlazador (`-Wl,`); corregir según errores.

## 9.3 sudo: demasiado permisivo
`sudo -l` → `(ALL) ALL` y **dos veces `(ALL) NOPASSWD: ALL`**. Cualquiera que entre por SSH (incluida la IA) es root sin contraseña.
- Aceptable temporalmente en una máquina de pruebas aislada detrás del Redmi, pero **peligroso**: un comando equivocado puede borrar el sistema o la partición BOOT.
- Recomendado: dejar solo
  ```
  rubano1421 ALL=(ALL) ALL
  rubano1421 ALL=(root) NOPASSWD: /usr/local/sbin/wiiu-kext, /sbin/kextload, /sbin/kextunload, /usr/sbin/kextstat, /usr/sbin/diskutil, /sbin/reboot
  ```
  (editar con `sudo visudo`; quitar las líneas duplicadas `NOPASSWD: ALL`). Y en Claude Code (Mac), exigir confirmación para cualquier `ssh wiiu sudo`.

## 9.4 Datos pendientes
1. Salidas de 9.1.
2. Salida del comando de cabeceras de 9.2.
3. Resultado de la compilación de prueba de 9.2 (primer paso real).

---

# PARTE 10 — Quinta salida: nodo gx2 y WiiCafeFB reales (2026-09-26)

> Solo investigación. No se ha ejecutado nada en la consola más allá de lecturas.

## 10.1 Nodo OpenBIOS `gx2@c200000` (confirmado)
| Propiedad | Valor | Significado |
|---|---|---|
| `reg` | `0c200000 00080000 8f000000 00384000` | MMIO 512 KB + framebuffer TV |
| `IODeviceMemory` | `{0xc200000,0x80000}`, `{0xffffffff8f000000,0x384000}` | índice 0 = registros, índice 1 = VRAM. La dirección `0xffffffff8f000000` es `0x8F000000` con extensión de signo al imprimirse como 64 bits; al usarla como `IOPhysicalAddress` (32 bits) es correcta |
| `AAPL,vram-memory` | `8f000000 00384000` | 0x384000 = 1280×720×4 exactos → **no hay VRAM sobrante** declarada |
| `interrupts` | `2`, padre `interrupt-controller@c000000` | Línea 2 del controlador de Espresso (PI), gestionado por `WiiInterruptController`. Es la vía para las IRQ de GPU (fase 1.4/2) |
| `width/height/depth/linebytes` | `0x500`/`0x2d0`/`0x20`/`0x1400` | 1280×720, 32 bpp, 5120 bytes por línea |
| `AAPL,boot-display` | presente | Es la pantalla de arranque |
| `address` | `8f000000` | usado por BootX para la consola |

Consecuencias:
- **Doble búfer / page flip (fase 1.3)** necesita una segunda superficie: no cabe en `0x384000`. Opciones: reservar otra zona física contigua con `IOBufferMemoryDescriptor` (kIOMemoryPhysicallyContiguous, 3.6 MB — puede fallar tras mucho uptime; hacerlo en `start()`), o ampliar el `reg` en OpenBIOS/wiiu-loader (p. ej. `0x8F000000` + `0x800000`) comprobando que no pisa el D2 en `0x8FE00000`.
- **GamePad (D2)** en `0x8FE00000` no aparece en el árbol: habría que añadirlo como `reg` extra (índice 2) si se retoma.

## 10.2 `WiiCafeFB` en ejecución
- `IOFBConfig.IOFBModes` = un único modo ID 1, `DM` = 1280×720 @ 60 Hz (`0x003c0000`), flags `DF=0x3`.
- `IOFBMemorySize = 0x384000`.
- `IOFBCursorInfo`: cursor hardware 32×32, 32 bpp → el cursor HW funciona.
- `IOFramebufferOpenGLIndex = 0`, `IOFBTransform = 0`.
- Hijos: `display0 (IODisplayConnect)` → `AppleDisplay`; `IOFramebufferUserClient` (el WindowServer).
- **No tiene** `IOCFPlugInTypes` → hoy no hay plugin 2D; en la fase 3 se añadirá esa propiedad (en el Info.plist de la personalidad o con `setProperty` en `start()`).

## 10.3 Corrección sobre `IOAccelerationUserClient`
Está colgado de **`IODisplayWrangler`**, no de un acelerador: es el cliente genérico que usa el WindowServer para pedir IDs de acelerador (`IOAccelerator::createAccelID`). Es normal en cualquier Mac y **no** indica que el WindowServer esté buscando aceleración. (Corrige lo dicho en 8.3.)

## 10.4 Red y USB
- El Redmi aparece como dispositivo USB **"Fedora rescate"** en `usb@d050000` (mismo controlador OHCI que el receptor de teclado/ratón "USB Receiver"), con `AppleUSBCDC` + `AppleUSBCDCECMControl` + `AppleUSBCDCECMData` → `IOEthernetInterface`. Confirmado: driver ECM nativo de Tiger.
- Compartir OHCI (USB 1.1, 12 Mbit/s) con el teclado/ratón: transferencias grandes pueden hacer lenta la entrada; otra razón para `--bwlimit`.
- `usb@d130000` libre (puerto frontal): se podría mover el Redmi ahí para separarlo del teclado **[recomendación, NO VERIFICADO qué puerto físico es]**.

## 10.5 Cabeceras disponibles en la Wii U
`/System/Library/Frameworks/Kernel.framework/Headers/IOKit/graphics/`: `IOAccelClientConnect.h`, `IOAccelSurfaceConnect.h`, `IOAccelTypes.h`, `IOAccelerator.h`, `IODisplay.h`, `IOFramebuffer.h`, `IOFramebufferShared.h`, `IOGraphicsDevice.h`, `IOGraphicsEngine.h`, `IOGraphicsInterfaceTypes.h`, `IOGraphicsTypes.h` → **suficiente para compilar kexts gráficos en la propia Wii U** (mismas que `MacPPCKernelSDK`).
- `IOGraphicsInterface.h` (la del plugin GA, espacio de usuario) no está ahí: buscarla en `/System/Library/Frameworks/IOKit.framework/Headers/graphics/`. Es la fuente buena para el orden exacto de la vtable (duda 7 de la Parte 2):
  ```bash
  ssh wiiu 'ls /System/Library/Frameworks/IOKit.framework/Headers/graphics/; cat /System/Library/Frameworks/IOKit.framework/Headers/graphics/IOGraphicsInterface.h'
  ```
- Receipts instalados: `BSDSDK`, `DevSDK`, `DevToolsSystem`, `OpenGLSDK`, `X11SDK`, `gcc3.3`, `gcc4.0`. **No** está `MacOSX10.4.Universal` (por eso no hay `/Developer/SDKs`); no hace falta para kexts ni para el plugin (se compila contra el sistema).

## 10.6 Estado de las dudas abiertas
| Duda | Estado |
|---|---|
| Registros MMIO y VRAM del nodo | ✅ Resuelta (10.1) |
| Línea de interrupción | ✅ Línea 2 del PI de Espresso |
| Toolchain en la Wii U | ✅ gcc 4.0.1 + cabeceras de Kernel.framework |
| Orden de la vtable GA | ⏳ leer `IOGraphicsInterface.h` de IOKit.framework en la Wii U |
| CP vivo / microcódigo | ⏳ sonda fase 0 |
| Memoria libre para anillo/2º búfer | ⏳ revisar `WiiPE` y probar `IOBufferMemoryDescriptor` contiguo |
| Vanilla con Wiintosh | ⏳ prueba en consola |

---

# PARTE 11 — Investigación con los datos reales

## 11.1 Memoria y framebuffer
- OpenBIOS (`arch/ppc/wii/ofmem.c`) declara como RAM **solo MEM1** (`ramsize = 0x02000000`) y mapea todo lo que está **≥ `0x8F000000` (`CAFE_GFX_BASE`) como I/O sin caché** (`WIm`, modo `0x6a`). Por eso `memory@0` dice 32 MB.
- Los 2 GB (`hw.memsize = 0x80000000`) los añade el lado Mac OS X (WiiPE / parches de BootX‑XNU). **[NO VERIFICADO dónde exactamente]**: 2 GB exactos no cuadra con MEM1 (32 MB) + MEM2 hasta `0x8F000000` (0x7F000000 = 2 GB − 16 MB). Comprobar antes de reservar memoria física fija:
  ```bash
  ssh wiiu 'sysctl hw.physmem hw.usermem hw.memsize'
  # y en el fork: grep -rn "0x8F000000\|mem2\|memsize\|max_mem" WiiPlatform/ ../openbios/arch/ppc/wii/
  ```
- Regla práctica: **no usar direcciones físicas fijas** fuera de `0x8F000000–0x8FFFFFFF`. Para anillos, IB, shaders, write‑back y un 2º framebuffer, usar `IOBufferMemoryDescriptor` con `kIOMemoryPhysicallyContiguous` (el kernel garantiza que es suya). La zona `0x8F384000–0x8FDFFFFF` (entre el framebuffer TV y el D2) está fuera de la RAM que ve OpenBIOS y probablemente libre → candidata para un 2º framebuffer (10.9 MB) **[NO VERIFICADO que el kernel no la use; confirmar con 11.1]**.
- Hay un `IORangeAllocator` de MEM2 en `WiiPE` (`kWiiFuncPlatformGetMem2Allocator`), pero **solo se crea en Wii** (necesita la propiedad `mem2-addresses`); en Wii U devuelve NULL. Se podría ampliar para Wii U y repartir la zona de 11.1 de forma ordenada (framebuffers, anillo, log persistente).

## 11.2 Interrupción de la GPU
- `gx2` → `interrupts = 2`, padre `interrupt-controller@c000000` = **WiiInterruptController** (Processor Interface de Espresso, `0x0C003000`, 32 vectores).
- En `include/WiiProcessorInterface.hpp` el vector 2 se llama `kWiiPIVectorDVD` (numeración heredada de Wii/GameCube). En Wii U los vectores del PI se reinterpretan (Cafe usa registros por núcleo en `0x78`/`0x7C`) y OpenBIOS asigna el 2 a la GPU **[NO VERIFICADO contra WiiUBrew "Hardware/Processor Interface"; confirmar que el bit 2 de `kWiiPIRegCafeInterruptCauseBase` se activa con una interrupción de GPU]**.
- Prueba segura (fase 1.4): habilitar solo `DxMODE_INT_MASK` bit 0 (vblank D1) durante 1 s, registrar el handler con `provider->registerInterrupt(0, ...)`, contar llamadas (esperado ≈ 60) y deshabilitar. Si no llega nada, la interrupción pasa por el anillo IH y hay que esperar a la fase 2.

## 11.3 Modo y cursor
- Único modo: ID 1, 1280×720 @ 60 Hz, `DF=0x3` (válido + seguro). Añadir modos = solo cambiar el viewport/escalador (DCE) sin tocar el timing HDMI.
- `IOFBCursorInfo` 32×32×32 bpp confirmado.

## 11.4 USB
- El Redmi ("Fedora rescate") y el receptor teclado/ratón comparten `usb@d050000` (OHCI 12 Mbit/s). Mantener `rsync --bwlimit=200` y transferir en momentos sin uso interactivo.

---

# PARTE 12 — Proyectos similares y qué aprovechar

## 12.1 NetBSD en Wii U (⭐ el más útil) — licencia BSD‑2
Jared McNeill añadió soporte Wii U a NetBSD‑current en **enero de 2026** (anuncio: `mail-index.netbsd.org/port-powerpc/2026/01/10/msg003726.html`). Código en `github.com/NetBSD/src`, `sys/arch/evbppc/nintendo/` y `sys/arch/evbppc/include/wiiu.h`. **SMP funciona** en los 3 núcleos. Arranca con el mismo linux-loader que Wiintosh.

Archivos clave:
| Archivo | Qué enseña |
|---|---|
| `dev/wiiufb.c` (637 líneas) | Framebuffer TV **o** GamePad (`video=drc`), cursor HW de **64×64** ARGB premultiplicado, bloqueo de actualización del cursor, `REG_OFFSET(d, r) = d*0x800 + r` (**confirma** que D2 = D1 + 0x800, incl. cursor `0x6C00`) |
| `include/wiiu.h` | Constantes de plataforma (abajo) |
| `pic_pi.c` | Controlador PI por núcleo (`INTSR(n)=0x0C000078+n*8`, `INTMSK(n)=0x0C00007C+n*8`) |
| `cpu.c`, `ipi_latte.c` | Arranque de los núcleos 1 y 2 (vector de arranque, `SCR` wake bits, `HID4/HID5`), IPIs por buzón |
| `dev/ehci_ahb.c` | USB 2.0 (Wiintosh solo tiene OHCI 1.1) |
| `machdep.c` | Protocolo de linux-loader (argv, apagado/reinicio por IPC) |

Constantes confirmadas (`wiiu.h`):
```c
WIIU_MEM1_BASE 0x00000000  WIIU_MEM1_SIZE 0x02000000   /* 32 MB */
WIIU_MEM0_BASE 0x08000000  WIIU_MEM0_SIZE 0x00300000   /* 3 MB  */
WIIU_MEM2_BASE 0x10000000  WIIU_MEM2_SIZE 0x80000000   /* 2 GB → hasta 0x8FFFFFFF */
WIIU_GX2_BASE  0x0c200000  WIIU_GX2_SIZE 0x80000
WIIU_PI_IRQ_MB_CPU(n) (20 + n)      /* IPIs */
WIIU_PI_IRQ_GPU7      23            /* ¡IRQ de la GPU en el PI! */
LT_GPUINDADDR (Hollywood priv + 0x620), LT_GPUINDDATA (+0x624)  /* acceso indirecto a registros GPU */
LT_GPUINDADDR_REGSPACE_GPU (3u << 30)
WIIU_BOOT_VECTOR 0x08100100
CPU = 248.625 MHz × 5 = 1.243 GHz; timebase = bus/4
```
NetBSD pone sus framebuffers en `0x17500000`/`0x178C0000` (los reubica); Wiintosh usa `0x8F000000`/`0x8FE00000` (último 16 MB de MEM2).

### ⚠️ Hallazgo importante: número de IRQ de la GPU
- OpenBIOS de Wiintosh (`arch/ppc/wii/wii.fs`) declara `gx2` con **`interrupts = 2`** en el PI.
- NetBSD usa **`WIIU_PI_IRQ_GPU7 = 23`** en el mismo PI.
- `WiiInterruptController` (Wiintosh) usa el número de vector directamente como bit del registro de causa del PI de Cafe (`readCafeIntCause32(0)`), así que el vector 2 **no** sería la GPU. El valor 2 probablemente es un marcador sin uso (WiiCafeFB nunca registra interrupciones).
- **Acción:** para la fase 1.4 usar el vector **23** (cambiar el nodo en OpenBIOS o, sin tocar OpenBIOS, leer la causa del PI directamente en la sonda). Verificar en la sonda: con vblank D1 habilitado en `DxMODE_INT_MASK`, el bit 23 de `0x0C000078` debería activarse.
- Esto sustituye lo dicho en 11.2.

### Otras oportunidades gracias a NetBSD (fuera del alcance de la GPU, pero muy beneficiosas)
- **SMP en Wiintosh:** Tiger soporta multiprocesador en PPC. Portar el arranque de núcleos de `cpu.c` (trampolín en `0x08100100`, bits `SCR`) + IPIs (`ipi_latte.c`, IRQ 20–22) a `WiiCPU`/`WiiPE` daría **3 núcleos** en vez de 1 (`hw.ncpu: 1` hoy). Esto aceleraría todo el escritorio (Quartz es software). Proyecto grande; proponerlo a upstream.
- **EHCI:** USB 2.0 para la red del Redmi (480 Mbit/s en vez de 12) y el disco.
- **Cursor 64×64:** Wiintosh usa 32×32; el hardware admite 64×64.

## 12.2 linux-wiiu / linux-loader (GPL‑2)
- `gitlab.com/linux-wiiu/linux-wiiu`: framebuffer TV + GamePad, sin aceleración (issue "Support GX2 card+acceleration" #19 sin resolver: el driver `radeon` depende de PCI).
- linux-loader (base de `Wiintosh/wiiu-loader`): configuración de D1/D2, crossbar según `LT_GPU_ENDIANNESS`, protocolo IPC `CMD_POWEROFF 0xcafe0001`, `CMD_REBOOT 0xcafe0002`.

## 12.3 wiiMac (Bryan Keller) — Mac OS X 10.0 en Wii
- `github.com/bryankeller/wiiMac`, blog `bryankeller.github.io/2026/04/08/porting-mac-os-x-nintendo-wii.html`.
- Framebuffer con doble búfer RGB→YUV (el VI de la Wii solo muestra YUV). Útil como otro ejemplo de IOFramebuffer para hardware Nintendo; no aplica a la GPU R7xx.

## 12.4 RadeonHD.kext (osx86, Dong Luo) — referencia de IOKit + R6xx/R7xx
- `github.com/AustinSMU/osx86-driver-radeonhd`: `IOFramebuffer` para Radeon HD 2xxx–4xxx portado de `xf86-video-radeonhd`. **Sin aceleración 2D/3D**. Útil como ejemplo de código de pantalla R7xx dentro de un kext de Mac OS X (modos, escalador, cursor). Hubo intentos en PowerPC Leopard (hilo de MacRumors).

## 12.5 VMsvga2 (Zenith432) y VMQemuVGA — aceleración en Mac OS X por terceros
- VMsvga2 (SourceForge `vmsvga2`): kext framebuffer + **plugin GA (blits 2D para mover ventanas)** + superficies `IOAccelSurface`; **nunca** implementó GLD (OpenGL). Es el mejor ejemplo existente de plugin GA y de clases `IOAccelerator` de un tercero (10.5/10.6 x86; adaptar a 10.4 PPC).
- VMQemuVGA (`github.com/startergo/VMQemuVGA`): en agosto de 2026 logró despachar las 2 primeras funciones GL de un GLD → confirma lo difícil que es la fase 4.

## 12.6 Código de GPU R600/R700 reutilizable
| Proyecto | Licencia | Para qué |
|---|---|---|
| Linux `drivers/gpu/drm/radeon` (`r600.c`, `rv770.c`, `r600d.h`, `r600_cs.c`) | GPL‑2 | Secuencias de CP, IH, fences; **no copiar código tal cual** al kext (licencia distinta a la de Wiintosh, que es BSD‑3); usar como referencia de registros |
| `xf86-video-ati` (`r6xx_accel.c`, `r600_exa.c`, `r600_shader.c`) | MIT | **Sí se puede portar**: relleno/copia 2D con shaders ya ensamblados y soporte big‑endian |
| Mesa `r600` (classic 7.x / gallium) | MIT | Fase 4 y compilador de shaders R700 |
| decaf-emu (`libgpu/latte`) | GPL‑3 | Registros y PM4 tal como los usa la Wii U; desensamblador PM4 para depurar |
| Cemu | MPL‑2.0 | Comportamiento de Latte (tiling, formatos) |
| AMD docs abiertas (R6xx/R7xx 3D, R700 ISA, RV630/RV770 display) | Documentación | Fuente primaria |
| NetBSD `wiiufb.c` | BSD‑2 | Compatible con Wiintosh: se puede copiar código |

**Licencias:** `Wiintosh/osx-drivers` es BSD‑3 (ver `LICENSE`). Se puede incorporar código MIT/BSD (xf86-video-ati, NetBSD, Mesa) con su aviso; el código GPL (Linux, decaf) solo como referencia.

## 12.7 Vanilla (GamePad por software)
Ver Parte 4. GPL‑2. Pixel 6a compatible sin parches.

---

# PARTE 13 — Correcciones y fe de erratas (leer antes de actuar)

Las partes se escribieron en orden cronológico; donde se contradigan, **vale lo más reciente**:
1. **Dónde corre Claude:** en el **MacBook** (Parte 5), no en el Redmi (partes 1–3 lo suponían). El Redmi es solo el puente SSH.
2. **SSH a Tiger:** con `ProxyJump` desde el Mac **no** hace falta `update-crypto-policies` en Fedora (5.2).
3. **Pixel 6a:** conectado por **USB al Mac** (Parte 6); Bluetooth PAN y túneles de la Parte 4.4 / 5.4 ya no son necesarios.
4. **Sistema:** Mac OS X **10.4.11** (no 10.4.0). Toolchain: gcc 4.0.1 en la Wii U (Parte 9–10). No hay `/Developer/SDKs`; se compila contra `Kernel.framework`.
5. **IRQ de la GPU:** vector **23** del PI (NetBSD), no el 2 que declara OpenBIOS (Parte 12.1). Sustituye 11.2 y 2.3/4.4 donde digan "línea 2".
6. **D2 / cursor D2:** los offsets `+0x800` están **confirmados** por NetBSD (`wiiufb.c`), ya no son "NO VERIFICADO".
7. **Cursor:** el hardware admite **64×64** (NetBSD); Wiintosh usa 32×32.
8. **`IOAccelerationUserClient`:** es el cliente genérico de `IODisplayWrangler`, no indica aceleración (Parte 10.3).
9. **`ioreg` de Tiger:** sin `-r`; `-c/-l/-n` excluyentes; usar `ioreg -p IODeviceTree -n gx2 -w0 -x` y `ioreg -c Clase -w0 -x` (Parte 9.1).
10. **Boot-arg de depuración:** `-wii<prefijo>dbg`, p. ej. `-wiifbdbg` (Parte 8.5).
11. **GamePad:** no hay GamePad físico; Vanilla probablemente solo funciona en Aroma (Parte 4.2). La "2ª pantalla GamePad" queda aparcada.
12. **Memoria:** MEM2 = `0x10000000–0x8FFFFFFF` (2 GB, NetBSD). Los framebuffers de Wiintosh están en los últimos 16 MB de MEM2; OpenBIOS mapea ≥`0x8F000000` como I/O. Reservar memoria nueva siempre con `IOBufferMemoryDescriptor` (Parte 11.1).

---

# PARTE 14 — Resultados en hardware (2026-09-26/27) y pregunta abierta

> Escrita por Claude Code en el MacBook tras una sesión de pruebas en la Wii U real.
> Todo lo de esta parte está **medido en la consola**, salvo lo marcado [NO VERIFICADO].
> Prevalece sobre las Partes 1–13 donde se contradigan.

## 14.1 Estado: qué funciona ya

Código en el fork local `osx-drivers`, rama `gpu` (sin subir a GitHub). Bitácora completa en `docs/BITACORA.md`.

| Pieza | Estado |
|---|---|
| Compilar en la propia Wii U (gcc-4.0.1, sin Darling) | ✅ Reproduce la release 0.5.2 con los mismos símbolos exportados. `make OSX_VERSION=tiger DARLING_SHELL= CC=gcc-4.0 CXX=g++-4.0 LD=/usr/bin/ld AS=/usr/bin/as`. **Siempre `make clean`**: el reloj de la Wii U va ~2 h adelantado y make no ve los cambios que llegan por rsync. |
| Fase 0: sonda `WiiGX2Probe.kext` (carga en caliente) | ✅ Niveles 0–5, de solo lectura a pruebas de DMA |
| Fase 2: anillo PM4, fence, IB | ✅ |
| Motor 2D `WiiGX2Accel.kext` + user client + `gx2ctl` | ✅ Copia y relleno de rectángulos del framebuffer por CP_DMA, estable, 0 timeouts |
| Fase 3: `WiiGX2GA.plugin` (plugin GA) | ✅ Lo carga `IOPSAllocateBlitEngine` (calidad 1000) y **el WindowServer lo arranca**, pero apenas lo usa (ver 14.4) |

## 14.2 Datos del hardware confirmados

- **Memoria:** OpenBIOS (`arch/ppc/wii/macosx/macosx.c`) le pasa a XNU `PhysicalDRAM = MEM1 0x0–0x01FFFFFF + MEM2 0x10000000–0x8DFFFFFF` = los 2 GB de `hw.physmem`. **`0x8E000000–0x8FFFFFFF` queda fuera del kernel**: ahí están el fb de la TV (`0x8F000000`, 0x384000) y el del GamePad (`0x8FE00000`). Resuelve la duda de 8.3/11.1.
- **Estado que deja Cafe OS:** CP **parado** (`CP_ME_CNTL = 0x14000000`, ME_HALT|PFP_HALT). IH, DMA y RLC **apagados** (`IH_RB_CNTL` bit0 = 0, `DMA_RB_CNTL = 0`, `RLC_CNTL = 0`): la GPU no escribe en la RAM de Mac OS X. Los registros 3D de Cafe OS siguen puestos (`SQ_CONFIG = 0xE4000007`, `GB_TILING_CONFIG = 0x00044902`, `CC_GC_SHADER_PIPE_CONFIG = 0xFFFCF000` → probablemente 2 SIMD activos, como RV710).
- **Microcódigo:** sigue cargado. Tamaños exactos de RV710 (ME 1360, PFP 848 palabras) y las 11 primeras palabras idénticas a `RV710_me.bin`, **pero es un firmware propio de Nintendo**: difieren 1053/1360 palabras del ME y 625/848 del PFP. Cargar el RV710 de linux-firmware (camino 3 del plan) queda desaconsejado. Hay un volcado privado en el Mac (no va al repo).
- **Anillo:** la secuencia de Linux `r600_cp_resume` + `r600_cp_start` funciona sobre el microcódigo presente: `SOFT_RESET_CP` (no borra el microcódigo), `CP_RB_CNTL = bufsz | blksz<<8 | BUF_SWAP_32BIT | RB_NO_UPDATE`, `ME_INITIALIZE(1, 0, max_hw_contexts-1 = 3, DEVICE_ID(1))`, `CP_ME_CNTL = 0xFF`. `SET_CONFIG_REG(SCRATCH_REG0)` responde en < 10 µs.
- **Fence:** `EVENT_WRITE_EOP(CACHE_FLUSH_AND_INV_EVENT_TS, EVENT_INDEX 5)` con la dirección `| 2` (ENDIAN_SWAP 8IN32) y DATA_SEL(1) escribe el valor de 32 bits con el orden correcto para la CPU big-endian.
- **IB:** funciona con el opcode `0x32` (INDIRECT_BUFFER_PRIV, el que usa Linux) y la dirección `| 2` (swap). ⚠️ **Errata de la Parte 2 §6.3:** `0x3F` es el INDIRECT_BUFFER normal (no se ha probado); el verificado es `0x32`.
- **CP_DMA (0x41):** copia memoria→memoria sin shaders (un paquete por fila, `CP_SYNC` en el último, `WAIT_UNTIL(WAIT_CP_DMA_IDLE)` + fence). No sirve para rellenar directamente (SAIC solo vale para registros); el relleno se hace copiando una fila del color desde RAM.
  - ⚠️ Tras usar CP_DMA, `GRBM_STATUS` se queda en `0xA0003028` y `CP_STAT = 0x80100042` (ocupado) aunque los fences llegan. Parar con halt + `SOFT_RESET_CP` lo deja limpio. [causa NO VERIFICADA]
- **IRQ de la GPU:** no probada. Con `DxMODE_INT_MASK = 0`, el bit 23 del PI no está pendiente (coherente con NetBSD).
- **User client en 10.4 (xnu-792):** `is_io_connect_method_structureI_structureO` **omite el lado vacío**: con `count0 == 0` llama `(output, outputCount)` y con `count1 == 0` llama `(input, inputCount)`. Costó un kernel panic. Tiger además no exporta `__udivdi3` (nada de divisiones de 64 bits en kexts) y `clock_get_uptime` usa `AbsoluteTime`.

## 14.3 Medidas (512×256 píxeles, 32 bpp, media de 10)

| Operación | GPU (CP_DMA) | CPU sobre el fb |
|---|---|---|
| Leer 256 KB del fb a RAM | 142 µs (~1,8 GB/s) | 14,3–14,7 ms (**~18 MB/s**) |
| Copia pantalla→pantalla 512 KB | 512 µs | 14 404 µs (**×28**) |
| Relleno 512 KB | 571 µs | 1 650 µs (×2,9; escribir por CPU va a ~317 MB/s) |

**Leer el framebuffer con la CPU es lentísimo; escribirlo no tanto.**

## 14.4 El problema: el WindowServer carga el plugin pero casi no lo usa

- Tras `killall loginwindow`, syslog: `WindowServer[510]: WiiGX2GA: started`. Contador de envíos a la GPU: 13 → 16 al iniciar la sesión.
- El humano arrastró ventanas e hizo scroll un rato: **ningún glitch, ninguna mejora notable, y el contador se quedó en 16**. Quartz no pidió ni una copia al mover ventanas ni al hacer scroll.
- Hipótesis: Tiger sin Quartz Extreme compone en software desde las copias de las ventanas en RAM y escribe en el fb con la CPU; el blitter GA de copia pantalla→pantalla casi no se usa. Si además lee el destino (sombras, transparencias, menús translúcidos), cada lectura del fb sin caché cuesta a ~18 MB/s.

## 14.5 Las tres opciones (a investigar cuál es mejor)

1. **Instrumentar el plugin:** registrar cada llamada (qué `GetBlitter` pide Quartz, cuántos `Synchronize`/`WaitComplete`/`Flush`, con qué rectángulos). Barato; hace falta cerrar sesión y que el humano use el escritorio.
2. **Blitter memoria→pantalla (`kIOBlitSourceMemory`)**, opcional en IOGraphicsLib y hoy rechazado. Si Quartz lo usara para subir las ventanas, la GPU podría hacer esa subida. Complicación: la fuente es memoria del WindowServer → el kernel tendría que fijarla y traducirla a físicas (`IOMemoryDescriptor::withAddress(task)` + `prepare()`), con coste por llamada.
3. **Mapear el framebuffer con caché write-through** para la CPU: las lecturas irían a caché y las escrituras seguirían llegando a la memoria que lee la pantalla. Es la que más promete, porque no depende de que Quartz llame a la GPU. Datos ya localizados:
   - OpenBIOS `arch/ppc/wii/ofmem.c`, `ofmem_arch_default_translation_mode()`: en Cafe, todo lo ≥ `CAFE_GFX_BASE` (`0x8F000000`) va con modo `0x6a` = WIMG con **I** (sin caché) y **G** (guardado).
   - OpenBIOS `arch/ppc/wii/macosx/xnu.c`, `xnu_patch_io_bats()`: parchea en XNU el **BAT de vídeo** (DBAT3) y evita que se llene DBAT2. Hay que averiguar qué WIMG pone XNU en ese BAT para el fb.
   - IOGraphics de la época de Tiger (`IOFramebuffer.cpp`): en PPC, `kIOFBMapCacheMode = kIOMapInhibitCache` (en i386, `kIOMapWriteCombineCache`), y así mapea `vramMap` (el de la consola). [NO VERIFICADO cómo se mapea el fb que recibe el WindowServer por `IOFramebufferUserClient::clientMemoryForType`]
   - Riesgos a estudiar: (a) si la GPU escribe en el fb (CP_DMA del plugin), la CPU vería líneas de caché obsoletas → invalidar (`dcbi`/`dcbf`) los rangos tocados tras cada blit, o no mezclar las dos cosas; (b) ¿Espresso (750CL modificado) implementa bien write-through (W=1, I=0) en MEM2 con el MEM de Latte? (c) ¿el BAT de XNU manda sobre el mapeo de páginas del WindowServer?; (d) cualquier otro que lea el fb (cursor HW, captura de pantalla).

## 14.6 Preguntas para investigar (en este orden)

1. En Tiger 10.4.11 PPC **sin QE**, ¿qué operaciones del GA usa CoreGraphics/WindowServer de verdad? ¿Mueve ventanas y hace scroll con `IOFBBlitVRAMCopy` o lo compone todo en software? ¿Usa `kIOBlitSourceMemory`? (Pistas: el `ATIRage128GA.plugin` de Tiger exporta `_SetSurface`, `_LockSurface`, `_UnlockSurface`, `_SwapSurface` y `_SetDestination`, lo que sugiere superficies aparte de los blitters. VMsvga2 documentó qué pedía el WindowServer en 10.5/10.6.)
2. ¿Qué WIMG usa XNU 8.11 PPC para el BAT de vídeo y para el mapeo del fb del WindowServer? ¿Dónde se cambia (parche de OpenBIOS, `WiiCafeFB::getApertureRange` o las opciones de `clientMemoryForType`)?
3. ¿Hay precedente de framebuffer write-through en Mac OS X PPC (BootX, Classic, Mac mini G4…) y cuánto mejora?
4. ¿Vale la pena doble búfer en RAM con caché + volcado por CP_DMA al fb en cada vblank? (El CP_DMA de 3,6 MB tardaría ~2 ms según lo medido.)

## 14.7 Cómo probar sin riesgo (ya montado)

- Los kexts de prueba se cargan en caliente desde `/tmp` (`sudo kextload`), no van en el mkext. Al reiniciar no se cargan y Tiger vuelve a su estado normal.
- `WiiGX2Accel.kext` publica `IOCFPlugInTypes` en `WiiCafeFB` solo mientras está cargado. El plugin está en `/System/Library/Extensions/WiiGX2GA.plugin`, y su `Probe` falla si el kext no está.
- Pruebas sin WindowServer: `gatest` (carga el bundle con CFPlugIn) e `iopstest` (pasa por `IOPSAllocateBlitEngine`). `gx2ctl info` muestra el contador de envíos a la GPU.
- Ver la pantalla desde el Mac: `ssh wiiu 'screencapture -x /tmp/c.png'` + scp.
- Cualquier cambio en OpenBIOS o en el mapeo del fb implica reiniciar y que el humano relance Wiintosh desde Aroma.

---

# PARTE 15 — Respuestas a 14.6 con el código fuente de Tiger, y por dónde seguir

> Fuentes leídas (código de Apple publicado en `github.com/apple-oss-distributions`):
> **xnu-792.24.17** (el kernel exacto de la Wii U: Darwin 8.11.0), **IOGraphics-193.2.2**
> (familia gráfica de 10.4.x) e **IOKitUser-277.8** (`graphics.subproj`: IOGraphicsLib e
> IOAccelSurfaceControl). Números de línea de esas versiones. Lo que sigue es análisis de
> código; lo marcado [NO VERIFICADO] debe confirmarse en la consola.

## 15.1 Respuesta a 14.6.1: por qué el WindowServer no usa el plugin — **faltan las superficies CGS**

`IOPSAllocateBlitEngine` (IOKitUser-277.8, `IOGraphicsLib.c` ~3477–3540) pide **cinco** blitters, no tres:

| Puntero | `GetBlitter(type, source)` | Obligatorio | Para qué |
|---|---|---|---|
| `copyProc` | `CopyRects \| CopyOperation`, `SourceDefault` | sí (si falla, no hay motor) | `IOFBBlitVRAMCopy` / `IOPSBlitCopy` (pantalla→pantalla) |
| `fillProc` | `Rects \| CopyOperation`, `SourceSolid` | sí | `IOPSBlitFill` / `IOPSBlitInvert` |
| **`copyRegionProc`** | **`CopyRegion \| OperationType0`, `SourceFramebuffer`** | no (si falla → 0) | **`IOFBBlitSurfaceCopy`** y **`IOFBBlitSurfaceSurfaceCopy`** |
| `memCopyProc` | `CopyRects \| CopyOperation`, `SourceMemory` | no | `IOFBMemoryCopy` (fuente = memoria del framebuffer por `byteOffset`, `memory.ref = 0`) |

Las funciones clave son:
- `IOFBBlitSurfaceSurfaceCopy(blitterRef, options, sourceSurfaceID, destSurfaceID, region, x, y)`: **copia una superficie CGS a otra o al framebuffer** (`destSurfaceID == 0` → `SetDestination(kIOBlitFramebufferDestination)`), con `kIOBlitTypeCopyRegion` y fuente `kIOBlitSourceCGSSurface` (el `sourceSurfaceID` va en el último parámetro del blitter).
- `IOFBBlitSurfaceCopy(...)`: framebuffer → superficie (`AllocateSurface(kIOBlitHasCGSSurface, &dest, surfaceID)` + `SetDestination(kIOBlitSurfaceDestination)`).

Una "superficie CGS" es el **backing store de una ventana alojado en memoria del acelerador**, creado por el WindowServer con `IOAccelCreateSurface()` (IOKitUser `IOAccelSurfaceControl.c`): abre el **acelerador** (`IOServiceOpen(accelerator, kIOAccelSurfaceClientType)`), le da el id de ventana y profundidad (`kIOAccelSurfaceSetIDMode`), su forma (`kIOAccelSurfaceSetShape…`) y la bloquea para escribir (`kIOAccelSurfaceWriteLock` → `IOAccelSurfaceInformation` con `address[]`, `rowBytes`, …: **la CPU dibuja directamente en ella**). Luego el flush de la ventana a pantalla es un blit región→framebuffer que hace la GPU.

**Cómo encuentra el acelerador** (`IOAccelFindAccelerator`, `IOAccelSurfaceControl.c` 30–97): lee la propiedad **`IOAccelTypes`** del framebuffer (una **ruta del registro** en texto), obtiene ese servicio con `IORegistryEntryFromPath`, exige que sea subclase de **`IOAccelerator`** y lee **`IOAccelIndex`** (índice de framebuffer). En la Wii U, `ioreg` mostró `IOAccelerator = 0` y `WiiCafeFB` sin `IOAccelTypes` → **el WindowServer ni siquiera intenta superficies** y compone todo en RAM con la CPU, escribiendo al fb. Eso explica 14.4: el contador no se movió porque solo se pediría `copyProc` en casos raros.

Esto cuadra con la pista del `ATIRage128GA.plugin` (Rage 128 no tiene QE, pero exporta `SetSurface/LockSurface/UnlockSurface/SwapSurface`): **en 10.2–10.4 sin QE, la aceleración 2D real es "ventanas en superficies del acelerador + blits región a pantalla"**, no el blitter pantalla→pantalla.

**Conclusión:** la opción que falta en 14.5 es la **4ª y la buena**:
> **Opción 4 — Implementar `IOAccelerator` + `IOAccelSurface` (kernel) + `copyRegionProc` y `AllocateSurface/FreeSurface/SetDestination` en el plugin.**

Encaja especialmente bien en la Wii U porque la "VRAM" es la misma DDR3 (MEM2): una superficie puede ser simplemente memoria del kernel física/contigua (o por páginas) que la CPU del WindowServer mapea **con caché** y la GPU copia al fb por CP_DMA. Ganancias esperadas: la composición deja de leer el fb sin caché (18 MB/s) y el volcado de cada ventana a pantalla lo hace la GPU (×28 medido).

### Qué implementar (esquema, verificar contra las cabeceras de la Wii U)
Kernel (`WiiGX2Accel.kext`):
1. Clase `WiiGX2Accelerator : IOAccelerator` (cabecera `IOAccelerator.h` está en Kernel.framework de la Wii U).
2. En `WiiCafeFB` (o desde el accelerator al arrancar): `setProperty(kIOAccelTypesKey /*"IOAccelTypes"*/, <ruta IOService del accelerator>)` y `setProperty(kIOAccelIndexKey /*"IOAccelIndex"*/, 0)`. Obtener la ruta con `getPath(buf, &len, gIOServicePlane)`.
3. `newUserClient(type == kIOAccelSurfaceClientType /*0*/)` → `WiiGX2SurfaceClient : IOUserClient` con los métodos de `enum eIOAccelSurfaceMethods` (`IOAccelSurfaceConnect.h`), **en ese orden exacto**:
   `ReadLockOptions, ReadUnlockOptions, GetState, WriteLockOptions, WriteUnlockOptions, Read, SetShapeBacking, SetIDMode, SetScale, SetShape, Flush, QueryLock, ReadLock, ReadUnlock, WriteLock, WriteUnlock, Control, SetShapeBackingAndLength` (confirmar índices y tipos scalar/struct en `IOAccelSurfaceControl.c`, que es quien los llama; recordar la particularidad de `structureI_structureO` en xnu-792 de 14.2).
   - `SetIDMode(wid, modebits)`: guardar id de ventana y formato (`kIOAccelSurfaceModeColorDepth8888`/`1555`).
   - `SetShape(options, IOAccelDeviceRegion)`: tamaño y posición en pantalla; (re)asignar la memoria de la superficie (`IOBufferMemoryDescriptor`, fila alineada a 32 bytes).
   - `WriteLock/ReadLock`: mapear la superficie en la tarea (`createMappingInTask`) y devolver `IOAccelSurfaceInformation` (`address[0]`, `rowBytes`, `width`, `height`, `pixelFormat`).
   - `WriteUnlock`: **hacer `dcbst` (flush) del rango escrito** para que la GPU lea datos correctos (la superficie está mapeada copyback en el WindowServer).
   - `Flush(options, framebufferMask)` (`IOAccelFlushSurfaceOnFramebuffers`): copiar la región visible de la superficie al fb con CP_DMA (+ fence). Es el camino de "flush de ventana".
   - `GetState`: `kIOAccelSurfaceStateIdleBit` cuando no hay DMA pendiente.
4. Mantener una tabla `surfaceID → superficie` para que el plugin (`AllocateSurface(kIOBlitHasCGSSurface, …, surfaceID)`) y `copyRegionProc` con `kIOBlitSourceCGSSurface` resuelvan la superficie origen.

Plugin (`WiiGX2GA.plugin`):
- `GetBlitter(CopyRegion|OperationType0, SourceFramebuffer)` y aceptar `SourceCGSSurface` en la llamada (`source` = surfaceID).
- `AllocateSurface/FreeSurface/SetDestination(kIOBlitSurfaceDestination)` que llamen al kext.
- `IOBlitCopyRegion`: `region` (lista de rectángulos `IOAccelBounds`, int16) + `deltaX/deltaY`.

Precedentes/ayuda: **VMsvga2** implementó exactamente estas clases (`VMsvga2Accel`, `VMsvga2Surface`, cliente de superficies y GA) en 10.5/10.6 x86; la interfaz de 10.4 es la misma familia de cabeceras (comparar selectores). Es la mejor referencia de código para esta fase.

Riesgos: el WindowServer confiará en las superficies para **todas** las ventanas → un fallo = escritorio roto (probar con `killall loginwindow`; tener SSH para descargar el kext). Memoria: cada ventana a 32 bpp; limitar el total y devolver error (el WindowServer vuelve a RAM normal si `IOAccelCreateSurface` falla) [NO VERIFICADO el fallback, comprobar].

## 15.2 Respuesta a 14.6.2: WIMG del fb — confirmado en el código

1. **Kernel/consola:** `IOFramebuffer.cpp` (IOGraphics-193.2.2, l.63–65 y 3924): `vramMap = fbRange->map(kIOFBMapCacheMode)`, con `kIOFBMapCacheMode = kIOMapInhibitCache` en PPC.
2. **BAT de vídeo:** `osfmk/ppc/bat_init.c` (`PEMapSegment`): BAT de 256 MB con `wimg = PTE_WIMG_IO` (I+G), **`vs = 1, vp = 0` → solo modo supervisor**. **No afecta al WindowServer** (modo usuario usa la tabla de páginas). Responde 14.5‑3(c): el BAT no manda sobre el mapeo del WindowServer.
3. **WindowServer:** `IOFramebufferUserClient::clientMemoryForType` (l.102–140) devuelve `userAccessRanges[type]` (= `getApertureRange(kIOFBSystemAperture)`) y **no toca `*flags`**. `IOUserClient::mapClientMemory` (xnu, `IOUserClient.cpp` ~905) usa las opciones de usuario (`mapFlags & kIOMapUserOptionsMask`) → el WindowServer mapea con `kIOMapDefaultCache`.
4. **Default cache en PPC:** `IODefaultCacheBits(pa)` (xnu `osfmk/device/iokit_rpc.c` l.395): si la página física **tiene `phys_entry`** (está en la RAM del kernel) usa sus atributos; si no, **`VM_WIMG_IO`** (sin caché + guardado). Como `0x8F000000` está fuera de `PhysicalDRAM` (14.2) → **I+G**. Eso explica los 18 MB/s de lectura.
5. `IOMemoryDescriptor::doMap` (xnu `IOMemoryDescriptor.cpp` ~1790) sí respeta `kIOMapWriteThruCache` → `DEVICE_PAGER_WRITE_THROUGH | COHERENT | GUARDED` (W=1, I=0, M=1, G=1).

**Dónde cambiarlo sin tocar IOGraphics ni OpenBIOS:** en `WiiCafeFB::getApertureRange()` devolver, en vez del `IODeviceMemory`, **una subclase propia de `IOGeneralMemoryDescriptor`** (p. ej. `WiiWTMemoryDescriptor`) que sobrescriba el método virtual `map(task_t, IOVirtualAddress, IOOptionBits options, IOByteCount offset, IOByteCount length)` y, **solo si** `(options & kIOMapCacheMask) == kIOMapDefaultCache`, añada `kIOMapWriteThruCache`. El `vramMap` del kernel pide `kIOMapInhibitCache` explícito y seguirá sin caché (consola/panic seguros). Verificar en la cabecera de 10.4 que `map(...)` es virtual y su firma exacta [NO VERIFICADO la firma en `Kernel.framework/Headers/IOKit/IOMemoryDescriptor.h`].

Precauciones:
- **Alias WIMG:** la misma página física quedará I+G en el kernel y W en el WindowServer. La arquitectura PPC lo considera indefinido; en la práctica, solo es problema si **otro** escribe el fb mientras la CPU del WindowServer tiene líneas cacheadas: consola del kernel (solo en panic), cursor por software (no se usa: hay cursor HW) y **la GPU**. Tras cada blit de la GPU sobre el fb, el plugin (que corre dentro del WindowServer y tiene el mismo mapeo) debe hacer **`dcbf` en modo usuario** sobre las líneas del rectángulo destino (en PPC `dcbf`/`dcbst` están permitidos en modo usuario; `dcbi` no).
- `screencapture` y demás lectores pasan por el WindowServer (mismo mapeo) → coherentes.
- Espresso/750CL soporta W=1 en páginas normales de DRAM (MEM2 es DDR3 real); no hay razón de hardware para que falle [NO VERIFICADO en consola].
- Coste de escrituras: con W=1 las escrituras siguen yendo a memoria (similar a hoy, ~317 MB/s), las lecturas pasan a caché L1/L2.

**Experimento mínimo (recomendado como siguiente paso, ~1 día):** implementar solo `WiiWTMemoryDescriptor`, reiniciar con ese `WiiGraphics`, y medir con la misma prueba de 14.3 hecha **desde un proceso de usuario** que mapee el fb por `IOConnectMapMemory(kIOFBSystemAperture)` (lectura de 256 KB) + la sensación al mover ventanas. Si la lectura pasa de ~18 MB/s a cientos de MB/s y no hay artefactos, es la mejora más barata.

## 15.3 Respuesta a 14.6.3: precedentes
- Macs PPC reales: el fb está en VRAM PCI/AGP y siempre se mapea sin caché; Apple no hizo write-through porque la VRAM no es memoria del sistema. No hay precedente directo en Mac OS X.
- **NetBSD Wii U** (`wiiufb.c`): mapea el fb para las aplicaciones (X11/wsdisplay mmap) con `BUS_SPACE_MAP_LINEAR | BUS_DMA_PREFETCHABLE` (es decir, **con caché/prefetch**, no I+G), y en el kernel usa `mapiodev(..., prefetchable=true)`. El comentario de `wiiufb.c` (l.598–612) avisa: el fb temprano va cacheable por el BAT y *"no hagáis flush del fb entero cada vez, será muy lento"*. Es el precedente más cercano: **misma consola, fb con caché**. [NO VERIFICADO si NetBSD PPC traduce PREFETCHABLE a write-through o a copyback; mirar `sys/arch/powerpc/oea/pmap.c` y `bus_space.c`]
- Linux en Wii U (linux-wiiu, `simplefb`) mapea el fb como memoria normal del sistema para fbdev [NO VERIFICADO].

## 15.4 Respuesta a 14.6.4: doble búfer en RAM + volcado por CP_DMA
Viable pero **peor que 15.1/15.2**:
- Volcar 3,6 MB por frame a 60 Hz = 216 MB/s de ancho de banda continuo (DDR3 lo aguanta) y ~2 ms de CP por frame, **aunque nada cambie** (IOGraphics de 10.4 no pasa zonas dañadas al driver).
- Si el búfer en RAM es copyback, la CPU tendría que hacer `dcbst` de 3,6 MB por frame (~115 000 líneas) → caro. Con write-through no hace falta flush, pero entonces es lo mismo que 15.2 más una copia extra.
- Solo compensa para **tearing** (page flip). Aplazar hasta tener VBL (15.5).

## 15.5 Otras palancas encontradas
1. **`GetBeamPosition` y VBL:** `IOFBBeamPosition` llama `GetBeamPosition` del plugin; el WindowServer lo usa para *beam sync*. Implementarlo leyendo `D1CRTC_STATUS_POSITION` (`0x60A0`) es trivial. Y dar a `WiiCafeFB` interrupción de VBL real (`registerForInterruptType(kIOFBVBLInterruptType)` / semáforo de `IOFramebuffer`) usando la **IRQ 23** del PI + `DxMODE_INT_MASK` bit 0 permitiría que el WindowServer sincronice en vez de sondear. Probar también desactivar beam sync en **Quartz Debug** para medir su efecto.
2. **Herramientas de medida ya instaladas (Xcode 2.4):**
   - `/Developer/Applications/Performance Tools/Quartz Debug.app`: "Flash screen updates", "Frame meter", desactivar beam sync. Mostrará qué redibuja el WindowServer y a cuántos FPS.
   - `sample WindowServer 10 -file /tmp/ws.txt` mientras el humano arrastra una ventana: pila de llamadas con tiempos → dirá si el tiempo va en leer el fb (funciones de blend/composición con destino en el fb) o en otra cosa. **Hacer esto primero** (coste cero).
   - Shark/CHUD (si está instalado) con el perfil de tiempo del sistema.
3. **SMP** (Parte 12.1): el WindowServer compone en software con 1 núcleo de 3. Portar el arranque de núcleos de NetBSD daría la mayor mejora general, pero es un proyecto de plataforma aparte (kernel `WiiCPU`/`WiiPE`), a proponer a Goldfish64.
4. **Estado "ocupado" tras CP_DMA (14.2):** `CP_STAT = 0x80100042` parece "CP_DMA/ME ocupado" residual. Hipótesis a probar: faltaba un `WAIT_UNTIL(WAIT_CP_DMA_IDLE)` + `EVENT_WRITE(CACHE_FLUSH_AND_INV)` o `SURFACE_SYNC` tras la última fila, o el bit `CP_SYNC` solo en el último paquete deja el PFP esperando; comparar con `r600_copy_cpdma()` de Linux (`r600.c`), que emite `WAIT_UNTIL` antes del bucle y `SURFACE_SYNC` + `EVENT_WRITE_EOP` después [NO VERIFICADO].

## 15.6 Plan recomendado (orden, coste, riesgo)

| # | Tarea | Coste | Riesgo | Qué decide |
|---|---|---|---|---|
| 1 | `sample WindowServer` + Quartz Debug mientras se arrastran ventanas | minutos | ninguno | Dónde se va el tiempo (¿lecturas del fb?) |
| 2 | Instrumentar el plugin (opción 1 de 14.5): registrar `GetBlitter` pedidos y llamadas | horas | ninguno | Confirmar que pide `copyRegionProc`/`memCopyProc` y los rechazamos |
| 3 | `WiiWTMemoryDescriptor` en `getApertureRange` (15.2) | ~1 día | bajo (reiniciar para volver) | Si W=1 elimina el cuello de 18 MB/s |
| 4 | `GetBeamPosition` + VBL por IRQ 23 (15.5.1) | 1–2 días | bajo | Beam sync sin sondeo |
| 5 | **Superficies CGS: `IOAccelerator` + `IOAccelSurface` + `copyRegionProc`** (15.1) | semanas | medio (escritorio) | La aceleración 2D "de verdad" de Tiger sin QE |
| 6 | SMP (NetBSD) — proyecto aparte | semanas | alto | Rendimiento general |

**Recomendación:** hacer 1 → 2 → 3 ya (baratos y dan datos); si 1–2 confirman que Tiger usaría superficies, invertir en 5, usando VMsvga2 como referencia de código y las cabeceras `IOAccelSurfaceConnect.h`/`IOAccelTypes.h` de la propia Wii U.

---

# PARTE 16 — Resultados de Claude Code tras la Parte 15 (2026-09-27)

> Resumen de lo que envió el Claude del Mac (texto completo en su `docs/BITACORA.md`).
- Confirmado: el WindowServer mapea el fb con `kIOMapDefaultCache` → I+G; el modo de caché lo decide quien mapea (`kIOMapUserOptionsMask = 0xFFF` incluye `kIOMapCacheMask`); el plugin GA arranca pero no recibe ni una llamada.
- ❌ **No hay write‑through en xnu‑792 PPC:** `pmap_enter` y `pmap_map_block` convierten los flags a `mmFlgGuarded`/`mmFlgCInhib` y `mapping_make` solo pone I y G; **el bit W nunca se pone** → `kIOMapWriteThruCache` = copy‑back. Medido: leer 256 KB pasa de 12,3 ms a 0,27–1,69 ms con caché, pero las escrituras se quedan en caché (16 360/16 384 mal) y aparece basura al desalojar líneas.
- ❌ `sample` y `vmmap` fallan (`NSCFArray insertObject:atIndex: nil`) y **dejan el WindowServer suspendido** (hay que reanudarlo con `task_resume`). No usar. No existen Quartz Debug ni Shark.
- Base disponible: `WiiGX2Accel.kext` (CP, anillo, IB 0x32, fence EOP, user client CopyRects/FillRects/WaitIdle, `clientMemoryForType(0)` = fb), `WiiGX2GA.plugin` (copia/relleno, calidad 1000), `gx2ctl`.

---

# PARTE 17 — Respuestas a 16.5 (con el código de xnu‑792.24.17, IOKitUser‑277.8, IOGraphics‑193.2.2)

## 17.1 (Pregunta 3) El parche del bit W es **pequeño y preciso** — hacerlo antes que las superficies
Cadena confirmada en xnu‑792.24.17:
- `osfmk/ppc/pmap.h`: `VM_WIMG_WTHRU = VM_MEM_WRITE_THROUGH | VM_MEM_COHERENT | VM_MEM_GUARDED` (W=0x8, M=0x2, G=0x1).
- `osfmk/ppc/pmap.c` l.1096 (`pmap_enter`) y l.1146/1168 (`pmap_map_block*`): `mflags = mmFlgUseAttr | (flags & VM_MEM_GUARDED) | ((flags & VM_MEM_NOT_CACHEABLE) >> 1)` → **se pierde W**.
- `osfmk/ppc/mappings.c` l.348–350 (`mapping_make`): `wimg = 0x2; if (pattr & mmFlgCInhib) wimg |= 0x4; if (pattr & mmFlgGuarded) wimg |= 0x1;` y l.369 `mp->mpVAddr = ... | (wimg << 3)`. `mpW = 0x40` existe en `mappings.h` (l.242) pero no se usa.

**Firma única:** los modos de caché de PPC dan estos `pattr`:
| Petición | `pattr` en `mapping_make` |
|---|---|
| `kIOMapInhibitCache` / `VM_WIMG_IO` | CInhib + Guarded |
| `kIOMapWriteCombineCache` (`VM_WIMG_WCOMB` = I+M) | CInhib |
| `kIOMapCopybackCache` / RAM normal | 0 |
| **`kIOMapWriteThruCache` (`VM_WIMG_WTHRU`)** | **solo Guarded** |

"Guarded sin CInhib" **solo** lo produce write‑through [comprobar con `grep` que ningún otro llamador pase `mmFlgGuarded` sin `mmFlgCInhib`]. Parche: en `mapping_make`, tras la línea 350, **si `pattr == mmFlgGuarded` → `wimg |= 0x8`** (W). Resultado: WIMG = W+M+G = 0b1011, válido en PPC; para todo lo demás no cambia nada.

Dónde aplicarlo — **ya hay infraestructura**: `WiiPlatform/src/PE/WiiPE_Patcher.cpp` localiza la cabecera Mach‑O del kernel en tiempo de ejecución y su tabla de símbolos (`findKernelMachHeader`, usa `nlist`). Con eso:
1. Buscar el símbolo `_mapping_make`.
2. Localizar en su código la secuencia de las líneas 348–350 (desensamblar con `otool -tv -p _mapping_make /mach_kernel` en la Wii U para ver las instrucciones exactas: `li rX,2`, `rlwinm/andi.` del bit CInhib, `ori rX,rX,4`, bit Guarded, `ori rX,rX,1`).
3. Sustituir por una secuencia equivalente que añada `ori rX,rX,8` cuando Guarded=1 y CInhib=0 (si no cabe en el hueco, saltar a un trampolín en memoria del kext y volver; hacer `dcbst`+`sync`+`icbi`+`isync` tras escribir).
Alternativa sin tocar código: el parcheador de OpenBIOS (`arch/ppc/wii/macosx/xnu.c`, mismo mecanismo que `xnu_patch_io_bats`) antes de arrancar XNU — más seguro (antes de que haya mapeos), pero requiere reflashear OpenBIOS en la SD.

Después del parche, el `WiiWTMemoryDescriptor` de 15.2 (forzar `kIOMapWriteThruCache` en mapeos por defecto del aperture) o simplemente pedirlo desde `gx2ctl cachebench` debería dar: lecturas con caché (0,3–1,7 ms / 256 KB) y **escrituras visibles** (W=1). Validar con el mismo `cachebench` (16 384/16 384 correctas).
Con W=1 nunca hay líneas sucias → tras un blit de la GPU sobre el fb, `dcbf` (o `dcbi` en kernel) solo **invalida**: correcto. El plugin debe hacerlo sobre el rectángulo destino antes de devolver.

Precedente: no hay registro de parchear el WIMG de xnu PPC; los Macs PPC nunca lo necesitaron (VRAM en tarjeta). El 750CL/Espresso implementa W por página (es arquitectura PowerPC estándar) [NO VERIFICADO en consola: lo dirá `cachebench`].

**Por qué antes que el paso 5:** 1–2 días, reversible (se carga en caliente si se hace desde un kext; o se quita el parche), y ataca el cuello medido (lecturas del fb a ~20 MB/s) sin depender de lo que decida CGS.

## 17.2 (Pregunta 2) Superficies CGS: interfaz exacta y experimento mínimo
Llamadas exactas de `IOAccelSurfaceControl.c` (IOKitUser‑277.8) al user client de tipo `kIOAccelSurfaceClientType` (= 0):

| Índice | Método | Llamada | Entrada / salida |
|---|---|---|---|
| 0 | ReadLockOptions | scalarI_structureO | in: `options` (1) · out: `IOAccelSurfaceInformation` |
| 1 | ReadUnlockOptions | scalarI_scalarO | in: `options` (1) |
| 2 | GetState | (no lo llama esta librería; lo usa CGS [NO VERIFICADO]) | out: estado (`kIOAccelSurfaceStateIdleBit`) |
| 3 | WriteLockOptions | scalarI_structureO | in: `options` · out: `IOAccelSurfaceInformation` |
| 4 | WriteUnlockOptions | scalarI_scalarO | in: `options` |
| 5 | Read | structureI_structureO | in: `IOAccelSurfaceReadData {x,y,w,h, client_addr, client_row_bytes}` |
| 6 | SetShapeBacking | scalarI_structureI | in: `options, fbIndex, backing (VA del cliente), rowbytes` (4) + `IOAccelDeviceRegion` |
| 7 | SetIDMode | scalarI_scalarO | in: `wid, modebits` (2) |
| 8 | SetScale | scalarI_structureI | in: `options` + `IOAccelSurfaceScaling` |
| 9 | SetShape | scalarI_structureI | in: `options, fbIndex` (2) + `IOAccelDeviceRegion` |
| 10 | Flush | scalarI_scalarO | in: `framebufferMask, options` (2) |
| 11 | QueryLock | scalarI_scalarO | sin argumentos |
| 12 | ReadLock | scalarI_structureO | out: `IOAccelSurfaceInformation` |
| 13 | ReadUnlock | scalarI_scalarO | — |
| 14 | WriteLock | scalarI_structureO | out: `IOAccelSurfaceInformation` |
| 15 | WriteUnlock | scalarI_scalarO | — |
| 16 | Control | scalarI_scalarO | in: `selector, arg` (2) · out: `result` (1) |
| 17 | SetShapeBackingAndLength | scalarI_structureI | in: `options, fbIndex, backing, rowbytes, backingLength` (5) + región; si devuelve `kIOReturnUnsupported`/`BadArgument`, la librería reintenta con el 6 |

Estructuras (IOGraphics‑193.2.2, `IOAccelTypes.h`/`IOAccelSurfaceConnect.h`): `IOAccelDeviceRegion {UInt32 num_rects; IOAccelBounds bounds; IOAccelBounds rect[]}` con `IOAccelBounds {SInt16 x,y,w,h}`; `IOAccelSurfaceInformation {vm_address_t address[4]; UInt32 rowBytes, width, height, pixelFormat; IOOptionBits flags; IOFixed colorTemperature[4]; UInt32 typeDependent[4]}`.

**Hallazgo clave: `SetShapeBacking(AndLength)`.** El WindowServer puede pasar **su propio backing store en RAM** (`backing` = dirección virtual en su tarea + `rowbytes`). O sea, la superficie no tiene por qué vivir en memoria del driver: el kernel envuelve ese buffer (`IOMemoryDescriptor::withAddress(backing, len, kIODirectionOut, task)` + `prepare()` → páginas físicas) y en `Flush` lo copia al fb con CP_DMA (una orden por tramo físico contiguo de cada fila, o por página), tras `dcbst` del rango (el buffer es copy‑back en el WindowServer). Esto es justamente el volcado de ventanas que hoy hace la CPU escribiendo al fb sin caché.

**Qué hace CGS si algo falla:** CoreGraphics es cerrado; no hay código que lo confirme [NO VERIFICADO]. Por diseño debería volver a software si `IOAccelFindAccelerator` o `IOAccelCreateSurface` fallan (es lo que pasa hoy sin `IOAccelTypes`).

**Experimento mínimo (sin riesgo real), antes de implementar nada:**
1. Clase `WiiGX2Accelerator : IOAccelerator` en `WiiGX2Accel.kext`; en `start()`: `getPath(buf, &len, gIOServicePlane)` y `WiiCafeFB->setProperty("IOAccelTypes", buf)` + `setProperty("IOAccelIndex", 0)`. Quitarlas en `stop()`.
2. `newUserClient(task, sec, type, ...)`: **registrar con IOLog `type`, el proceso y devolver `kIOReturnUnsupported`** (o un user client que registre cada selector y sus argumentos y devuelva `kIOReturnUnsupported`).
3. `killall loginwindow` con el kext cargado y SSH abierto; mirar syslog.
   - Si aparece `type 0` desde WindowServer → CGS **sí** usa superficies sin QE → invertir en el paso 5 con `SetShapeBacking` + `Flush`.
   - Si no aparece nada → CGS de 10.4 no usa superficies sin QE en este hardware; abandonar el paso 5 y quedarse con 17.1.
   - Si el escritorio no vuelve → descargar el kext por SSH y `killall loginwindow` otra vez.

## 17.3 (Pregunta 1) Medir el WindowServer sin `sample`
- **No arreglar `sample`**: usa el framework privado de símbolos (vmutils) de 10.4; el `nil` probablemente viene de una región o imagen que no reconoce. No compensa.
- **Muestreador propio** (el que propuso el Claude del Mac) — viable y sencillo en 10.4:
  - `task_for_pid(WindowServer)` (root), `task_threads`, y en bucle cada ~1 ms `thread_get_state(PPC_THREAD_STATE)` → `srr0` (PC) y `r1` (pila). **Sin `thread_suspend`** (evita repetir el congelado; el estado puede ser ligeramente inconsistente, suficiente para estadística).
  - Pila: en PPC el marco es `[r1+0] = marco anterior`, `[r1+8] = LR guardado` (ABI de Darwin 32‑bit); leer con `vm_read_overwrite` 3–5 niveles.
  - Símbolos: en 10.4 los frameworks del sistema viven en la región compartida **pre‑enlazada a direcciones fijas** (prebinding), así que `nm -n /System/Library/Frameworks/ApplicationServices.framework/Frameworks/CoreGraphics.framework/CoreGraphics` da direcciones absolutas utilizables sin desplazamiento [NO VERIFICADO: comparar con una dirección conocida, p. ej. `dlsym` de `CGSMainConnectionID` en un proceso de prueba].
  - Histograma por función → ver si domina la composición que lee el fb.
- `gdb -p <pid>` (Xcode 2.4 trae gdb) con `bt` ~20 veces es la alternativa manual; `gdb` no usa vmutils. Hacer `detach` siempre.
- Alternativas indirectas: `top -l 0 -s 1 -stats pid,cpu,command` (CPU del WindowServer al arrastrar), `sc_usage WindowServer` y `fs_usage -w -f cachehit` (llamadas al sistema).

## 17.4 (Pregunta 4) `dcbf` de todo el fb por vblank — no
- 3,6 MB / 32 B = 115 200 `dcbf` por frame. Las líneas limpias cuestan pocos ciclos (≈ 1 ms por frame a 1,24 GHz, estimado); las sucias se escriben igual que ahora pero en ráfagas de 32 B. Parece barato, pero:
  - Las escrituras de la CPU serían invisibles hasta 16 ms y cualquier desalojo natural de una línea sucia ya pinta en cualquier momento (es lo que causó la tira de basura).
  - La GPU y la CPU se pisarían (la GPU escribe; luego un `dcbf` de una línea sucia antigua la machaca), justo lo medido en 16.2.
  - `dcbf` desde el kernel debe hacerse sobre un mapeo **cacheable** del mismo físico (las cachés del 750 están etiquetadas por dirección física; sobre un mapeo I=1 el comportamiento no es fiable).
- Con el parche de 17.1 (W=1) no hay líneas sucias y todo esto sobra.

## 17.5 Plan actualizado (sustituye a la tabla 15.6)
| # | Tarea | Coste | Riesgo | Decide |
|---|---|---|---|---|
| 1 | Experimento `IOAccelTypes` + registro de `newUserClient` (17.2) | horas | bajo | ¿CGS usa superficies sin QE? |
| 2 | Parche W en `mapping_make` (17.1) + validar con `cachebench` + plugin con `dcbf` tras blits | 1–2 días | medio (kernel; reversible) | Lecturas del fb con caché y escrituras visibles |
| 3 | Si 1 = sí: superficies con `SetShapeBacking` + `Flush` por CP_DMA (17.2) | 1–2 semanas | medio | Volcado de ventanas por GPU |
| 4 | Muestreador propio (17.3) | 1 día | bajo | Dónde gasta el WindowServer tras 2/3 |
| 5 | `GetBeamPosition` + VBL (IRQ 23) | 1–2 días | bajo | Beam sync |
| 6 | SMP (NetBSD) | semanas | alto | Rendimiento general |

---

# PARTE 18 — Resultados del plan 17.5 (2026-09-27), resumen

- ✅ **El WindowServer pide superficies.** `WiiGX2Accel` hereda ahora de `IOAccelerator` y, mientras está cargado, publica en `WiiCafeFB` `IOAccelTypes = "IOService:/WiiPE/gx2@c200000/WiiGX2Accel"` e `IOAccelIndex = 0`. Su user client propio usa el tipo `'gx2c'` (0x67783263). Tras `killall loginwindow`, el WindowServer carga el plugin GA y **pide una vez `newUserClient type 0`** al arrancar; al recibir Unsupported sigue en software, sin errores.
- Trampas nuevas:
  - `IOAccelSurfaceControl.h` no viene en las cabeceras de Tiger, pero `IOAccelFindAccelerator`, `IOAccelCreateSurface` e `IOAccelDestroySurface` sí están exportadas por IOKit.framework: basta declarar los prototipos a mano.
  - Las KPI (`proc_selfname`…) no se pueden mezclar con dependencias `com.apple.kernel.*`.
  - La `sys/proc.h` de MacPPCKernelSDK no compila contra Tiger.
  - `kextunload` con el WindowServer conectado funciona.
- Observación del humano: nota las ventanas "más fluidas" y vio una ventana semitransparente con rayas horizontales (foto). [Ver 19.4]

---

# PARTE 19 — Respuestas a 18.3 y siguiente paso

Fuentes nuevas: **VMQemuVGA** (`github.com/startergo/VMQemuVGA`, **licencia MIT**, deriva de VMsvga2 de Zenith432): `FB/VMAccelSurfaceClient.cpp` (1 173 líneas) implementa el cliente de superficies con la tabla verificada contra `VMsvga2Surface.cpp`, y su `LEDGER.md` registra lo que hizo el WindowServer real de 10.6 con él. Lo de 10.6 es orientativo: 10.4 puede diferir → **registrar todo**.

## 19.1 Tabla de métodos exacta (IOExternalMethod, estilo antiguo, válido en xnu‑792)
| # | Método | Tipo | count0 | count1 |
|---|---|---|---|---|
| 0 | ReadLockOptions | `kIOUCScalarIStructO` | 1 | variable (`IOAccelSurfaceInformation`) |
| 1 | ReadUnlockOptions | `kIOUCScalarIScalarO` | 1 | 0 |
| 2 | GetState | `kIOUCScalarIScalarO` | 0 | 1 (devolver `kIOAccelSurfaceStateIdleBit`) |
| 3 | WriteLockOptions | `kIOUCScalarIStructO` | 1 | variable |
| 4 | WriteUnlockOptions | `kIOUCScalarIScalarO` | 1 | 0 |
| 5 | Read | `kIOUCScalarIStructI` | 0 | variable (`IOAccelSurfaceReadData`) |
| 6 | SetShapeBacking | `kIOUCScalarIStructI` | 4 | variable (región) |
| 7 | SetIDMode | `kIOUCScalarIScalarO` | 2 (wid, modebits) | 0 |
| 8 | SetScale | `kIOUCScalarIStructI` | 1 | variable |
| 9 | SetShape | `kIOUCScalarIStructI` | 2 (options, fbIndex) | variable (`IOAccelDeviceRegion`) |
| 10 | Flush | `kIOUCScalarIScalarO` | 2 (framebufferMask, options) | 0 |
| 11 | QueryLock | `kIOUCScalarIScalarO` | 0 | 0 (la respuesta es el código: Success / CannotLock) |
| 12 | ReadLock | `kIOUCScalarIStructO` | 0 | variable |
| 13 | ReadUnlock | `kIOUCScalarIScalarO` | 0 | 0 |
| 14 | WriteLock | `kIOUCScalarIStructO` | 0 | variable |
| 15 | WriteUnlock | `kIOUCScalarIScalarO` | 0 | 0 |
| 16 | Control | `kIOUCScalarIScalarO` | 2 | 1 |
| 17 | SetShapeBackingAndLength | `kIOUCScalarIStructI` | 5 | variable |

Ojo con tu hallazgo de 14.2 (xnu‑792 omite el lado vacío en `structureI_structureO`); aquí casi todo es `ScalarI*` y los argumentos llegan en orden: escalares primero, luego el puntero a la estructura y su tamaño (en `StructO`: `p[n] = info*`, `p[n+1] = size*`).

## 19.2 Secuencia observada (10.6, VMQemuVGA) y lo que espera el WindowServer
1. `IOServiceOpen(accel, type 0)` → **`SetIDMode(wID = 1, modebits = 0x24)`**: wID 1 es la **superficie propia del WindowServer** (pantalla entera); 0x24 = `ColorDepth8888 (0x4) | WindowedBit (0x20)`.
2. **`SetShape(options, fbIndex, región)`**: región con `bounds` = pantalla (en 10.6 también la barra de menús 1680×22, ventanas y un 64×64 del spinner).
3. Bucle por cada actualización: **`QueryLock` → `WriteLock` → (el WindowServer dibuja) → `WriteUnlock` → `Flush(fbMask, options)`**. En 10.6 se vio a unos 39 Hz de forma estable.
4. `GetState` → idle.

Lo que devuelve `WriteLock` (según VMsvga2 `surface_write_lock_options` y VMQemuVGA l.946–1136):
- Validar `*infoSize >= sizeof(IOAccelSurfaceInformation)`; si no → `kIOReturnBadArgument`.
- Sin id/forma/bpp → `kIOReturnNotReady`; doble bloqueo → `kIOReturnCannotLock`; sin memoria → `kIOReturnNoMemory`.
- **Backing del driver** (no del cliente): `IOBufferMemoryDescriptor` creado en el **primer** WriteLock, que solo crece y dura hasta cerrar la superficie; mapeado en la tarea del WindowServer (`createMappingInTask(owningTask, 0, kIOMapAnywhere)`) y en el kernel.
- `info->address[0] = base_mapeo_cliente + shape_y*rowBytes + shape_x*bpp`; `rowBytes` = paso de la **asignación** (ancho de pantalla × 4); `width/height` = forma actual; `pixelFormat = modebits`; `colorTemperature[0] = 0x1CCCC` (precedente de GeForce.kext).
- Lección de VMQemuVGA: **un WriteLock que funciona sin un Flush que funcione = pantalla azul/rota** ("the blue-screen boot"). Implementar los dos a la vez.

`Flush`: copiar la región de la forma desde el backing al fb **respetando los dos pasos distintos** (el de la superficie y el del fb), recortando a la forma.

**Mínimo aceptable:** métodos 2, 7, 9, 10, 11, 14, 15 reales; 0/1/3/4/12/13 delegando en los mismos; 6/17 → `kIOReturnUnsupported` (la librería no reintenta si falla el 6); 5/8/16 → `kIOReturnUnsupported` o éxito vacío, registrando si se llaman.

## 19.3 Diseño para la Wii U (mejor que el parche W)
El backing de la superficie es **RAM normal de MEM2, con caché copy‑back para el WindowServer**: las lecturas de la composición van a caché. Además se evita el cuello de 18 MB/s **sin tocar el kernel**.
- `IOBufferMemoryDescriptor::withOptions(kIOMemoryPhysicallyContiguous | kIOMemoryKernelUserShared, rowBytes*alto, PAGE_SIZE)`. 3,6 MB contiguos para la superficie wID 1; si falla la contigüidad, usar varios tramos y un CP_DMA por tramo.
- **Flush:**
  1. `dcbst` (flush de la caché de datos, `flushDataCache`) del rango de la región en el mapeo **del kernel** de la superficie. Las cachés del 750 van por dirección física, así que vale cualquier alias cacheable.
  2. `sync`.
  3. CP_DMA fila a fila superficie → fb (ya lo tienes: ×28 más rápido que la CPU).
  4. Fence.
  5. **Esperar el fence antes de volver** (primera versión síncrona). Más adelante: volver ya y hacer que el siguiente `WriteLock` espere al fence anterior.
- Coste del flush de caché: ~1 `dcbst` por cada 32 bytes de la región actualizada. Con una región de 512 KB son ~16 000 instrucciones (del orden de 0,1–0,5 ms), frente a los 14 ms actuales escribiendo sin caché.
- El **parche del bit W (17.1) pasa a ser opcional**: solo haría falta si algo más siguiera leyendo el fb con la CPU.

Riesgos:
- Si el WindowServer usa la superficie para toda la pantalla y el Flush falla → pantalla congelada. Mantén el SSH abierto y el `kextunload` a mano.
- Memoria: si 10.4 abre superficies por ventana, limita la memoria total y devuelve `kIOReturnNoMemory`; se supone que el WindowServer volvería a software [NO VERIFICADO].

## 19.4 Sobre la sensación de fluidez y la foto
- Con la Parte 18, el WindowServer **no** usa ninguna superficie (recibe Unsupported) y el contador de envíos no se mueve. En teoría nada debería ir más rápido. Dos posibilidades:
  1. Percepción o condiciones distintas (tras `killall loginwindow` la sesión está "limpia": menos ventanas y cachés vacías).
  2. Algún efecto real de tener `IOAccelTypes` publicado (el WindowServer podría cambiar de camino) [NO VERIFICADO].
- Cómo comprobarlo: grabar en vídeo el mismo arrastre de ventana con el kext cargado y sin él, con la misma sesión, y comparar.
- **La foto:** la ventana se ve **semitransparente** con el fondo detrás y hay **rayas horizontales** abajo a la derecha. Las ondas de colores son muaré de fotografiar la TV. Tiger no hace transparentes las ventanas al arrastrarlas, pero **sí durante las animaciones de cerrar/aparecer (fundido)**. Posible explicación: se capturó un fundido a medias y las rayas son restos de un redibujado parcial. También podrían ser líneas de caché sucias de las pruebas de `cachebench` de la Parte 16, desalojadas más tarde, como la "tira de basura" que ya se vio.
  → Pedir al humano que diga si aparece **sin** haber hecho pruebas de caché desde el último reinicio. Si reaparece después de reiniciar, es un bug nuestro y hay que investigarlo.

## 19.5 Parche W (respuesta 18.3‑1), por si se sigue necesitando
- **Mejor en tiempo de ejecución desde `WiiGX2Accel`** (o `WiiPE`), antes de que el WindowServer mapee nada: es reversible reiniciando y no hay que tocar OpenBIOS. `WiiPE_Patcher.cpp` ya sabe encontrar símbolos del kernel.
- Qué buscar en `otool -tv -p _mapping_make /mach_kernel`: tras la búsqueda del physent, una secuencia `li rW,2` → prueba del bit `mmFlgCInhib` (0x2) de `pattr` → `ori rW,rW,4` → prueba del bit `mmFlgGuarded` (0x1) → `ori rW,rW,1` → `rlwinm/slwi rX,rW,3,...` + `or` hacia `mpVAddr`.
- Parche: sustituir la instrucción `ori rW,rW,1` (la que añade G) por un `b trampolín`. En el trampolín: `ori rW,rW,1` (la original) → `andi. rT,pattr,2` (usar un registro muerto en ese punto, y comprobar que cr0 no está vivo) → `bne vuelta` → `ori rW,rW,8` → `b vuelta`. Después, `dcbst`/`sync`/`icbi`/`isync` sobre las dos zonas.
- Que el humano pase el desensamblado completo de `_mapping_make` y lo concretamos.
- **Prioridad: por debajo de 19.3.**

## 19.6 Plan actualizado (sustituye a 17.5)
| # | Tarea | Coste | Riesgo |
|---|---|---|---|
| 1 | User client de superficies en **modo registro**: métodos 0–17 que registran argumentos. SetIDMode/SetShape/QueryLock/GetState devuelven éxito; WriteLock devuelve `kIOReturnNoMemory` (para que el WindowServer vuelva a software). Resultado: la secuencia real de 10.4 (wID, modebits, regiones) | horas | bajo |
| 2 | WriteLock + Flush reales (19.2/19.3) para **wID 1** (pantalla completa); el resto de wID, igual que en el paso 1 | 2–4 días | medio (pantalla) |
| 3 | Superficies por ventana si 10.4 las pide | 1–2 semanas | medio |
| 4 | Flush asíncrono + `GetBeamPosition`/VBL (IRQ 23) | días | bajo |
| 5 | Parche W (19.5), solo si hace falta | 1–2 días | medio |
| 6 | SMP (NetBSD) | semanas | alto |

---

# PARTE 20 — Superficies CGS funcionando en Tiger (2026-09-27), resumen

- **Secuencia real de 10.4.11:** un solo cliente, `SetIDMode(wid 1, 0x24)`. Por cada actualización: `SetShape(0xD = NonBlocking|IdentityScale|FrameSync, fb 0, región repintada)` → `QueryLock` → `WriteLock(infoSize 68)` → `WriteUnlock` → `Flush(1, 0)` → `SetShape(0x1, región vacía)`. La forma es la **zona repintada**, no una ventana. Si WriteLock devuelve NoMemory, el WindowServer sigue en software.
- **Implementación:**
  - Backing de pantalla completa con caché (`kIOMemoryKernelUserShared`, no contiguo), sembrado desde el fb por CP_DMA.
  - WriteLock devuelve `base + y·5120 + x·4`.
  - Flush hace `dcbf` + CP_DMA por segmento físico + fence. Es asíncrono: WriteLock espera al flush anterior.
- **Convenciones de xnu‑792:**
  - ScalarI→ScalarO: `(in…, &out…)`.
  - ScalarI→StructO: `(in…, out, &outCount)`.
  - ScalarI→StructI: `(in…, struct, size)`, **salvo con 5 escalares: `(in0..in4, struct)` sin tamaño**.
  - `IOAccelSurfaceInformation` de Tiger mide **68 bytes**; la de MacPPCKernelSDK, 84 → hay que usar una estructura propia.
- **Resultado:** miles de flushes, 0 timeouts, 0 errores, sin glitches. El camino de la GPU cuesta < 1 % de CPU; el **WindowServer está al ~44 % de CPU** componiendo en software en un solo núcleo. El humano lo nota "un pelín más fluido, sobre todo el scroll".

---

# PARTE 21 — Respuestas a 20.4

## 21.1 ¿Qué más puede delegar CGS de 10.4 sin QE?
Con el código disponible (IOKitUser‑277.8, IOGraphics‑193.2.2):
- **Del cliente de superficies ya se usa todo lo útil.** `Read` (índice 5) es para leer píxeles de la superficie (capturas, `CGWindowListCreateImage`); implementarlo, desde el backing, deja las capturas coherentes, pero no ahorra CPU. `SetScale` (8) es para superficies escaladas (vídeo, OpenGL), que sin QE no se ven. `Control` (16) no tiene selectores documentados; basta con registrarlo.
- **Superficies por ventana:** la secuencia medida muestra **solo wID 1**. Tiger sin QE mantiene los *backing stores* de las ventanas en RAM y compone él mismo en la superficie de pantalla. No hay más que delegar por esta vía.
- **Blitters del GA:** `copyRegionProc` / `IOFBBlitSurfaceSurfaceCopy` y `memCopyProc` existen en IOGraphicsLib, pero el contador muestra que no se llaman. Probablemente solo se usan con superficies de ventana o en modos concretos [NO VERIFICADO]. Déjalos implementados y registrando.
- **Conclusión:** el 44 % es composición en software (mezclas alfa, sombras, transparencias de menús, en un G3 sin AltiVec). Sin QE (OpenGL) Tiger no ofrece más delegación. Las palancas que quedan:
  1. **SMP** (21.2): no acelera un único hilo de composición, pero las aplicaciones y el resto del sistema dejan de competir con el WindowServer por el único núcleo.
  2. Menos trabajo de composición: probar **16 bpp** (`kIOAccelSurfaceModeColorDepth1555`), que reduce a la mitad los bytes que mueve la CPU (medir si el WindowServer lo acepta con superficie 1555) [NO VERIFICADO].
  3. Flush en vblank (FrameSync, bit 0x8 de SetShape): usar `GetBeamPosition`/VBL para no repintar a más ritmo del que se ve.

## 21.2 Plan concreto de SMP (núcleos 1 y 2)

**Lo que ya hay:**
- xnu‑792 PPC soporta hasta 256 CPU (`MAX_CPUS 256`, `osfmk/ppc/exception.h`).
- Arranque de secundarios (`osfmk/ppc/cpu.c`, `cpu_start()` ~l.287): si `start_paddr == 0x100` (vector de reset) escribe en `ResetHandler` (memoria baja) `RESET_HANDLER_START`, `_start_cpu` y `&PerProcTable[cpu]`, y luego llama a **`PE_cpu_start()` → `IOCPU::startCPU(start_paddr, arg_paddr)`**. Después espera `SignalReady`.
- Sincronización del timebase: `cpu_sync_timebase()` usa `cpu_signal(master, SIGPcpureq, CPRQtimebase)`, o sea, **IPIs**; no hace falta `time_base_enable` si las IPIs funcionan.
- IPIs: `PE_cpu_signal()` → **`IOCPU::signalCPU(target)`**.

**Lo que falta en Wiintosh (`WiiCPU.cpp`):**
- `_numCPUs = 1` fijo.
- `startCPU`, `haltCPU`, `quiesceCPU` e `initCPU(!boot)` vacíos.
- `WiiInterruptController` solo lee la causa del núcleo 0 (`readCafeIntCause32(0) // TODO`).
- El árbol de OpenBIOS solo tiene `PowerPC,Espresso@0`.

**Hardware (de NetBSD, `sys/arch/evbppc/nintendo/cpu.c`, `pic_pi.c`, `ipi_latte.c`, `powerpc/include/oea/spr.h`):**
- **SCR = SPR 947 (0x3B3)**: `WAKE(n) = 1 << (23 - n)` despierta el núcleo n; `IPI_PEND(n) = 1 << (20 - n)` es la IPI pendiente del núcleo n (se envía poniéndolo y se reconoce borrándolo en bucle hasta que se quede a 0).
- Vector de arranque de los secundarios: **`0x08100100`** (MEM0).
- Trampolín de NetBSD, a copiar allí (con `sync` + `icbi` después):
  ```
  lis   r3, hi(entry) ; ori r3, r3, lo(entry) ; mtsrr0 r3
  li    r3, 0         ; mtsrr1 r3                 ; MSR = 0 (modo real)
  lis   r3, 0x0011    ; ori r3, r3, 0x0024 ; mtspr 1008 (HID0), r3
  lis   r3, 0xb1b0    ; mtspr 1011 (HID4), r3 ; sync
  lis   r3, 0xe7fd    ; ori r3, r3, 0xc000 ; mtspr 944 (HID5), r3 ; sync
  rfi
  ```
  Para XNU, `entry` = **`start_paddr` (0x100)**: el secundario entra por el vector de reset de XNU, que lee `ResetHandler` y salta a `_start_cpu` con su `per_proc`.
- El PI tiene registros por núcleo: `INTSR(n) = 0x0C000078 + n*8`, `INTMSK(n) = 0x0C00007C + n*8`. IPIs = IRQ 20 + n.
- El boot CPU de NetBSD pone en `SCR`: `(spr & ~0x40000000) | 0x80000000`, y `HID5 |= H5A | PIRE` (PIR = número de núcleo). Revisar qué hace ya Wiintosh/OpenBIOS con eso.
- Topología: el núcleo 1 tiene más L2 (2 MB frente a 512 KB).

**Pasos:**
1. **OpenBIOS:** añadir `PowerPC,Espresso@1` y `@2` en `/cpus` (`reg` 1/2, `state "stopped"`, mismas propiedades que @0), o que `WiiPE` cree los nubs.
2. **`WiiCPU`:**
   - `_numCPUs = 3`; registrar cada CPU con `ml_processor_register` (`boot_cpu = false`, `start_paddr = 0x100`).
   - El núcleo de arranque: `processor_start` como ya hace. Los secundarios: `processor_start(machProcessor)` → XNU llama a `startCPU`.
   - `startCPU(start_paddr, arg)`: mapear `0x08100100` (`IOMemoryDescriptor::withPhysicalAddress` + map, o `ml_phys_write`), copiar el trampolín con `entry = start_paddr`, `flushDataCache`/`icbi`, y `mtspr 947, mfspr(947) | (1 << (23 - n))`.
   - `initCPU(false)` en el secundario: habilitar su interrupción externa en el PI (`INTMSK(n)`), sus IPIs, y `setCPUState(kIOCPUStateRunning)`.
   - `signalCPU(target)`: `mtspr 947, mfspr(947) | (1 << (20 - target))`.
   - `ipiHandler`: comprobar `IPI_PEND(cpu_number())` en SCR, borrarlo en bucle y llamar `ipi_handler()`.
3. **`WiiInterruptController`:** usar `cpu_number()` para leer/escribir `INTSR/INTMSK` de **ese** núcleo. Enrutar los dispositivos solo al núcleo 0 al principio (máscaras de 1 y 2 a 0 salvo IPI). Primero comprobar IPI en SCR.
4. **Timebase:** XNU lo sincroniza con `CPRQtimebase` una vez que funcionan las IPIs. Si hubiera deriva, copiar `md_presync/md_sync_timebase` de NetBSD.
5. **Cachés:** Espresso es coherente entre núcleos (bus 60x/MEI). El trampolín fija HID0/4/5 como NetBSD. XNU inicializa las cachés del secundario en `_start_cpu` según el PVR: comprobar que acepta el PVR de Espresso igual que en el núcleo 0 (Wiintosh ya lo hace funcionar en el 0).
6. **Seguridad:** boot-arg `cpus=1` (XNU lo respeta: `max_ncpus`) para volver a un núcleo. Probar primero despertando **solo el núcleo 1**.

**Riesgos:**
- Un fallo en el secundario cuelga el arranque: siempre con `-v` y con `cpus=1` a mano.
- Latte puede necesitar que IOSU/loader haya dejado los núcleos 1/2 en espera en `0x08100100` (NetBSD arranca desde el mismo linux-loader, así que es de esperar que sí).
- Proponerlo a upstream (Goldfish64): toca `WiiPlatform` y OpenBIOS.

## 21.3 Hacerlo permanente con seguridad
Orden recomendado:
1. **Interruptor por boot-arg** en `WiiGX2Accel::start()`: si existe `-nogx2` (o `wiigx2=0`), devolver `false`. El plugin ya falla limpio sin el kext y `WiiCafeFB` no publica `IOAccelTypes`/`IOCFPlugInTypes` → Tiger vuelve al framebuffer simple.
2. **Instalar en `/System/Library/Extensions/WiiGX2Accel.kext`** (root:wheel, 755/644), **sin** `OSBundleRequired`:
   - **Arranque seguro (`-x`) no lo carga** → otra vía de escape sin tocar la SD.
   - Lo carga `kextd` por matching de `NTDOY,gx2` (`IOProbeScore` bajo, `IOMatchCategory` propio) antes de `loginwindow`. Si llegara tarde, el WindowServer arranca en software y usa la GPU en el siguiente inicio de sesión.
   - Después: `sudo touch /System/Library/Extensions` y `kextcache -k /System/Library/Extensions` para regenerar la caché.
3. **No meterlo en el mkext de la SD todavía:** el mkext se carga siempre (incluso con `-x` si es `Root`) y un fallo obligaría a sacar la SD. Hacerlo solo cuando lleve semanas estable, y proponerlo a upstream como parte de `WiiGraphics`.
4. **Apagado limpio:** en `stop()` y al apagar/reiniciar (registrarse en `IOPMrootDomain` con `registerPrioritySleepWakeInterest` o equivalente de 10.4), **detener el CP** (`CP_ME_CNTL = ME_HALT|PFP_HALT`) para que el anillo en RAM de Mac OS X no se ejecute al reiniciar por Aroma/Cafe OS.
5. **Recuperación documentada:**
   - En OpenBIOS: `setenv boot-args "-v -nogx2"` o `"-x"`.
   - Por SSH: `sudo mv /System/Library/Extensions/WiiGX2Accel.kext /tmp/` + `sudo touch /System/Library/Extensions`.

## 21.4 Plan (sustituye a 19.6)
| # | Tarea | Coste | Riesgo |
|---|---|---|---|
| 1 | Permanente seguro (21.3): boot-arg, /S/L/E, parar el CP al apagar | 1 día | bajo |
| 2 | `Read` (capturas coherentes) + registro de `Control/SetScale` | horas | bajo |
| 3 | VBL / `GetBeamPosition` (IRQ 23) y flush sincronizado a vblank | 1–2 días | bajo |
| 4 | Probar superficie 16 bpp | horas | bajo |
| 5 | **SMP** (21.2), empezando por el núcleo 1 | 1–3 semanas | alto |
| 6 | Proponer a Goldfish64 (PR a `Wiintosh/osx-drivers`) | — | — |

---

# PARTE 22 — Plan 21.4, pasos 1–3 (2026-09-27), resumen
- ✅ **Permanente:** `WiiGX2Accel.kext` + `WiiGX2GA.plugin` en `/System/Library/Extensions`, sin `OSBundleRequired`, con boot-args `-nogx2`/`-gx2off`. Tras reinicios reales el WindowServer compone por la GPU desde el primer momento (187 flushes/50 s, 0 timeouts). El CP se detiene al apagar/reiniciar vía `registerPrioritySleepWakeInterest` [NO VERIFICADO que se ejecute].
- **Trampas:**
  - Al arrancar, `kIOMemoryPhysicallyContiguous` devuelve **MEM1** (p. ej. `0x00844000`), y el backing no contiguo también puede tener páginas en MEM1. Hoy esos tramos los copia la CPU.
  - `kextload -n -t` da por bueno un kext con símbolos sin resolver: `copyout` es KPI, así que `Read` usa `IOMemoryDescriptor::withAddress(task)`.
- `Read` implementado y verificado. Control/SetScale no los llama el WindowServer. `GetBeamPosition` = `D1CRTC_STATUS_POSITION & 0x1FFF`.

---

# PARTE 23 — Respuestas a 22.5

## 23.1 ¿La GPU accede a MEM1 en la dirección física 0?
**Muy probablemente sí, a la misma dirección física que la CPU.**
- MEM1 (32 MB de eDRAM) está **dentro** de Latte, junto a la GPU, y es donde Cafe OS pone los render targets de GX2 (Copetti: "32 MB de EDRAM (MEM1) para operaciones rápidas: render targets…").
- GX2/TCL le pasa a la GPU **direcciones físicas** obtenidas con `OSEffectiveToPhysical` (decaf‑emu `tcl_ring.cpp`), y MEM1 está en la física `0x00000000–0x01FFFFFF` (NetBSD `wiiu.h`: `WIIU_MEM1_BASE 0`). Es decir, la GPU usa las mismas direcciones que la CPU también para MEM1.
- [NO VERIFICADO en hardware] **Prueba barata**, con `gx2ctl` o la sonda:
  1. Reservar una página de MEM1 (las que el kext ya retiene).
  2. Llenarla con un patrón por CPU + `flushDataCache`.
  3. CP_DMA MEM1 → una zona de prueba en MEM2 y comparar.
  4. Luego al revés (MEM2 → MEM1), invalidar la caché (`dcbf`) y leer por CPU.
  5. Si coincide en los dos sentidos, **quitar el rechazo de MEM1 y la ruta por CPU**.
- Precaución: la eDRAM puede tener más latencia o restricciones de alineación para el CP_DMA que la DDR3. Probar tamaños y alineaciones de 4 bytes, 32 bytes y página.
- Si fallara: pedir el backing con `IOBufferMemoryDescriptor::inTaskWithPhysicalMask(kernel_task, opts, size, mask)` o la variante de 10.4 disponible con una máscara que **excluya < 0x10000000**. Si no existe en 10.4, conservar el enfoque de reintentos.

## 23.2 ¿Flush en vblank?
**No ahora.**
- Nadie ha visto tearing, el flush de una zona típica tarda < 1 ms y el WindowServer ya marca `FrameSync` (0x8) sin que se note nada.
- Valor real de la IRQ 23: menos CPU sondeando y permitir *beam sync*. Es poca ganancia para el riesgo de tocar interrupciones.
- Dejarlo como tarea menor y **probarlo solo si aparece tearing** (p. ej. al hacer scroll rápido en Safari o con vídeo).

## 23.3 ¿SMP o PR primero? → **PR primero, SMP después.**
1. **PR a Goldfish64** (`Wiintosh/osx-drivers`), porque:
   - Lo que hay está verificado y es de bajo riesgo: el CP, las superficies CGS, 0 timeouts y reinicios reales.
   - Un PR pequeño se revisa mejor que uno enorme con SMP dentro.
   - SMP toca `WiiPlatform` y OpenBIOS, que son del autor: conviene hablarlo antes.
   - Antes del PR hay que cerrar la **duda de MEM1** (23.1), para no subir la ruta de reintentos si sobra.

   Contenido sugerido (varios PRs o commits separados):
   - (a) `WiiGX2Accel` (CP/anillo/fence, IOAccelerator, cliente de superficies) + `WiiGX2GA.plugin`, **en un kext o `PlugIns` aparte**, desactivable con `-nogx2`, sin tocar `WiiCafeFB` salvo lo mínimo (las propiedades se publican desde el kext).
   - (b) Herramientas: `gx2ctl` y la sonda, en `tools/`.
   - (c) Documentación: resumen técnico (registros verificados, microcódigo de Nintendo, xnu‑792 y sus convenciones de user client, estructura de 68 bytes, trampas de Tiger). **Sin** volcados de microcódigo ni nada de Nintendo.
   - Build: el Makefile debe seguir compilando con el toolchain del CI oficial (Darling + gcc 4.2) además de con gcc‑4.0 en la Wii U. Probar ambos o avisar en el PR.
   - Licencia: BSD‑3 como el repo. Si se usó código de VMsvga2/VMQemuVGA (MIT) o de NetBSD (BSD‑2), mantener sus avisos; si solo se usó como referencia, citarlo.
   - Mencionar en el PR la limitación de 10.4.11 PPC probada en una sola consola, y los boot-args de escape.
2. **SMP (núcleo 1)** después, con el plan 21.2, idealmente coordinado con Goldfish64 (le puede interesar más que la GPU).
3. Pequeños: probar 16 bpp; IRQ 23 solo si hay tearing.

## 23.4 Aviso de seguridad pendiente
`sudo` en la Wii U sigue siendo `NOPASSWD: ALL` duplicado (Parte 9.3). Ya no hace falta para el día a día (el kext se carga solo). **Reducirlo** a la regla mínima antes de seguir con SMP, que implica más reinicios y comandos root.

---

# PARTE 24 — MEM1 y PR (2026-09-27), resumen
- ✅ **La GPU accede a MEM1 en la física 0.** El autotest `gx2ctl selftest` (MEM1 `0x01212000` ↔ MEM2, CP_DMA en los dos sentidos, (4,100)/(32,1000)/(0,página)) dio 6/6 correctas. `GX2AllowMEM1 = true` por defecto; la copia por CPU dejó de crecer.
- Flush en vblank: no se ha hecho (sin tearing).
- **PR preparado** (rama `pr/gx2-accel` sobre upstream `main`, 3 commits):
  - (a) WiiGX2Accel + GA + `make accel`, `-nogx2`;
  - (b) tools (`gx2ctl`, `gatest`) y la sonda en la raíz;
  - (c) `docs/GX2.md`.
  Sin código copiado ni microcódigo. No probado con Darling + gcc 4.2.1 (el documento lo dice). Pendiente: permiso e identidad de autor.

---

# PARTE 25 — Respuestas a 24.4 (revisión del PR)

## 25.1 ¿WiiGX2Probe en `tools/` o en la raíz?
**Déjala en la raíz**, junto al resto de kexts (`WiiAudio/`, `WiiGraphics/`…): es un kext y la estructura del repo es "un kext por carpeta en la raíz" con `common/kext.mk` relativo. Adaptar `kext.mk` solo para la sonda cambia infraestructura del autor sin necesidad.
- **No** añadirla a `KEXTS` del Makefile principal ni al mkext: que se compile con un objetivo aparte (`make probe`), igual que `make accel`.
- Documentar en `docs/GX2.md` que es una herramienta de diagnóstico, no un driver.
- Opcional: ofrecer en la descripción del PR quitar la sonda si el autor prefiere un PR más pequeño.

## 25.2 ¿SMP en el PR o en un issue aparte?
**Issue aparte, y después del PR.**
- El PR debe contener solo lo verificado (GPU 2D). Mezclar un plan no implementado que toca `WiiPlatform` y OpenBIOS (del autor) complica la revisión.
- Abre un issue en `Wiintosh/osx-drivers` (o en `Wiintosh/Wiintosh`), titulado p. ej. *"SMP on Wii U (Espresso cores 1–2): proposal"*, con:
  1. Referencias: NetBSD `evbppc/nintendo/cpu.c`, `pic_pi.c`, `ipi_latte.c`, `oea/spr.h` (SCR = SPR 947, `WAKE(n) = 1<<(23-n)`, `IPI_PEND(n) = 1<<(20-n)`, vector `0x08100100`, trampolín HID0/4/5) y xnu‑792 `osfmk/ppc/cpu.c` (`cpu_start`, `ResetHandler`, `PE_cpu_start`, `cpu_sync_timebase`).
  2. Los cambios propuestos en OpenBIOS (`/cpus`), `WiiCPU` (startCPU/signalCPU/ipiHandler/initCPU) y `WiiInterruptController` (por núcleo).
  3. La pregunta al autor: ¿lo prefiere en OpenBIOS o en WiiPE? ¿Ha probado ya algo?
- Enlazar el issue desde el PR ("follow-up: #N").

## 25.3 Revisión del PR antes de publicar (lista de comprobación)
1. **Descripción del PR**, clara y corta:
   - qué hace (el WindowServer compone por la GPU vía superficies CGS + CP_DMA);
   - qué no hace (ni QE ni OpenGL);
   - resultados (0 timeouts, reinicios reales, Tiger 10.4.11, una sola consola);
   - cómo desactivarlo (`-nogx2`, `-gx2off`, Safe Boot);
   - toolchain probado.
2. **Versiones de OS X:** el repo compila para 10.0–10.4. Si el kext solo está probado en 10.4 (Tiger), que `make accel` solo se construya para `tiger`, o que el Info.plist/`start()` se niegue en otras versiones. Dilo en el PR. Riesgos a revisar en código:
   - la estructura de 68 bytes (`IOAccelSurfaceInformation`);
   - las convenciones de user client de xnu‑792;
   - la dependencia de `IOGraphicsFamily`.
   Todo eso puede diferir en 10.2/10.3.
3. **No romper el CI existente:** el CI construye todos los kexts con Darling. Si `accel` no está en `KEXTS`, el CI no se ve afectado. Confirmar que ningún archivo común (`common/kext.mk`, `include/`) cambió de forma que afecte a los demás kexts.
4. **Estilo:** igual que el repo (cabeceras `//  Archivo.cpp` con copyright, `WIIDBGLOG/WIISYSLOG`, `WiiDeclareLogFunctions("gx2")` → boot-arg `-wiigx2dbg`, 2 espacios, nombres `kWiiGX2Reg…` como en `GX2Regs.hpp`). **Reutilizar `WiiGraphics/src/Cafe/GX2Regs.hpp`** si se definen los mismos registros, en vez de duplicarlos.
5. **Copyright/autor:** en las cabeceras nuevas, "Copyright © 2026 <nombre del humano>". Licencia BSD‑3 como el repo (sin añadir otra). Citar las referencias en `docs/GX2.md`, no en la licencia.
6. **Nada privado:** sin microcódigo, sin rutas de tu máquina, sin IPs (172.16.42.x), sin el usuario `rubano1421` en los scripts, ni la bitácora personal.
7. **Commits:** mensajes en inglés, sin menciones a modelos, autor = la identidad que elija el humano.
8. **Tamaño:** si el diff es muy grande, ofrecer separarlo (a) primero y (b)+(c) después.

## 25.4 Pasos para publicar (los hace el humano o Claude con permiso)
1. Fork de `Wiintosh/osx-drivers` a la cuenta del humano.
2. `git remote add fork git@github.com:<usuario>/osx-drivers.git && git push fork pr/gx2-accel`.
3. Abrir el PR contra `Wiintosh/osx-drivers:main` desde la web (o `gh pr create`).
4. Después, abrir el issue de SMP (25.2) y enlazarlo.
5. Seguir el PR: responder a Goldfish64 y adaptar a lo que pida.

---

# PARTE 25b — PR publicado (2026-09-27), resumen del Claude del Mac
- **PR abierto:** https://github.com/Wiintosh/osx-drivers/pull/1 (fork `Rubanoxd/osx-drivers`, rama `gx2-accel`, autor Rubanoxd). 3 commits. Sin checks: el CI de upstream necesita aprobación para forks y usa un toolchain privado.
- Instalado y verificado tras varios reinicios reales; 0 timeouts; WindowServer al ~44 % de CPU en **un solo núcleo** → siguiente frente: SMP.
- `sudo` NOPASSWD en la Wii U: el humano decide dejarlo como está.

---

# PARTE 26 — SMP en Wiintosh: investigación y plan por pasos

> Fuentes: xnu‑792.24.17 (`osfmk/ppc/cpu.c`, `start.s`, `lowmem_vectors.s`, `model_dep.c`), osx-drivers 0.5.2 (`WiiPlatform/src/PE/WiiCPU.cpp`, `Interrupts/*`), OpenBIOS Wiintosh (`arch/ppc/wii/init.c`, `macosx/xnu.c`) y NetBSD (`sys/arch/evbppc/nintendo/cpu.c`, `machdep.c`, `pic_pi.c`, `ipi_latte.c`, `powerpc/include/oea/spr.h`). Lo no comprobado en consola va marcado.

## 26.1 Estado actual (núcleo 0)
- `WiiCPU` **es un `IOCPU`**. `start()`:
  1. Exige `WiiPE`.
  2. Fija `_numCPUs = 1` (`// TODO: Only handle one CPU`).
  3. Lee `reg` del nodo de CPU → `setCPUNumber`.
  4. Marca `_isBootCPU = true` (la lectura de `state` no cambia nada).
  5. Crea el `IOCPUInterruptController` con `initCPUInterruptController(_numCPUs)`.
  6. Llama `ml_processor_register` con `start_paddr = 0x100`, `supports_nap = false`, `time_base_enable = NULL`, y después `processor_start`. **Solo si `physCPU < _numCPUs`.**
- `initCPU(boot)`: si es boot, habilita la interrupción de CPU y registra `ipiHandler` en `cpuNub` interrupción 0; si no, `// TODO`. Pone `Running`.
- `startCPU`, `haltCPU`, `quiesceCPU`: **vacíos** (`startCPU` devuelve `KERN_SUCCESS` sin hacer nada).
- **`signalCPU` no está sobrescrito** → `IOCPU::signalCPU` base, que no hace nada.
- `ipiHandler`: llama `ipi_handler()` sin mirar ningún registro.
- OpenBIOS (`arch/ppc/wii/init.c` l.~329): define **una sola** CPU `PowerPC,Espresso` (en `ioreg` solo aparece `cpus/PowerPC,Espresso@0`).
- Parche de CPU ya existente: `xnu_patch_cpu_check()` en OpenBIOS (`macosx/xnu.c` l.63–108) cambia en la **tabla `processor_types` de `__start`** la entrada del 750CX (máscara `0xFFFF0F00`, PVR `0x00080200`) por máscara `0xFFFF0000`, PVR `0x7001xxxx`. Así el Espresso usa `init750CX` → `init750`.

## 26.2 Cómo arranca XNU un secundario (xnu‑792)
1. `processor_start(proc)` → `cpu_start(cpu)` (`osfmk/ppc/cpu.c` l.287):
   - Si `start_paddr == EXCEPTION_VECTOR(T_RESET)` (= 0x100), escribe con `ml_phys_write` en `ResetHandler` (memoria baja, dentro de los vectores en física 0): `RESET_HANDLER_START`, `_start_cpu` y `&PerProcTable[cpu]`.
   - Guarda el timebase actual en `proc_info->ruptStamp`, `sync; isync`.
   - Llama **`PE_cpu_start(cpu_id, start_paddr, proc_info)` → `IOCPU::startCPU`** (nuestra función).
   - Luego **duerme hasta que el secundario ponga `SignalReady`** (`thread_sleep_simple_lock` sobre `cpu_flags`). No hay timeout visible en ese bucle: **si el secundario no llega, el arranque se queda colgado** en ese punto [comprobar si hay timeout más arriba].
2. El secundario debe entrar en **modo real (MSR = 0, IP = 0) en la física 0x100**.
   - `lowmem_vectors.s` l.59–90: el vector de reset lee `ResetHandler`; si es `RESET_HANDLER_START`, lo borra, carga `r4 = _start_cpu` y `r3 = &PerProcTable[cpu]` y salta.
   - `_start_cpu` (`start.s` l.92) → `allstart` (l.144): recorre **la misma tabla `processor_types`** (ya parcheada por OpenBIOS) → `init750CX` → `init750`. Con `firstBoot` lee **L2CR** (si no es válido, desactiva la función L2) y fija HID0.
   - **Por tanto el PVR de Espresso ya vale para los secundarios**; no hace falta otro parche [NO VERIFICADO: comprobar que `firstBoot` y la lectura de L2CR no fallan en los núcleos 1/2].
3. El secundario inicializa su `per_proc`, llama `PE_cpu_machine_init` → **`IOCPU::initCPU(false)`**, activa interrupciones y pone `SignalReady`. Después, el maestro sincroniza el **timebase**: `cpu_sync_timebase()` (cpu.c l.687–) manda `cpu_signal(master, SIGPcpureq, CPRQtimebase)` → **necesita IPIs funcionando**. Como `time_base_enable = NULL`, el timebase se sincroniza por software con IPIs.
4. Boot-arg **`cpus=N`**: `machine_startup()` (`model_dep.c` l.216) → `max_ncpus = N`; `cpu.c` l.267 rechaza registrar más CPU (`real_ncpus >= max_ncpus`). **`cpus=1` sí funciona como red de seguridad.**

## 26.3 Hardware de Espresso para SMP (NetBSD)
- **SPR de Espresso** (`oea/spr.h`): `HID5 = 944 (0x3B0)`, **`SCR = 947 (0x3B3)`**, `CAR = 948 (0x3B4)`, `BCR = 949 (0x3B5)`, `HID4 = 1011 (0x3F3)`, `HID2 = 920 (0x398)`.
  - `SCR_WAKE(n) = 1 << (23 − n)`: despierta el núcleo n.
  - `SCR_IPI_PEND(n) = 1 << (20 − n)`: IPI pendiente para el núcleo n. Se envía poniéndolo desde cualquier núcleo y se reconoce **en el núcleo destino** borrándolo **en bucle hasta leer 0** (`pic_pi.c` `pi_ipi_ack`).
- **Configuración del núcleo de arranque en modo nativo Wii U** (`machdep.c` l.~415):
  ```
  HID5 |= H5A (0x80000000) | PIRE (0x40000000)       ; habilita HID5 y el registro PIR (id de núcleo)
  SCR  = (SCR & ~0x40000000) | 0x80000000
  CAR |= 0xFC100000
  BCR  = 0x08000000
  isync
  ```
  Esto se hace en el núcleo 0 **antes** de despertar los demás [NO VERIFICADO qué deja hecho ya el loader/OpenBIOS: leer HID5/SCR/CAR/BCR del núcleo 0 bajo Wiintosh con un kext antes de tocar nada].
- **Trampolín** (`cpu.c` l.56–76), copiado a **`WIIU_BOOT_VECTOR = 0x08100100`** (MEM0) + `__syncicache`:
  ```
  lis r3,hi(entry); ori r3,r3,lo(entry); mtsrr0 r3
  li  r3,0; mtsrr1 r3                          ; MSR = 0 → modo real, IP = 0
  lis r3,0x0011; ori r3,r3,0x0024; mtspr 1008,r3   ; HID0
  lis r3,0xb1b0;                  mtspr 1011,r3   ; HID4
  sync
  lis r3,0xe7fd; ori r3,r3,0xc000; mtspr 944,r3    ; HID5
  sync
  rfi
  ```
  Para XNU: **`entry = 0x100`** (vector de reset de XNU), sin pasar argumento: XNU lo coge de `ResetHandler`. `mtsrr1 0` garantiza IP = 0 → vectores en 0.
- **Despertar:** `mtspr SCR, mfspr(SCR) | WAKE(n)`. NetBSD espera un "ack" del secundario con un bucle largo y deja un `printf` que, según su comentario, acelera el arranque "sin saber por qué" (probablemente un retardo o el efecto de un flush). Tenerlo en cuenta si el secundario tarda.
- **Timebase en NetBSD:** el maestro publica `TB + 100000`, espera a llegar a ese valor, marca `running = 0`, y el secundario escribe TBL = 0, TBU, TBL. Solo hace falta si el método de XNU (IPIs) no funciona.
- **Topología:** el núcleo 1 tiene L2 de 2 MB y los 0/2 de 512 KB (NetBSD marca 0 y 2 como "más lentos").
- **Interrupciones por núcleo:** `PI INTSR(n) = 0x0C000078 + 8n`, `INTMSK(n) = 0x0C00007C + 8n`. Las IPIs **no pasan por el PI**: son la excepción externa del núcleo con `SCR_IPI_PEND(n)`. NetBSD, en cada excepción externa, mira primero `SCR_IPI_PEND(cpu)`: si está, lo reconoce y devuelve la "IRQ" software 20+n; si no, lee `INTSR(cpu) & mask`.

## 26.4 Respuestas concretas a 25.2
1. **Hoy:** ver 26.1. OpenBIOS solo crea el nodo del núcleo 0; `WiiCPU` es un `IOCPU` con SMP sin implementar.
2. **Secuencia del núcleo 1:** 26.2. `ResetHandler`/`PerProcTable` los rellena **XNU** (`cpu_start`). Nosotros solo tenemos que poner el núcleo en modo real en 0x100. El PVR ya está parcheado en `processor_types`, que es la tabla que usa `allstart` también para los secundarios. Timebase: por IPIs (`CPRQtimebase`), con el método de NetBSD como alternativa.
3. **Trampolín:** sí, el de NetBSD con `entry = 0x100`.
   - `0x08100100` está en **MEM0** (3 MB en `0x08000000`, NetBSD `wiiu.h`). No está en `PhysicalDRAM` de XNU, así que hay que mapearlo con `IOMemoryDescriptor::withPhysicalAddress(0x08100000, 0x1000, kIODirectionInOut)` + `map(kIOMapInhibitCache)`, o usar `ml_phys_write`.
   - **Antes de escribir, leer y registrar** lo que hay (lo puede estar usando el loader).
   - Los HID del trampolín de NetBSD (HID0 `0x00110024`, HID4 `0xB1B00000`, HID5 `0xE7FDC000`) son los de su kernel; XNU vuelve a fijar HID0 en `init750`. Empezar con los mismos valores.
4. **IPIs e interrupciones:**
   - XNU llama `PE_cpu_signal(source, target)` → **`IOCPU::signalCPU(target)`** → implementar en `WiiCPU`: `SCR |= IPI_PEND(target->getCPUNumber())`.
   - Recepción: la IPI llega como **excepción externa del núcleo destino** → XNU → `IOCPUInterruptController::handleInterrupt` con el vector de **ese** núcleo (`initCPUInterruptController(3)`) → `WiiCPU::ipiHandler` de ese núcleo. Ahí: si `SCR & IPI_PEND(yo)`, reconocerlo en bucle y llamar `ipi_handler()`. **Si no, en el núcleo 0 pasar al `WiiInterruptController`** (hoy el PI está colgado de la interrupción 0 del `cpuNub` del núcleo 0: comprobar el enlace exacto en `WiiInterruptController` / `WiiPE`).
   - `WiiInterruptController` hoy usa `readCafeIntCause32(0)` fijo. **Primera versión:** dejar todos los dispositivos en el núcleo 0 y poner `INTMSK(1) = INTMSK(2) = 0` (los secundarios solo reciben IPIs). Así no hace falta hacerlo por núcleo todavía.
5. **Caché y coherencia:**
   - Espresso es coherente entre núcleos (NetBSD corre SMP con su pmap normal).
   - Riesgo XNU: su código MP de PPC se escribió para G4/G5. Revisar que las instrucciones que usa para MP son válidas en Espresso: `tlbie` + `tlbsync` (difusión de TLB entre núcleos; ¿implementa el 750CL `tlbsync` y la difusión de `tlbie`? NetBSD usa la misma secuencia en `oea/pmap.c`), `lwarx/stwcx.` (sí), `icbi` difundido (necesario para código modificado).
   - Las L2 son privadas por núcleo y coherentes por snooping. `init750` lee/sombrea L2CR por núcleo.
   - No debería hacer falta nada especial [NO VERIFICADO: primer punto a mirar si hay corrupciones aleatorias].
6. **Riesgos y recuperación:**
   - `cpus=1` funciona (26.2). Probar primero **solo el núcleo 1** (registrar 2 CPU y dejar el 2 fuera, o `cpus=2`).
   - Si el secundario no arranca: **cuelgue en `cpu_start`** esperando `SignalReady`, antes o durante el arranque de IOKit. Con `-v`, lo último en pantalla será el registro de `WiiCPU`. **Pedir foto de la pantalla.**
   - Recuperación: en OpenBIOS `setenv boot-args "-v cpus=1"`.
7. **Upstream:** issue antes de código (texto en 26.6).

## 26.5 Plan por pasos (cada uno se prueba sin riesgo antes del siguiente)
| # | Paso | Cómo probar | Si cuelga, pedir… |
|---|---|---|---|
| 0 | Kext de diagnóstico (solo lectura) en el núcleo 0: leer y registrar `PVR`, `HID0/2/4/5`, `SCR`, `CAR`, `BCR`, `L2CR`, `PIR` y el contenido de `0x08100100` (64 bytes) | carga en caliente | — |
| 1 | Añadir en `WiiCPU` `signalCPU` + `ipiHandler` con SCR, **con 1 CPU**: un kext de prueba se manda una IPI a sí mismo (`SCR |= IPI_PEND(0)`) y comprueba que llega y se reconoce | carga en caliente; si hay tormenta de interrupciones, reiniciar | foto |
| 2 | OpenBIOS: nodos `PowerPC,Espresso@1` y `@2` (`reg` 1/2, `state "stopped"`) — o crearlos desde `WiiPE`. `WiiCPU`: `_numCPUs = 3`, `initCPUInterruptController(3)`, registrar las 3 CPU pero **`startCPU` devuelve `KERN_FAILURE`** para los secundarios | arranque normal; `sysctl hw.ncpu` debe seguir en 1 (o 3 "no iniciados") y todo igual. Probar también `cpus=1` | foto con `-v` |
| 3 | Aplicar en el núcleo 0 la configuración de NetBSD (HID5 H5A\|PIRE, SCR, CAR, BCR) si el paso 0 muestra que falta | arranque normal | foto |
| 4 | `startCPU` real **solo para el núcleo 1**: escribir el trampolín (`entry = 0x100`), `flushDataCache` + `icbi`, `SCR |= WAKE(1)`. `initCPU(false)`: `INTMSK(1) = 0`, habilitar su vector del `IOCPUInterruptController`, `Running` | arrancar con **`-v cpus=2`**. Éxito = `hw.ncpu: 2`, `sysctl hw.activecpu` = 2, `top` repartiendo | **foto de la pantalla** (se colgará en `cpu_start` si el núcleo no llega) |
| 5 | Estabilidad: compilar el repo en la Wii U con `make -j2`, dejarlo horas; vigilar el timebase (`date` frente al Mac) y los panics | uso real | panic.log / foto |
| 6 | Núcleo 2 (`cpus=3`) | igual | foto |
| 7 | Interrupciones por núcleo en `WiiInterruptController` (opcional; los dispositivos en el núcleo 0 bastan) | — | — |

## 26.6 Texto propuesto del issue (inglés) para Goldfish64
```
Title: Proposal: SMP support on Wii U (Espresso cores 1 and 2)

Hi! Following up on the GX2 PR (#1): with GPU compositing working, WindowServer
is now CPU-bound (~44% of one core). Espresso has 3 cores and XNU 8.x PPC is
MP-capable, so I'd like to propose SMP, and ask how you'd prefer it done
before writing code.

What I found (references):
- NetBSD evbppc/nintendo (jmcneill, 2026) runs SMP on Wii U: cpu.c (secondary
  trampoline at 0x08100100 setting HID0/HID4/HID5, woken via SCR SPR 947
  WAKE(n)=1<<(23-n)), ipi_latte.c / pic_pi.c (IPIs via SCR IPI_PEND(n)=1<<(20-n),
  acked on the target core), machdep.c (boot core: HID5 H5A|PIRE, SCR, CAR, BCR).
- xnu-792 cpu_start() fills ResetHandler with _start_cpu/PerProcTable and calls
  PE_cpu_start -> IOCPU::startCPU; the secondary enters at the reset vector
  (0x100, real mode) and goes through allstart, which uses the processor_types
  table that OpenBIOS already patches for Espresso. Timebase is synced over
  IPIs (CPRQtimebase). The "cpus=N" boot-arg caps max_ncpus (safe fallback).

Proposed changes:
1. OpenBIOS: add PowerPC,Espresso@1/@2 nodes (or have WiiPE create them).
2. WiiCPU: numCPUs=3; startCPU writes the trampoline (entry 0x100) and sets
   SCR WAKE(n); signalCPU sets SCR IPI_PEND(n); ipiHandler acks it;
   initCPU(false) masks all PI IRQs on secondaries (devices stay on core 0).
3. Possibly per-core PI handling in WiiInterruptController later.

Questions:
- Would you rather have the CPU nodes in OpenBIOS or created by WiiPE?
- Have you already tried SMP, or seen issues with tlbie/tlbsync or L2 on Espresso?
- Any constraints on MEM0 (0x08100100) under wiiu-loader?

I'd test step by step with "cpus=1/2" as fallback and report results here.
```

---

# PARTE 27 — SMP paso 0 (2026-09-27), resumen del Claude del Mac
- Kext `WiiSMPProbe` (solo lectura, en caliente). **HID4 = SPR 1011** (`IBM750CL_SPR_HID4` de NetBSD; ojo: `SPR_HID4 = 0x3F4` de `oea/spr.h` es el del 970). BCR no se lee (NetBSD solo lo escribe).
- Núcleo 0 bajo Tiger:

| Registro | Valor | Comparación con NetBSD |
|---|---|---|
| PVR | `0x70010201` | |
| HID0 | `0x0011C064` | |
| HID4 | `0x80000000` | |
| HID5 | `0x80000000` | **sin PIRE** |
| SCR | `0x80000000` | coincide |
| CAR | `0x00000000` | NetBSD hace `\|= 0xFC100000` |
| L2CR | `0x80000000` | |

  `hw.ncpu = hw.activecpu = 1`.
- MEM0 `0x08100100` contiene `li r3,0x40; mtmsr r3; isync; b 0x08000110` (stub previo del loader/IOSU); el resto está a cero.
- Issue de SMP redactado por el Claude del Mac (no le llegó el texto de 26.6); pendiente de permiso.
- Pregunta: ¿se puede probar la auto-IPI en caliente sin reemplazar `WiiCPU` (que va en el mkext)?

---

# PARTE 28 — Respuestas a la Parte 27 y **corrección importante al plan 26.5**

## 28.1 ⚠️ Corrección: el `IOCPUInterruptController` bloquea hasta que arrancan TODOS los núcleos
xnu‑792 `iokit/Kernel/IOCPU.cpp`:
- `initCPUInterruptController(sources)`: `numCPUs = sources`, reserva `vectors[numCPUs]` y llama **`ml_init_max_cpus(numCPUs)`**.
- `enableCPUInterrupt(cpu)` (la llama cada `initCPU`): `ml_install_interrupt_handler(cpu, cpuNumber, this, handleInterrupt)`, `enabledCPUs++`, y `thread_wakeup` cuando `enabledCPUs == numCPUs`.
- **`registerInterrupt(...)`: si `enabledCPUs != numCPUs` → `assert_wait` + `thread_block`.** Quien registre una interrupción en el controlador de CPU **se duerme hasta que todos los núcleos declarados hayan hecho `initCPU`.**
- `handleInterrupt(source)`: `source` = número del núcleo que recibió la excepción externa → llama al `vectors[source]` registrado.

Cómo está cableado hoy (osx-drivers 0.5.2):
- `WiiInterruptController::start` (PI) hace `getPlatform()->setCPUInterruptProperties(provider)` (especificadores 0…numCPUs‑1 hacia `IOPlatformInterruptController`) y **`provider->registerInterrupt(0, …)`** → el PI ocupa el **vector 0** (excepción externa del núcleo 0).
- `WiiCPU::initCPU(true)` intenta `cpuNub->registerInterrupt(0, ipiHandler)`. El nodo de CPU de OpenBIOS no tiene `interrupts`, así que probablemente falla en silencio (y si no, chocaría con el PI en el vector 0 → `NoResources`). Hoy `ipiHandler` **no está conectado a nada** [comprobar en el log de arranque].

**Consecuencias para el plan:**
1. **El paso 2 de 26.5 tal como estaba colgaría el arranque.** Con `initCPUInterruptController(3)` y los secundarios sin arrancar, el `registerInterrupt(0)` del PI se queda esperando para siempre → sin USB, sin SD, sin nada.
2. **`numCPUs` del controlador de CPU debe ser exactamente el número de núcleos que van a arrancar**: `min(3, cpus=N, modo SMP activado)`. Con `cpus=1` o sin el boot-arg de SMP → `initCPUInterruptController(1)`, como hoy.
3. Por la misma razón, **si un secundario no arranca, el sistema se cuelga igual** (en `cpu_start` esperando `SignalReady`, o en el `registerInterrupt` del PI).
4. **Enrutado de IPIs en SMP:**
   - Núcleo 0: su excepción externa va al vector 0 = `WiiInterruptController::handleInterrupt`. Ahí hay que **comprobar primero la IPI** (SCR `IPI_PEND(0)` / causa del PI bit 20), reconocerla y llamar al `ipi_handler` de XNU (es `cpu_signal_handler`, el mismo para todos los núcleos: usa `cpu_number()` internamente).
   - Núcleos 1 y 2: sus vectores 1 y 2 necesitan un **manejador solo de IPI** (registrado por `WiiInterruptController` con `provider->registerInterrupt(1/2, …)` usando los especificadores que ya crea `setCPUInterruptProperties`, o por cada `WiiCPU` secundario).
   - `WiiInterruptController::handleInterrupt` hoy lee **siempre** `readCafeIntCause32(0)`: en los secundarios no debe usarse (solo IPI).

## 28.2 Las IPIs pasan por el PI (bit 20+n)
NetBSD `pic_pi.c`:
- `pi_enable_irq(20+n)` pone el bit `20+n` en **`INTMSK(n)`** del núcleo n (`pi_irq_affinity`: las IPI van a su núcleo).
- En cada excepción externa, `pi_get_irq` mira primero `SCR & IPI_PEND(cpu)`: si está, `pi_ipi_ack` (borra el bit en SCR en bucle hasta leer 0) y devuelve la IRQ `20+cpu`.
- `pi_ack_irq` escribe además **`INTSR(cpu) = 1 << irq`**.

→ Para recibir una IPI en el núcleo n: **habilitar el bit 20+n en `INTMSK(n)`**; al atenderla, **borrar SCR `IPI_PEND(n)` (en bucle) y escribir `INTSR(n) = 1<<(20+n)`**. [NO VERIFICADO si `INTSR(n)` bit 20+n se activa solo por el SCR; lo dice la prueba A de 28.3.]
En Wiintosh eso encaja con `WiiInterruptController`: el vector 20 del PI del núcleo 0 es un vector normal (bit 20 de `INTMSK(0)`/`INTSR(0)`).

## 28.3 Probar la auto‑IPI en caliente sin tocar `WiiCPU` (respuesta a la pregunta)
Sí, con un kext aparte (`WiiSMPProbe`) en dos pruebas, de menos a más riesgo:

**Prueba A — sondeo con interrupciones desactivadas (sin riesgo):**
```cpp
boolean_t en = ml_set_interrupts_enabled(FALSE);     // MSR[EE]=0: no se toma ninguna excepción
UInt32 scr0  = mfspr(947);
UInt32 sr0   = rd(0x0C000078);                        // INTSR(0)
mtspr(947, scr0 | (1u << 20));                        // SCR IPI_PEND(0)
eieio(); sync();
UInt32 scr1  = mfspr(947);
UInt32 sr1   = rd(0x0C000078);                        // ¿aparece el bit 20 en INTSR(0)?
for (int i = 0; i < 1000 && (mfspr(947) & (1u << 20)); i++)
  mtspr(947, mfspr(947) & ~(1u << 20));               // reconocer (bucle, como NetBSD)
wr(0x0C000078, 1u << 20);                             // limpiar la causa del PI
UInt32 scr2 = mfspr(947), sr2 = rd(0x0C000078);
ml_set_interrupts_enabled(en);
IOLog("SMPProbe A: SCR %08x→%08x→%08x  INTSR0 %08x→%08x→%08x\n", scr0, scr1, scr2, sr0, sr1, sr2);
```
Resultado esperado: `scr1` con el bit 20 puesto; `sr1` con el bit 20 (si la IPI pasa por el PI); `scr2` y `sr2` limpios. Como `INTMSK(0)` bit 20 está a 0, aunque el PI marque la causa no hay excepción. Si `scr2` no se limpia → **no** activar interrupciones y reportar.

**Prueba B — entrega real por el PI (riesgo bajo‑medio), solo si A salió bien:**
- `WiiInterruptController` es un `IOInterruptController`: desde el kext, localizarlo (`waitForService(serviceMatching("WiiInterruptController"))` + `OSDynamicCast(IOInterruptController, …)`) y usar sus métodos **públicos**: `registerInterrupt(this /*nub*/, 20, this, &handler, 0)` y `enableInterrupt(this, 20)`.
- Handler: si `SCR & IPI_PEND(0)` → reconocer en bucle + `count++`. `WiiInterruptController::handleInterrupt` ya limpia la causa del PI al final.
- Poner `SCR |= IPI_PEND(0)` una vez, esperar 10 ms, leer `count` (esperado 1), repetir 100 veces; después `disableInterrupt` + `unregisterInterrupt(this, 20)`.
- Riesgo: si la excepción llegara **sin** el bit 20 en `INTSR(0)` (la prueba A lo habría mostrado), el PI no la reconocería y habría tormenta de interrupciones → cuelgue → reinicio. Por eso primero la A.
- Así se valida la entrega de IPIs **sin cambiar el mkext**.

## 28.4 Cómo cambiar `WiiPlatform` (mkext) sin peligro
Todo lo nuevo de SMP va **detrás de un boot‑arg** (p. ej. `-wiismp`). Sin él, el código sigue exactamente el camino de 0.5.2: `numCPUs = 1`, sin tocar SCR/HID5/CAR ni MEM0.
1. Compilar el mkext nuevo y comprobar que **sin** `-wiismp` arranca igual (varios reinicios).
2. Guardar en la partición BOOT una copia `Wii_tiger.mkext.bak` de la 0.5.2. Recuperación extrema: el MacBook monta la FAT de la SD y restaura.
3. Probar SMP poniendo en OpenBIOS `setenv boot-args "-v -wiismp cpus=2"`. Si cuelga: foto y `setenv boot-args "-v"` → vuelve al camino de siempre, sin sacar la SD.
4. `numCPUs = 1 + (núcleos secundarios habilitados por boot‑args)` (28.1).

## 28.5 Notas sobre los valores leídos
- **HID5 sin PIRE:** PIRE hace que el `PIR` refleje el número de núcleo. XNU PPC no usa `PIR` para `cpu_number()` (usa el `per_proc` en SPRG), así que no es imprescindible. NetBSD lo activa en el núcleo 0; hacerlo solo con `-wiismp`.
- **CAR = 0** frente al `|= 0xFC100000` de NetBSD: función no documentada (¿configuración de coherencia/caché compartida?). Aplicarlo solo en el camino `-wiismp`, **antes** de despertar secundarios, igual que NetBSD. No tocarlo en modo normal.
- **MEM0 `0x08100100` = `li r3,0x40; mtmsr r3; isync; b 0x08000110`:** es el stub que el loader deja para los núcleos (pone MSR[IP] y salta a `0x08000110`, seguramente un bucle de espera del loader). Antes de sustituirlo:
  - volcar también `0x08000100–0x08000200` para entender qué hay en `0x08000110`;
  - **guardar los bytes originales** y restaurarlos si `startCPU` falla o en `stop`;
  - usar el trampolín de NetBSD con `entry = 0x100` y `MSR = 0` (IP = 0 → vectores de XNU en 0).

## 28.6 Plan SMP revisado (sustituye a 26.5)
| # | Paso | Dónde | Riesgo |
|---|---|---|---|
| 0 | ✅ Lectura de registros (Parte 27) + volcar `0x08000100–0x200` | kext en caliente | ninguno |
| 1 | Prueba A (28.3) | kext en caliente | ninguno |
| 2 | Prueba B (28.3) | kext en caliente | bajo‑medio (reinicio si falla) |
| 3 | `WiiPlatform` con `-wiismp`: sin el flag, idéntico a 0.5.2; con el flag, IPI en el PI (vector 20 del núcleo 0 → `ipi_handler`) todavía con 1 núcleo | mkext + boot‑arg | bajo |
| 4 | Con `-wiismp cpus=2`: nodo `@1` (creado por `WiiPE` para no tocar OpenBIOS al principio), `numCPUs = 2`, HID5/SCR/CAR como NetBSD, trampolín en MEM0, `SCR |= WAKE(1)`, vector 1 con manejador solo de IPI, `INTMSK(1)` = solo bit 21 | mkext + boot‑arg | alto: foto si cuelga, quitar el flag |
| 5 | Estabilidad (`make -j2`, horas, reloj, panics) | — | — |
| 6 | Núcleo 2 (`cpus=3`), vector 2, `INTMSK(2)` = solo bit 22 | — | alto |
| 7 | Upstream: issue (26.6) y luego PR | — | — |

---

# PARTE 29 — Issue y prueba A (2026-09-27), resumen del Claude del Mac
- Issue abierto en **Wiintosh/Wiintosh#24** (osx-drivers tiene los issues desactivados), enlazado desde el PR #1.
- **Prueba A:**
  - Condiciones: `INTMSK(0) = 0x01000050` (bit 20 enmascarado), interrupciones desactivadas, `SCR |= 1<<20`, `eieio; sync` y 10 µs de espera.
  - Resultado: **SCR se lee `0x80000000` (sin el bit 20) e `INTSR(0) = 0x00010000` (sin el bit 20)**. No hubo nada que reconocer ni ninguna excepción. **El bit 20 no se latchea.**
- Estado del núcleo 0: HID5 `0x80000000` (sin PIRE), CAR 0, SCR `0x80000000`.
- Contenido de MEM0:
  - `0x08100100`: `li r3,0x40; mtmsr r3; isync; b 0x08000110`.
  - `0x08000100`: `lis r3,0x1400; ori r3,r3,0x100; mtsrr0; li r3,0; mtsrr1; rfi`.
  - Desde `0x08000120`: código que lee el PVR, lo compara con 0x7001 y hace `mfspr/mtspr` de los SPR 1011/944/947.

---

# PARTE 30 — Respuestas a la Parte 29 (y un bloqueo serio para SMP)

## 30.1 Por qué no se latchea el bit 20: **el bit de IPI del núcleo 0 es probablemente el 18, no el 20**
- **WiiUBrew, *Hardware/Espresso*, tabla SCR** (numeración LSB‑0, la misma que usan sus tablas de HID4/HID5, donde el bit 31 = H4A/H5A = `0x80000000`, que coincide con tus lecturas):

  | Bit | Significado |
  |---|---|
  | 18 | Core 0 pending ICI |
  | 19 | Core 1 pending ICI |
  | 20 | Core 2 pending ICI |
  | 21 | Wake up Core 2 |
  | 22 | Wake up Core 1 |
  | 23 | Wake up Core 0 |
  | 26 | RMA enabled |
  | 27 | con 26: claves ancast de vWii |
  | 28–31 | control de bootrom/keystore |

  Los bits de wake coinciden con NetBSD (`WAKE(n) = 1<<(23−n)`) y con fail0verflow (*"core 1 scr |= 0x00400000; core 2 scr |= 0x00200000"*).
- **Pero los bits de ICI están al revés que en NetBSD** (`IPI_PEND(n) = 1<<(20−n)` → núcleo 0 = bit 20, núcleo 2 = bit 18). Solo coinciden en el **núcleo 1 (bit 19)**.
- Tu prueba encaja con WiiUBrew: escribiste el bit 20 ("pending del núcleo 2", núcleo dormido) y no se quedó puesto. [NO VERIFICADO: puede ser que las ICI a un núcleo dormido se descarten, o que no se admita una ICI a uno mismo.]
- **Prueba A' (sin riesgo, igual que la A):** con `EE = 0`, para cada bit `b ∈ {18, 19, 20}`:
  1. `SCR |= 1<<b`; `eieio; sync`; 10 µs;
  2. leer `SCR` y `INTSR(0)`;
  3. borrar el bit en bucle; escribir en `INTSR(0)` lo que haya aparecido;
  4. volver a leer.

  Resultado esperado si WiiUBrew acierta: **el bit 18 se queda puesto**, y quizá aparece algún bit nuevo en `INTSR(0)` (el 20 de NetBSD es el número de IRQ del PI, `MB_CPU(0) = 20`, que no tiene por qué coincidir con el bit de SCR). Apuntar **qué bit de `INTSR(0)`** se activa: ese es el que hay que desenmascarar en `INTMSK(0)` para la prueba B.
- En el SMP real, núcleo 0 → núcleo 1 = **bit 19** en ambas convenciones. La duda solo afecta a las IPI hacia los núcleos 0 y 2.

## 30.2 ¿Aplicar CAR/BCR/HID5.PIRE en caliente?
- **Significado:** no hay documentación pública de CAR ni BCR. fail0verflow: *"CAR … BCR … bit assignments are unknown"* y *"just flipping the two boot bits in SCR is enough to get the two other cores up … although coherency will probably be broken/disabled"*. Lo más probable es que **CAR/BCR configuren la coherencia/el bus entre núcleos**, y NetBSD los fija antes de despertar núcleos (`CAR |= 0xFC100000`, `BCR = 0x08000000`) [interpretación, NO VERIFICADO].
- **`HID5 |= PIRE`: sí, en caliente.** Solo habilita el registro PIR (id de núcleo). Lee PIR antes y después para comprobarlo.
- **CAR y BCR: no en caliente por ahora.** Son globales, de semántica desconocida y probablemente de coherencia/caché: cambiarlos con cachés sucias y Tiger en marcha podría corromper memoria. **No hacen falta para que se latchee una ICI.** Aplicarlos solo en el camino `-wiismp`, lo antes posible en el arranque (`WiiPE::start`, o antes, desde OpenBIOS), y justo antes de despertar el núcleo 1, como NetBSD.
- El único riesgo de probarlos en caliente es un cuelgue, que se arregla reiniciando: los SPR no son persistentes. Si hiciera falta, probar después de A'.

## 30.3 Qué hay en MEM0 (de wiiu-loader `arm/system/ppc.c` y `ppc_elf.c`)
- `ppc_elf.c` `_translate_physaddr()`: **`0xFFE00000–0xFFF1FFFF` ↔ física `0x08000000 + (addr − 0xFFE00000)`**. Por tanto:
  - **`0x08100100` = `0xFFF00100`**, el vector de reset con `MSR[IP] = 1`. fail0verflow: *"Cores 1 and 2 boot with MSR[IP]=1, thus at the high vectors"*. Por eso NetBSD pone ahí su trampolín (`WIIU_BOOT_VECTOR`).
  - **`0x08000100` = `0xFFE00100`**: el inicio del cuerpo *ancast* del PPC.
- `ppc_prepare()`: el loader arranca el PPC, "compite" con la ROM, copia un **wait stub a `0x14000100`** (MEM2) y un **jump stub** (`lis/ori 0x14000100; mtsrr0; li 0; mtsrr1; rfi`) al inicio del cuerpo ancast (`0x08000100`). El núcleo 0 queda esperando en `0x14000100` hasta que el loader escribe la entrada en `0x14000000`.
- Lo que leíste:
  - **`0x08000100`** es el jump stub del loader.
  - **`0x08000120` en adelante** es **el resto del código de arranque de Nintendo** (el cuerpo ancast que el loader no sobrescribió): la inicialización por núcleo de Cafe OS (PVR, HID4 = SPR 1011, HID5 = 944, SCR = 947).
  - **`0x08100100`** (`li r3,0x40; mtmsr r3; isync; b 0xFFE00110`) es el vector de reset de los secundarios que dejó el arranque de Nintendo: pone `MSR = 0x40` (IP) y salta **al cuerpo ancast + 0x10**.
- **Consecuencia:** si hoy despiertas el núcleo 1, entra por `0xFFF00100` y salta a `0xFFE00110`, que ahora cae **en mitad del jump stub del loader** (`mtsrr1` + `rfi` con un `SRR0` indefinido) o en el wait stub de `0x14000100` (memoria de Mac OS X) → ejecutaría basura. **Hay que escribir el trampolín en `0x08100100` antes del `WAKE`.**
- **¿Imitar `0x08000120+`?** Sí, como referencia: es la inicialización original de Nintendo para cada núcleo. Desensambla toda la rutina (hasta el primer `rfi`/`b` fuera) y compara los valores de HID4/HID5/SCR que escribe con los del trampolín de NetBSD (`HID0 0x00110024`, `HID4 0xB1B00000`, `HID5 0xE7FDC000`). Si difieren, prioriza **los de Nintendo** para los secundarios, porque es su código de producción. Guarda el desensamblado en `docs/` (código de Nintendo: solo las instrucciones relevantes y los valores, no un volcado).

## 30.4 ⚠️ Bloqueo serio para SMP: errata de `lwarx/stwcx.` en Espresso
- **linux-wiiu `smp-patches`** (README): *"there was an errata affecting the load-exclusive and store-exclusive instructions used to implement atomics. To work around it, a cache flush needs to be inserted before each stwcx instruction — on Linux, this means patching compilers, libraries, the kernel, etc."* Parchean GCC (`-mcpu=espresso` emite `dcbst` antes de `stwcx.`) y glibc.
- **NetBSD** hace lo mismo:
  - `asm.h`: `POWERPC_STWCX_PRE(ra,rb)` → `dcbst ra,rb`.
  - `lock.h`: `dcbst 0,%1` antes de cada `stwcx.`.
  - Una variante de libc `powerpc_espresso`.
  - `trap.c`: `fix_stwcx()`, que emula un `stwcx.` que trapea haciendo `dcbst` + `stwcx.` en el kernel.
- **Para Tiger significa que:**
  1. **El kernel XNU** usa `lwarx/stwcx.` en todos sus locks y atómicos (`osfmk/ppc/hw_lock.s`, `hw_vm.s`, `commpage/*`, `libkern` OSAtomic…). Con 2+ núcleos, sin `dcbst` previo, **un `stwcx.` puede tener éxito cuando no debería → locks y contadores corruptos → panics aleatorios**.
  2. **El espacio de usuario** (libSystem, frameworks, apps) también tiene `lwarx/stwcx.`: los atómicos de la *commpage* (que el kernel puede parchear), pero también código en línea en bibliotecas que no podemos recompilar.
- **Qué hacer antes de despertar un núcleo:**
  - a) **Medir el alcance:**
    ```bash
    otool -tv /mach_kernel | grep -c 'stwcx\.'
    otool -tv /usr/lib/libSystem.B.dylib | grep -c 'stwcx\.'
    ```
    y buscar en la commpage de xnu‑792 (`osfmk/ppc/commpage/*.s`: spinlocks, `compare_and_swap`, `atomic_add`). Revisar si `OSAtomic*` de libSystem en 10.4 llama a la commpage o lleva `lwarx/stwcx.` propios.
  - b) **Parche del kernel:** sustituir cada `stwcx.` por `ba stub_k`, con `stub_k` = `dcbst rA,rB; stwcx. rS,rA,rB; b vuelta` (cr0 se conserva porque lo pone el propio `stwcx.`). Como el kernel de PPC está mapeado 1:1 en memoria baja, los stubs pueden ir en una zona reservada **por debajo de 32 MB** (alcance de `ba`). Hacerlo desde el parcheador de OpenBIOS (antes de arrancar XNU) es lo más seguro. **Esto también cubre los kexts** que ya estén cargados cuando se parchee; los kexts nuevos (Wiintosh incluido) habría que compilarlos con el `dcbst` en su código ensamblador o parchearlos al cargarse.
  - c) **Commpage:** parchear las rutinas atómicas que XNU copia a la commpage (xnu‑792 `osfmk/ppc/commpage/`), o sus plantillas antes de que se copien.
  - d) **Espacio de usuario sin recompilar:** no hay una solución general. Opciones:
    1. Parchear en binario `libSystem.B.dylib` (con copia de seguridad): arriesgado.
    2. **Aceptar el riesgo** y medirlo con una prueba de estrés: dos hilos haciendo `OSAtomicIncrement32` sobre el mismo contador con los dos núcleos activos, comparar el resultado esperado con el obtenido, y lo mismo con `pthread_mutex`.
- **Recomendación:** antes de invertir en IPIs y trampolín, decidir si SMP compensa. Pasos: el recuento (a); si el kernel tiene decenas o centenares de sitios, el parche (b) es factible con el parcheador de OpenBIOS; y el riesgo en espacio de usuario se medirá con la prueba de estrés una vez el núcleo 1 funcione. **Avisar a Goldfish64 en el issue #24** (es un requisito de diseño para upstream).

## 30.5 Plan SMP revisado (sustituye a 28.6)
| # | Paso | Riesgo |
|---|---|---|
| 1 | Prueba A' (bits 18/19/20 del SCR + qué bit de `INTSR(0)` aparece) | ninguno |
| 2 | Desensamblar la rutina de Nintendo de `0x08000120+` y comparar sus HID con NetBSD | ninguno |
| 3 | Recuento de `stwcx.` en `mach_kernel`, libSystem y la commpage (30.4a); avisar en #24 | ninguno |
| 4 | Prueba B (IPI al núcleo 0 por el bit correcto del PI) | bajo‑medio |
| 5 | Diseñar el parche `dcbst`+`stwcx.` del kernel en OpenBIOS (30.4b); decidir con Goldfish64 | — |
| 6 | `WiiPlatform` con `-wiismp` (28.4) + trampolín en `0x08100100` + CAR/BCR/HID5 al estilo NetBSD/Nintendo + `WAKE(1)` = `1<<22`, IPI al núcleo 1 = bit 19 | alto |
| 7 | Prueba de estrés de atómicos en espacio de usuario con 2 núcleos | medio |
| 8 | Núcleo 2 | alto |

---

# PARTE 31 — A', rutina de Nintendo y errata (2026-09-27), resumen
- **A'** (con `EE = 0` e `INTMSK(0) = 0x01000050`):
  - bit 18 → se latchea (`SCR 0x80040000`);
  - bit 19 → se latchea (`0x800C0000`);
  - bit 20 → no se latchea;
  - **`INTSR(0)` no reacciona en ningún caso**;
  - los bits 18/19 **no se pueden borrar desde el núcleo 0** (ni escribiendo 0 ni con W1C).

  ⇒ **NetBSD tenía razón:** `IPI_PEND(n) = 1<<(20−n)` (20 = núcleo 0, 19 = núcleo 1, 18 = núcleo 2). No hay IPI a uno mismo y solo el núcleo destino puede reconocerla. **Corrige la Parte 30.1**: la lectura LSB‑0 de la tabla de WiiUBrew era errónea. Estado actual: `SCR = 0x800C0000` hasta reiniciar, sin efectos.
- **Rutina por núcleo de Nintendo** (`0xFFE00100`):
  1. `L2CR = 0`.
  2. Si `PVR>>16 == 0x7001`: `HID5 = 0xC0000000`; SPR 1007 = número de núcleo.
  3. Solo en el núcleo 0: `SCR &= ~0x40000000; SCR |= 0x80000000; CAR |= 0xFC100000; BCR = 0x08000000; isync` (= NetBSD).
  4. En cada núcleo: `HID0 = 0x00110024`, `HID2 (920) = 0x000F0000`, **`HID4 = 0xB3B00000`** (NetBSD `0xB1B00000`), **`HID5 |= 0x7FFDC000` → `0xFFFDC000`** (o `|= 0x6FBD4300` si `PVR & 0xFFFF == 0x101`; NetBSD `0xE7FDC000`).
  5. Despierta los núcleos con `SCR |= 0x00200000` y `|= 0x00400000`, y va marcando el progreso con palabras "bs0N".
- **Recuento de `stwcx.`:** `mach_kernel` 165 (incluidas las plantillas de la commpage: `atomic.s` 12 + `spinlocks.s` 12), libSystem 5, CoreGraphics 20, CoreFoundation/IOKit/WindowServer 0. Comentado en #24.

---

# PARTE 32 — Respuestas a la Parte 31

## 32.1 Cómo entra una IPI (NetBSD)
- `ipi_latte_establish_ipi()` registra, para cada núcleo n, la IRQ **`WIIU_PI_IRQ_MB_CPU(n) = 20+n`** con `intr_establish` → `pi_enable_irq(20+n)` → **pone el bit `20+n` en `INTMSK(n)`** (`pi_irq_affinity`: la IPI de n va a n).
- En cada excepción externa, `pi_get_irq()` **mira primero `SCR & IPI_PEND(cpu)`**. Si está puesto: `pi_ipi_ack()` (borra el bit en SCR en bucle desde **ese** núcleo) y devuelve la IRQ `20+cpu`; `pi_ack_irq()` escribe además `INTSR(cpu) = 1<<(20+cpu)`. Si no: lee `INTSR(cpu) & mask`.
- Interpretación, coherente con A': **el bit de SCR es el "latch" de la IPI**, y la línea de interrupción externa del núcleo destino se activa **a través del PI solo si `INTMSK(n)` tiene el bit `20+n`** (por eso NetBSD lo habilita). `INTSR(0)` no reaccionó porque el bit latcheado era el de otro núcleo (18/19) y la IPI a uno mismo (20) no existe [NO VERIFICADO que `INTSR(n)` refleje el bit 20+n; NetBSD no lo lee: decide por SCR].
- **Para Wiintosh:** en el núcleo n, habilitar el bit `20+n` en `INTMSK(n)`. En el manejador de la excepción externa de n (vector n del `IOCPUInterruptController`; en el núcleo 0 es el PI), comprobar `SCR & (1<<(20−n))` **antes** que el PI; si está, reconocer en bucle, escribir `INTSR(n) = 1<<(20+n)` y llamar a `ipi_handler()`. **Solo se puede probar con el núcleo 1 despierto** (núcleo 0 → bit 19 → núcleo 1, y al revés con el bit 20).
- Limpieza de las IPIs pendientes que dejó A' (`SCR` bits 18/19): hasta reiniciar se quedan puestas. **Reiniciar antes de despertar ningún núcleo**; si no, el secundario recibiría una IPI nada más habilitar su máscara.

## 32.2 Valores para los secundarios: **los de Nintendo**
- Es el código de producción que arranca esos mismos núcleos en Cafe OS. NetBSD funciona con valores parecidos; las diferencias son:
  - `HID4`: Nintendo `0xB3B00000` frente a NetBSD `0xB1B00000`. La diferencia es el bit `0x02000000` = **SBE (BAT secundarios)**, que NetBSD activa más tarde en `cpu_features_enable()`. XNU no usa los BAT 4–7: es inocuo.
  - `HID5`: Nintendo `0xFFFDC000` frente a NetBSD `0xE7FDC000`. La diferencia son los bits `0x18000000`, que WiiUBrew no documenta. Usar los de Nintendo.
  - `HID2 = 0x000F0000`: solo lo pone Nintendo (probablemente relacionado con paired singles/DMA; XNU no lo toca).
  - SPR 1007 = número de núcleo (equivale a PIR; con `HID5 = 0xC0000000` → PIRE).
- **Trampolín propuesto para los secundarios** (en `0x08100100`):
  ```
  ; MSR aún con IP=1; estamos en modo real
  li    r3,0 ; mtspr L2CR,r3                            ; como Nintendo
  lis   r3,0xC000 ; mtspr 944(HID5),r3 ; isync
  li    r3,<n> ; mtspr 1007,r3                          ; id de núcleo (cada núcleo necesita su trampolín o leer el PIR)
  lis   r3,0x0011 ; ori r3,r3,0x0024 ; mtspr 1008(HID0),r3
  lis   r3,0x000F ; mtspr 920(HID2),r3
  lis   r3,0xB3B0 ; mtspr 1011(HID4),r3 ; sync
  mfspr r3,944 ; oris r3,r3,0x7FFD ; ori r3,r3,0xC000 ; mtspr 944,r3 ; sync ; isync
  <habilitar L2 (ver abajo)>
  lis   r3,0 ; ori r3,r3,0x100 ; mtsrr0 r3              ; entrada de XNU (vector de reset)
  li    r3,0 ; mtsrr1 r3                                ; MSR = 0 → IP = 0
  rfi
  ```
  - El id de núcleo: como los dos secundarios comparten vector, leer `PIR` (SPR 1023) después de habilitar PIRE, o despertar un núcleo cada vez y reescribir el trampolín entre medias.
  - **L2:** Nintendo pone `L2CR = 0` y lo activa más tarde (en código que no has visto). XNU `init750` **lee** L2CR con `firstBoot` y, si no está activo, **desactiva la función L2** en ese núcleo (funciona, pero sin L2 y más lento). Opciones: (a) aceptarlo al principio (lo más seguro); (b) después, invalidar y activar la L2 en el trampolín (`L2CR` con L2I, esperar L2IP = 0, luego L2E con el tamaño del núcleo: 2 MB en el 1 y 512 KB en el 2, `HID5` bits `L2CR`/`L2CR_L2SIZ`) — copiar la secuencia de Nintendo si aparece en el resto de la rutina ("bs0N").

## 32.3 Diseño del parche de `stwcx.` (errata)
**Principio:** cada `stwcx. rS,rA,rB` debe ir precedido de `dcbst rA,rB` sobre la misma dirección. Como no se pueden insertar instrucciones en el sitio, se sustituye por un salto a un stub:
```
stub_i:  dcbst  rA,rB
         stwcx. rS,rA,rB        ; pone cr0 como el original
         b      vuelta_i        ; = dirección original + 4
```
Salto de ida: `b stub_i` (relativo, ±32 MB) o `ba stub_i` (absoluto, < 32 MB). No toca ningún registro ni cr0.

**1) Kernel (165 sitios, 141 sin contar la commpage):**
- **Dónde poner los stubs (165 × 12 B ≈ 2 KB):** candidato principal, el segmento **`__HIB`** del propio kernel: física/virtual `0x7000–0xE000` (28 KB, `Kernel-__HIB` en `memory-map`), mapeado 1:1, ejecutable y alcanzable con `ba`. Contiene el código de hibernación, que en Wiintosh no se usa. **Comprobar antes** con `nm -n /mach_kernel` qué símbolos hay en `0x7000–0xE000` y que ninguno se llama fuera de hibernar (`hibernate_*`, `IOHibernate*`; en PPC también algo de `lowmem`); usar la parte final libre o la de funciones de hibernación. Alternativa: huecos de alineación en `__VECTORS` (`0x0–0x7000`), más arriesgado.
- **Dónde aplicarlo:** en **OpenBIOS** (`macosx/xnu.c`, junto a `xnu_patch_cpu_check`), sobre la imagen del kernel en memoria antes de saltar a XNU: sin concurrencia y con la tabla de símbolos disponible. Recorrer `__TEXT` (y `__HIB`/`__VECTORS` si tienen `stwcx.`) buscando la codificación de `stwcx.` (opcode 31, XO 150, Rc = 1: `(insn & 0xFC0007FF) == 0x7C00012D`), generar el stub y sustituir por `ba`. Con un contador en el log de OpenBIOS: esperado 165 (menos los que estén en las plantillas de la commpage, que se tratan aparte).
- **Excluir los `stwcx.` dentro de las plantillas de la commpage** (ver 2): con un salto no funcionarían en modo usuario.
- **Solo con `-wiismp`**: con un solo núcleo la errata no aplica y el kernel queda intacto.
- **Kexts cargados después** (los de Wiintosh, el de GX2, los de Apple): buscar `stwcx.` en `/System/Library/Extensions/*` con `otool`. Los propios, compilarlos con `dcbst` delante. Si algún kext de Apple lo usa, parchearlo al cargar (más complejo) o aceptarlo si es de poco uso.

**2) Commpage (atomic.s 12 + spinlocks.s 12):**
- xnu‑792 copia las plantillas a la commpage en `commpage_populate()` (`osfmk/ppc/commpage/commpage.c`), eligiendo variantes por CPU (UP/MP, 32/64 bits). **El código se ejecuta en modo usuario en `0xFFFF8000+`**: no puede saltar a stubs del kernel.
- Opción A (recomendada): **después** de `commpage_populate` y **antes** de despertar secundarios, desde `WiiPE` (un solo núcleo): localizar en la commpage (dirección de kernel de la commpage: `commPagePtr` o similar en xnu‑792; alternativa: leerla por su dirección fija en un mapeo del kernel) cada `stwcx.` y sustituirlo por `b stub` a un **stub dentro de la propia commpage**, en su espacio libre (entre rutinas o al final de la página), con salto relativo. Después, `dcbst`/`sync`/`icbi`/`isync`.
- Opción B: parchear las plantillas en la imagen del kernel desde OpenBIOS antes de que se copien. Solo es posible si hay hueco dentro de cada plantilla, porque las direcciones de la commpage son fijas (`_COMM_PAGE_*`).
- Ojo: es probable que XNU elija variantes **UP** de los spinlocks mientras solo hay 1 CPU al poblar la commpage. Con SMP hay que asegurarse de que se usen las MP, y parchearlas.

**3) Espacio de usuario (libSystem 5, CoreGraphics 20):**
- **Primero medir** con una prueba de estrés tras despertar el núcleo 1 (kernel y commpage ya parcheados): 2 hilos × 10⁷ `OSAtomicIncrement32` sobre el mismo contador (resultado esperado frente a obtenido); lo mismo con `OSAtomicCompareAndSwap32` y `pthread_mutex_lock` + incremento. Si hay pérdidas, la errata afecta de verdad en la práctica.
- **Si afecta:** parchear en disco `libSystem.B.dylib` y `CoreGraphics` con copia de seguridad:
  - stubs en la **holgura al final del segmento `__TEXT`** de cada binario (relleno hasta el límite de página; comprobar con `otool -l` que `filesize` deja hueco);
  - salto relativo `b` (el binario se carga entero, así que la distancia se mantiene);
  - volver a ejecutar `update_prebinding` si hace falta.
- 25 sitios son pocos: es manejable con una herramienta pequeña que valide cada sustitución (instrucción esperada, rangos) y guarde el binario original.
- **Mientras tanto:** el WindowServer (que usa CoreGraphics) es multihilo. Sin el parche, con 2 núcleos podría haber fallos raros de refcount → cuelgues o cierres del WindowServer. Probar primero con el sistema en uso normal y la prueba de estrés antes de dejarlo por defecto.

## 32.4 Plan SMP (sustituye a 30.5)
| # | Paso | Riesgo |
|---|---|---|
| 1 | Reiniciar (limpia `SCR` 18/19). `nm -n /mach_kernel` del rango `__HIB`; localizar la commpage y su espacio libre | ninguno |
| 2 | Parcheador de `stwcx.` del kernel en OpenBIOS (solo con `-wiismp`), stubs en `__HIB`; verificar arrancando **con 1 núcleo** (`-wiismp cpus=1`): el sistema debe funcionar igual (el parche es inocuo en UP) | bajo (se revierte con boot-args) |
| 3 | Parche de la commpage desde `WiiPE` (tras `commpage_populate`), también probado con 1 núcleo | bajo |
| 4 | `WiiPlatform -wiismp`: `numCPUs` según boot-args (28.1), IPIs (32.1), trampolín de Nintendo (32.2) sin L2, `WAKE(1)` | alto (foto si cuelga; quitar el flag) |
| 5 | Prueba de estrés de atómicos en usuario + uso normal | medio |
| 6 | Si hace falta: parche en disco de libSystem/CoreGraphics (32.3‑3) | medio |
| 7 | L2 en los secundarios; núcleo 2 | alto |

---

# PARTE 33 — Dónde poner los stubs (2026-09-27), resumen del Claude del Mac
- **`__HIB` no está libre:** `__HIB,__data` (0x7000–0xC00C) empieza con `_intstack` (la pila de interrupciones). Solo **`__HIB,__text` (0xC020–0xCD60, 3 392 B)** sería reutilizable (`hibernate_restore_phys_page`, `hibernate_machine_entrypoint`, `WKdm_decompress`, `hibernate_sum`, `hibernate_page_*`, `hibernate_kernel_entrypoint`). Caben 165 × 12 B = 1 980 B.
- **Commpage:** página `0xFFFF8000` usada hasta +0xFA8, 84 B libres, 7 `stwcx.` (variantes UP); página `0xFFFF9000` usada hasta +0x3F4, **3 080 B libres**, 0 `stwcx.`. `_commpage_populate` = 0xBCE70; `_commPagePtr32` = 0x36E000.
- Propuesta: parchear en caliente desde un kext (un núcleo, interrupciones desactivadas).

---

# PARTE 34 — Respuestas a la Parte 33

## 34.1 La clave: la commpage se decide en el arranque, así que SMP (y el parche) van en el arranque
xnu‑792:
- `osfmk/kern/startup.c`: `PE_init_iokit()` → … → **`commpage_populate()`**, antes de lanzar el espacio de usuario.
- `commpage_populate()` → `commpage_init_cpu_capabilities()` → `commpage_cpus()` → **`ml_get_max_cpus()`**, que **se duerme hasta que alguien fija el máximo de CPU** (`MAX_CPUS_SET`, `machine_routines.c` l.502). Quien lo fija es **`ml_init_max_cpus(numCPUs)`**, que se llama desde **`IOCPUInterruptController::initCPUInterruptController(numCPUs)`**, es decir, desde **`WiiCPU::start`**.
- Si `cpus == 1` → `_cpu_capabilities |= kUP` → se copian las variantes **UP** (`spinlock_32_*_up`, `memory_barrier_up`); si es > 1, las **MP**. Se hace **una sola vez** ("called once, during kernel initialization … before user-mode code is running").

⇒ **Con `-wiismp` y `numCPUs = 2` en `WiiCPU::start`, XNU elige solo las variantes MP de la commpage al arrancar.** No hay que forzar nada. Hacerlo en caliente, en cambio, dejaría la commpage UP, que no es válida con SMP. Volver a llamar a `commpage_populate` con usuarios en marcha no es seguro: se reescribe código que se está ejecutando. **Por eso el SMP real debe decidirse en el arranque (mkext de WiiPlatform con `-wiismp`), no en caliente.**

## 34.2 ¿Parchear el `__text` del kernel en caliente? Vale **como banco de pruebas en UP**, no como mecanismo definitivo
- **Escritura:** el texto del kernel está mapeado sin escritura. Escribe por un **mapeo físico propio** (`IOMemoryDescriptor::withPhysicalAddress(pa, len, kIODirectionInOut)` + `map()`), o con `ml_phys_write`. El kernel está 1:1 (VA = PA), así que la dirección física es la del símbolo.
- **Cachés**, por cada palabra escrita:
  1. `dcbst` sobre la línea (desde el mapeo por el que escribiste);
  2. `sync`;
  3. `icbi` sobre la **dirección de ejecución** (la VA del kernel);
  4. `sync; isync`.

  El 750 no tiene I‑cache coherente con la D‑cache. Hazlo con las interrupciones desactivadas, y **primero escribe y sincroniza los stubs**, y después sustituye los `stwcx.`.
- **BAT:** en xnu‑792 PPC los BAT solo se usan para la E/S (`PEMapSegment`) y el vídeo; el texto del kernel va por la tabla de páginas. Un mapeo físico propio no choca con eso [comprobar que la VA del mapeo no cae en un segmento con BAT].
- **Código ejecutándose:** con un solo núcleo y `EE = 0`, nadie ejecuta otra cosa mientras escribes. El caso "un hilo entre `lwarx` y `stwcx.`" es correcto, como dices. El kext que parchea no debe usar ninguno de los sitios parcheados durante el parche (evita llamadas a `IOLog`/locks en ese tramo).
- **Uso recomendado:** un kext de prueba que aplique el parche del kernel en UP (sin la commpage) para verificar el recuento (165 − los de la commpage que estén en `__TEXT`), que no rompe nada y que la lógica de generar/validar stubs es correcta. **La versión definitiva, la misma lógica ejecutada en `WiiPE::start`** (ver 34.4).

## 34.3 Memoria para los stubs del kernel
1. **`__HIB,__text` (0xC020–0xCD60)**: aceptable con condiciones.
   - Esas funciones solo se ejecutan al **hibernar/restaurar** (`IOHibernateSystemSleep` desde `IOPMrootDomain` si `hibernatemode ≠ 0`; `hibernate_machine_entrypoint` lo llama el booter al restaurar). En 10.4 `WKdm` solo sirve para la imagen de hibernación.
   - **Condiciones:**
     - Poner `sudo pmset -a hibernatemode 0` y desactivar el reposo (`pmset -a sleep 0`), porque `IOSleepSupported` aparece en `IOPMrootDomain`.
     - Antes de parchear, buscar con `otool -tv /mach_kernel` referencias (`bl`/`b`) a esas funciones desde fuera de `__HIB`, para asegurarse de que solo las usa el camino de hibernación.
     - Dejar **sin tocar** `_hashLookupTable` (0xCD60, datos) y `_gIOHibernateCurrentHeader`.
   - 1 980 B de 3 392: hay margen.
2. **Alternativa más limpia (si lo anterior da reparos):** que **OpenBIOS reserve una página de MEM1** (p. ej. `0x01FF0000`), la quite de `PhysicalDRAM` y la meta en el mapa de memoria de XNU como región del kernel ejecutable. Lo difícil es que XNU la mapee ejecutable; habría que hacerlo con el mismo mecanismo con que XNU mapea `__TEXT`. Más trabajo; solo si `__HIB,__text` no sirve.
3. **Para la commpage:** los stubs van en **la segunda página (`0xFFFF9000`, 3 080 B libres)** y se llega con `b` relativo: la commpage es contigua, a ±4 KB. Las variantes MP tendrán más `stwcx.` que las 7 UP; recuéntalos tras un arranque con `-wiismp`.

## 34.4 ¿OpenBIOS o WiiPlatform? → **WiiPlatform en el arranque, sin OpenBIOS**
Secuencia con `-wiismp` (todo en el mkext; sin el flag no se hace nada):
1. **`WiiPE::start`** (arranca muy pronto, con un solo núcleo, antes que `WiiCPU`): parchear los `stwcx.` del **kernel** (stubs en `__HIB,__text`, con la lógica validada en 34.2). Aplicar HID5 PIRE, y CAR/BCR según Nintendo/NetBSD.
2. **`WiiCPU::start`** (núcleo 0): `numCPUs = 1 + secundarios habilitados` (según `cpus=N`), `initCPUInterruptController(numCPUs)` → esto desbloquea `commpage_populate`, que elige las variantes **MP**.
3. **`WiiCPU::startCPU(núcleo 1)`**:
   - Esperar (con timeout) a que la commpage esté poblada: `_commPagePtr32 != 0` y la palabra de `_COMM_PAGE_CPU_CAPABILITIES` con el número de CPU correcto.
   - Parchear sus `stwcx.` (stubs en la página 2).
   - Escribir el trampolín en `0x08100100` y `SCR |= WAKE(1)`.
   - Riesgo de carrera: el espacio de usuario podría haber empezado ya a usar la commpage, pero con un solo núcleo activo y una escritura atómica por instrucción es seguro, igual que en el kernel.
4. Los **kexts que se carguen después** con `stwcx.`: los de Wiintosh/GX2, compilarlos con `dcbst`. Para los de Apple, recuento con `otool` sobre `/System/Library/Extensions/*/Contents/MacOS/*`; si hay alguno relevante, parchearlo al cargarse o aceptar el riesgo.

**Ventajas:** no hay que tocar OpenBIOS (del autor); todo el mecanismo va en un solo mkext; con el flag desactivado el sistema es idéntico al de siempre; y se prueba primero con `-wiismp cpus=1` (el parche activo, pero un solo núcleo: el sistema debe ir igual).

## 34.5 Plan (sustituye a 32.4)
| # | Paso | Riesgo |
|---|---|---|
| 1 | `pmset hibernatemode 0` + `sleep 0`; comprobar con `otool` que nada fuera de `__HIB` llama a sus funciones | ninguno |
| 2 | Kext de prueba en caliente (UP): parche del kernel con stubs en `__HIB,__text`; contar y validar; uso normal un rato; reiniciar para deshacer | bajo |
| 3 | Mover esa lógica a `WiiPE::start` con `-wiismp`; arrancar con `-wiismp cpus=1` | bajo (quitar el flag) |
| 4 | `WiiCPU` con `numCPUs` según boot‑args; con `-wiismp cpus=2`, comprobar solo que la commpage sale con variantes MP (vm_read) **sin despertar todavía el núcleo** (dejar que `startCPU` devuelva error; ojo: `IOCPUInterruptController` bloquearía el PI → antes de esta prueba, confirmar que el bloqueo solo afecta a `registerInterrupt` y no se cuelga el arranque; si se cuelga, saltar al paso 5) | medio |
| 5 | Parche de la commpage + trampolín + `WAKE(1)` (`-wiismp cpus=2`) | alto (foto; quitar el flag) |
| 6 | Estrés de atómicos + uso normal | medio |
| 7 | libSystem/CoreGraphics si hace falta; L2; núcleo 2 | alto |

---

# PARTE 35 — Plan 34.5, pasos 1–2 (2026-09-27), resumen del Claude del Mac
- `pmset -a hibernatemode 0 sleep 0` aplicado.
- **¿Quién llama a `__HIB,__text` desde fuera?** `hibernate_page_list_setall/discard`, `hibernate_set_page_state` y `hibernate_write_image` (desde `ml_ppc_sleep`), y **`cpu_machine_init` → `hibernate_machine_init`** (también en los secundarios), que en arranque normal vuelve enseguida. Conclusión: **`__HIB,__text` es reutilizable sin hibernación.**
- **Parche en caliente (UP) probado:** `WiiStwcxPatch`, **142 sitios**.
  - Cálculo: 165 − 23 dentro de plantillas de la commpage (`compare_and_swap*`, `atomic_*`, `spinlock_*`, `bigcopy_970`, `pthread_self_uftrap`).
  - Stubs en `0xC020–0xC6C8`; cada sitio pasa a `ba stub` por un mapeo físico, con `dcbst/sync/icbi/sync/isync`. 0 discrepancias.
  - Probado con `make`, 8 `dd` en paralelo y el WindowServer por la GPU: sin incidencias. Al descargarlo restaura el original.
  - Sitios: ~66 en `hw_vm.s`, `fpu/vec_switch`, locks (`mutex_*`, `lck_*`, `*Patch_isync/eieio`), `hw_atomic_*`, `OSAddAtomic`, `IOTrySpinLock`… Algunos se ejecutan con la traducción desactivada: `ba` funciona porque el kernel es V=R.

---

# PARTE 36 — Respuestas a la Parte 35

## 36.1 Tabla fija o escaneo en el arranque → **escaneo con símbolos + comprobación del recuento**
- **Hay tabla de símbolos en el arranque:** `WiiPE_Patcher.cpp` ya localiza la cabecera Mach‑O del kernel (`findKernelMachHeader`) y su `LC_SYMTAB`/`__LINKEDIT` (usa `nlist`). En `WiiPE::start`, que va muy pronto y antes de que se descarten los símbolos del kernel, `__LINKEDIT` sigue en memoria [comprobar: si en algún caso no estuviera, abortar el parche].
- **Algoritmo:**
  1. Recorrer `__TEXT,__text` buscando `(insn & 0xFC0007FF) == 0x7C00012D` (`stwcx.`).
  2. Excluir los sitios que caen **dentro de las plantillas de la commpage**. El rango de cada plantilla va desde su símbolo hasta el siguiente símbolo de `__text` (ordenar el `nlist` por dirección). Lista de nombres a excluir: la misma que usaste (`compare_and_swap*`, `atomic_*`, `spinlock_*`, `bigcopy_970`, `pthread_self_uftrap`…). Guárdala en el código como lista de prefijos.
  3. **Comprobación de seguridad:** si `sw_vers`/`version` del kernel es `8.11.0` y el recuento no es **142**, **no parchear** y registrar el error. Para otras versiones, desactivado por defecto (o solo con un boot‑arg de "forzar" y registrando la lista).
  4. Además, exigir que cada stub quepa en `__HIB,__text` (`0xC020–0xCD60`), comprobándolo con los símbolos de ese rango.
- **Ventaja:** no depende de una tabla precalculada, pero sigue siendo prudente fuera de 10.4.11. La tabla offline sirve como prueba cruzada en el primer arranque: registrar las 142 direcciones y compararlas.

## 36.2 Qué entra en el paso 3 (`-wiismp cpus=1`)
**Entra:**
- El parche de `stwcx.` del kernel en `WiiPE::start` (36.1), solo con `-wiismp`.
- El cálculo `numCPUs = 1 + secundarios` en `WiiCPU`. Con `cpus=1` vale 1: el mismo camino de siempre, pero ejercitando el código nuevo.
- El registro en el log de todo lo que se decide (flags, recuento de parches, `numCPUs`).
- `HID5 |= PIRE` (inocuo; lo hacen Nintendo y NetBSD). Leer `PIR` antes y después.

**Espera al paso 5 (justo antes del `WAKE`):**
- `CAR |= 0xFC100000` y `BCR = 0x08000000`: semántica desconocida y globales. Mínimo tiempo de exposición.
- Trampolín en MEM0, parche de la commpage, IPIs por SCR/PI, `INTMSK(1)`.

Criterio de éxito del paso 3: arranque normal, `hw.ncpu = 1`, WindowServer por la GPU, `make` y uso normal sin incidencias. Comparar tiempos con y sin `-wiismp`: el `dcbst` extra en cada `stwcx.` cuesta un poco.

## 36.3 `lwarx/stwcx.` en kexts
- **Contar antes de despertar el núcleo 1:**
  ```bash
  for f in /System/Library/Extensions/*.kext/Contents/MacOS/* \
           /System/Library/Extensions/*.kext/Contents/PlugIns/*.kext/Contents/MacOS/*; do
    n=$(otool -tv "$f" 2>/dev/null | grep -c 'stwcx\.'); [ "$n" -gt 0 ] && echo "$n $f"
  done | sort -rn
  ```
  y lo mismo con los kexts de Wiintosh/GX2 (fuentes: buscar `stwcx`/`lwarx`/`__sync_` en el código).
- **Lo esperable:** casi todos los kexts usan los atómicos **exportados por el kernel** (`OSAddAtomic`, `OSCompareAndSwap`, `IOLock*`, `IOSimpleLock*`, `lck_*`), que ya quedan parcheados en el kernel. Solo tendrán `stwcx.` propios los que lleven ensamblador en línea.
- **Qué hacer según el resultado:**
  - **0 o pocos, y en kexts que no se usan en la Wii U** (drivers de hardware Apple que no cargan): ignorarlos.
  - **En kexts cargados** (`kextstat`): parchearlos en `WiiCPU::startCPU` antes del `WAKE`, recorriendo `kmod_info` (dirección y tamaño de cada kext cargado). Los stubs tienen que estar a ±32 MB del kext (no hay `ba` a esas direcciones): reservar una página de stubs **por kext** junto a él (`kmem_alloc` no lo garantiza; alternativa: usar la holgura final del `__TEXT` del propio kext, como en espacio de usuario).
  - Kexts cargados **después** del `WAKE` no se cubren. Si alguno relevante tiene `stwcx.`, documentarlo o cargarlo antes.
  - Los propios (Wiintosh, GX2): si alguno tiene `stwcx.` en línea, añadir `dcbst` en el código fuente.
- Contar también en los binarios de usuario más usados (ya hecho: libSystem 5, CoreGraphics 20) y, por completitud, en `/usr/lib/*.dylib` y `/System/Library/Frameworks/*/…` con el mismo bucle, para saber el alcance real del espacio de usuario.

## 36.4 Plan (sustituye a 34.5)
| # | Paso | Riesgo |
|---|---|---|
| 1 | ✅ pmset + comprobación de `__HIB` | — |
| 2 | ✅ Parche en caliente (UP), 142 sitios | — |
| 3 | `WiiPE::start` con escaneo + símbolos + recuento de 142, `HID5 PIRE`, `numCPUs` según boot‑args; arrancar con `-wiismp cpus=1` | bajo |
| 4 | Recuento de `stwcx.` en kexts y bibliotecas (36.3) | ninguno |
| 5 | `-wiismp cpus=2`: CAR/BCR, parche de la commpage (MP), kexts si hace falta, trampolín, IPIs, `WAKE(1)` | alto (foto; quitar el flag) |
| 6 | Estrés de atómicos + uso normal | medio |
| 7 | libSystem/CoreGraphics si hace falta; L2; núcleo 2 | alto |

---

# PARTE 37 — Plan 36.4: paso 3 OK y paso 4 (2026-09-27), resumen del Claude del Mac
- **BootX en Wii U no pasa boot‑args** (el NVRAM de OpenBIOS arranca vacío; `com.apple.Boot.plist` no se lee). El SMP se activa con las claves **`WiiSMP` / `WiiSMPCPUs`** de la personalidad de WiiPE en el Info.plist del mkext (`-wiismp`/`-nowiismp` quedan por si acaso). El mkext se rehace con un script propio; el original está en BOOT como `.bak`.
- **Parche en el arranque:** `142 stwcx. sites to patch, 23 in commpage templates` → `patched 142 … 0 mismatches` (igual que la tabla offline). Lleva las comprobaciones de `_version` 8.11.0 y del recuento 142 (`-wiismpforce` para saltárselas).
- `HID5: 0x80000000 → 0xC0000000` (PIRE) sin problemas. **`mfspr 1007` antes de poner PIRE → excepción de programa (panic)**; se quitó la lectura.
- `hw.ncpu 1`, GPU y uso normal; `make` 10,2 s → 9,9 s: el parche no tiene coste medible.
- **Recuento fuera del kernel:**
  - Kexts: solo ATIRadeon9700 (22 `stwcx.`), que no se carga en la Wii U. Los de Wiintosh/GX2, ninguno.
  - Espacio de usuario: libSystem 5, CoreGraphics 20, CoreAudio 44, OSServices 37, Security 35, CoreMIDI 35, AudioToolbox 28, QuickTime 31, ColorSync 31, DesktopServicesPriv 32, OpenTransport 17, QuartzCore 12, HIToolbox 4, JavaScriptCore 111, DiskImages 171.
  - Cifras dudosas (probablemente datos): Kerberos 728, URLAccess 1108, CALCore 939, libwx 395.

---

# PARTE 38 — Respuestas a la Parte 37 (paso 5, `cpus=2`)

## 38.1 PIR (SPR 1007)
- WiiUBrew lista **PIR = SPR 0x3EF (1007)** en Espresso (no el 1023 del 604/74xx). La rutina de Nintendo **lo escribe** (`mtspr 1007, n`) después de poner `HID5 = 0xC0000000` (PIRE). O sea: con PIRE, el PIR es un registro que **fija el software**, y sin PIRE su acceso provoca una excepción de programa (lo que viste).
- **No hace falta para XNU:** cada núcleo sabe quién es por su `per_proc`, que XNU le pasa en `ResetHandler` (`cpu_start` → `&PerProcTable[cpu]`). El trampolín tampoco lo necesita si despiertas **los núcleos de uno en uno** (escribes el trampolín del núcleo 1, `WAKE(1)`, esperas a que XNU lo dé por arrancado y, más adelante, el del 2).
- Si quieres dejarlo igual que Nintendo: en el trampolín, tras `HID5 = 0xC0000000`, `li r3,<n>; mtspr 1007,r3`. Para comprobar en caliente que ahora se puede leer (PIRE ya está puesto desde el arranque), un kext con `mfspr 1007` debería devolver 0 o lo que dejara el loader. Si hay excepción → panic → reiniciar; es opcional.

## 38.2 Espacio de usuario: qué es imprescindible antes del núcleo 1
- **Imprescindible:** el **parche de la commpage** (variantes MP). A través de ella pasan los spinlocks de pthread y los `OSAtomic*` de libSystem en 10.4 PPC (`_COMM_PAGE_COMPARE_AND_SWAP*`, `ATOMIC_ADD*`, `SPINLOCK_*`), que son lo más usado.
- **Los `stwcx.` en línea de los frameworks:** no hay forma práctica de restringir los procesos de usuario a un núcleo en xnu‑792 (no existe afinidad para hilos de usuario; `thread_bind` solo es del kernel). Opciones:
  1. **Aceptar el riesgo, medirlo (paso 6) y parchear después por prioridad** (paso 7). Recomendado.
  2. Parchearlos en disco antes de arrancar con 2 núcleos (mucho trabajo por adelantado).
- **Filtrar falsos positivos** antes de decidir: un sitio real es un `stwcx.` precedido, **pocas instrucciones antes (≤ 16) y en la misma función**, por un `lwarx` con el mismo `rA,rB`. Con ese filtro, los recuentos de Kerberos/URLAccess/CALCore/DiskImages bajarán a su valor real.
- **Prioridad de parcheo en disco** (según uso con 2 hilos concurrentes en núcleos distintos):
  1. libSystem (5) y CoreGraphics (20), por el WindowServer;
  2. CoreAudio (44), AudioToolbox (28) y QuartzCore (12), porque el audio tiene hilos de E/S en paralelo;
  3. HIToolbox (4), OSServices (37) y Security (35);
  4. lo demás, si la prueba de estrés o el uso muestran fallos.

## 38.3 Orden concreto para `WiiSMP` con `cpus=2`
**⚠️ Recuperación primero:** sin boot‑args, si el arranque con 2 núcleos se cuelga, la única vuelta atrás es **editar la SD desde el Mac** (restaurar el `.bak` o poner `WiiSMP = false` en el plist del mkext). Tenlo preparado antes de probar, y deja **logs por IPC a Starbuck** (`WIIDBGLOG` con el boot‑arg de depuración activado por plist) en cada paso, para que la foto de la pantalla diga dónde se paró.

**1. `WiiPE::start`** (núcleo 0, antes que `WiiCPU`):
1. Parche de `stwcx.` del kernel (ya hecho).
2. `HID5 |= PIRE` (ya hecho).
3. **Solo si `WiiSMPCPUs > 1`:** `CAR |= 0xFC100000`, `BCR = 0x08000000`, `isync` (como Nintendo en el núcleo 0 antes de despertar).
4. **Crear el nub `PowerPC,Espresso@1`** bajo `/cpus` (`IOPlatformDevice` con `reg = 1`, `state = "stopped"`, mismo `compatible`/`device_type` que el @0) para que case otra instancia de `WiiCPU`.

**2. `WiiCPU::start` (núcleo 0):**
- `numCPUs = 1 + (WiiSMPCPUs − 1)` = 2 → `initCPUInterruptController(2)` → `ml_init_max_cpus(2)` → la commpage sale **MP**.
- `ml_processor_register(boot = true)` + `processor_start` (como ahora).

**3. `WiiCPU::start` (núcleo 1):** `ml_processor_register(boot = false, start_paddr = 0x100)` → `processor_start` → XNU `cpu_start` rellena `ResetHandler` → **`WiiCPU::startCPU`**:
1. Esperar, con un timeout de 5 s, a que la commpage esté poblada (`*_commPagePtr32 != 0` y en `_COMM_PAGE_CPU_CAPABILITIES` el campo `kNumCPUs = 2`, sin `kUP`).
2. **Parchear los `stwcx.` de la commpage** (variantes MP) con stubs en la página `0xFFFF9000`; `dcbst/sync/icbi/isync`.
3. Guardar los 64 bytes originales de `0x08100100` y escribir el **trampolín de Nintendo** (32.2) con `SRR0 = 0x100`; `flushDataCache` + `icbi`.
4. `INTMSK(1) = 0` (todavía sin IPIs; ver 5).
5. `SCR |= 1<<22` (`WAKE(1)`).
6. `return KERN_SUCCESS`. XNU duerme hasta `SignalReady`.

**4. Núcleo 1 despierto:** trampolín → `0x100` → `_start_cpu` → `allstart` (tabla ya parcheada para Espresso) → … → `PE_cpu_machine_init` → **`WiiCPU(1)::initCPU(false)`**:
1. `gCPUIC->enableCPUInterrupt(this)` → `enabledCPUs = 2` → se despiertan los `registerInterrupt` que estaban esperando (el del PI incluido).
2. Registrar el **vector 1** con un manejador solo de IPI (p. ej. `cpuNub->registerInterrupt(1, …)` tras `setCPUInterruptProperties(cpuNub)`; como ya son 2 CPU habilitadas, no se bloquea).
3. **Después** de registrar el vector: `INTMSK(1) = 1 << 21` (IPI del núcleo 1). Nunca habilites la máscara antes de tener manejador: la IPI no se reconocería y habría una tormenta de interrupciones.
4. `setCPUState(kIOCPUStateRunning)`.

**5. IPIs (en `WiiPlatform`):**
- `WiiCPU::signalCPU(target)`: `SCR |= 1 << (20 − target->getCPUNumber())`.
- Núcleo 0: en `WiiInterruptController::handleInterrupt`, **antes** que el PI: si `SCR & (1<<20)` → borrar en bucle, `INTSR(0) = 1<<20`, `ipi_handler()`. Habilitar `INTMSK(0) |= 1<<20` al registrar el PI.
- Núcleo 1: el manejador del vector 1 hace lo mismo con el bit 19 (`SCR`) y `INTSR(1) = 1<<21`.
- `cpu_sync_timebase` usa IPIs hacia el núcleo 0: el camino del núcleo 0 tiene que estar listo antes del `WAKE`.

**Diagnóstico si se cuelga** (foto):
- Último log antes de `WAKE` → el trampolín o el núcleo no llegó a XNU.
- Llega a `initCPU(false)` y se para → interrupciones/IPIs o timebase.
- Llega a `SignalReady` y se cuelga después → errata/atómicos o caché (primer sospechoso: L2 apagada en el núcleo 1, que no debería colgar; después, la errata en espacio de usuario).
- Opcional: que el trampolín escriba una marca en MEM0 (p. ej. `0x08100000 = 0xB00710AD`) antes del `rfi`, para saber si el núcleo llegó a ejecutarlo.

## 38.4 Plan (sustituye a 36.4)
| # | Paso | Riesgo |
|---|---|---|
| 5a | Preparar la recuperación por SD (mkext `.bak`, plist con `WiiSMP=false`) y los logs por IPC | ninguno |
| 5b | `WiiSMP` con 2 núcleos según 38.3 | alto (foto + restaurar la SD) |
| 6 | Estrés: `OSAtomicIncrement32`/`CompareAndSwap32`/`pthread_mutex` (por la commpage) + uso normal con audio | medio |
| 7 | Filtro `lwarx…stwcx.` (38.2) y parche en disco por prioridad | medio |
| 8 | L2 en el núcleo 1; núcleo 2 (`WAKE(2) = 1<<21`, IPI bit 18, `INTMSK(2) = 1<<22`) | alto |

---

# PARTE 39 — Paso 5b: `WAKE(1)` provoca una excepción 0x1700 en el núcleo 0 (2026-09-27), resumen
- Implementado (commit f6688c8 del fork del Mac, `WiiSMPCPUs = 2`):
  - `WiiPE`: parche del kernel, PIRE, CAR/BCR, nodo `@1`.
  - Subclase `WiiCPUInterruptController`, que puede dar por "habilitado" un secundario que falle.
  - `startCPU`: commpage MP parcheada (stubs en +0x1700, después de `_COMM_PAGE_END`); trampolín por un mapeo sin caché (HID5/HID4 del núcleo 0, HID0 sin ICE/DCE, HID2 `0x000F0000`, L2CR 0, marca `"bsS1"` en `0x08100000`, SRR0 0x100, SRR1 0); `INTMSK(1) = 0`; `SCR |= 1<<22` con EE = 0; espera de 200 ms a la marca.
- **Intento 1:** panic por `dcbf` en `0xE0` (la página 0 no está mapeada en el kernel). Se quitaron los `dcbf`.
- **Intento 2:** **`Unresolved kernel trap(cpu 0): 0x1700 - Thermal`** justo después del `mtspr SCR` con `WAKE` (PC en el sondeo del log por IPC, MSR `0x9030` con EE = 1). No se llegó a comprobar la marca del trampolín. La SD ha vuelto al mkext de 1 CPU.

---

# PARTE 40 — La excepción 0x1700 **es la IPI de Espresso**

## 40.1 Qué es 0x1700 en Espresso
**NetBSD, `sys/arch/powerpc/include/trap.h`:**
```c
/* The following are only available on 750/7400: */
#define EXC_THRM   0x1700   /* Thermal Management Interrupt */
/* The following are only available on IBM Espresso: */
#define EXC_IPI    0x1700   /* Inter-processor Interrupt */
```
**NetBSD, `sys/arch/evbppc/nintendo/machdep.c` (`cpu_startup`):**
```c
oea_install_extint(pic_ext_intr);                 /* vector 0x500 */
#ifdef MULTIPROCESSOR
if (wiiu_native) {
    ipi_latte_init();
    oea_install_extint_vec(pic_ext_intr, EXC_IPI); /* ¡el mismo manejador en 0x1700! */
}
#endif
```
⇒ En Espresso, las ICI **no llegan por 0x500 (excepción externa) sino por el vector 0x1700**, que en el 750 era el del *Thermal Assist*. NetBSD instala en 0x1700 el mismo código que en 0x500. Su `pi_get_irq` mira primero `SCR & IPI_PEND(cpu)`, reconoce la IPI y la despacha. Esto explica también por qué `INTSR` no reaccionó en A': la IPI no pasa por la causa del PI.

XNU no lo sabe: su vector 0x1700 (`lowmem_vectors.s` l.599) es:
```
. = 0x1700
mtsprg 2,r13 ; mtsprg 3,r11 ; li r11,T_THERMAL ; b .L_exception_entry
```
y `T_THERMAL` acaba en `Unresolved kernel trap`, que es lo que viste.

**Por qué llegó una IPI al núcleo 0 justo con el `WAKE`** [NO VERIFICADO]: o el despertar genera una ICI hacia el núcleo que despierta (un "ack"), o quedó puesto `IPI_PEND(0)` (bit 20) por el arranque de Nintendo/loader. En cualquier caso, **el núcleo 0 debe aceptar ICI por 0x1700 antes del `WAKE`**, y tolerar una ICI sin bit pendiente (espuria): registrarla y volver.

## 40.2 Solución: redirigir 0x1700 a la ruta de interrupción externa de XNU (1 instrucción)
El vector 0x500 de XNU (`lowmem_vectors.s` l.267) es:
```
. = 0x500
mtsprg 2,r13 ; mtsprg 3,r11 ; li r11,T_INTERRUPT ; b .L_exception_entry
```
Tienen la **misma forma**: basta cambiar la **3ª instrucción del vector 0x1700** (física `0x1708`) por la del 0x500 (física `0x508`), es decir, `li r11,T_THERMAL` → `li r11,T_INTERRUPT`. Así XNU trata la ICI como una interrupción externa: `interrupt()` → manejador de la CPU → `IOCPUInterruptController::handleInterrupt(source = núcleo)`:
- núcleo 0 → vector 0 → `WiiInterruptController::handleInterrupt` (que ya mira `SCR` bit 20 antes del PI);
- núcleo 1 → vector 1 → tu manejador de IPI (`SCR` bit 19).

Es lo mismo que hace NetBSD, pero sin copiar código.

**Cómo aplicarlo** (en `WiiPE::start`, solo con `WiiSMP`, antes de crear el nodo `@1`):
1. Leer las palabras físicas `0x1700..0x170C` y `0x500..0x50C` con `ml_phys_read`. **Comprobar** que `0x1700/0x1704` son `mtsprg 2,r13 / mtsprg 3,r11`, que `0x1708` es `li r11,<T_THERMAL>` y que `0x508` es `li r11,<T_INTERRUPT>` (misma codificación salvo el inmediato). Si no, abortar SMP.
2. `ml_phys_write(0x1708, palabra_de_0x508)`.
3. **Sincronizar cachés por dirección física:** `sync_cache64(0x1700, 16)` (xnu `osfmk/ppc/cache.s` l.175–194, trabaja con la traducción de datos desactivada). Es la solución al panic del intento 1: la página 0 no tiene VA en el kernel, así que nada de `dcbf/icbi` por VA sobre memoria baja.
4. Aplicar lo mismo si en algún momento hace falta vaciar el `ResetHandler` (física `0xE0`): `sync_cache64(0xE0, 16)`. Probablemente no hace falta: `cpu_start` lo escribe con `ml_phys_write`, que ya es coherente para la D‑cache, y el secundario lo lee con las cachés apagadas (tu HID0 sin ICE/DCE) desde memoria. Con `DCE = 0` en el secundario, la línea podría seguir **sucia en la D‑cache del núcleo 0** → **sí, hacer `sync_cache64(0xE0, 16)` en `startCPU` justo antes del `WAKE`** (y lo mismo para `PerProcTable[1]` y la pila/`per_proc` que lea el secundario al principio, si los lee con la caché apagada).

**Manejo en el núcleo 0 (y 1):**
- En el manejador: si `SCR & IPI_PEND(yo)` → borrar en bucle y llamar `ipi_handler()`. **Si no hay bit pendiente**: contarla como espuria, registrarla (limitado) y volver.
- **Riesgo de tormenta:** si la ICI fuera de nivel y su causa no se pudiera borrar, entraría en bucle. Primera prueba: registrar `SCR` y el número de ICI espurias en los primeros ms tras el `WAKE`.
- ¿Afecta `MSR[EE]` a 0x1700? NetBSD lo trata como una interrupción externa (con EE). Tu panic ocurrió con EE = 1. Supón que EE la enmascara; hacer el `WAKE` con EE = 0 y activar EE después de tener el vector redirigido.

## 40.3 THRM y la rutina de Nintendo
- **No toques THRM1–3 ni TIE:** en Espresso el *Thermal Assist* del 750 no se usa como tal, y Nintendo **reutiliza THRM3 como registro de puntero** (su `mfspr r4,thrm3` para la estructura "bs0x"). El vector 0x1700 es la IPI; desactivar "thermal" no tiene sentido.
- **`bl 0x240 … 0x314` de Nintendo antes de despertar:** conviene verlos, porque pueden contener la inicialización/invalidación de L1/L2 y la puesta de ICE/DCE que tu trampolín omite. **Pásamelos** (el desensamblado de `0x08000240–0x08000330` o toda la rutina desde `mem0.dis`, solo instrucciones) y los traduzco a un trampolín completo. Mientras tanto, el trampolín actual (cachés apagadas) es aceptable para llegar a XNU: `_start_cpu`/`init750` configura HID0 y cachés por su cuenta.

## 40.4 Orden actualizado para el siguiente intento
1. `WiiPE::start`: …lo de antes… + **redirección 0x1708** (40.2) con comprobación de las 4 palabras.
2. `WiiInterruptController`: manejo de ICI por `SCR` **tolerante a espurias** en el núcleo 0; `INTMSK(0) |= 1<<20` como NetBSD (inocuo).
3. `startCPU(1)`: commpage, trampolín, `sync_cache64` de `0xE0` (`ResetHandler`) y de los datos por núcleo que lea el secundario, `INTMSK(1) = 0`, `WAKE` con EE = 0, y reactivar EE. Esperar la marca 200 ms **registrando `SCR` y el número de ICI recibidas**.
4. Núcleo 1: vector 1 con el manejador de IPI y, después, `INTMSK(1) = 1<<21`.
- Si vuelve a colgarse: foto. Si el panic ya no es 0x1700, anotar el nuevo vector/PC.


---

# PARTE 41 — (resumen del informe del Mac) El núcleo 1 entra en XNU y se cuelga

- La redirección 0x1700 funciona. El núcleo 1 ejecuta el trampolín (marca visible); SCR tras WAKE = `0x80400000`.
- Entra en XNU (`initCPU(true)`) y ambas CPUs quedan habilitadas.
- **Síntoma:** una interrupción por núcleo y luego `processor_start(1)` no vuelve. Se queda en la manzana, sin panic.
- Estado del núcleo 1: HID4 `0x80000000` y HID5 `0xC0000000` copiados del 0 (no son los valores de Nintendo), L2CR = 0, cachés apagadas al inicio.
- Preguntas: bits de coherencia de HID2/4/5, L2 del núcleo 1, otras explicaciones del patrón, diagnóstico barato.

---

# PARTE 42 — Respuesta: es el handshake de `cpu_sync_timebase` sin coherencia

## 42.1 El patrón encaja exactamente (xnu-792 `osfmk/ppc/cpu.c`)
1. **Núcleo 1** (`cpu_sync_timebase`) crea `syncClkSpot` **en su pila**, envía la señal `SIGPcpureq/CPRQtimebase` al maestro (esa es la única IPI que recibe el núcleo 0) y espera en `while (!syncClkSpot.avail)`.
2. **Núcleo 0** (`cpu_timebase_signal_handler`, dentro de la interrupción y **con EE=0**) escribe `abstime`, pone `avail = TRUE` y espera en `while (!ready)`.
3. Si la escritura de `avail` del núcleo 0 se queda en su L1/L2 y el núcleo 1 no la ve (sin snooping, o el 1 lee desde su propia caché o sin caché), los dos giran para siempre.
   - El núcleo 0 queda atrapado **con interrupciones apagadas** dentro del handler, así que no hay más interrupciones, ni panic, ni watchdog. Es justo "1 interrupción por núcleo y parada".
   - La interrupción que recibe el núcleo 1 es probablemente el decrementador o la ICI inicial.

**Conclusión:** no es el ack del ICI (pregunta 3). El ack por SCR basta: si no bastara, veríamos una tormenta de interrupciones, no una parada limpia. Es **coherencia de datos**.

## 42.2 Bits de coherencia (pregunta 1)
| Registro | Nintendo (IOSU/Cafe) | NetBSD | Tu núcleo 1 |
|---|---|---|---|
| HID0 | `0x00110024` y luego ICE/DCE | igual más cachés | copiado |
| HID2 | `0x000F0000` | — | copiado |
| HID4 | `0xB3B00000` | `0xB1B00000` | `0x80000000` ❌ |
| HID5 | `0xC0000000 \| 0x7FFDC000` = `0xFFFDC000` | `0xE7FDC000` | `0xC0000000` ❌ |

- HID4 `0xB3B00000` = H4A | L2FM(64B) | BPD | SBE | ST0 | LPE | DBP | **L2MUM** | **L2_CCFI**.
  - **L2MUM** (modo multiunidad de L2) y **L2_CCFI** son los candidatos a "coherencia entre núcleos". **[NO VERIFICADO bit a bit]**
  - Nintendo y NetBSD ponen ambos siempre.
- HID5: `0x7FFDC000` incluye PIRE y los bits de L2 por núcleo (L2CR enable/size) y de snoop/UDMA.
- **Regla práctica:** copia **exactamente** los valores de Nintendo (tabla, columna 1) en el trampolín del núcleo 1, **antes** de encender cachés (HID0 ICE/DCE) y antes de saltar a XNU.
- **Núcleo 0:** que HID4 lea `0x80000000` **[verificar leyendo con `mfspr 1011` desde un kext]** implicaría que el 0 tampoco tiene L2MUM/CCFI.
  - Así que el 0 tampoco hace snoop de las escrituras del 1.
  - Hay que ponerlos también en el 0, **en caliente desde `WiiPE::start` antes del WAKE**, como hace NetBSD en `cpu_setup` (con la L2 activa).
  - Hazlo con interrupciones apagadas y la secuencia `sync; mtspr HID4; isync`. Primero pon solo los bits que faltan (`|= 0x33B00000`).
  - Si da miedo, prueba antes con L2MUM|L2_CCFI solos (`|= 0x00300000`). Si cuelga: recuperación desde la SD (WiiSMP=false).
- **No toques** L2FM ni BPD en caliente si difieren del valor actual (cambian la geometría de la L2); limita el OR a los bits de modo.

## 42.3 L2 del núcleo 1 (pregunta 2)
- Cada núcleo de Espresso tiene su **propia L2** (512 KB / 2 MB / 512 KB). No es compartida.
- Por eso el snooping entre L2 es lo que da coherencia, y `L2CR = 0` en el 1 es aceptable al principio: sin L2 no hay coherencia que perder en el 1.
  - Pero el 1 **sí** necesita L1 con snoop (HID4/HID5 correctos) y el 0 necesita snoop de su L2.
- Después, para rendimiento: invalidar la L2 del 1 (L2CR L2I, esperar a que L2IP=0) y poner L2E. El tamaño lo marca HID5, no el L2CR del maestro.
  - XNU copia el L2CR del maestro en `cacheInit` (`pfL2CR`). Si se copia con L2E sin invalidar antes, mete basura. **Revisa que el trampolín invalide o que `pfL2CR` del núcleo 1 quede a 0 hasta que lo hagas bien.**
- Rutina pendiente: pide al humano otra vez el volcado de Nintendo 0x08000240–0x330. Probablemente contiene justo esta secuencia de init de L2.

## 42.4 Diagnóstico barato (pregunta 4) — antes de tocar el kernel
**Sonda de coherencia en el trampolín, sin entrar en XNU:**
1. Elige una palabra en MEM2 (p. ej. dentro del buffer físico que ya usas para marcas).
2. En el núcleo 1, con los HID de Nintendo y **DCE encendido**, haz un bucle: `contador++; stw` (sin dcbst). Un segundo campo solo lo actualizas con `dcbst; sync` cada N vueltas.
3. En el núcleo 0 (kext), lee la palabra por una **mapeo con caché** y otro **sin caché** (IOMemoryDescriptor `kIOMapInhibitCache`) varias veces.
   - Con caché y cambia → hay snoop ✅.
   - Solo cambia sin caché o solo el campo con dcbst → escrituras visibles solo tras flush, **sin snoop**.
   - No cambia nunca → el 1 no avanza o escribe en otra dirección.
4. Repite en sentido inverso: el 0 escribe con caché y el 1 lo lee y pinta un color en el framebuffer según lo que ve. Esto modela exactamente `avail`.
5. Repite con y sin el `|= 0x00300000` en el HID4 del núcleo 0 para identificar el bit.

- Pintar dentro de `cpu_sync_timebase` también sirve, pero requiere parches al kernel. Hazlo solo si la sonda dice que hay coherencia y aun así se cuelga.
  - Pinta después de `cpu_signal`, dentro del bucle `avail`, y después de `ready`. En el handler del 0, pinta después de `avail = TRUE`.

## 42.5 Otra trampa: lwarx/stwcx. y cachés apagadas
- `cpu_signal` usa `hw_compare_and_store` (lwarx/stwcx.) sobre `MPsigpStat` del **destino**.
- Con DCE=0 en el 1, las reservas sobre memoria sin caché en 750-class **no son fiables**: stwcx. puede fallar siempre o tener éxito sin reserva.
  - El bucle `while (cpu_signal(...) != KERN_SUCCESS)` pasó una vez (el 0 recibió la señal), así que aquí no está la parada.
  - Pero asegúrate de que el 1 enciende ICE/DCE **antes** de `initCPU`/XNU. Una vez dentro, XNU asume cachés encendidas (`cacheInit` en `start.s`).

## 42.6 Orden recomendado
1. Leer y registrar HID0/2/4/5 y L2CR del núcleo 0 (kext, solo lectura).
2. Trampolín del núcleo 1: HID2 = `0x000F0000`, HID4 = `0xB3B00000`, HID5 = `0xFFFDC000`, HID0 = `0x00110024`, invalidar L1 (ICFI/DCFI), luego ICE|DCE. Mantener L2CR = 0 y `pfL2CR` del 1 a 0.
3. Núcleo 0 en `WiiPE::start`, antes del WAKE: HID4 `|= 0x00300000` (L2MUM|L2_CCFI) y `sync; isync`. **Pedir confirmación.**
4. Sonda 42.4 (sin XNU): validar coherencia en ambos sentidos.
5. Reintentar el arranque SMP. Si se cuelga, pintar dentro de `cpu_sync_timebase` (42.4 final).
6. Pedir al humano el volcado 0x08000240–0x330 para completar la init de L2.


---

# PARTE 43 — Rutina de Nintendo 0x08000240–0x330 (volcado de MEM0, del Mac)

- 0x240–0x280, **solo en el núcleo 0**: SCR |= 0x80000000, CAR |= 0xFC100000, BCR = 0x08000000, isync.
- 0x284–0x2F0, **en cada núcleo**: HID0 = 0x00110024, HID2 = 0x000F0000, HID4 = 0xB3B00000, HID5 |= 0x7FFDC000 (o |= 0x6FBD4300 si PVR&0xFFFF == 0x101), isync.
  - Espresso aquí es PVR 0x70010201, así que corresponde **0x7FFDC000**. Esto confirma los valores de 42.2/42.6.
- 0x2F4–0x330: MMCR0 = MMCR1 = 0, comprobación de la base sacada de THRM3 (bucle infinito si falla), punteros a su estructura, SPRG0 = 0.
- **No hay init de L2** en este tramo. Está en 0x314/0x480/0x5E4 (**no hace falta** para el paso 42.6: el núcleo 1 arranca con L2CR = 0).
- El paso 6 de 42.6 queda cumplido.


---

# PARTE 43b — (informe del Mac) La coherencia la activan CAR/BCR; el núcleo 1 se atasca en XNU

- Sonda 42.4 en caliente. Registros del núcleo 0: HID0 `0x0011C064`, HID2 `0`, HID4 `0x80000000`, HID5 `0xC0000000`, L2CR `0x80000000`.
- Solo con los HID de Nintendo en el núcleo 1 **no** hay coherencia en ningún sentido (ni con L2MUM/CCFI en el núcleo 0).
- **Con CAR |= 0xFC100000 y BCR = 0x08000000 en el núcleo 0 hay coherencia completa en los dos sentidos.** WiiPE ya los pone al arrancar con SMP.
- Arranque SMP:
  1. L1 del núcleo 1 invalidada y activa en el trampolín: el núcleo 1 entra en XNU, pero la memoria se corrompe (pilas libres con `0x55555555` → panic en `stack_alloc`).
  2. L1 del núcleo 1 apagada: no llega a `initCPU`. Tras 15 s el núcleo 0 sigue, pero no vuelve a recibir interrupciones externas y la rueda se congela.
- Los hilos de `IOCreateThread` en `WiiInterruptController::start` también daban panic en `stack_alloc`; se han quitado.

---

# PARTE 44 — Respuesta: `init750nb` enciende la L1 del núcleo 1 con basura

## 44.1 El orden real en xnu-792 (`osfmk/ppc/start.s` y `machine_routines_asm.s`)
Secuencia de `_start_cpu` → `allstart` en el núcleo 1:
1. Busca el PVR en la tabla y llama a `ptInitRout` en `doOurInit`. Para el 750 no es el primer arranque, así que usa **`init750nb`**:
   ```
   lwz   r11,pfHID0(r30)   ; HID0 del maestro = 0x0011C064 (ICE|DCE)
   sync ; mtspr hid0,r11 ; isync ; sync ; blr
   ```
   Esto enciende ICE/DCE **sin ICFI/DCFI**, así que la L1 del núcleo 1 arranca con etiquetas aleatorias, incluidas **líneas "sucias"**.
2. Después `cacheInit`: lee HID0 (r9). Si ICE o DCE están activos, **vacía la L1 por software**: `cisnlck` lee 1,5 × tamaño de L1 desde `0xFFF00000`.
   - Leer de `0xFFF00000` no hace daño: en la Wii U es el espejo de MEM0/SRAM y solo se lee.
   - El daño lo hace el **desalojo**: cada línea sucia de basura se escribe en su dirección física aleatoria. Ese es el `0x55555555` en las pilas del caso 1, y lo que en el caso 2 puede tocar código o datos compartidos.
   - Luego apaga la L1, la invalida y la **enciende bien** (ICE|DCE|ICFI|DCFI). A partir de ahí la L1 es correcta.
3. L2 en `cacheInit`: si L2CR (hardware) es 0 va a `ciinvdl2` con r3 = `pfl2cr`. **Si `pfl2cr == 0`, deja la L2 apagada y termina**; si no, la invalida con el valor del maestro.
4. `hw_setup_trans` / `hw_start_trans`, luego `ppc_init_cpu` → `cpu_init` → `PE_cpu_machine_init` (→ `initCPU(true)`), y `slave_main`.

**Por qué el caso 1 también se corrompía** aunque tu trampolín invalidara la L1: `init750nb` vuelve a escribir HID0 sin tocar ICFI/DCFI, y las líneas que el trampolín dejó sucias antes de que XNU conozca el mapa se escriben en `cacheInit`. Además, el valor de HID0 lo decide XNU, no tu trampolín.

## 44.2 Por qué el caso 2 congela al núcleo 0 (hipótesis coherente)
- `cacheInit` coge **`tlbieLock`** (lwarx/stwcx.) y lo suelta con un `stw` normal al final.
- Si la basura escrita en el paso 2 cae sobre código o datos del núcleo 1 (o sobre el propio lock, o sobre un `tlbie` en curso), el núcleo 1 se queda colgado **con `tlbieLock` cogido**.
- El núcleo 0 hace `tlbie` con ese lock y con **interrupciones apagadas** (`hw_rem_map`, `mapping_*`, `hw_protect`). Se queda girando para siempre: no hay más interrupciones externas ni panic. Encaja con tu contador a 0.
- **Comprobación barata:** tras los 15 s de `cpuFailedToStart`, lee `tlbieLock` desde el kext. Es una palabra en memoria baja (busca el símbolo `_tlbieLock` o la dirección absoluta `li r5,tlbieLock` en `cacheInit`). Si vale ≠0, confirmado.
- No es un problema de `tlbsync`: sin `pfSMPcap`, XNU no lo emite y tampoco es obligatorio en el 750 (NetBSD sí lo usa en Espresso MP, pero eso no cuelga).

## 44.3 Solución (pregunta 1): parchear el per_proc del núcleo 1 antes del WAKE
Es mejor que parchear `cacheInit`, porque el kernel queda intacto:
- `pf.pfHID0` del núcleo 1 = `0x0011C064 & ~0x0000C000` = **`0x00110064`** (sin ICE/DCE).
  - Así `init750nb` no enciende la L1, `cacheInit` ve r9 sin ICE/DCE, **se salta el vaciado por software** y hace el invalidar + encender limpio.
- `pf.l2cr` del núcleo 1 = **0** (y `pf.l2crOriginal` = 0). Así `cacheInit` deja la L2 del 1 apagada.
  - La L2 del núcleo 1 es de **2 MB**, no de 512 KB como la del 0 (`L2SIZ` distinto), así que copiar el L2CR del 0 sería incorrecto de todas formas. Se activa después en caliente, con invalidación y el tamaño correcto.
- Trampolín: HID de Nintendo con la **L1 apagada** (tu caso 2) y sin dejar nada en caché.
- Los per_proc de los secundarios se crean en `cpu_per_proc_alloc` / `cpu_start` y `pf` se copia del maestro. Parchéalos **en `startCPU` (justo antes del WAKE)**, con el puntero `per_proc` que ya tienes para el ResetHandler, y haz `sync_cache64` de la línea.

**Offsets** (no hay cabecera pública de `per_proc_info`). Sácalos del `mach_kernel` con `otool -tv` en la Wii U:
- `pfHID0`: busca `init750nb`, la secuencia `lwz r11,N(r30)` / `sync` / `mtspr 1008,r11` / `isync` / `sync` / `blr`. N es el offset. Aparece varias veces: coincide con `init750FXnb` (`lwz r13,N(r30)`).
- `pfl2cr`: en `cacheInit`, justo después de `mfspr r8,1017` va `lwz r3,N(r12)`.
- `pfl2crOriginal`: en `init750` (primer arranque) hay dos `stw r13,…(r30)` seguidos tras `mfspr r13,1017`; el primero es `l2crOriginal` y el segundo `l2cr`.
- Verifícalos leyendo el per_proc del maestro: `pfHID0` debe valer `0x0011C064` y `pfl2cr` `0x80000000`.

## 44.4 Otras dependencias del 750 en `_start_cpu` (pregunta 2)
| Punto | ¿Afecta a Espresso? |
|---|---|
| MSSCR0 / flush por hardware (`pfL1fab`, `pfL2fab`, `pfLClck`) | No: el 750 no tiene esos bits en la tabla, así que usa el camino por software |
| "ROM" en `0xFFF00000` (vaciado de L1 y L2) | Solo si la L1/L2 ya está activa al entrar. Con 44.3 no se usa |
| `tlbieLock` + 128 `tlbie` | Sí, es la zona de riesgo (44.2); con la L1 limpia debería ir bien |
| AltiVec / `dssall` | No (no hay `pfAltivec`) |
| `init750` (primer arranque) lee L2CR/HID0 | Solo en el maestro |
| Térmico (`ml_thrm_init`, THRM1-3) | No lo llama `_start_cpu`. **No tocar THRM3** (Nintendo lo usa como puntero) |
| `ppc_init_cpu`: SCOM/GUS | Solo en 64 bits |

## 44.5 Diagnóstico y orden (pregunta 3)
Primero sin parchear código:
1. Parchear `pfHID0 = 0x00110064` y `pfl2cr = pfl2crOriginal = 0` en el per_proc del núcleo 1 antes del WAKE (44.3). Trampolín con HID de Nintendo y L1 apagada.
2. Si sigue sin llegar a `initCPU`: a los 15 s lee `tlbieLock` y el `per_proc` del núcleo 1 desde el núcleo 0. XNU guarda datos útiles ahí, como `cpu_flags` y el estado de `hw_start_trans`/`SDR1`. Lee también la marca del trampolín.
3. Solo si hace falta, pinta cuadrados: parcha **en `WiiPE::start`** 3 o 4 puntos con `b` a stubs en `__HIB,__text` que escriban un color en el framebuffer físico (`0x8F000000`, modo real, sin caché → `stw` + `dcbf` no hace falta con la L1 apagada; con la L1 activa usa `dcbst`) y vuelvan.
   - Puntos: entrada de `cacheInit`, tras `cinoSMP` (lock suelto), tras `hw_start_trans` y la entrada de `ppc_init_cpu`.
   - Los stubs no deben usar la pila (r0/r2/r3 ya salvados en SPRG o en registros libres de ese punto).
4. Cuando llegue a `initCPU`, recuerda que `cpu_sync_timebase` ya tiene coherencia gracias a CAR/BCR (43b).
5. Después: activar la L2 de 2 MB del núcleo 1 en caliente (invalidar, `L2SIZ` correcto, L2E).


---

# PARTE 45 — (informe del Mac) El núcleo 1 arranca en XNU; las interrupciones externas se congelan

- `startCPU` recibe el per_proc **virtual** del núcleo 1. `_PerProcTable` está en 0x365000 (entradas de 16 B). `pfHID0` está en +0xE0 y `pfl2cr`/`pfl2crOriginal` en +0x110. En el maestro `pfl2cr` vale 0, porque `init750` no reconoce el L2SIZ de Espresso. Se parchean en el núcleo 1.
- **El timebase es compartido entre núcleos.** Las escrituras de TB en `__start_cpu` y `cpu_sync_timebase` se han cambiado por nop.
- Balizas en lowGlo 0x5F00: el núcleo 1 moría al activar la traducción. Faltaban 7 stwcx. en `__VECTORS` (ya parcheados). Con los HID de Nintendo moría; con HID4/HID5 copiados del 0 (y HID2 sin tocar) pasa.
- **smp21**: `processor_start(1)` devuelve KERN_SUCCESS. Los contadores de interrupciones externas se congelan (núcleo 0 ≈ 32–63, núcleo 1 ≈ 8–15). La rueda sigue girando, pero no pasa de la manzana y no hay red.
- IPI: `signalCPU` hace `SCR |= 1<<(20-n)` con IOSimpleLock y EE=0. La recepción en 0x1700 hace `handleInterrupt(source=cpu)`: borra el bit (con lock) y llama a `ipi_handler`; en el source 0 llama también a super (PI). INTMSK(1)=0.

---

# PARTE 46 — Respuesta: la causa más probable son las máscaras de Latte/PI, no el núcleo 0

## 46.1 El núcleo 0 probablemente **no** está colgado
- La rueda (`vc_progress`) la mueve un callout de reloj que puede correr en cualquier núcleo, y el contador del núcleo 0 solo cuenta **externas**. Que se congele no demuestra EE=0: también encaja con que **las fuentes de Latte/PI se quedaron enmascaradas**.
- En ese caso el disco (SD/USB), la red y el resto dejan de interrumpir. Los hilos de IOKit esperan E/S para siempre, el arranque no avanza y no hay panic. Los dos núcleos siguen vivos, en reposo (doze) y despertados por el decrementador.
- Además, si todo espera E/S casi no hay ASTs, y por eso también se congela el contador de IPIs del núcleo 1.
- Si el núcleo 0 estuviera girando con EE=0 dentro de un `hw_lock_lock`, lo normal sería un panic "simple lock deadlock/timeout", y no lo hay.

## 46.2 El fallo: read-modify-write de la máscara sin spinlock (osx-drivers)
`LatteInterruptController.cpp` y `WiiInterruptController.cpp` (PI/Cafe):
- `disableVectorHard()`: `mask = readReg32(Mask0); mask &= ~bit; writeReg32(Mask0, mask);`
- `enableVector()`: igual, con `|=`.
- `handleInterrupt()` llama a `disableVectorHard()` desde el núcleo 0 (vector con `interruptDisabledSoft`).
- `IOInterruptController::enableInterrupt()` (xnu `IOInterruptController.cpp:270`) llama a `enableVector()` **desde el hilo que lo pida**, por ejemplo el workloop de un `IOInterruptEventSource`. Ese hilo ahora puede estar en el **núcleo 1**.

Con un solo núcleo esto era seguro, porque el manejador corre con EE=0 y nadie más toca el registro. Con dos núcleos:
```
núcleo 0 (handler, vector A)      núcleo 1 (workloop, vector B)
m = Mask0   (A=1,B=0)
                                  m' = Mask0  (A=1,B=0)
Mask0 = m & ~A  (A=0,B=0)
                                  Mask0 = m' | B   (A=1,B=1)  ← A reactivado sin querer
```
o, al revés, **B se pierde**: B queda enmascarado para siempre porque `interruptDisabledHard` ya es 0 y nadie volverá a llamar a `enableVector(B)`.
- `IOInterruptEventSource` hace disable/enable **en cada interrupción** (normalInterruptOccurred → `disableInterrupt`; `checkForWork` → `enableInterrupt`). Así que la carrera es muy frecuente en cuanto el planificador lleva workloops al núcleo 1. Encaja con que el congelamiento llegue poco después de `processor_start`.
- Los controladores de Apple con MP (OpenPIC/MPIC) tienen **un registro por fuente**, sin RMW; por eso IOKit no lo protege. Hollywood/Latte/PI tienen una sola palabra de máscara compartida.

**Arreglo** (en los dos controladores, y también en Hollywood por coherencia):
1. Añadir un `IOSimpleLock *maskLock` (spin, no `IOLock`).
2. Mantener una **máscara sombra** en memoria (`shadowMask0/1`) y no leer el hardware para hacer el RMW.
3. En `enableVector`, `disableVectorHard` y cualquier escritura de máscara:
   ```cpp
   IOInterruptState st = IOSimpleLockLockDisableInterrupt(maskLock);
   shadowMask0 |= bit;            // o &= ~bit
   writeReg32(kWiiLatteIntRegPPCInterruptMask0, shadowMask0);
   eieio();
   IOSimpleLockUnlockEnableInterrupt(maskLock, st);
   ```
   `IOSimpleLockLockDisableInterrupt` es válido tanto desde el manejador (EE ya a 0) como desde un hilo.
4. En `handleInterrupt`, usar `shadowMask` en vez de leer la máscara (o leerla bajo el lock).
5. El lock usa `hw_lock_lock` (lwarx/stwcx. del kernel ya parcheados con dcbst).

## 46.3 Diagnóstico barato antes del arreglo (pregunta 2)
No hay forma de leer el PC de otro núcleo en Espresso (no hay registro de depuración cruzado accesible). En su lugar:
1. **Contadores de XNU por CPU:** cada `per_proc` tiene `hwCtr` (contadores de excepciones por tipo, incluidos los decrementadores).
   - Desde el núcleo que dibuja las balizas, vuelca el per_proc del núcleo 0 (y del 1) **dos veces con 1 s de diferencia** y compara.
   - La palabra que sube unos 100/s (HZ) es el contador de decrementadores. Si sube en el núcleo 0 → el núcleo 0 **vive con EE=1**.
2. **Estado de las máscaras en el momento del congelamiento:** lee y pinta `Latte PPC0 Cause0/1` y `Mask0/1`, y `PI INTSR(0)`/`INTMSK(0)`.
   - Si `cause & ~mask` ≠ 0 en un bit que debería estar habilitado (el vector tiene handler registrado y no está soft-disabled), **46.2 queda confirmado**.
   - Compáralo con `vectors[i].interruptDisabledSoft/Hard`.
3. Si el paso 1 dice que el núcleo 0 **no** avanza, entonces sí está girando con EE=0. En ese caso añade balizas en `hw_lock_lock`/`hw_lock_mbits` (dirección del lock en SPRG o en lowGlo).

## 46.4 IPI: ack como NetBSD y otros detalles (pregunta 1b)
- NetBSD (`evbppc/nintendo/pic_pi.c`, `pi_ipi_ack`) **repite** el borrado hasta que el bit lee 0:
  ```c
  do { mtspr(SCR, spr & ~IPI_PEND(cpu)); spr = mfspr(SCR); } while (spr & IPI_PEND(cpu));
  ```
  Haz lo mismo (bajo tu lock). Una sola `mtspr` puede no bastar si el otro núcleo escribe SCR a la vez.
- NetBSD procesa **primero** el IPI (SCR) y después la PI en la misma entrada. Los IPIs son su IRQ 20+n ("MB_CPU(n)") con afinidad por núcleo.
- NetBSD **no** usa lock para enviar (`mtspr(SCR, mfspr(SCR)|mask)`), pero tu lock no hace daño si todos los RMW de SCR (WAKE incluido) lo usan.
- Protocolo de XNU (`cpu.c`): `cpu_signal` es **asíncrono** (0,5 ms de timeout para coger `MPsigpStat` y vuelve). Si un IPI se pierde, el `MPsigpStat` del destino se queda en "mensaje pendiente". Los siguientes SIGPast/SIGPwake se **fusionan** y devuelven éxito sin mandar nada, así que ese núcleo ya no recibe más IPIs y el contador se congela sin cuelgue.
  - Comprobación: lee `MPsigpStat` del per_proc del núcleo 1 cuando se congele. Si tiene `MPsigpMsgp`, hay un IPI perdido.
  - Consumidores síncronos que esperan a otro núcleo: solo `cpu_sync_timebase` (ya pasado), `cpu_broadcast` (solo `pms.c`, no se usa en el 750) y SIGPdebug (debugger). Ninguno encaja con un cuelgue a mitad de arranque.
- (c) `pfSMPcap`/`tlbsync`: XNU no espera nada por ello. Sin `pfSMPcap` simplemente no emite `tlbsync`. La difusión de `tlbie` entre núcleos es un riesgo aparte **[NO VERIFICADO]**: provocaría corrupción, no un congelamiento limpio.
- Reposo: `WiiCPU` registra `supports_nap = false`, así que XNU hace **doze** (el 750 tiene `pfCanDoze`). NetBSD también elige DOZE para Espresso (`cpu_subr.c`), no NAP. Correcto; **no actives nap** (en nap el 750 no hace snoop).

## 46.5 stwcx. restantes (pregunta 3)
- **Commpage UP:** con `ml_get_max_cpus() > 1`, `commpage_populate` copia solo las variantes MP. Las UP **no llegan a la commpage**, así que no hace falta parchearlas.
  - Compruébalo: en `_cpu_capabilities` (commpage 0xFFFF8010) el bit `kUP` (0x8000) debe estar a 0.
- **Kexts:** ya se comprobó que en el mkext solo tiene stwcx. ATIRadeon9700, que no se carga. Pero **kextd carga más kexts después** desde `/System/Library/Extensions`. Pasa el escáner (lwarx con el mismo rA,rB a ≤16 instrucciones) por **todos los binarios que aparezcan en `kextstat`** tras un arranque UP completo. Los kexts normales usan `OSAddAtomic`/`IOSimpleLock` del kernel, que ya están parcheados.
- **Userland** (libSystem 5, CoreGraphics 20, CoreAudio 44…): no causa un congelamiento del kernel, pero **sí** puede corromper locks de procesos en cuanto corran hilos en los dos núcleos. Déjalo para después de arrancar. Opción rápida: parchear en disco (con copia) solo `libSystem.B.dylib` primero.

## 46.6 Orden recomendado
1. **Diagnóstico** 46.3 pasos 1 y 2 (sin cambiar lógica): ¿el núcleo 0 vive?, ¿`cause & ~mask` ≠ 0?, ¿`MPsigpStat` del núcleo 1 con Msgp?
2. **Arreglo 46.2** (maskLock + máscara sombra) en Latte, PI/Cafe y Hollywood. Este cambio es bueno también para upstream.
3. **Ack del IPI en bucle** (46.4).
4. Reintentar el arranque SMP. Si pasa de la manzana, escanear los kexts de `kextstat` (46.5) y después libSystem.
5. Opcional: escribe los valores de HID del núcleo 1 idénticos al 0 (lo que funciona). Los de Nintendo quedan aparcados. HID2 `0x000F0000` activa excepciones de error de DMA/locked cache (DCHEE/DNCEE/DCMEE/DQOEE en Gekko) **[NO VERIFICADO en Espresso]**, lo que podría explicar que muriera al traducir.


---

# PARTE 47 — (informe del Mac) Vectores de Latte pendientes y enmascarados

- Nueva herramienta: capturadora Elgato HD60 S+ con OBS; `screencapture -l <id>` de la ventana del proyector.
- smp23 (maskLock + sombra + ack del IPI en bucle): el hilo de diagnóstico **corre en el núcleo 0** y su latido avanza, así que el núcleo 0 vive.
  - PI: cause `0x00010000` (bit 16 siempre activo), mask `0x01000010`.
  - **Latte: cause0 `0x01010000` (vectores 16 y 24), mask0 `0x00000080` (solo el 7)**. Dos dispositivos piden interrupción y están enmascarados.
  - Interrupciones externas: núcleo 0 ≈ 4–7, núcleo 1 ≈ 2–3.
- smp24 (volver a habilitar dentro de `handleInterrupt` si ya no está soft-disabled): todas las máscaras a 0, el latido se para y la rueda desaparece. **Peor.**

---

# PARTE 48 — Respuesta: el workloop se queda en el `next_thread` de un núcleo 1 dormido

## 48.1 Por qué quedan soft-disabled (pregunta 1)
Cadena en xnu-792:
1. Llega la interrupción del SDHC (Latte 16/24) al núcleo 0. `IOInterruptEventSource::disableInterruptOccurred` hace `prov->disableInterrupt()` (soft) y `signalWorkAvailable()`.
2. En la siguiente interrupción de esa fuente, el manejador ve `interruptDisabledSoft` y la enmascara (`Hard`). Esto es correcto y esperado.
3. `signalWorkAvailable` despierta el hilo del workloop. En `thread_setrun` (`kern/sched_prim.c:1985-2030`):
   - Si el `last_processor` del hilo está **IDLE**, o si hay **cualquier procesador en `idle_queue`**, pone `processor->next_thread = hilo`, `state = PROCESSOR_DISPATCHING`.
   - Después llama a `machine_signal_idle(processor)`, que manda un **IPI `SIGPwake`** (solo si `pfCanDoze|pfWillNap`).
   - El núcleo 0 está ocupado en la interrupción, así que el elegido es casi siempre el **núcleo 1 en reposo**.
4. Si el núcleo 1 no sale del reposo, el hilo se queda en `processor[1]->next_thread` **para siempre**. Nadie más lo coge, porque ya no está en ninguna cola. Así, `checkForWork` → `enableInterrupt` nunca ocurre y los vectores quedan soft-disabled y enmascarados.
   - Es exactamente lo que ves: el núcleo 0 vive y los hilos que ya estaban en su cola siguen corriendo (el latido), pero cada workloop que pasa por el núcleo 1 se pierde.

Por qué el núcleo 1 no despierta:
- `machine_idle` pone **doze** (HID0 DOZE + MSR[POW]), porque el 750 tiene `pfCanDoze` y `supports_nap=false`.
- Un núcleo en doze sale con: interrupción externa, **decrementador**, SMI o machine check. Que la **ICI de Espresso (0x1700)** despierte de doze está **[NO VERIFICADO]**. En el 750, 0x1700 es la interrupción térmica.
- En XNU un núcleo ocioso sin temporizadores pendientes pone el decrementador **muy lejos** (etimer, no hay tick periódico). Así que depende del IPI.
- NetBSD también usa doze en Espresso, pero tiene un **tick periódico (HZ)**: el decrementador despierta al núcleo cada 10 ms y esconde el problema.
- Otra posibilidad: el IPI sí llega, pero `MPsigpStat` del núcleo 1 quedó con un mensaje pendiente (46.4) y los SIGPwake se fusionan sin enviarse.

## 48.2 Por qué smp24 lo empeora (pregunta 2)
- El protocolo de IOKit (`IOInterruptController.cpp:270-331`: Soft / Hard / `interruptActive` / `while (interruptActive)`) **ya es correcto en SMP**. No hace falta tocarlo; lo único que necesitaba lock era el RMW de la máscara (46.2, ya hecho).
- Rehabilitar la fuente **dentro del manejador**, cuando es de nivel y sigue pendiente (el driver aún no ha servido el dispositivo, porque su workloop no ha corrido), hace que vuelva a dispararse nada más salir.
  - El resultado es una **tormenta** en el núcleo 0 con el mismo vector (disable → enable → disable…), que come todo el tiempo de CPU. Por eso se para el latido.
  - En el cruce con el camino normal de otro núcleo, la sombra acaba a 0.
  - La cascada PI 24 cae por el mismo camino: el Latte se registra como "dispositivo" del PI.
- **Revierte smp24 por completo.**

## 48.3 Prueba barata de si el núcleo 1 ejecuta (pregunta 3)
Sin código en el núcleo 1:
1. Desde tu hilo de diagnóstico (núcleo 0) lee cada segundo el `per_proc` del núcleo 1 (dirección virtual de `PerProcTable[1]`):
   - `hwCtr.hwDecrementers` y `hwCtr.hwExternals` (en `struct hwCtrs` el orden es: hwInVains, hwResets, hwMachineChecks, hwDSIs, hwISIs, **hwExternals**, hwAlignments, hwPrograms, hwFloatPointUnavailable, **hwDecrementers**…). Busca el offset de `hwCtr` volcando el per_proc del núcleo 0 dos veces: la palabra que sube como tu contador de externas es hwExternals, y 4 palabras después va hwDecrementers.
   - `MPsigpStat`: si `MPsigpMsgp` está activo y no cambia → IPI perdido o no consumido.
   - `processor[1]->state` / `next_thread`: están en la `struct processor`; más fácil, pinta `pp->...->active_thread`. Si ves `next_thread != 0` con el estado DISPATCHING durante segundos → confirmado.
2. Con eso sabes: si hwDecrementers[1] no sube, el núcleo 1 duerme y no despierta. Si hwExternals[1] no sube tras nuevos SIGPwake, el IPI no le llega o no lo despierta de doze.

## 48.4 Prueba decisiva: quitar el doze
- `machine_idle` (`machine_routines_asm.s:~830`) hace `bt pfCanDozeb,yesnap` leyendo **SPRG2** (las features vivas). El per_proc se reescribe en `allstart` (`stw r17,pfAvailable` + `mtsprg 2`), así que **parchear el per_proc antes del WAKE no sirve**.
- Parchea esa instrucción `bt pfCanDozeb,yesnap` (bit CR 6) por un **`nop`** en `WiiPE::start`. Así ningún núcleo entra en doze: `machine_idle` va a `nonap`, reactiva EE y vuelve, y el bucle ocioso **vuelve a mirar `next_thread` continuamente**.
  - Búscala en `otool -tv` de `_machine_idle`: tras `lis r4,hi16(dozem)` (`lis r4,0x80`).
  - Coste: los núcleos ociosos giran en vez de dormir (más consumo y calor en la Wii U; vigila la temperatura, sin tocar THRM).
- Si con esto **arranca**, el problema es "la ICI no despierta de doze" (o IPIs perdidos). Soluciones definitivas, por orden:
  1. Quedarse sin doze en el núcleo 1 (y mantener doze en el 0, que despierta con la PI). Hace falta un parche que mire el número de CPU: stub en `__HIB` que compruebe `PP_CPU_NUMBER` en SPRG0.
  2. Un tick periódico en los secundarios (decrementador acotado, p. ej. 10 ms) como NetBSD.
  3. Averiguar si algún bit de HID/SCR hace que la ICI despierte de doze **[NO VERIFICADO]**.

## 48.5 Orden
1. Revertir smp24. Mantener maskLock + sombra + ack en bucle.
2. Añadir al diagnóstico: hwDecrementers/hwExternals de los dos núcleos, `MPsigpStat` del núcleo 1 y `next_thread`/estado del procesador 1.
3. Arranque con `bt pfCanDozeb` → nop (48.4).
4. Según el resultado, elegir la solución de 48.4 y seguir con 46.5 (kexts de kextstat, luego userland).


---

# PARTE 49 — (informe del Mac) Sin doze no se arregla; máscaras de referencia

- smp24 revertido. Doze anulado: `_machine_idle+0x58` (0xAF3F8, `beq cr1,+0x10`) → nop.
- Referencia con 1 núcleo: PI mask0 `0x01000050`; Latte mask `0x000000A0 / 0x00000008`.
  - Latte 16 y 24 están **siempre** pendientes y sin usar (la Parte 47 era una pista falsa).
  - En SMP faltan **PI 6** y **Latte 5**: quedan soft-disabled y enmascarados.
- hwCtr del núcleo 0: `+0x814` = hwExternals (+43/s), `+0x824` = hwDecrementers (+337/s).
- smp26: `processor_start(1)` **no volvió** (sin cuadrado 15) y `registerInterrupt(0)` del núcleo 0 tampoco (sin cuadrado 14). El núcleo 1 sí pasó por initCPU y enableCPUInterrupt (11, 12, 23). **No determinista.**

---

# PARTE 50 — Respuesta: dos carreras en IOCPU y un núcleo 1 que no vuelve al bucle ocioso

## 50.1 Sin doze, las IPI dan igual: el núcleo 1 no está en `idle_thread` (pregunta 1)
`idle_thread` (xnu `kern/sched_prim.c:2537`):
```c
while (*threadp == THREAD_NULL && *gcount == 0 && *lcount == 0) {
    ... machine_idle();      // sin doze: reactiva EE y vuelve al momento
    (void)splsched();
}
```
- Con el doze anulado, el bucle **consulta `processor->next_thread` sin parar**. Si `thread_setrun` pone ahí el workloop (`sched_prim.c:1996/2021`), el núcleo 1 lo recoge **sin necesitar ninguna IPI**.
- Si aun así PI 6 / Latte 5 quedan soft-disabled, el núcleo 1 **no está ejecutando el bucle ocioso**. Hay tres casos:
  - (a) está parado con EE=0 (girando en un lock o en un handshake);
  - (b) está ejecutando un hilo que nunca cede (bucle dentro de un driver);
  - (c) ha muerto (excepción en bucle o salto a basura).
- El scheduler sigue viendo al procesador 1 como IDLE o RUNNING y le **despacha hilos que nunca corren**. Cada workloop que cae ahí se pierde: primero PI 6 y Latte 5, y el arranque se para.
- Las IPI (SIGPast/SIGPwake) llegan con EE=1 si el núcleo está vivo; no son la causa ahora.

## 50.2 La no-determinación: carreras reales en `IOCPUInterruptController` (pregunta 2)
`xnu/iokit/Kernel/IOCPU.cpp:387-432`:
```c
void enableCPUInterrupt(IOCPU *cpu) {
  ml_install_interrupt_handler(...);
  enabledCPUs++;                                  // (1) no atómico
  if (enabledCPUs == numCPUs) thread_wakeup(this);
}
IOReturn registerInterrupt(...) {
  ... IOUnlock(vector->interruptLock);
  if (enabledCPUs != numCPUs) {                   // (2) comprobación...
    assert_wait(this, THREAD_UNINT);              //     ...y espera NO atómicas
    thread_block(THREAD_CONTINUE_NULL);
  }
}
```
Y `WiiCPU::initCPU(true)` (osx-drivers `WiiCPU.cpp:133-150`) hace `enableCPUInterrupt(this)` y luego `cpuNub->registerInterrupt(0, …)`, **en cada núcleo**.
- **Wakeup perdido (2):**
  1. El núcleo 0 (en su `initCPU`) lee `enabledCPUs == 1` y decide dormir.
  2. **Antes** de su `assert_wait`, el núcleo 1 ejecuta `enabledCPUs++` y `thread_wakeup(this)`. Nadie espera todavía, así que el wakeup se pierde.
  3. El núcleo 0 hace `assert_wait` + `thread_block` y **duerme para siempre**. Es el cuadrado 14 ausente.
- Con un solo núcleo, o en los Mac de Apple (donde el esclavo tarda mucho más en llegar ahí), la ventana nunca se abre. En Espresso, con el WAKE casi inmediato, a veces sí. **Esto explica que dependa del arranque.**
- El `++` no atómico (1) solo importa si dos núcleos lo ejecutan a la vez. Hoy el núcleo 0 lo hace mucho antes, pero con 3 núcleos sería otra carrera.
- El cuadrado 15 ausente (`processor_start(1)` no vuelve) es la otra cara: el hilo que lo llamó duerme en `cpu_start` esperando `SignalReady` del núcleo 1 (`cpu.c:363-371`). El núcleo 1 no llegó a `cpu_flags |= SignalReady` (`cpu.c:180-190`); se quedó entre `initCPU` y el final de `cpu_machine_init`, es decir:
  - en `registerInterrupt(0)` de **su** `initCPU` (IOTakeLock es un mutex, llamado aquí con interrupciones apagadas en un núcleo que aún arranca; `IOLockLock` contendido **bloquearía el hilo**),
  - o en `while (!(mproc_info->cpu_flags & SignalReady))`,
  - o en el handshake de `cpu_sync_timebase`, que necesita que el núcleo 0 procese la IPI CPRQtimebase.

**Arreglo (en osx-drivers, sin tocar el kernel):**
- Crear `WiiCPUInterruptController : IOCPUInterruptController` (si ya tienes la subclase del `handleInterrupt`, úsala) y sobrescribir:
  - `enableCPUInterrupt(cpu)`: `ml_install_interrupt_handler(...)` igual que el original; luego `OSIncrementAtomic(&_enabled)` (contador **propio**, porque `enabledCPUs` es `private`) y `thread_wakeup(this)`.
  - `registerInterrupt(...)`: copia el cuerpo del original (rellenar `vectors[source]`; `vectors` es `protected` en `IOInterruptController`) y cambia la espera por un **bucle sin carrera**:
    ```cpp
    while (_enabled < numCPUs) IOSleep(1);     // sondeo: imposible perder el wakeup
    ```
    Si el llamante es un núcleo que aún arranca (el núcleo 1 dentro de `cpu_machine_init`), `_enabled` ya vale `numCPUs` y no duerme.
  - En `initCPU(true)` de los secundarios, **no llames a `registerInterrupt` con un mutex** si puedes evitarlo: registra los vectores de todos los núcleos desde el núcleo 0 antes del WAKE y deja al secundario solo `enableCPUInterrupt` + `enableInterrupt`.
- Esto también vale para upstream (con 3 núcleos la carrera del `++` aparece seguro).

## 50.3 Diagnóstico del núcleo 1 que no dependa de `processor_start`
Lanza el hilo de diagnóstico **antes** de `processor_start(1)`, por ejemplo desde `WiiPE::start` o con un `IOTimerEventSource` propio, ligado al núcleo 0 si puedes (`thread_bind` no se exporta; basta con que corra antes de arrancar el 1). Cada segundo pinta:
1. `per_proc[1] + 0x814` (hwExternals) y `+ 0x824` (hwDecrementers). Tienen el mismo layout que en el núcleo 0; el per_proc virtual del núcleo 1 es el `arg` de `startCPU`.
2. `per_proc[1]->cpu_flags` (¿SignalReady/BootDone?) y `MPsigpStat`. Los offsets se sacan con `otool` de `cpu_signal` (`lwz …,MPsigpStat(rX)`) y de `cpu_machine_init` (`lhz …,cpu_flags`).
3. **Ping al núcleo 1**: cada segundo pon tú `SCR |= IPI_PEND(1)` (con tu lock) **sin mensaje de XNU**. En tu `handleInterrupt(source=1)` incrementa un contador global de pings recibidos.
   - Si sube, el núcleo 1 vive con EE=1.
   - Si no sube, está con EE=0 o muerto. Diferéncialo con hwDecrementers: si sube, tiene EE=1 a ratos.
4. **Dónde está el núcleo 1:** en ese mismo `handleInterrupt(source=1)` guarda el PC interrumpido.
   - En XNU la savearea de la interrupción queda en `current_thread()->machine.pcb`. Su `save_srr0` está en un offset fijo; sácalo de `otool -tv` de `_interrupt` (`lwz rX,save_srr0(r3)`), en la zona que lee SRR0 para `T_DECREMENTER`/perfmon.
   - Guarda también `current_thread()`. Así ves en qué hilo y en qué PC gira el núcleo 1 (busca el PC en `nm mach_kernel` / en los kexts).
   - Si el PC está siempre en el mismo sitio, tienes el bucle.
5. Si ni el ping ni el decrementador suben: EE=0 o muerto. Pon balizas en `hw_lock_lock`/`hw_lock_mbits` (guardar la dirección del lock en lowGlo) y en `lck_mtx_lock`.

## 50.4 Candidatos a "el núcleo 1 no vuelve al bucle" (por orden)
1. **Mutex en contexto de arranque:** `initCPU` del núcleo 1 llama a `registerInterrupt` → `IOTakeLock`. Si está contendido, `lck_mtx_lock` bloquea el hilo de arranque del procesador antes de `SignalReady`, un estado del que XNU no sabe salir. Se arregla con 50.2 (registrar desde el núcleo 0).
2. **Un hilo del driver en un bucle sin ceder** (por ejemplo el propio workloop de PI 6 / Latte 5 esperando un registro, o el `while (vector->interruptActive)` de `IOInterruptController::enableInterrupt`, que gira sin límite si `interruptActive` se queda a 1). El PC de 50.3.4 lo dirá.
3. **stwcx. sin parchear** en kexts que cargó kextd **después** del mkext (46.5): un lock corrupto → giro eterno. Escanea los de `kextstat` y los binarios de `/System/Library/Extensions` que se cargan antes del escritorio.
4. **tlbie no difundido entre núcleos** **[NO VERIFICADO]**. NetBSD y Linux en Espresso con SMP asumen que sí se difunde. Si no fuera así, el núcleo 1 usaría traducciones viejas tras cada `pmap_remove`, lo que da corrupción aleatoria y no determinista. Se prueba al final si todo lo demás falla.

## 50.5 ¿Aparcar el núcleo 1? (pregunta 3)
- XNU 10.4 lo haría con `processor_exit` → `processor_shutdown` (es lo que usa CHUD `chudxnu_enable_cpu`). Eso lleva a `cpu_sleep`/`PE_cpu_halt` → `WiiCPU::haltCPU`/`quiesceCPU`, que están **en TODO**. Sin implementarlos se cuelga o devuelve basura. No compensa ahora.
- "Arrancar el 1 y no dejarle hilos" no existe en xnu-792: `processor_assign`/psets múltiples están desactivados.
- Aparcarlo en el trampolín (sin entrar en XNU) equivale a UP. No mide nada nuevo.
- **Recomendación:** no aparcar. Con 50.2 + 50.3 deberías ver exactamente dónde está el núcleo 1 en el siguiente arranque.

## 50.6 Orden
1. Arreglar las carreras de 50.2 (contador atómico propio + espera por sondeo; registrar los vectores desde el núcleo 0).
2. Hilo de diagnóstico lanzado antes de `processor_start(1)` con hwExternals/hwDecrementers del núcleo 1, cpu_flags, MPsigpStat, ping por SCR y PC + hilo de la última interrupción del núcleo 1 (50.3).
3. Arrancar y, según el PC, seguir 50.4.
4. Mantener doze anulado hasta que el núcleo 1 funcione. Después, reactivarlo y comprobar si la ICI despierta de doze (48.1).


---

# PARTE 51 — (informe del Mac) Contador atómico aplicado; solo falta el vector 5 de Latte

- `WiiCPUInterruptController`: `_enabled` con OSIncrementAtomic; `registerInterrupt` con `while (_enabled < numCPUs) IOSleep(1)`; el secundario solo hace `enableCPUInterrupt`. `ml_install_interrupt_handler` se resuelve por la tabla de símbolos.
- smp27: `processor_start(1)` ✓ y `registerInterrupt(0)` ✓, ahora siempre.
  - Núcleo 0 vivo. Núcleo 1 vivo: hwDecrementers sube; hwExternals ≈ 12.
  - PI mask = normal (`0x01000050`).
  - **Latte mask0 = `0x00000080`: falta el vector 5** (en UP, `0x000000A0`). Se queda en la manzana, sin red.

---

# PARTE 52 — Respuesta: el workloop del vector 5 no está esperando CPU, está bloqueado

## 52.1 ¿Qué dispositivo es el vector 5? (pregunta 3)
- Latte mantiene la numeración de Hollywood para los periféricos AHB. OpenBIOS (`arch/ppc/wii/tree.fs`) usa: **4 = EHCI (0x0D040000), 5 = OHCI0 (0x0D050000), 6 = OHCI1 (0x0D060000), 7 = SDHC (0x0D070000), 8 = SDIO (0x0D080000)**, 2 = AES, 3 = SHA, 30 = IPC.
- La máscara de referencia `0xA0` = vectores **5 (OHCI0, USB 1.1)** y **7 (SDHC)**. Así que lo que se pierde es **el USB**, que lo gestiona `WiiOHCI` con un `IOFilterInterruptEventSource`.
  - Si el disco del sistema o la red van por USB, el arranque se queda esperando E/S. Eso encaja con la manzana y la falta de red.
- **Confírmalo** en un arranque UP: `ioreg -l -w0 | grep -B2 -A12 'usb@d050000'` y mira `interrupts`. Mira también en `ioreg -l -p IOService` de qué controlador cuelga el disco raíz (`df /` → `disk0s…` → IOUSBMassStorage…).

## 52.2 En xnu-792 nada depende de una IPI para recoger el hilo (pregunta 1)
- Con el doze anulado, `idle_thread` (`sched_prim.c:2537`) consulta **en bucle** `processor->next_thread`, la cola del pset y la del procesador. Las colas del procesador solo se usan para hilos ligados a él (`bound_processor`); el resto va a la cola del **pset**, compartida.
- Si el workloop es ejecutable, **uno de los dos núcleos lo ejecuta en microsegundos**, sin IPI.
- SIGPast solo hace falta para **expulsar** un hilo de menor prioridad que esté corriendo. Sin IPI, esa expulsión llega igualmente al vencer el quantum (decrementador, ~10 ms), y el decrementador del núcleo 1 funciona.
- **Conclusión:** el hilo del workloop de OHCI **no es ejecutable**. Está dormido esperando algo (gate, evento, mutex) o está corriendo sin fin dentro del driver. No es un problema del planificador ni de las IPI.

## 52.3 Diagnóstico decisivo: 4 lecturas desde tu hilo de diagnóstico
Son objetos tuyos (osx-drivers), así que puedes leerlos directamente; guarda punteros globales al arrancar:
1. **`LatteInterruptController::vectors[5]`** (`vectors` es `protected` en `IOInterruptController`): `interruptActive`, `interruptDisabledSoft`, `interruptDisabledHard` (los tres son `volatile char`, `IOInterruptController.h:40-52`).
2. **IES de OHCI** (`WiiOHCI::_interruptEventSource`): `producerCount` y `consumerCount` (`protected` en `IOInterruptEventSource.h:76-82`; declara una subclase vacía con un accesor, o léelos por offset), `autoDisable`, `explicitDisable`.
3. **Workloop de OHCI** (`getWorkLoop()`; `protected` en `IOWorkLoop.h:82-116`): `workToDo`, `workThread` y el **dueño del gate**. `gateLock` es un `IORecursiveLock*` = `{ lck_mtx_t *mutex; thread_t thread; UInt32 count; }` (`IOLocks.cpp:80`), así que lee `gateLock->thread` y `gateLock->count`.
4. **PC del núcleo 1 y del núcleo 0**: el ping por SCR de 50.3 guarda `current_thread()` y `save_srr0` en `handleInterrupt(source=1)`. Para el núcleo 0 lo mismo en cualquier interrupción externa suya.

**Tabla de interpretación:**
| Lo que ves | Significa | Siguiente paso |
|---|---|---|
| producer > consumer, `workToDo=1`, `gateLock->thread == 0` | el hilo del workloop no se despertó o no se planifica | lee `workThread` → `state`/`wait_event` (52.4) |
| `gateLock->thread == X` ≠ `workThread` | **otro hilo tiene el gate** y el workloop espera en `closeGate` | ¿dónde está X? Si es el `current_thread()` del ping del núcleo 1, mira su PC: está girando con el gate cogido |
| `gateLock->thread == workThread` | el workloop está **dentro de la action** (`WiiOHCI::handleInterrupt`) y no vuelve | el PC del núcleo 1 (o del 0) cae dentro de WiiOHCI: es un bucle del driver |
| producer == consumer, Soft=0, **Hard=1** | `enableInterrupt` se ejecutó pero `enableVector` se perdió | carrera en el protocolo Soft/Hard (52.4) |
| producer == consumer, Soft=1 | `enable()` no se llamó (`explicitDisable`) o se deshabilitó después | revisa llamadas a `disable()`/`disableInterrupt` del driver |

## 52.4 Hipótesis por orden, con lo que ya se sabe
1. **Carrera filtro (núcleo 0) ↔ UIM/action (núcleo 1) en WiiOHCI.** Con un núcleo, el filtro solo podía *interrumpir* al código del gate; ahora **corre a la vez**. Estado compartido sin lock completo:
   - `_intWriteDoneHead`/`_intRootHubStatus` (flags sin lock, `WiiOHCI_Interrupts.cpp:134/243`);
   - el recorrido de la done queue y de los TD en el **filtro** (`:62-134`, `getTransferFromPhys`, `currTransfer->…`) mientras la action o la UIM desenlazan o liberan TDs en el núcleo 1 (`completeFailedEndpointGenTransfers`, `removeEndpointTransfers`, `returnTransfer`).
   - Un TD reutilizado mientras el filtro lo recorre puede dejar una lista circular: la action gira eternamente con el gate cogido (fila 3 de la tabla).
   - **Arreglo:** que el filtro solo lea `HcDoneHead`/estado, lo apunte y reconozca, y que **todo** el recorrido de TDs se haga en la action (bajo el gate). O proteger las listas con un spinlock que usen filtro, action y UIM (`IOSimpleLockLockDisableInterrupt`).
2. **Espera activa con el gate cogido**: `completeFailedEndpointGenTransfers` (`WiiOHCI_Descriptors.cpp:760-763`) borra SOF y hace `while (!(IntStatus & SOF)) IODelay(10)`. Si el filtro del núcleo 0 borra SOF justo después (`WiiOHCI_Interrupts.cpp:152-156`, cuando SOF está habilitada en IntEnable), el bucle espera al siguiente frame. Si además la interrupción SOF queda deshabilitada y el bit no vuelve a subir, se queda girando. Es menos probable, pero la fila 3 lo mostraría con el PC en ese bucle.
3. **Protocolo Soft/Hard (fila 4)**: es seguro si `sync` es barrera completa entre núcleos: `IOInterruptController::enableInterrupt` hace `Soft=0; sync;` antes de leer `interruptActive`, y tu `handleInterrupt` hace `Active=1; sync;` antes de leer `Soft`. Comprueba que **tu** versión de Latte `handleInterrupt` (con el maskLock) conserva el `sync()` entre `interruptActive = 1` y la lectura de `interruptDisabledSoft`, y que `interruptActive = 0` va **después** de `disableVectorHard`.
4. **Lost wakeup en IOWorkLoop**: revisado, `threadMain` y `signalWorkAvailable` (`IOWorkLoop.cpp:248-332`) usan `workToDoLock` correctamente, así que es poco probable. Si la fila 1 lo muestra, lee el `wait_event` del `workThread`. Busca en los primeros 0x100 bytes del `struct thread` la dirección `&workloop->workToDo`: si está y `workToDo == 1`, es un wakeup perdido.

## 52.5 Cómo comprobar si tus signalCPU generan el ICI (pregunta 2)
Tu idea es correcta. Añade además:
- `sent[1]`: envíos a 1 (en `signalCPU`, dentro del lock, tras la `mtspr`).
- `seen[1]`: veces que `handleInterrupt(source=1)` ve el bit 19.
- `spurious[1]`: entradas a 0x1700 en el núcleo 1 **sin** el bit.
- `MPsigpStat` del per_proc 1 (si `MPsigpMsgp` se queda fijo, un mensaje no se consumió).
- Píntalos en **decimal** (no por potencias de 2).
- `sent ≈ seen` → las IPI funcionan. `sent ≫ seen` sin `MPsigpMsgp` fijo → se fusionan (normal). `sent ≫ seen` con `MPsigpMsgp` fijo → ICI perdida.
- Con el doze anulado esto **ya no explica** el bloqueo (52.2), pero conviene saberlo antes de reactivar el doze.

## 52.6 Orden
1. Confirmar que el vector 5 = OHCI0 (ioreg en UP) y por qué controlador va el disco raíz.
2. Diagnóstico 52.3 (vectors[5], producer/consumer, gate/workThread, PC de los dos núcleos). Una captura basta para situarse en la tabla.
3. Según la fila: arreglar WiiOHCI (52.4.1/2) o el protocolo (52.4.3).
4. Contadores de IPI (52.5) en decimal, para cuando se reactive el doze.


---

# PARTE 53 — (informe del Mac) Sin log por IPC y sin WiiUSB: sigue igual

- smp27 **sin WiiUSB**: se para antes, en el paso 20/21 (`WIISYSLOG("woke core…")` hace un log por IPC a IOSU con sondeo).
- smp28 = smp27 + log por IPC desactivado desde el WAKE: igual que smp27.
  - `processor_start(1)` y `registerInterrupt(0)` vuelven. Latido del núcleo 0 vivo.
  - Núcleo 1: hwDecrementers avanza; **hwExternals = 3**. handleInterrupt ≈ 2–3 en cada núcleo.
  - PI mask `0x01000010` (falta el 6), Latte mask0 `0x00000080` (falta el 5).
- El número de vectores perdidos varía entre arranques. El núcleo 1 recibe muy pocas externas y el núcleo 0 también, pero su decrementador sí va.

---

# PARTE 54 — Respuesta: el núcleo 1 no está ocioso; cómo verlo con lo que XNU ya cuenta

## 54.1 El bucle ocioso no espera ninguna IPI (pregunta 1)
- `idle_thread` (xnu `kern/sched_prim.c:2537-2550`) es un **bucle de sondeo**: `while (next_thread == NULL && runq_pset == 0 && runq_local == 0) { machine_idle(); splsched(); }`. Con el doze anulado, `machine_idle` solo reactiva EE y vuelve, así que `next_thread` se consulta continuamente. No hace falta AST ni IPI.
- **La deducción importante:** cuando el núcleo 1 está **ocioso de verdad**, está en `pset->idle_queue`. `thread_setrun` (`sched_prim.c:1985-2030`) le pasa **cualquier** hilo que despierte (cientos por segundo durante el arranque) y llama a `machine_signal_idle` → `cpu_signal(SIGPwake)`. Esto se envía porque `pf.Available` del núcleo 1 sigue teniendo `pfCanDoze` (solo anulaste la instrucción, no el bit). Con tus contadores deberías ver **muchas** externas en el núcleo 1.
- Solo ves ~3. Así que **el núcleo 1 no está en la cola de ociosos**: está `PROCESSOR_RUNNING` con un hilo que **no suelta la CPU**. El decrementador sube, pero no hay cambio de contexto. Pasa si el hilo gira con la **expulsión desactivada** (dentro de un simple lock / `disable_preemption`), o si cada vuelta vuelve a coger lo mismo.
- Encaja con que se pierdan vectores de forma variable: el hilo atascado en el núcleo 1 puede ser **el propio workloop** (OHCI o el del PI 6), o uno que tiene algo que ellos necesitan (un lock, el gate).
- Y con que el núcleo 0 reciba pocas externas: sus fuentes (PI 6, Latte 5) están enmascaradas esperando al workloop; solo quedan SDHC y el decrementador.

## 54.2 Verlo sin escribir código nuevo: contadores y estructuras que XNU ya mantiene
**hwCtr** empieza en **per_proc + 0x800**. Lo he validado con tus datos: +0x814 = hwExternals, +0x824 = hwDecrementers, +0x88C = hwPreemptions y +0x890 = hwContextSwitchs, que son justo los que viste moverse (`osfmk/ppc/exception.h:152-248`).
| Offset per_proc | Campo | Qué dice |
|---|---|---|
| +0x888 | hwSIGPs | señales SIGP procesadas por ese núcleo |
| +0x88C | hwPreemptions | expulsiones |
| **+0x890** | **hwContextSwitchs** | **si no sube en el núcleo 1, está atascado en un solo hilo** |
| +0x990 | numSIGPast | SIGPast recibidas |
| +0x994 | numSIGPcpureq | cpureq recibidas (timebase…) |
| +0x99C | numSIGPwake | SIGPwake recibidas |
| +0x9A0 | numSIGPtimo | **enviadas por ESTE núcleo** que caducaron (el destino no consumió el mensaje) |
| +0x9A4 / +0x9A8 | numSIGPmast / numSIGPmwake | enviadas por este núcleo y **fusionadas** (no se mandó IPI) |
| +0x9BC | numSIGPcall | SIGPcall recibidas |
Recibidos en el per_proc del **receptor**; timo/merged en el del **emisor** (núcleo 0).

**`struct processor`** del núcleo 1 (`kern/processor.h:113-122`): el `processor_t` es tu `machProcessor` del WiiCPU 1.
| Offset | Campo |
|---|---|
| +0x08 | `state` (0 OFF_LINE, 1 RUNNING, 2 IDLE, 3 DISPATCHING, 5 START) |
| +0x0C | `active_thread` |
| +0x10 | `next_thread` |
| +0x14 | `idle_thread` |
| +0x1C | `current_pri` |

**Interpretación:**
- `state=1`, `active_thread ≠ idle_thread` y siempre el **mismo** puntero, `hwContextSwitchs[1]` quieto → hilo atascado. Compara ese puntero con el `workThread` del IOWorkLoop de OHCI/PI y con `gateLock->thread` (52.3). Mira `current_pri`: el workloop de un driver suele ir a 80-ish.
- `state=3` con `next_thread` fijo → el núcleo 1 no recoge. Improbable con el bucle de sondeo; apuntaría a coherencia (54.3).
- `state=2` y `hwContextSwitchs[1]` sube → el núcleo 1 está bien. El problema está en el hilo bloqueado (52.3).
- En el núcleo 0: si `numSIGPmwake`/`numSIGPmast` suben mucho y `numSIGPwake[1]` no, los mensajes se fusionan porque `MPsigpStat` del 1 se quedó con `MPsigpMsgp` (46.4).

## 54.3 PC del núcleo 1 y prueba de coherencia en modo virtual (pregunta 2)
Tu idea de contar envíos y recepciones es buena. Píntalos en **decimal**, no en binario. Añade dos cosas con el **ping por SCR** (sin mensaje XNU):
1. **PC**: en `handleInterrupt(source=1)` guarda `current_thread()` y el `save_srr0` de `current_thread()->machine.pcb` (offset con `otool` de `_interrupt`). Con el hilo atascado y EE=1 a ratos, el ping entra y te da **dónde gira**; busca el PC en `nm` del kernel y de los kexts.
   - Si el ping **no** entra nunca, el núcleo 1 gira con EE=0. Mira entonces si el hilo tiene un simple lock: la dirección suele estar en r3 de la savearea del decrementador.
2. **Coherencia en modo virtual**:
   - Antes de cada ping, el núcleo 0 escribe `gPingSeq++` (variable global del kext).
   - En `handleInterrupt(source=1)`, el núcleo 1 copia `gPingEcho = gPingSeq`.
   - Si `gPingEcho` va siempre un número por detrás o se queda viejo, no hay coherencia en virtual.
   - XNU mapea toda la RAM con **M=1** (`mappings.c:348`, `wimg = 0b0010`), igual que el modo real de la sonda, así que debería pasar. Pero es la única pieza de coherencia que falta verificar fuera de la sonda en modo real.

## 54.4 Por qué el IPC y WiiUSB cambiaban el punto de parada
- El log por IPC sondea un registro de IOSU con un bucle. Si otro núcleo o el propio IOSU tardan, se alarga el tiempo con EE=0 o con un lock, y cambia el orden de la carrera. Así que no es una causa: **solo cambia el calendario**.
- Quitar WiiUSB cambia qué hilo acaba atascado en el núcleo 1, no el problema de fondo. Mantén el IPC desactivado desde el WAKE (o protégelo con un spinlock y no lo uses desde el núcleo 1).

## 54.5 Dejar el núcleo 1 fuera (pregunta 3)
- `processor_shutdown`/`processor_exit`: pasan por `cpu_sleep` → `WiiCPU::haltCPU/quiesceCPU` (TODO). **No.**
- **Arranque tardío (recomendado como prueba):** es exactamente lo que hace XNU al despertar del reposo (`IOCPUSleepKernel`, `IOCPU.cpp:117-121`, llama a `processor_start` en caliente).
  1. En `WiiCPU::start` del núcleo 1, **no** llames a `processor_start`. Guarda `machProcessor`.
  2. El núcleo 0 **no** debe esperar al 1: en tu `registerInterrupt` reimplementado, quita el `while (_enabled < numCPUs)`. Esa espera solo asegura que las IPI tengan destino, y `cpu_signal` ya comprueba `SignalReady` y `running`.
  3. Arranca el núcleo 1 más tarde desde un hilo: con un temporizador (p. ej. 180 s tras `WiiPE::start`, cuando ya hay escritorio y SSH) o bajo demanda (sysctl o propiedad de IORegistry que se cambie por SSH). Parchea la commpage MP con dcbst **antes** del WAKE, igual que ahora.
  4. Si el sistema sigue vivo con el núcleo 1 arrancado, mide por SSH (`sysctl hw.ncpu`, `top`, pruebas de carga). Si se congela, lo hará en un estado conocido, con SSH hasta ese momento y con el diagnóstico de 54.2 en pantalla.
- `ml_get_max_cpus` ya es 2 desde el arranque, así que la commpage MP y `hw.ncpu` se quedan como ahora.

## 54.6 Orden
1. Añadir al hilo de diagnóstico, **sin código nuevo en XNU**: `processor[1]` (+0x08/+0x0C/+0x10/+0x14/+0x1C), hwContextSwitchs de los dos núcleos y los contadores SIGP de 54.2 (recibidos en el 1; timo/merged en el 0). Una captura con dos lecturas separadas 2 s basta.
2. Si `active_thread` del núcleo 1 está fijo: el ping de 54.3 para su PC, y compararlo con el workThread/gate de OHCI y PI.
3. En paralelo, preparar el **arranque tardío** (54.5) como vía para llegar al escritorio y medir.


---

# PARTE 55 — (informe del Mac) El núcleo 1 se queda en su primer hilo y todo se congela en <1 s

- smp29 (solo diagnóstico): el hilo de diagnóstico **solo pinta su primera vuelta**. El sistema se congela en <1 s tras `processor_start(1)`, que **sí** vuelve (pasos 14/15 ✓).
  - processor[1]: state = 1 (RUNNING), next_thread = 0, current_pri = 81.
  - active_thread = 0x02AE1740; idle_thread = 0x02AE1400 (0x340 antes).
  - hwContextSwitchs: núcleo 1 = **1**, núcleo 0 = 82.
  - Núcleo 1: hwExternals = hwDecrementers = numSIGPwake = hwSIGPs = 0.
  - Núcleo 0: numSIGPtimo = numSIGPmwake = 0.
- smp30 = smp29 + `cpu_sync_timebase` → `blr`: **idéntico**.
- `cpu_machine_init` (0x93098), en el secundario:
  1. lock `rht_lock` (0x3912C0) + espera;
  2. `PE_cpu_machine_init`;
  3. si per_proc+0x1B4 ≠ 0, tres `mttb`;
  4. espera de `SignalReady` del maestro;
  5. `cpu_sync_timebase` y `ml_init_interrupt`; pone BootDone|SignalReady;
  6. lock 0x3912CC + `thread_wakeup`.

---

# PARTE 56 — Respuesta: el núcleo 1 muere entre SignalReady y su primer cambio de contexto; el núcleo 0 muere en el camino del panic

## 56.1 Qué hilo es y dónde está (pregunta 1)
- **Sí, es el hilo de arranque.** `processor_start` (`kern/processor.c:500-536`) crea primero el `idle_thread` (`idle_thread_create`) y justo después el hilo dedicado `processor_start_thread` (`kernel_thread_create`). Salen seguidos de la zona de hilos (0x340 = tamaño de `struct thread`).
  - `slave_main` → `load_context` lo pone en marcha: ese es el **único** cambio de contexto (hwContextSwitchs = 1).
  - Corre entero a `splsched` (EE=0), así que hwDecrementers = 0 y hwExternals = 0 son **normales** aquí. No indican por sí solos un lock.
- **Ya pasó `SignalReady`:** `processor_start(1)` solo vuelve cuando el maestro, en `cpu_start` (`ppc/cpu.c:363-371`), ve `SignalReady` del núcleo 1. Así que los pasos 1-5 de tu lista **están hechos**. Ni el lock 0x3912C0 (`rht_lock`) ni la espera de SignalReady del maestro son el sitio.
- Lo que le queda al núcleo 1 hasta su **segundo** cambio de contexto (xnu-792):
  1. `cpu_machine_init` (fin): `hw_atomic_and(ppXFlags)`, `thread_wakeup(&cpu_flags)` → despierta al hilo del maestro que espera en `cpu_start`: **`thread_setrun` desde el núcleo 1**. Después `simple_unlock(SignalReadyLock)` y `pmsPark()`.
  2. `slave_machine_init` → `clock_init()` → `sysclk_init` → `setTimerReq()` (programa el DEC; inocuo).
  3. `processor_start_thread` → `thread_terminate(self)` (`kern/thread_act.c:119-143`): `thread_terminate_internal` (mutex del hilo), `ml_set_interrupts_enabled(FALSE)`, `ast_taken(AST_APC)` → `thread_terminate_self` → `thread_block` → `thread_select`/`thread_dispatch` (lock `pset->sched_lock`, cola del reaper con `thread_wakeup`) → cambio a `idle_thread` (sería hwContextSwitchs = 2).
- En 1 y 3 el núcleo 1 despierta hilos. `thread_setrun` elige un procesador. Si el hilo va al núcleo 0 (que está ejecutando), llama a `cause_ast_check(0)` → `cpu_signal(0, SIGPast)` → **`PE_cpu_signal` → tu `WiiCPU::signalCPU` ejecutado por primera vez desde el núcleo 1**. Con `cpu_sync_timebase` anulado, es la primera vez que el núcleo 1 escribe SCR para señalizar.

## 56.2 Por qué se congela también el núcleo 0 sin ver un panic
- Los spinlocks de XNU/IOKit (`simple_lock`, `IOSimpleLockLock` = `lck_spin_lock` = `ppc_usimple_lock`, `osfmk/ppc/hw_lock.s:1712-1797`) **sí tienen timeout**: tras `LockTimeOut` hacen `panic("simple lock (0x%08X) deadlock detection, pc=0x%08X")`. Así que un núcleo atascado en un spinlock **no gira para siempre**: provoca un panic.
- Pero el panic, con 2 CPU, hace esto (`ppc/model_dep.c:590-614`): `lock_debugger()` → para cada otra CPU `cpu_signal(tcpu, SIGPdebug)` → **tu `signalCPU`** → `hw_cpu_sync(&debugger_sync, LockTimeOut)` → solo **después** `draw_panic_dialog()`.
  - Si `signalCPU` se bloquea (su IOSimpleLock lo tiene el otro núcleo, que está muerto, o lo tiene **el mismo núcleo**: panic desde dentro de `signalCPU` o del ack con el lock cogido → recursión), el panic **nunca llega a pintarse**.
  - Además, el panic anidado del lock recursivo intenta lo mismo → "nested panic" → cuelgue.
  - Resultado: los dos núcleos con EE=0 y **pantalla congelada sin diálogo de panic**. Es exactamente lo que ves.
- Así que lo más probable es: **algo falla en el núcleo 1 en 56.1 (un panic, un trap o un lock), y el camino del panic se queda atascado en tu `signalCPU`**. O bien el núcleo 1 se atasca *dentro* de `signalCPU` con el lock cogido, y el núcleo 0, al señalizar, cae en el timeout → panic → `signalCPU` → mismo lock → cuelgue.

## 56.3 per_proc+0x1B4 (pregunta 2)
- Es `per_proc->hibernate` (`ppc/cpu.c:158-175`). Solo es ≠ 0 al volver de **hibernación** (`hibernate_machine_init`). Con `hibernatemode 0` (lo tienes así por los stubs de `__HIB`) vale siempre 0. **No hace falta anularlo**; si quieres ser completo, pon esos tres `mttb` a nop igual que los otros.

## 56.4 Diagnóstico (pregunta 3): balizas **por núcleo** + gancho en `_panic` + `signalCPU` a prueba de panics
Tus balizas en lowGlo sirven. Hazlas por núcleo: el stub hace `mfsprg r11,0` y compara con la dirección del per_proc de la CPU 0 (o lee `cpu_number` del per_proc), y escribe en `0x5F00 + 0x40*cpu + 4*n`. Así el núcleo 0 y el 1 dejan cada uno su **último punto alcanzado**.

**A. Puntos (núcleo 1 y núcleo 0), por orden de 56.1:**
1. Tras `bl _PE_cpu_machine_init` en `cpu_machine_init`.
2. Justo antes de `ori …,SignalReady` / `sth …,cpu_flags` (0x2).
3. `_thread_wakeup_prim` (entrada).
4. `_thread_setrun` (entrada) y `_cause_ast_check` (entrada).
5. `_cpu_signal` (entrada) y **tu `signalCPU`**: entrada, tras coger el lock, tras `mtspr SCR` y salida (en C, sin stub).
6. `_clock_init` (entrada).
7. `_thread_terminate` (entrada), `_ast_taken` (entrada), `_thread_terminate_self` (entrada).
8. `_thread_block_reason` (entrada), `_thread_select` (entrada), `_thread_dispatch` (entrada) y `_idle_thread` (entrada).
9. **`_panic` (entrada)**, **`_Debugger` (entrada)** y `_unresolved_kernel_trap` si existe el símbolo; si no, `_trap` (entrada).

**B. Gancho en `_panic` que se vea aunque todo se congele:**
- Primera instrucción de `_panic` → `b stub`.
- El stub guarda r3 (formato), r4, r5 y LR en lowGlo (por núcleo), pinta un cuadro rojo directamente en el framebuffer físico (el trampolín ya sabe hacerlo en modo real; en virtual usa tu mapeo del FB desde C), ejecuta la instrucción desplazada y vuelve a `_panic+4`.
- Mejor aún, desde el stub llama a una función C de tu kext (`WiiDiagPanicHook(r3,r4,r5,lr)`) que pinte esos 4 valores en filas. Esa función no debe usar locks ni IOLog.
- Con r3 (cadena) sabrás el tipo de panic; con r4/r5 la dirección del lock y el PC ("simple lock deadlock", "Unresolved kernel trap", etc.).

**C. Que `signalCPU` no pueda bloquear el camino del panic:**
- Sustituye `IOSimpleLockLock` por un **try-lock acotado** (p. ej. 10.000 intentos con `IOSimpleLockTryLock`). Si no lo consigue, haz la `mtspr` igualmente: el `|=` de un solo bit es idempotente y, como mucho, se pierde la carrera de otro bit.
- Guarda el **dueño** (número de CPU). Si el dueño es la propia CPU (recursión desde un panic), **no** esperes.
- Así `Debugger` completa su `hw_cpu_sync` (con timeout) y **aparece el diálogo de panic**. Aunque no hay `-v`, tus balizas y el gancho B dirán el resto.

**D. `_isBootCPU` y `initCPU`:** confirma que en el núcleo 1 no se ejecuta **nada** que haga IOLog, IPC ni `IOLockLock` (mutex). `initCPU` corre en el paso 2 de `cpu_machine_init`, con EE=0 y antes del primer cambio de contexto.

## 56.5 ¿Plan B ya? (pregunta 4)
- De acuerdo contigo: el arranque tardío **no evita** este fallo, porque el primer hilo del núcleo 1 recorre el mismo camino. Solo lo retrasaría.
- Úsalo después, cuando el núcleo 1 llegue a `idle_thread`, para aislar problemas de drivers.
- **Antes, arreglar esto.** Con 56.4 deberías ver en un solo arranque: último punto de cada núcleo + panic (si lo hay) con su cadena y PC.

## 56.6 Orden
1. `signalCPU` con try-lock acotado y detección de recursión (56.4.C). Es barato y probablemente convierte el cuelgue en un panic visible.
2. Gancho en `_panic` (56.4.B).
3. Balizas por núcleo en los puntos 56.4.A.
4. Arrancar (mantén `cpu_sync_timebase` → `blr`: el TB es compartido y así quitas un handshake). Con el último punto de cada núcleo y el panic, arreglar el fallo concreto.


---

# PARTE 57 — (informe del Mac) Con 56.6 el núcleo 1 llega a idle_thread; vuelve OHCI enmascarado

- Cambios de 56.4 aplicados:
  - `wiiSCRModify` con try-lock acotado + detección de recursión, EE=0;
  - gancho `_panic` → `wiiPanicHook`;
  - balizas por núcleo (0x5F00 + 0x80·cpu; "último" en +0x68).
- smp31: el stub leía el per_proc por SPRG0 (**dirección física**) → DSI. Aun así **se vio el diálogo de panic** (56.4.C funciona). Corregido con `mfsprg r11,1; lwz r11,0x200(r11)`.
- smp32: el núcleo 0 se colgaba en un `IOLog` tras el WAKE. Se han quitado los WIISYSLOG posteriores al WAKE.
- **smp33**: sin congelación ni panic. Latido vivo; hwDecrementers del núcleo 1 sube.
  - Balizas del núcleo 1: 0–13 (hasta `idle_thread`) y 20–23. Balizas del núcleo 0: 2, 3, 5, 7–13, 20–23; nunca `cause_ast_check` (4).
  - Núcleo 1: hwExternals = 24 y hwContextSwitchs = 22, **fijos entre 180 s y 300 s**.
  - PI mask normal; **Latte mask0 `0x80`: falta el 5 (OHCI0)**. Manzana, sin SSH.

---

# PARTE 58 — Respuesta: comprobar si el núcleo 1 está ocioso o ejecutando la action de OHCI

## 58.1 Lo que dicen (y no dicen) las balizas
- Las balizas marcan **"se alcanzó alguna vez"** y el "último" es el último **punto con baliza** que ejecutó ese núcleo.
- `idle_thread` (xnu `kern/sched_prim.c:2563-2635`), cuando recoge un hilo, hace `thread_run(idle_thread, …, new_thread)` y **salta directamente** a ese hilo. No pasa por `thread_block`/`select`/`dispatch`, así que **no toca ninguna baliza más**. Por tanto, "último = 13" es compatible con dos situaciones muy distintas:
  - (a) el núcleo 1 está en el bucle ocioso;
  - (b) el núcleo 1 recogió un hilo en `idle_thread` y **lo está ejecutando desde entonces**.
- hwContextSwitchs quieto + hwDecrementers subiendo encaja con (b): un hilo que no termina, con la expulsión activa pero **sin otro hilo ejecutable** en ese núcleo. La expulsión por quantum solo cambia de hilo si hay algo que poner; si no, no cuenta cambio de contexto.
- Encaja además con el vector 5: si ese hilo es el **workloop de OHCI** dentro de `WiiOHCI::handleInterrupt` (su action), `checkForWork` nunca llega a `enable()` → Latte 5 queda soft-disabled y enmascarado para siempre (52.4.1/52.4.2).

## 58.2 Pregunta 1: por qué el núcleo 0 no le da más hilos
- `thread_setrun` (`sched_prim.c:1985-2030`) prueba primero el **último procesador** del hilo si está IDLE, y luego cualquier procesador de `pset->idle_queue`. Solo va a `cause_ast_check` cuando nadie está ocioso y hay que **expulsar** (prioridad mayor que `current_pri` de otro procesador). Así que no ver `cause_ast_check` en el núcleo 0 es normal.
- Con el sistema casi parado (esperando al USB), los pocos hilos que despiertan tienen `last_processor = 0` y el núcleo 0 suele estar ocioso: **se quedan en el 0**. Que el núcleo 1 no reciba hilos no es un fallo en sí.
- Si el núcleo 1 estuviera en el caso (b), está `PROCESSOR_RUNNING` y **fuera** de `idle_queue`. Solo recibiría algo por expulsión (`cause_ast_check`) si llega un hilo de mayor prioridad que su `current_pri`.
- **Léelo ahora** (ya tenías estas filas en smp29; vuelve a pintarlas en smp33):
  - `processor[1]` +0x08 `state`, +0x0C `active_thread`, +0x10 `next_thread`, +0x14 `idle_thread`, +0x1C `current_pri`.
  - `pset = *(processor[1] + 0x18)`: `pset+0x00` = `idle_queue.next`, **`pset+0x08` = `idle_count`**, `pset+0x0C` = `active_queue.next` (`kern/processor.h:78-81`).
| Lectura | Caso |
|---|---|
| `state=2 (IDLE)`, `active_thread == idle_thread`, `idle_count ≥ 1` | (a) ocioso de verdad: el problema está en que el workloop de OHCI **no es ejecutable** → 58.4 |
| `state=1`, `active_thread ≠ idle_thread` **fijo**, `current_pri` ≈ 80-ish | (b) el núcleo 1 ejecuta un hilo sin fin → 58.3 para ver dónde |
| `state=3`, `next_thread ≠ 0` fijo | el ocioso no recoge el hilo → coherencia en modo virtual (54.3) |

## 58.3 PC del núcleo 1 (sirve para el caso b)
- Stub en **`_interrupt`** (entrada; `ppc/interrupt.c:48`: `interrupt(int type, struct savearea *ssp, dsisr, dar)`): guarda **r4 (`ssp`)** en `gSSP[cpu]` (lowGlo por núcleo, igual que tus balizas), ejecuta la instrucción desplazada y vuelve.
- En tu `handleInterrupt(source=1)` (el ping por SCR de 54.3), lee de `gSSP[1]` (`osfmk/ppc/savearea.h:95-150`):
  - `save_srr0` = **ssp+0x184** (palabra baja de un `uint64_t` en +0x180): el PC interrumpido;
  - `save_lr` = **ssp+0x19C**;
  - `save_r3` = ssp+0x9C;
  - `save_srr1` = ssp+0x18C (bit EE, PR).
  Cópialos a globales y píntalos desde el hilo del núcleo 0.
- Dos o tres pings bastan. Si el PC cae en `WiiOHCI` (mira la dirección de carga del kext con `kextstat` en un arranque UP, o `WiiOHCI::handleInterrupt` por símbolo), está ahí. Con el LR ves quién lo llamó.

## 58.4 Estado del workloop de OHCI (pregunta 2) — sin offsets a mano
Lo más seguro es **no** calcular offsets: compila accesores contra las cabeceras de Tiger (Kernel.framework) con subclases que solo añaden métodos **no virtuales**, y convierte el puntero:
```cpp
class DiagIES : public IOInterruptEventSource {
public:
  unsigned prod() { return producerCount; }
  unsigned cons() { return consumerCount; }
  bool     autoDis() { return autoDisable; }
  bool     explDis() { return explicitDisable; }
};
class DiagWL : public IOWorkLoop {
public:
  void *gate()    { return gateLock; }        // IORecursiveLock*
  void *thread()  { return (void*)workThread; }
  bool  todo()    { return workToDo; }
};
// uso: ((DiagIES*)ohci->_interruptEventSource)->prod()
```
- `IORecursiveLock` (`iokit/Kernel/IOLocks.cpp:80`) = `{ lck_mtx_t *mutex; thread_t thread; UInt32 count; }` → dueño en **gate+0x4** y cuenta en **gate+0x8**.
- `vectors[5]` desde tu `LatteInterruptController`: `interruptActive`, `interruptDisabledSoft`, `interruptDisabledHard` (bytes 0, 1, 2 del `IOInterruptVector`).
- Interpretación (resumen de 52.3):
  - `gate->thread == workThread == processor[1]->active_thread` → **la action de OHCI no vuelve** (caso b): arreglar WiiOHCI (52.4.1: el filtro en el núcleo 0 recorre la done queue y los TD mientras la action o la UIM los tocan en el núcleo 1; 52.4.2: bucle `while (!SOF)`).
  - `gate->thread == X` distinto de `workThread` → otro hilo tiene el gate. Mira si X es el `active_thread` de algún núcleo y su PC (58.3).
  - `prod > cons`, `gate->thread == 0`, `todo == 1` → el workloop no se planifica (improbable; mira `processor[*]`).

## 58.5 Pregunta 3: ¿plan B ya?
- Si 58.2/58.3 confirman que la action de OHCI no vuelve en el núcleo 1, el arranque tardío **también** fallaría en cuanto haya actividad USB concurrente. El problema es de WiiOHCI con dos núcleos, no del arranque.
- Si el disco raíz no va por USB, el arranque tardío podría llegar al escritorio. Pero teclado, ratón o red por USB volverían a disparar el fallo.
- **Orden recomendado:**
  1. Una sola prueba con las filas de `processor[1]` + `idle_count` (58.2), el stub `_interrupt` + ping para el PC (58.3) y los accesores de 58.4.
  2. Si es el caso (b) en WiiOHCI: arreglarlo (el filtro solo lee y reconoce el estado; todo el recorrido de TDs y listas se hace en la action, bajo el gate; o un spinlock común filtro/action/UIM). Después, SMP normal.
  3. Plan B (54.5) solo si el diagnóstico apunta fuera de USB o para medir mientras se arregla OHCI.


---

# PARTE 59 — (informe del Mac) smp34 llega al escritorio con 2 núcleos, pero sin paralelismo

- **Escritorio + SSH**, `hw.ncpu: 2`, `hw.activecpu: 2`. Latte mask0 `0xA0` y PI `0x01000050` normales; OHCI0 con prod = cons = 0x10D y el gate libre.
- smp34 = smp33 + diagnóstico de 58 (con un ping por SCR al núcleo 1 cada segundo).
- **Sin paralelismo**: un bucle `sh` tarda 14 s solo y 28 s con dos copias a la vez.
- `processor[1]`: state = 1, current_pri = **95**, `idle_count` = 0, active_thread = 0x02AE20C0 (no es el idle).
- **1 ping recibido** en más de 2 minutos: SRR0 = `ml_set_interrupts_enabled+0x70`, LR = `ast_taken+0xDC`, EE = 1.
- Balizas del núcleo 1: 0, 1, 6 (`clock_init`), 7 (`thread_terminate`), 8 (`ast_taken`), **14 (`Debugger`)**, 21 y 22 (SCR). **No** pasa por 9 (`thread_terminate_self`), block/select/dispatch ni idle. Sin gancho de panic pintado.

---

# PARTE 60 — Respuesta: el núcleo 1 entra en panic en `thread_terminate` y se queda colgado sin que se vea

## 60.1 La secuencia exacta (xnu-792)
`processor_start_thread` → `thread_terminate(self)` (`kern/thread_act.c:119-143`):
```c
result = thread_terminate_internal(thread);   // act_abort → install_special_handler_locked
if (thread->task == kernel_task) {             //   → thread_ast_set(thread, AST_APC)   (hw_atomic_or)
    ml_set_interrupts_enabled(FALSE);          //   → ast_propagate(thread->ast)          (*pending_ast |= …)
    ast_taken(AST_APC, TRUE);
    panic("thread_terminate");                 // ← SOLO se llega aquí si ast_taken NO ejecutó el APC
}
```
`ast_taken` (`kern/ast.c:90-161`):
```c
reasons &= *myast;  *myast &= ~reasons;        // myast = &per_proc->pending_ast
...
ml_set_interrupts_enabled(enable);             // ← aquí entró tu único ping (LR = ast_taken+0xDC)
if (reasons & AST_BSD) bsd_ast(thread);
if (reasons & AST_APC) act_execute_returnhandlers();   // hace spllo() y llega a special_handler
ml_set_interrupts_enabled(FALSE);                       //   → thread_terminate_self (baliza 9)
```
- Si `AST_APC` hubiera estado en `reasons`, `act_execute_returnhandlers` habría hecho **`spllo()`** (EE=1 → más pings) y `special_handler` → **`thread_terminate_self`** (baliza 9). No pasó ninguna de las dos cosas.
- Así que `reasons` **no tenía AST_APC**: `ast_taken` vuelve → **`panic("thread_terminate")`** → `Debugger` (baliza 14).
- Dentro de `Debugger` (`ppc/model_dep.c:555-625`, con `panicstr` puesto):
  1. comprime `debug_buf`;
  2. `PESavePanicInfo` (tu PE);
  3. `cpu_signal(0, SIGPdebug)`. Esas son tus balizas 21/22 en el núcleo 1: `signalCPU` con el lock y la `mtspr`;
  4. `hw_cpu_sync(&debugger_sync, LockTimeOut)` (caduca);
  5. `draw_panic_dialog()`, que el WindowServer tapa después o que tu framebuffer no muestra;
  6. **`PEHaltRestart(kPEHangCPU)` → el núcleo 1 queda colgado para siempre con EE=0**.
- Es exactamente lo que ves: prioridad 95 (MAXPRI_KERNEL = el `processor_start_thread`), `RUNNING`, sin cambios de contexto, sin pings y fuera de `idle_queue`.
- Tu gancho de `_panic` no pintó nada: revisa si el stub de `_panic` salta a `wiiPanicHook` también en el núcleo 1 (en smp31 funcionó porque el panic fue un DSI en `trap`). Con el núcleo 1 ya "aparcado", el panic en sí no se ve.

## 60.2 Por qué ahora llega al escritorio (pregunta 3)
- **El ping no tiene que ver.** El núcleo 1 murió en su hilo de arranque, `RUNNING` a prioridad 95 y fuera de `idle_queue`. El planificador nunca le da trabajo, así que el sistema funciona **como UP** y OHCI no tiene carreras.
- En **smp33**, el núcleo 1 sí terminó bien su hilo de arranque (llegó a `idle_thread`) y ejecutó hilos de verdad. Ahí fue donde **OHCI se colgó**. Es no determinista: unas veces el APC se pierde (smp34) y otras no (smp33).
- Hay **dos fallos distintos**:
  - (A) a veces el APC del hilo de arranque se pierde → panic silencioso en el núcleo 1;
  - (B) con el núcleo 1 trabajando de verdad, WiiOHCI se atasca (52.4 / 58.4).

## 60.3 Confírmalo ahora por SSH (el sistema está vivo)
Desde tu kext (o uno de prueba cargado por SSH), vuelca con `IOLog` y mira en `system.log`:
1. **`_panicstr`** (`const char *`, `kern/debug.c:84`): si apunta a `"thread_terminate"`, confirmado. Mira también `_panic_caller`, `_paniccpu` y `_nestedpanic`.
2. **`_debug_buf`** / **`_debug_buf_ptr`** (`char *`, `kern/debug.c:93-95`): el texto completo del panic. Puede estar comprimido por `packAsc`, pero la primera línea suele verse.
3. **`thread 0x02AE20C0`**: su campo `ast` y `active`. Saca los offsets de `otool -tv`:
   - `ast`: en `_act_execute_returnhandlers`, el `addi r3,rX,N` antes de `bl _hw_atomic_and` (el `thread_ast_clear`);
   - `active`: en `_thread_terminate_internal`, el `lbz/lwz …,N(r31)` antes de `act_abort`.
4. **`per_proc[1]->pending_ast`**: offset `PP_PENDING_AST` = el `addi r3,r3,N` de `_ast_pending` (`machine_routines_asm.s:1934-1937`).
5. `debugger_cpu`, `debugger_sync`, `debug_mode` y `per_proc[1]->debugger_active` (símbolos `_debugger_cpu`, `_debugger_sync`, `_debug_mode`).

**Cómo interpretarlo:**
| `thread->ast` | `pending_ast[1]` | Significa |
|---|---|---|
| sin 0x20 (AST_APC) | — | `thread_ast_set` (= `hw_atomic_or(&thread->ast, 0x20)`) **no escribió** → mira el stub dcbst de `hw_atomic_or` |
| con 0x20 | sin 0x20 | `ast_propagate` escribió en otro per_proc, o alguien lo pisó entre medias |
| con 0x20 | con 0x20 | `ast_taken` leyó mal (orden/coherencia) o `thread->active` ya era FALSE (`KERN_TERMINATED`) |

## 60.4 El sospechoso principal de (A): los stubs de `hw_atomic_or`/`hw_atomic_and`
- `thread_ast_set` es **`hw_atomic_or`** (`osfmk/ppc/hw_lock.s:582-590`):
  ```
  mr r6,r3
  ortry: lwarx r3,0,r6 ; or r3,r3,r4 ; stwcx. r3,0,r6 ; bne-- ortry ; blr
  ```
  Tu parcheo cambió `stwcx.` por `b stub` (dcbst + stwcx. + `b` de vuelta).
- Comprueba **con `otool` sobre la memoria en vivo** (o volcando los stubs) que para `hw_atomic_or`, `hw_atomic_and`, `hw_atomic_add/sub` y `hw_compare_and_store`:
  1. el stub hace `dcbst 0,r6` (el **mismo** rA/rB que el `stwcx.`) seguido de `stwcx. r3,0,r6` idéntico al original;
  2. vuelve **a la instrucción siguiente** (`bne--`) y no toca cr0 entre el `stwcx.` y el `bne`;
  3. no usa ningún registro vivo (r3, r4, r6).
  - Un stub que devolviera cr0 = EQ sin haber hecho el `stwcx.`, o que usara rA/rB equivocados, haría que `thread->ast` **no se escribiera a veces**. Eso es justo el síntoma no determinista.
- Con un solo núcleo (WiiSMP=false) ¿se aplican estos parches? Si **no** se aplican, el fallo (A) solo existe en SMP, lo que encaja. Si se aplican también en UP, el stub en sí estaría bien y la causa sería otra (fila 2 o 3 de la tabla).

## 60.5 Qué pintar si 60.3 no basta (pregunta 2)
- Gancho en `_Debugger` como el de `_panic`: guarda r3 (mensaje: "panic"), LR y `panicstr` por núcleo.
- Balizas en `_act_execute_returnhandlers` (entrada) y en `_special_handler` (entrada).
- Símbolos útiles: `_panicstr`, `_panic_caller`, `_paniccpu`, `_nestedpanic`, `_debug_buf`, `_debug_buf_ptr`, `_debugger_cpu`, `_debugger_sync`, `_debug_mode`, `_debugger_is_slave` (array por CPU; si existe en este kernel).

## 60.6 Orden
1. **Por SSH, sin reiniciar**: `panicstr`, `debug_buf`, `thread->ast`, `thread->active`, `pending_ast[1]` (60.3).
2. Revisar los stubs dcbst de `hw_atomic_*` y `hw_compare_and_store` (60.4).
3. Arreglado (A), volverá a verse (B) con OHCI. Entonces, con el núcleo 1 vivo y SSH disponible si llega a arrancar, aplicar el arreglo de WiiOHCI (58.4 / 52.4): el filtro solo lee y reconoce el estado; las listas y TDs solo se tocan en la action bajo el gate, o con un spinlock común.
4. El ping por SCR se puede quitar: no es lo que hace arrancar.
