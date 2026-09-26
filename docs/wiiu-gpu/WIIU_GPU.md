# Wiintosh GPU (Wii U) — investigación completa en un solo archivo

> Documento único para Claude Code. Cópialo a tu fork de `Wiintosh/osx-drivers`
> (p. ej. `docs/WIIU_GPU.md`) y añade a `CLAUDE.md`:
> `Lee docs/WIIU_GPU.md antes de tocar WiiGraphics o de pedir acciones físicas.`
>
> - **Parte 1** — Plan por fases (qué hacer).
> - **Parte 2** — Referencia técnica (registros, PM4, código).
> - **Parte 3** — Depuración y avisos al humano.
> - **Parte 4** — Pixel 6a con Vanilla en lugar de GamePad (cambia la fase 1: sin GamePad físico).
>
> Todo lo marcado **[NO VERIFICADO]** debe comprobarse en la consola antes de usarlo.

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
