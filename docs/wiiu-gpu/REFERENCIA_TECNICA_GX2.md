# Referencia técnica GX2/Latte para Wiintosh (complemento de PLAN_GPU_WIIU.md)

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
