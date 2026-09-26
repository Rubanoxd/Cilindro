# Plan: acelerar la GPU de la Wii U (GPU7/Latte) en Wiintosh — Mac OS X 10.4 Tiger

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
