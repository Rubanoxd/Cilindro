# GTA IV (XEX de Xbox 360, europeo) en Wii U — respuesta al encargo

*16-oct-2026. Investigación hecha en la nube, sin acceso a vuestro código, discos ni consola.*

Todo lo que digo de **nuestro XEX** es una hipótesis hasta que se mida. Las direcciones que doy como hechos son de **TU8** y salen del código C++ generado que trae LibertyRecomp (rama de OZORDI, commit `1922957d9f5a`). Lo he reensamblado y analizado aquí. Para pasarlas a nuestro XEX incluyo herramientas que buscan por estructura (ver §0).

---

## 0. Qué hay en esta carpeta (`gta4-wiiu/`)

| Fichero | Para qué |
|---|---|
| `GTA4-WiiU-respuesta.md` | Este documento. |
| `herramientas/busca_tu8.py` + `firmas_tu8.json` | Localiza en `out/imagen.bin` el equivalente de 37 funciones TU8 clave (pantalla de carga, hilo de render, distancia de dibujado, población, Interlocked*, resolve de profundidad…). Compara opcodes, registros y desplazamientos de estructura, no direcciones. Tolera inserciones o borrados de ±2 instrucciones. Probado sobre TU8 reensamblado y sobre una versión mutada (instrucciones insertadas y registros cambiados): recupera la dirección exacta con 0,96–1,00. Python 3 sin dependencias. |
| `herramientas/ppcscan.py` | Buscador binario sobre `imagen.bin`: `refs` (quién lee o escribe una dirección; propaga `lis`/`addi`/`ori`/`mr`), `inm` (dónde se forma una constante, p. ej. un puntero a función), `llamadas`, `atomicos` (lista de `lwarx`/`stwcx.` y fichero de sitios para el remedio del erratum), `patron` y `desen` (este último necesita capstone). Probado sobre TU8 reensamblado. |
| `referencia-tu8.md` | Desensamblado comentado de las funciones TU8 clave, con las direcciones globales calculadas. |
| `codigo/atomicos_espresso.h` | CAS, intercambio y suma atómicos con el `dcbst` del erratum de Espresso, más la reserva del intérprete (P7). |
| `codigo/muestreador.c` | Perfil estadístico por núcleo con Cafe OS (P5). Comprobado contra las cabeceras de wut; sin probar en consola. |
| `codigo/resolve_profundidad.frag` | Sombreador que empaqueta la profundidad GX2 en bytes de resolve de Xenos (D24FS8 → A8R8G8B8) (P2). |

Límites de `busca_tu8.py`: las funciones de menos de unas 10 instrucciones (manejadores de nativos, getters) son ambiguas por naturaleza, porque muchas son idénticas salvo la dirección global. Para esas, partid de `nativos.txt` y de `ppcscan.py`. La dirección que da puede quedar unas instrucciones desplazada si la versión cambió justo al principio de la función: comprobad el prólogo (`mflr r12`) con `ppcscan.py desen`.

Uso típico, desde la raíz de LibertyU:
```
python3 <ruta>/gta4-wiiu/herramientas/busca_tu8.py --imagen out/imagen.bin --lista
python3 <ruta>/gta4-wiiu/herramientas/busca_tu8.py --imagen out/imagen.bin 82145770 821B7048 821B7250 821DFFE8
python3 <ruta>/gta4-wiiu/herramientas/ppcscan.py refs 83137BB5 83137BB6 --funciones out/funciones.csv
python3 <ruta>/gta4-wiiu/herramientas/ppcscan.py atomicos --funciones out/funciones.csv --sitios out/sitios_stwcx.txt
```
(`--funciones` acepta cualquier CSV/TXT con «inicio,fin» o «inicio,longitud» en hex por línea.)

---

## 1. Resumen ejecutivo

1. **Primero P7, atómicos (barato, alta confianza).** El Espresso tiene un erratum en `stwcx.` que exige un `dcbst` justo antes (lo documenta y esquiva NetBSD). Además, todos los `Interlocked*` de la 360 van entre `mfmsr`/`mtmsrd`, privilegiadas en Espresso, y eso obliga a interpretarlos o a pasar por sitios. Mezclar una reserva simulada con `lwarx/stwcx.` reales explica de sobra las «sueltas perdidas». Son unos 60 sitios.
2. **P1 «una sola franja»**: es lo mismo que hace el PR #1262 en una superficie de altura completa. Quita unos 2200 dibujos por imagen (≈2 s de pintor) y casi la mitad del trabajo de píxeles del G-buffer. Hay que tomar el rectángulo del resolve de `vf0`; hoy se usa el alto de `RB_COPY_DEST_PITCH`, que puede desbordar la última franja.
3. **P4 «colores planos»**: lo primero es revisar los uniformes de estado del traductor GLSL antiguo de Xenia. Si `ps_param_gen` vale 0, el prólogo machaca `r0` (la UV) con `gl_FragCoord`: un texel o el mip 1×1 por objeto, es decir, color plano.
4. **P2**: GTA IV usa profundidad D24FS8 (20e4) y la **lee como A8R8G8B8 empaquetado**. El resolve de profundidad debe escribir los bytes de Xenos, no una R32F. También hay que revisar `halfZ` y el valor de borrado 20e4 (1,0 = 0xF00000).
5. **P3**: el parche condicional de `activa` (8212287C) tras `SET_CAM_BEHIND_PED` se prueba en una tarde. Si no basta, restaurar el delegado del hilo de render como hace TU8 (82145770 → 821B7250).
6. **P5**: el muro es el pintor (≈0,9 ms por dibujo). Hay que medir con el muestreador y bajar a ≤50 µs por dibujo. Luego: población y distancia vía `nativos.txt`, quitar reflejos y llevar VMX a *paired singles* en el AOT.
7. **Expectativa honesta**: 30 fps en la ciudad no son alcanzables. Con mucho trabajo, 5–12 fps en la ciudad con densidades bajas; el techo teórico está hacia 15–20 (ver §3).

---

## 2. Prioridades

### P1. Franjas (*predicated tiling*) correctas y baratas

**Diagnóstico (confianza alta).**
- En la Xenos, cada paquete PM4 de tipo 3 con el bit 0 de la cabecera a 1 está **predicado**: solo se ejecuta si `(BIN_SELECT & BIN_MASK) != 0`. D3D reproduce el mismo búfer de órdenes una vez por franja; antes de cada una cambia `BIN_SELECT`, `PA_SC_WINDOW_OFFSET` y el scissor.
- La máscara de cada dibujo debería salir de las extensiones de pantalla que la GPU escribe con `PM4_EVENT_WRITE_EXT` (0x5A). **Xenia escribe extensiones falsas de pantalla entera** (0..2560 en x e y, en unidades de 8 px). Si el port hace lo mismo, o ignora la predicación, *todos* los dibujos pasan en las tres franjas: es lo que veis (~1100 dibujos por franja).
- Con el desplazamiento aplicado en la vista y sin scissor, la franja k pinta de nuevo toda la escena desplazada 256·k filas. Se hace 3× el trabajo de vértices y de CPU del pintor, y 720 + 464 + 208 = 1392 filas de píxeles en vez de 720 (1,93×).
- El PR #1262 de xenia-canary resuelve justo esto en su caché de destinos: con desplazamientos negativos de franja completa, mete el desplazamiento en la base EDRAM y pinta con desplazamiento 0, en coordenadas de pantalla. Con superficies GX2 de 1280×720 por base, «meterlo en la base» equivale exactamente a **pintar la franja 0 en coordenadas de pantalla y copiar las filas 256·k en los resolves**. Vuestra propuesta es correcta y coincide con lo que Xenia considera la referencia.
- Tres fallos probables en el código actual (fragmentos 8.1/8.2):
  1. **Alto de la copia**: `resolve()` usa `alto` de `RB_COPY_DEST_PITCH` como alto. Xenia advierte que `copy_dest_height` puede incluir la parte superior del rectángulo origen, y usa el rectángulo de los 3 vértices de `vf0` (+ desplazamiento de ventana). En la franja 2 (filas 512..719 = 208 filas) un alto de 256 o 720 **escribe fuera de la textura destino**: corrupción de memoria por la GPU. Puede explicar cierres y congelaciones (P7).
  2. **Desplazamiento de ventana sin condición**: `pon_vista` lo aplica siempre. Solo afecta a los vértices si `PA_SU_SC_MODE_CNTL` (0x2205) bit 16 `vtx_window_offset_enable` está a 1.
  3. **Borrados**: si el borrado del resolve (`RB_COPY_CONTROL` 0x2318 bit 8 color, bit 9 profundidad) borra la superficie GX2 entera y no solo el rectángulo, en modo «una franja» destruiría las filas de las franjas 1 y 2 antes de copiarlas.

**Propuesta (`nucleo/gpu.c` + `wiiu/liberty/gx2r.c`).**

Estado nuevo en `gpu.c` (paquetes PM4 de tipo 3; opcodes de Xenia `xenos.h`):
```c
static uint64_t g_bin_mask = ~0ull, g_bin_select = ~0ull, g_sel_franja0 = 0;
static int g_franja = 0, g_modo_una_franja = 1;   /* activable por UDP: FRANJAS 1/3 */

/* en el despacho de tipo 3, antes del switch */
uint32_t op = (cab >> 8) & 0x7F, n = ((cab >> 16) & 0x3FFF) + 1;
int predicado = cab & 1;
switch (op) {
case 0x60: g_bin_mask   = (g_bin_mask   & ~0xFFFFFFFFull) | d[0]; break;          /* SET_BIN_MASK_LO */
case 0x61: g_bin_mask   = (g_bin_mask   &  0xFFFFFFFFull) | ((uint64_t)d[0] << 32); break;
case 0x62: g_bin_select = (g_bin_select & ~0xFFFFFFFFull) | d[0]; break;          /* SET_BIN_SELECT_LO */
case 0x63: g_bin_select = (g_bin_select &  0xFFFFFFFFull) | ((uint64_t)d[0] << 32); break;
case 0x50: g_bin_mask   = ((uint64_t)d[0] << 32) | d[1]; break;                    /* SET_BIN_MASK */
case 0x51: g_bin_select = ((uint64_t)d[0] << 32) | d[1]; break;                    /* SET_BIN_SELECT */
case 0x5A: /* EVENT_WRITE_EXT: escribir extensiones de pantalla entera, como Xenia */ break;
}
if (predicado && !(g_bin_select & g_bin_mask)) { saltar n palabras; return; }
```

Detección de franja en cada dibujo (DRAW_INDX 0x22, DRAW_INDX_2 0x36; también 0x34/0x35 *_BIN si aparecen):
```c
uint32_t wo = R(0x2080);
int wy = ((int32_t)((wo >> 16) << 17)) >> 17;            /* window_y_offset, 15 bits con signo */
int woe = (R(0x2205) >> 16) & 1;                          /* vtx_window_offset_enable */
int alto_franja = 256;                                    /* medirlo: el primer wy negativo de la trama */
g_franja = (woe && wy < 0 && (-wy % alto_franja) == 0) ? -wy / alto_franja : 0;
if (g_franja == 0 && predicado) g_sel_franja0 = g_bin_select;   /* qué bit es la franja 0 */

if (g_modo_una_franja && g_franja > 0 && !es_resolve(/* RB_MODECONTROL 0x2208 & 7 == 6 (kCopy) */)) {
    int ya_pintado = !predicado || (g_bin_mask & g_sel_franja0);
    if (ya_pintado) { cuenta_saltados++; return; }       /* se pintó en la franja 0 */
    cuenta_solo_franja_k++;                               /* raro: predicado solo para k>0 */
    /* pintarlo en coordenadas de pantalla con scissor = banda [256k, 256k+256) */
}
```

`pon_vista` (gx2r.c): en modo una franja, **no** sumar `wx/wy` (se pinta en coordenadas de pantalla) y sumarlos solo si `vtx_window_offset_enable`. El scissor sigue a pantalla completa en la franja 0 y a la banda en los dibujos raros de la franja k.

`resolve()` (gx2r.c):
```c
/* rectángulo real del resolve: 3 vértices float (k_32_32_FLOAT, endian 8in32) en vf0 */
float v[6]; lee_vf0(v);                         /* constante de búsqueda de vértices 0 */
float medio = (R(0x2302) & 1) ? 0.0f : 0.5f;    /* PA_SU_VTX_CNTL pix_center; Xenia suma 0.5 si es D3D */
int x0 = (int)floorf(min3(v[0],v[2],v[4]) + medio + 0.5f), y0 = (int)floorf(min3(v[1],v[3],v[5]) + medio + 0.5f);
int x1 = (int)floorf(max3(v[0],v[2],v[4]) + medio + 0.5f), y1 = (int)floorf(max3(v[1],v[3],v[5]) + medio + 0.5f);
/* modo antiguo: sumar wx/wy (EDRAM en coordenadas de franja). Modo una franja: NO sumarlos,
   así la fuente son las filas 256k.. de la superficie de pantalla completa */
if (!g_modo_una_franja && woe) { x0 += wx; x1 += wx; y0 += wy; y1 += wy; }
x0 &= ~7; y0 &= ~7; x1 = (x1 + 7) & ~7; y1 = (y1 + 7) & ~7;      /* D3D alinea a 8 */
int h = min(y1 - y0, sup->height - y0);                            /* RECORTAR al origen */
h = min(h, (int)dest->surface.height - fila_destino);              /* y al destino */
if (h <= 0) return;
GX2Rect rc = { x0, y0, x1, y0 + h };  GX2Point pt = { 0, fila_destino };
GX2CopySurfaceEx(&c->surface, 0, 0, &dest->surface, 0, 0, 1, &rc, &pt);
if (cc & (3 << 8)) borra_rect(rc);     /* SOLO el rectángulo (cuadrado con scissor), nunca la superficie entera */
```
El destino ya lo resuelve vuestro mecanismo de «la base cae dentro de una copia ya hecha». D3D avanza `RB_COPY_DEST_BASE` en macrobloques de 32×32: para 1280 px y 32 bpp, 256 filas = 0x140000 bytes. Xenia lo documenta en `GetResolveInfo` («D3D advances RB_COPY_DEST_BASE in 32x32 macro tiles based on the destination point»).

Ganancia esperada: unos 2200 dibujos menos por imagen (de ~4400), ≈ −2 s con el coste actual del pintor. En GPU, −2/3 del trabajo de vértices del G-buffer y −48 % de filas de píxeles.

**Cómo comprobarlo.**
- `TRAZA` con `FRANJAS 3` y `FRANJAS 1`. Contar dibujos en las tres franjas (esperado: ~1100/0/0) y comprobar que `cuenta_solo_franja_k` vale 0. Si no es 0, listar esos dibujos (sombreador, destino).
- Registrar en la trama los valores de `BIN_MASK`/`BIN_SELECT` por franja y si hay `EVENT_WRITE_EXT`.
- `VER 1B52A000`, `VER 1B8C2000`, `VER 1BC5A000`: sin costuras en las filas 256 y 512. Comparad con una captura `FOTO` del modo antiguo.
- `MIDE` antes y después: tiempo de pintor por imagen y dibujos por imagen.
- Para el desbordamiento: registrar en cada resolve `y0, y1, alto(RB_COPY_DEST_PITCH)` y la altura de la textura destino. Si `fila + alto > altura`, era un desbordamiento.

**Riesgos y alternativas.**
- Dibujos predicados solo para franjas > 0 (algo por franja que el juego pinte a mano): se detectan con el contador. Si existen, pintarlos en coordenadas de pantalla con scissor de banda (incluido arriba).
- Sombreadores que usan VPOS (`ps_param_gen`) con desplazamiento de ventana: en modo una franja VPOS queda en coordenadas de pantalla, que es lo que espera el juego. El PR #1262 lo justifica por eso.
- Alternativa más conservadora: seguir pintando las 3 franjas pero con scissor de banda (corrige el exceso de píxeles, no el de dibujos). Ganancia mucho menor.

**Fuentes.** xenia-canary PR #1262 y #1271; `src/xenia/gpu/pm4_command_processor_implement.h` l. 408–418 (predicado), 511–545 (BIN_MASK/SELECT), 943–976 (EVENT_WRITE_EXT falso); `draw_util.cc` l. 925–1010 (`GetWindowOffsetTiles`) y 1240–1640 (`GetResolveInfo`); `registers.h` (`PA_SC_WINDOW_OFFSET`, `PA_SU_SC_MODE_CNTL`, `RB_COPY_CONTROL`); `xenos.h` l. 1638–1694 (opcodes PM4). Patente US20060055701 (citada en el código de Xenia). Commit `583528e8e3d7`.

---

### P2. Resolves de profundidad (fuente 4 de `RB_COPY_CONTROL`)

**Diagnóstico.**
- (Alta) **GTA IV usa D24FS8** (profundidad float 20e4 + stencil 8). El pintor nativo de LibertyRecomp solo traduce `D3DFMT_D24FS8` (0x1A220197) como formato de profundidad (`graphics_system.cpp` l. 1445 y 2617).
- (Alta) **El juego lee el resolve de profundidad como A8R8G8B8**. En TU8, `sub_828D9768` crea una cabecera de textura con formato 0x18280186 (A8R8G8B8 de 360) sobre la memoria del resolve de profundidad. LibertyRecomp lo reproduce con `packed_depth_alias_ps.glsl` («sub_828D9768 reinterprets a resolved D24FS8 texture as A8R8G8B8»). Los sombreadores de luces decodifican esos bytes a mano, así que **hay que escribir los bytes de Xenos**: palabra = `(prof24 << 8) | stencil`, con prof24 en 20e4. En los canales de la textura, R = stencil, G/B/A = bytes 0/1/2 de prof24 (con el convenio de Xenia; el swizzle 0x60A del juego pone el stencil en azul).
- (Media) Al ignorar hoy el resolve, la luz en diferido reconstruye posiciones con basura. Por eso «Luces (77 dibujos)» no puede salir bien aunque el G-buffer fuera perfecto.
- Dos fallos relacionados que conviene comprobar:
  - **Rango de Z**: el traductor GLSL de Xenia (backend GL4) asumía `glClipControl(GL_UPPER_LEFT, GL_ZERO_TO_ONE)` global (`gl4_command_processor.cc` l. 289). En GX2 hace falta `GX2SetRasterizerClipControlEx(TRUE, !clip_disable, halfZ=TRUE)` según `PA_CL_CLIP_CNTL` (0x2204): bit 16 `clip_disable`, bit 19 `dx_clip_space_def`. Sin `halfZ`, la profundidad guardada es (z+1)/2: se pierde la mitad de la precisión y las posiciones reconstruidas salen mal.
  - **Valor de borrado**: `RB_DEPTH_CLEAR` (0x231D) >> 8 está en **20e4** cuando `RB_DEPTH_INFO` (0x2002) bit 16 = 1. 1,0 es 0xF00000. Leído como unorm daría 0,9375 y **recortaría todo lo que esté más allá de z = 0,9375** (geometría lejana desaparecida).

**Propuesta (`gx2r.c`, `resolve()` con `fuente == 4`).**
```c
/* 1. búfer de profundidad GX2 del destino EDRAM actual */
GX2DepthBuffer *db = destino_profundidad(R(0x2002));
int d24fs8 = (R(0x2002) >> 16) & 1;
/* 2. a textura (no está declarada en wut: declararla) */
void GX2ConvertDepthBufferToTextureSurface(GX2DepthBuffer *src, GX2Surface *dst, uint32_t mip, uint32_t slice);
GX2ConvertDepthBufferToTextureSurface(db, &g_prof_tex.surface, 0, 0);
/*    g_prof_tex: mismo ancho/alto; formato FLOAT_R32 si db es D32F, UNORM_R24_X8 si es D24S8.
      Para el stencil, otra GX2Texture sobre la MISMA imagen con UINT_X24_G8 (D24S8) o
      UINT_G8_X24 (D32F_S8X24). */
GX2Invalidate(GX2_INVALIDATE_MODE_TEXTURE, g_prof_tex.surface.image, g_prof_tex.surface.imageSize);
/* 3. pasada a pantalla completa (o al rectángulo de vf0, como en P1) sobre un GX2ColorBuffer
      RGBA8 que se registra en la tabla de resolves con la dirección RB_COPY_DEST_BASE (0x2319),
      igual que un resolve de color; sombreador codigo/resolve_profundidad.frag */
pinta_cuadrado(&g_prog_empaqueta, &g_prof_tex, &g_sten_tex, d24fs8, rect);
GX2Invalidate(GX2_INVALIDATE_MODE_COLOR_BUFFER | GX2_INVALIDATE_MODE_TEXTURE, dst_img, dst_size);
```
En modo «una franja» (P1), el resolve de profundidad de la franja k copia las filas 256k.. del mismo búfer, igual que el color.

Opciones de formato de la profundidad GX2:
- **D32F** (`GX2_SURFACE_FORMAT_FLOAT_R32` como profundidad): la conversión a 20e4 es exacta en el sombreador. Sin stencil, salvo con `FLOAT_X8_X24` (64 bpp).
- **D24S8 unorm**: tiene stencil, pero pierde precisión lejos respecto al 20e4.
- (Hipótesis) **`GX2_SURFACE_FORMAT_FLOAT_D24_S8` (0x811)**: el R6xx tiene `DEPTH_8_24_FLOAT`, probablemente el mismo 20e4 de la Xenos (ATI diseñó ambas). Si lo es, sería la opción idéntica en bits. Comprobarlo: borrar a 1,0, leer con `GX2ConvertDepthBufferToTextureSurface` y comparar.

Ruta de depuración (lenta): `GX2DrawDone()`, leer el búfer convertido con la CPU y empaquetar ahí. Sirve para validar el sombreador.

**Cómo comprobarlo.**
- En `TRAZA`: qué constantes de búsqueda leen 1B192000 (y las otras direcciones de resolve de profundidad), con qué formato (`dword_1` bits 0–5: 6 = k_8_8_8_8; 22/23 = k_24_8 / k_24_8_FLOAT) y qué swizzle (`dword_3` bits 1–12). Deberían ser k_8_8_8_8.
- `VER 1B192000`: hoy basura o negro; después, un degradado suave. Con 20e4 casi todo cae en exponente 14–15, así que el canal A (byte alto) varía poco y G/B mucho.
- `VER 1BC5A000` (luces): debería pasar de manchas a luz coherente con la geometría.
- Registrar `PA_CL_CLIP_CNTL` y `RB_DEPTH_CLEAR` en la trama; si el bit 19 está a 1 y no se usa `halfZ`, corregir.

**Riesgos y alternativas.** El orden de canales del alias A8R8G8B8 tiene que coincidir con cómo vuestro cargador trata k_8_8_8_8 desde memoria. Comprobadlo una vez con una textura conocida y permutad en el sombreador. `GX2ConvertDepthBufferToTextureSurface` cuesta una pasada de pantalla completa (~1 ms en Latte, estimado). Si CafeGLSL no acepta `floatBitsToUint`/`uint`, la conversión a 20e4 se puede hacer en float (exponente = `floor(log2(d))`, mantisa = `d/2^e − 1`).

**Fuentes.** LibertyRecomp `src/graphics/gta4_native/packed_depth_alias_ps.glsl` y `scene_depth_handoff_ps.glsl` (`depth20e4`), `graphics_system.cpp`, `native_clip_control.h`; TU8 `sub_828D9768` (ver `referencia-tu8.md`); xenia `xenos.h` l. 403–407 (D24S8/D24FS8 20e4) y 955+ (comentario de resolves), `xenos.cc` `Float20e4To32`, `shaders/texture_load_depth_unorm.cs.xesl` / `_float`, `vulkan/vulkan_texture_cache.cc` (k_24_8 → R32F con swizzle RRRR); Cemu `GX2_Surface_Copy.cpp` (`GX2ConvertDepthBufferToTextureSurface` = copia de superficie con retiling); wut `gx2/registers.h` (`GX2SetRasterizerClipControlEx`), `gx2/enum.h` (formatos), `cafe/gx2.def` (exportación sin cabecera); xenia antiguo `gl4_command_processor.cc` (commit `083f936b`).

---

### P3. Quitar la ilustración de carga cuando la partida ya corre

**Qué hace TU8 (confianza alta: leído en el código).**
- Globales TU8: `831D5335` activa, `5336` lista, `5337` completa, `5340` índice de pantalla, `5348` puerta de audio, `5349` (?), `831D5338` tiempo acumulado (float), `831D533C` delta, `831D4DC4` **diccionario de texturas de la carga**, `831D5498` definiciones (14 pantallas × 400 B; 4 capas × 96 B por pantalla, desde `loadingscreens.dat`).
- **La ilustración la pinta un delegado del hilo de render**, no el bucle principal. El objeto del hilo de render es `82B2F7E0` y su delegado de 16 B está en `+4048`.
  - `sub_821B7048(obj, delegado)` lo instala.
  - `sub_821B7250(obj)` restaura el de por defecto, que es la función de render del juego `sub_821B5C90`.
  - `sub_82145420` (arranque de la carga) y `sub_82145820` instalan el thunk `82143DC0` → `sub_82144188`, el pintor de la carga. Este solo pinta si activa ≠ 0 y dibuja las capas con `sub_82143DC8` (texturas del diccionario `831D4DC4`).
  - El cierre `sub_82145770(arg)` pone lista = 0, restaura el delegado (`821B7250`), vacía el hilo (`821B5A68`), libera recursos (`82287F08(-1)`, `8235C370("…")`, `82146690`/`821464B8`), pone el diccionario a 0 y activa = 0.
  - Lo llaman la activación del mundo `sub_82141F00` al final, con r3 = 0, y la máquina de arranque `sub_82140088`.
- Eso explica vuestras pruebas: leer BB7 en 821201B0 no cambia nada porque el bucle principal no decide qué se pinta.

**Qué es probable en nuestro XEX (confianza media).**
- La estructura de globales es la misma. Vuestras BB5/BB6/BB7 y 83137BE4 (visor HTML) están al mismo desfase entre sí que en TU8: BE4 − BB5 = 0x2F = 5364 − 5335. El desfase global es 0x9D780. Por tanto, probablemente:
  - índice de pantalla = **83137BC0**
  - puerta de audio = **83137BC8**
  - diccionario de la carga = **83137644**
  - definiciones = **83137D18**
  
  Comprobadlo con `PEEK` antes de fiaros.
- Vuestra `821227F0` (activa = 1 en **cada** trama) no existe en TU8: en TU8 solo escriben activa = 1 el arranque y el cierre. La lógica cambió entre versiones. En TU8, el caso 28 de su máquina de 48 casos (`sub_82151D08`, que también lee el visor HTML) cae en una etiqueta compartida sin efecto. Vuestro cierre puede estar en línea dentro de la rama «lista» (82122958 → `bl 82122638`; activa = 0).

**Propuesta (árbol de decisión; cada paso cuesta una prueba de ~15 min).**

*A. Prueba barata (hoy).* Cuando el núcleo vea la orden `SET_CAM_BEHIND_PED` (dirección en `nativos.txt`; el despacho está en 827D61D8), dejad activa a 0 sin tocar el arranque:
```
8212287C:  li r11,1          ->   b sitio_activa
sitio_activa:
    lis   r11, bandera_carga@ha
    lbz   r11, bandera_carga@l(r11)     ; 1 = partida en marcha (la pone el núcleo)
    xori  r11, r11, 1                   ; activa = !bandera
    b     0x82122880
```
`r11` está libre ahí (se iba a cargar con 1) y no se toca el resto. Para probarlo a mano antes de añadir el sitio: `POKE 8212287F 00` (convierte `li r11,1` en `li r11,0`). Hace falta una orden `POKEI` que haga `DCFlushRange` + `ICInvalidateRange` en los 3 núcleos (un hilo fijado por núcleo, como `runOnAllCores` de MemoryMappingModule); si no, la caché de instrucciones puede seguir ejecutando la instrucción vieja.
- Si desaparece la ilustración y se ve la partida: hecho. Después, endurecer el disparo (por ejemplo, cuando el gestor de cinemáticas pase a «terminado» en 82CC102C) y liberar el diccionario.
- Si la tele se queda negra o congelada con la carga: el delegado de carga sigue instalado. Pasad a B.

*B. Restaurar el delegado de render como TU8.*
```
python3 busca_tu8.py --imagen out/imagen.bin 821B7048 821B7250 821B5A68 82145770 82145420 82145820 82144188 821B5C90
python3 ppcscan.py inm <dir_de_nuestro_thunk_82143DC0> --funciones out/funciones.csv   # quién instala el pintor de carga
```
- `PEEK <obj+4048>` (16 bytes, obj = nuestro 82B2F7E0 equivalente; sale del `addi rX,rY,-2080` de 82145770/821B7250) durante la ilustración: si el puntero de método es el del pintor de carga, ahí está.
- Arreglo: llamar a nuestra función equivalente a `82145770` con **r3 = 1** (fuerza el cierre) desde un **hilo del juego**, en un punto seguro del bucle principal (por ejemplo, un sitio en 821201B0 que la llame una vez cuando `bandera_carga` = 1). No escribáis los 16 bytes desde el núcleo: el hilo de render los lee sin cerrojo.

*C. Si con activa = 0 y el delegado por defecto la ilustración sigue*, la pinta el render del juego (2D/HUD). Identificad las texturas:
- `PEEK 83137644` → diccionario → texturas → base (`dword_1` bits 12–31 << 12).
- En `TRAZA`, buscad cuál de los 171 dibujos de la composición (1874C000) usa esas bases. LibertyRecomp sitúa el productor de listas de HUD/carga en TU8 `sub_821BD028` / `sub_821F6550`.
- Saltad ese productor cuando `bandera_carga` = 1.

*D. Causa raíz (lista nunca vale 1), en paralelo.* «Lista» la escribe solo el caso 28 de 82130B88, ligado a `CGenericGameStorage`. Hipótesis (media-baja): una operación XAM de perfil o almacenamiento que no se completa como espera el juego (`XOVERLAPPED` sin `InternalLow`/evento, `ERROR_IO_PENDING` frente a `ERROR_SUCCESS`, o una notificación `XN_SYS_UI` / `XN_SYS_STORAGEDEVICESCHANGED` que nunca llega). Registrad:
- el estado de 82130B88 (el índice del `switch`) cada segundo;
- todas las llamadas Xam* de ese hilo con argumentos y resultado: `XamUserReadProfileSettings`, `XamContentCreateEx`, `XamContentClose`, `XamEnumerate`, `XamShowDeviceSelectorUI`, `XamContentGetDeviceState`, `XamUserGetSigninState`, `XNotifyGetNext`.

Si el estado se queda fijo, esa es la llamada a arreglar. Forzar lista = 1 reinicializa el mundo porque 82122638 cree que la sesión no ha empezado.

**Riesgos.** La solución A deja cargados el diccionario y el audio de carga (memoria) y desactiva futuras pantallas de carga (reintentos de misión): es aceptable para avanzar, pero conviene acabar con B o con D. Llamar a 82145770 desde el núcleo, fuera de un hilo del juego, es peligroso (r13, pilas, carreras con el hilo de render).

**Fuentes.** LibertyRecomp `gta4-recomp/src/gta4_transition_hooks.cpp` (globales `kLoading*`, hooks de `sub_82144188`, `sub_82141F00`, `sub_82145770`), `gta4_loading_parallax_fix.cpp` (pantallas de 400 B, capas de 96 B, 831D5498), `generated/gta4_recomp.0.cpp` (cuerpos) y `docs/uaf-handle-investigation/15-synthesis.md` (productor de listas HUD/carga); `referencia-tu8.md` (desensamblado comentado); MemoryMappingModule `memory_mapping.cpp` (`runOnAllCores`).

---

### P4. Texturas en el G-buffer (destino 0 con colores planos)

**Diagnóstico.** El traductor `xsomb` viene del GLSL antiguo de Xenia (backend GL4, 2015–2019; lo he leído en el historial: `src/xenia/gpu/glsl_shader_translator.cc`, commit `66a9c9d8`). Ese traductor depende de un bloque de estado por dibujo (`StateData`: `window_scale`, `vtx_fmt`, `alpha_test`, `ps_param_gen`, `texture_samplers[32]`, `texture_swizzles[32]`, `float_consts[512]`, `bool_consts[8]`, `loop_consts[32]`). Hay tres modos de fallo que dan colores planos, en orden de probabilidad:
1. (Media-alta) **`ps_param_gen` mal puesto.** El prólogo del sombreador de píxeles hace `if (state.ps_param_gen < 16) r[i] = mix(r[i], vec4(gl_FragCoord.xy, gl_PointCoord.xy), state.ps_param_gen == i)` (l. 351–360). Si el port pasa 0, o el uniforme se lee como 0, **r0 (normalmente la UV) se sustituye por la posición del píxel**. Con `pixel_center_integer` son enteros: con WRAP, `fract` = 0, todos los píxeles leen el texel (0,0) y las derivadas son enormes, así que sale el mip 1×1. **Resultado: un color plano por objeto.** El valor correcto es 0xFF salvo si `SQ_PROGRAM_CNTL` (0x2180) bit 18 `param_gen` = 1; en ese caso, `SQ_CONTEXT_MISC` (0x2181) bits 8–15 `param_gen_pos`.
2. (Media) **Modo de uniformes.** CafeGLSL avisa: «GX2 uses one uniform mode for both the vertex and pixel shader in each draw… The Wii U reads uniform values as zero when the wrong mode is selected» (README l. 182). Si VS y PS se compilaron por separado en modo `auto` y uno acabó en registros y el otro en bloque, el PS lee 0 en `ps_param_gen` (→ caso 1), en `texture_swizzles` (→ (R,R,R,R): gris) y en `bool/loop_consts` (ramas equivocadas). Que el VS funcione (perspectiva correcta) no lo descarta: puede ser el PS el que se queda en el otro modo.
3. (Media-baja) **LOD o mips.** Si `GX2InitSamplerLOD` recibe mal `mip_min_level` / `mip_max_level` / `lod_bias` (`dword_4` bits 2–5, 6–9, 12–21), o se ignoran `mip_address` (`dword_5` bits 12–31, *los mips van en otra dirección*) y `packed_mips` (`dword_5` bit 11, cola de mips empaquetada), lo lejano o todo sale del mip más pequeño o de basura.
4. (Baja) **Sampler sin textura.** El traductor, si el «handle» es 0, devuelve `vec4(src0.x, src0.y, 0, 1)`, la UV como color (degradados rojo/verde, no planos). Si veis eso, es un fallo de enlace de slots.
5. (Baja) **De-tiling / endian / BCn mal**: da ruido o bloques, no colores planos.

**Propuesta.**
- `gx2r.c`: en cada dibujo, `estado.ps_param_gen = (R(0x2180) >> 18 & 1) ? (R(0x2181) >> 8 & 0xFF) : 0xFF;`. Comprobad también `texture_swizzles` (`dword_3` bits 1–12, 3 bits por canal: 0–3 = XYZW, 4 = 0, 5 = 1) y los flags de textura presente.
- Compilad cada par VS/PS con `CompileShaderPair()` o `--uniform-mode block` y llamad siempre a `GX2SetShaderMode(GX2_SHADER_MODE_UNIFORM_BLOCK)`. No mezcléis modos entre dibujos sin cambiar el modo de sombreador.
- **Textura de diagnóstico** (orden UDP `TEXPRUEBA 1`). Para los dibujos cuyo destino 0 sea 1B52A000, enlazad en todos los slots una textura RGBA8 de 256×256 con mips: mip 0 = tablero blanco/negro de 8 px, mip 1 rojo, 2 verde, 3 azul, 4 amarillo, 5 cian, 6 magenta, 7+ gris. Lectura de `VER 1B52A000`:
  - se ve el tablero en perspectiva → UV y LOD bien: el fallo está en las texturas reales (formato, de-tiling, caché);
  - un color de mip N uniforme → LOD o derivadas;
  - un solo color sin tablero (blanco o negro) → UV constante: `ps_param_gen` o modo de uniformes;
  - degradado rojo/verde → slot sin textura.
- Registrad, para los 5 primeros dibujos del G-buffer, el bloque `StateData` en hex y las 6 palabras de cada constante de búsqueda usada. En trozos: el registro corta las líneas largas.

**Cómo comprobarlo.** La textura de diagnóstico separa los casos en una sola prueba. Después del arreglo, `VER 1B52A000` debe mostrar albedo con detalle. Si el destino 0 resulta no ser albedo (en GTA IV el destino 1 son normales; el reparto exacto está sin confirmar), comparad con `VER` de los cuatro destinos.

**Riesgos.** Cambiar a modo bloque para todos los sombreadores exige que el pintor suba siempre el estado como bloque. Ojo con el tamaño: 512 vec4 + estado ≈ 8,6 KB por bloque; hay que compartirlo entre dibujos (ver P5).

**Fuentes.** Xenia antiguo `glsl_shader_translator.cc` l. 113–124 (`StateData`), 341–360 (`r[64]` y `ps_param_gen`), 371–381 (bucle `do{switch}`), 681–735 (búsqueda de textura con alternativa `vec4(uv,0,1)`), 762/795 (LOD sin implementar); CafeGLSL README (modos de uniformes); xenia-canary `registers.h` (`SQ_PROGRAM_CNTL`, `SQ_CONTEXT_MISC`) y `xenos.h` l. 1245+ (`xe_gpu_texture_fetch_t`).

---

### P5. Rendimiento (el muro)

**Diagnóstico (confianza alta en el orden de magnitud).** 4–8 s por imagen, de los que **~4 s son del pintor** con 4000–4400 dibujos: ≈0,9 ms por dibujo, ~1,1 M ciclos. Los subtotales medidos (vértices 77 + estado 275 + texturas 157 ms) suman 0,5 s: **unos 3,5 s del pintor no están medidos**. Lo primero es medirlos.

Sospechosos, por orden:
1. subir las 512 vec4 de constantes en cada dibujo (8 KB × 2 × 4400 = 70 MB por imagen) con `GX2Set*UniformReg` (escrituras de registro en el búfer de órdenes) o con copias y `DCFlushRange` enteros;
2. hash de microcódigo o de texturas por dibujo;
3. esperas a la GPU (`GX2DrawDone`, marcas de tiempo, `VIGILA`) en resolves;
4. `DCFlushRange`/`GX2Invalidate` de rangos grandes por dibujo (≈1 ciclo por byte: 1 MB ≈ 1 ms);
5. el propio análisis PM4 con `R()` sobre un fichero de registros grande.

La GPU (0,2 s) no es el cuello todavía. Aun así, el traductor GLSL antiguo mete **todo el sombreador en `do { switch(pc) {…} } while (pc != 0xFFFF)`** con `vec4 r[64]` (l. 341, 371–381). En R700 eso es lento: bucle dinámico, `switch` e índices dinámicos de registros.

**Medir primero: `codigo/muestreador.c`.** Es un hilo de prioridad 0 por núcleo que cada 997 µs lee `OSThread->context.srr0` del hilo invitado expulsado (o el PC invitado del intérprete) y hace un histograma. Con `PERFIL` por UDP vuelca el top 40 por núcleo. Así sabréis cuánto es pintor, juego nativo, intérprete, AOT o espera. Combinadlo con temporizadores `OSGetSystemTick()` por fase dentro del pintor.

**Palancas concretas (detalle; la tabla está en §3).**

1. **Pintor GX2** (`gx2r.c`):
   - *Constantes*: copia sombra de 256+256 vec4 con mapa de rangos sucios por `SET_CONSTANT` (0x2D) / `LOAD_ALU_CONSTANT` (0x2F). Si nada cambió desde el dibujo anterior, **reutilizad el mismo puntero de bloque** (`GX2SetVertexUniformBlock`/`GX2SetPixelUniformBlock` solo cambian un puntero). Si cambió, copiad a un anillo en MEM2 (256 B alineado) y `DCFlushRange` solo de lo escrito.
   - *Sombreadores*: caché por (dirección de microcódigo, tamaño, suma rápida de 64 bits). El hash completo solo la primera vez por dirección.
   - *Vértices sin copia*: los sombreadores de búsqueda (*fetch shaders*) de GX2 admiten intercambio de bytes por atributo (`GX2AttribStream.endianSwap`: 8in16/8in32). Leed los búferes de vértices de la memoria invitada directamente, con `DCFlushRange` del rango. Para eso la memoria «física» (0x60000000) tiene que tener dirección física válida para GX2 (ver P7, SR).
   - *Índices sin copia*: `GX2_INDEX_TYPE_U16` / `U32` son **big-endian** (los `_LE` son los otros). Los índices de Xenos valen tal cual (con el campo de endian de `DRAW_INDX`).
   - *Texturas*: memo por trama (base + tamaño + número de imagen). Re-hash solo si cambia una suma rápida de la cabecera y de unas pocas líneas.
   - *Estado*: bits sucios por grupo de registros Xenos; no repetir `GX2Set*`.
   - *Búfer de órdenes*: `GX2Init` con `GX2_INIT_CMD_BUF_POOL_SIZE` grande (p. ej. 32 MB). Ningún `GX2DrawDone` dentro de la trama salvo depuración.
   - Objetivo: ≤50 µs por dibujo en una primera fase y 10–20 µs después.
2. **P1** (−50 % dibujos).
3. **Contenido**, con direcciones a sacar de nuestro XEX:
   - *Población*. En TU8 las globales son `82AA1888` (coches aleatorios), `82AA188C` (aparcados), `82AA2940` (peatones), `82AA2944/48` (peatones de escenario actual/siguiente). Las escriben los manejadores de nativos (`sub_82598EB0/ED0/EE8`: `lwz r10,8(r3); lfs f0,0(r10); stfs f0,G(r11); blr`) y los reinicios `823A5330`/`823F4860`. En nuestro XEX: `grep -i density out/nativos.txt` (`SET_CAR_DENSITY_MULTIPLIER`, `SET_PED_DENSITY_MULTIPLIER`, `SET_PARKED_CAR_DENSITY_MULTIPLIER`, `SET_RANDOM_CAR_DENSITY_MULTIPLIER`, `SET_SCENARIO_PED_DENSITY_MULTIPLIER` si existe), y `ppcscan.py desen <dir> 8` para ver la `stfs` y su global. Cada 100 ms el núcleo hace `if (*g > 0) *g = fminf(*g, 0.3f)`. **No toquéis los valores ≤ 0**: el juego usa 0 para vaciar la ciudad en misiones y transiciones (LibertyRecomp `gta4_population_policy.h`). Buscad también `SET_REDUCE_PED_MODEL_BUDGET` / `SET_REDUCE_VEHICLE_MODEL_BUDGET` en `nativos.txt`.
   - *Distancia*. En TU8, `sub_821DFFE8` calcula `viewport+2340 = viewport+2276 × flt_82A931BC` y publica `82A931B4`, que leen 13 funciones de LOD/culling. `busca_tu8.py 821DFFE8` da la función; dentro, la `lfs` con base de `lis` que se multiplica por `lfs fX,2276(r31)` (0xC01F08E4 si es f0) es nuestro multiplicador. Probad en vivo con `POKE` de 4 bytes: 0,6 = `3F 19 99 9A` y 0,5 = `3F 00 00 00`. Tocad la entrada (el multiplicador), no la salida, que se recalcula cada trama. Ojo: la función tiene VMX128, así que probablemente se interpreta.
   - *Reflejos*. GTA IV tiene fases separadas: `CRenderPhaseWaterReflection` y `CRenderPhaseMirrorReflection` (320×180: vuestro «prepaso» de 802 dibujos), `CRenderPhaseReflection` (paraboloide de 256×256 con mips) y `CRenderPhaseInteriorReflection`. Están en RTTI: buscad las cadenas `CRenderPhaseWaterReflection` etc. en `imagen.bin` → vtable → método de construir o pintar → `blr` en la entrada. También sirven `busca_tu8.py 822BCC20 822D93E8 825225E8`. Las texturas consumidoras se quedan con su último contenido o negro: aceptable.
4. **Intérprete → nativo/AOT.** En TU8 (código generado):
   - 54,6 % de las funciones (35,8 % de instrucciones) son limpias de 32 bits;
   - el **37,5 % de las instrucciones** está en funciones con VMX;
   - el **26,6 %** está en funciones con 64 bits pero sin VMX.

   Las instrucciones de 64 bits más frecuentes son `std` (24 882), `ld` (22 304), `fcfid` (4245), `extsw` (2515), `fctidz` (753), `rldicr` (695), `clrldi` (522), `rldicl` (419) y `mulld` (309). Casi todas tienen equivalente de 32 bits:
   - `std/ld rN,d(r1)` de prólogo o epílogo (guardar registros no volátiles) → `stw/lwz rN,d+4(r1)`: en nativo los GPR son de 32 bits.
   - `fctidz f; stfd f,X; lwz r,X+4` → `fctiwz` (difiere solo fuera de rango).
   - `extsw r; std r,X; lfd f,X; fcfid f` (int → float) → la secuencia clásica de 32 bits con 0x4330 (`xoris r,r,0x8000; stw r,X+4; lis r,0x4330; stw r,X; lfd f,X; fsub f,f,K`, con K = 0x4330000080000000). Va en un sitio con su propio registro temporal.
   - `clrldi/rldicl rD,rS,0,32` → `mr`; `mulld` usado como 32 bits → `mullw` (los 32 bits bajos son idénticos).
   - `ld/std` usados como copias de 8 B → `lfd/stfd` (copian 64 bits tal cual).

   Si un análisis estático (consumidores que solo usan 32 bits) convierte esto, gran parte de ese 26,6 % pasa a nativo. Lo mismo para los `Interlocked*` (P7): quitar el corchete `mfmsr/mtmsrd`.
5. **VMX128 → *paired singles* en el AOT.** Cada operación de 4 floats son 2 `ps_*`:
   - `vaddfp/vsubfp/vmulfp/vmaddfp` → `ps_add/ps_sub/ps_mul/ps_madd` sobre (xy, zw);
   - `vmsum4fp128` (producto escalar) → `ps_mul` + `ps_madd` + `ps_sum0` + `ps_merge00`;
   - `vspltw` → `ps_merge00/11`;
   - `vrsqrtefp/vrefp` → `ps_rsqrte/ps_res`;
   - `vsel` → `ps_sel`;
   - `vupkd3d128` y desempaquetados de vértices → `psq_l` con GQR (u8/s8/u16/s16 → float con escala, gratis);
   - `lvx/stvx` → 2× `psq_l/psq_st`;
   - `vperm` con máscara constante (cargada de tabla) → `ps_merge*` si permuta palabras de 32 bits; si no, escalar.

   GCC ya no tiene `-mpaired`: hay que emitir bloques de ensamblador por bloque básico, asignando los registros v a pares de FPR en el propio `aot.py`. Ojo: `stfd` solo guarda ps0, así que nunca dejéis que GCC derrame un FPR con datos emparejados. Referencia de estilo: `libogc/gu_psasm.S`. Esperable: ×2–×4 en las funciones calientes de matemáticas frente al AOT escalar. Recordad que VMX vacía los denormales y la FPU no (XenonRecomp lo trata por instrucción); en PS rige `FPSCR[NI]`.
6. **AOT estilo XenonRecomp.** Opciones `skip_lr`, `skip_msr`, `ctr_as_local`, `xer_as_local`, `reserved_as_local`, `cr_as_local`, `non_argument_as_local`, `non_volatile_as_local`. Según su README, «the local variable optimization particularly introduces the most improvements, as the calls to the register restore/save functions can be completely removed, and the redundant stores to the PPC context struct can be eliminated». En TU8 casi todos los prólogos llaman a `__savegprlr_N` / `__restgprlr_N` (`bl 0x829ff7xx`): en AOT, sustituid esas llamadas por guardados directos o por nada (registros locales).
7. **Hilos y memoria.**
   - El pintor en su núcleo, en paralelo con el juego: la imagen pasa a ser máx(juego, pintor) en vez de la suma. Hipótesis a verificar: GX2 se usa solo desde el núcleo de `GX2Init`. El hilo principal del juego, en el núcleo con 2 MB de L2 (núcleo 1) si no es el de GX2.
   - Las superficies que imitan la EDRAM, en **MEM1** (32 MB de eDRAM de Latte) y no en MEM2 (DDR3, ~12,8 GB/s). Con P1 bastan 4 destinos + profundidad = 17,6 MB.
   - Superficies EDRAM del tamaño que pide `RB_SURFACE_INFO` (paso) × altura estimada, no 1280×720 para todo (el prepaso de 320×180 o el reflejo de 256×256 no necesitan 3,5 MB cada uno).
8. **Resolución interna 960×540 / 640×360**: solo cuando la GPU sea el cuello (hoy es el 4 % de la imagen). Exige escalar vistas, scissor, resolves y constantes de medio píxel en todo el pintor (LibertyRecomp lo hizo solo para las 6 superficies de reflejos). Lo dejo para el final.
9. **Sombreadores sin `do{switch}`**: cuando el control de flujo de Xenos sea lineal (sin saltos ni bucles; la mayoría de los PS), emitir GLSL recto. Ganancia esperada ×2–×5 en esos PS (hipótesis; medidla con `MIDE` sobre una pasada concreta).

**Fuentes.** XenonRecomp README («Optimizations»); LibertyRecomp `gta4_draw_distance_policy.h`, `gta4_native_hooks.cpp` l. 173–174 y 4934–4955, `gta4_population_hooks.cpp`, `gta4_population_policy.h`, `docs/reflection-rendering-study/README.md`; wut `coreinit/thread.h` (`OSThread`, offsets), `gx2/enum.h` (tipos de índice, `GX2_INIT_CMD_BUF_POOL_SIZE`); Tom's Hardware (relojes de Espresso y Xenon).

---

### P6. Audio XMA real

**Diagnóstico (alta).** El XMA falso ya cumple el protocolo de contextos: el juego avanza y las cinemáticas corren en tiempo real. Solo falta descodificar y, además, **sacar el audio**: hoy la rutina del juego se llama cada 256 muestras, pero entiendo que su salida se descarta.

**Propuesta.**
1. **Descodificador.** No el `xma2` de FFmpeg tal cual, sino el **`xmaframes`** de la rama de FFmpeg de Xenia (`has207/FFmpeg`, rama `xmaframes`, `libavcodec/wmaprodec.c` l. 2136–2270). Recibe **una trama XMA suelta**: un byte de relleno (3 bits inicio, 3 bits fin) y luego la trama. El análisis de paquetes de 2048 B, con tramas que cruzan paquetes, saltos, bucles y subtramas, lo hace el emulador, como en `xenia/apu/xma_context_new.cc`. Esa capa (≈1000 líneas) es la que hay que portar a `xma_trabaja`, encima de vuestro anillo de salida ya hecho. Constantes: paquete de 2048 B (cabecera de 4), 512 muestras por trama, 128 por subtrama, bloques de salida de 256 B, máximo 31 bloques.
2. **Compilación para devkitPPC** (modelo: el `PKGBUILD` de `switch/ffmpeg` de devkitPro):
```
./configure --enable-cross-compile --cross-prefix=powerpc-eabi- --arch=ppc --cpu=750 --target-os=none \
  --disable-asm --disable-altivec --disable-vsx --disable-pthreads --disable-w32threads --disable-os2threads \
  --disable-runtime-cpudetect --disable-autodetect --disable-programs --disable-doc --disable-debug \
  --disable-avdevice --disable-avformat --disable-swscale --disable-swresample --disable-avfilter --disable-network \
  --disable-everything --enable-decoder=xmaframes \
  --extra-cflags="-mcpu=750 -meabi -mhard-float -O2 -ffunction-sections -fdata-sections -D__WIIU__ -I$DEVKITPRO/wut/include" \
  --enable-static --disable-shared --prefix=$PWD/salida
```
   Probables pegas con `--target-os=none` y newlib: `sysconf`/`av_cpu_count` (stub que devuelva 1), `clock_gettime`/`gettimeofday` (wut los da), `posix_memalign` (usar `memalign`). Solo hacen falta `libavcodec.a` y `libavutil.a`.
3. **Salida PCM.** FFmpeg da float planar (`AV_SAMPLE_FMT_FLTP`). Convertid a s16 con saturación (`ConvertFrame` de Xenia) e intercalad si es estéreo. En la Wii U **no hace falta intercambiar bytes**: es big-endian como espera el juego.
4. **Al altavoz.** Implementad `XAudioSubmitRenderDriverFrame`: el juego entrega 256 muestras × 6 canales float BE (formato que asume Xenia). Mezclad 5.1 → 2.0 (L = FL + 0,707·C + 0,707·SL; R = FR + 0,707·C + 0,707·SR; sin LFE) en un anillo s16. Sacadlo por sndcore2: `AXInitWithParams` (48 kHz), dos `AXVoice` LPCM16 en bucle sobre el anillo (`AXSetVoiceOffsets`, `AXSetVoiceDeviceMix` a TV y DRC, `AXSetVoiceState` PLAYING). La alternativa más sencilla es `AXRegisterDeviceFinalMixCallback` y sumar ahí cada trama de 3 ms.
5. **Hilo**: la descodificación en un hilo propio de prioridad media, en el núcleo menos cargado según el muestreador.

**Coste (estimación, a medir).** Una trama de 512 muestras por canal es un IMDCT de 512 más entropía y cuantización: ~60–100 k ciclos por canal y trama, unos 94 tramas/s por canal a 48 kHz → 6–9 M ciclos/s por canal, **≈0,5–0,8 % de un núcleo por canal**. Con el C genérico de FFmpeg en el 750, quizá el doble. Con 10–20 voces estéreo activas, 10–60 % de un núcleo. Rockbox mide unos 19 MHz para WMA estándar estéreo de 128 kbps en ARM sin SIMD; WMA Pro pesa más. Si se va de presupuesto, limitad las voces simultáneas descodificadas y dad silencio al resto.

**Cómo comprobarlo.** Primero escuchad la cinemática de ROMAN1 (diálogo y música). Registrad cuántos contextos están activos a la vez y medid el tiempo por trama con `OSGetSystemTick` dentro de `xma_trabaja`.

**Riesgos.** El reloj de las cinemáticas (`GET_CUTSCENE_AUDIO_TIME_MS` lee la pista +172) depende de que el consumo del anillo vaya a ritmo real. Al pasar a audio real, conservad el ritmo actual de `xma_trabaja` (por muestras consumidas, no por tiempo de descodificación).

**Fuentes.** has207/FFmpeg rama `xmaframes` (`wmaprodec.c`); xenia-canary `src/xenia/apu/xma_context.h` (l. 175–195), `xma_context.cc` (`ConvertFrame`), `xma_context_new.cc`; LibertyRecomp `glue/rexglue-sdk-main/src/audio/xma_context.cpp` (también usa `AV_CODEC_ID_XMAFRAMES`); devkitPro `pacman-packages/switch/ffmpeg/PKGBUILD`; wut `sndcore2/*.h`; Rockbox (cifras WMA, hilos de Hydrogenaudio).

---

### P7. Estabilidad

#### P7a. Sueltas perdidas de cerrojos (hilo 8) — **atacar primero**

**Diagnóstico (alta en el mecanismo; media en que sea *el* fallo).**
1. **Erratum de Espresso en `stwcx.`.** NetBSD (`sys/arch/powerpc/include/asm.h` l. 451–455): «Workaround for IBM Espresso erratum: `POWERPC_STWCX_PRE(ra, rb) dcbst ra,rb`», aplicado antes de **cada** `stwcx.` (también en `lock.h` y `atomic_op_asm.h`). Para el código de usuario, NetBSD hace saltar `stwcx.` y la emula con `dcbst` + `stwcx.` (`trap.c`, `fix_stwcx`, l. 1299–1340). El código de la 360 corre en nativo **sin** ese `dcbst`. Con un cerrojo de intercambio (`InterlockedExchange(&l,1)` para coger, `stw 0` para soltar), una `stwcx.` que tenga éxito tras la suelta de otro núcleo **pisa la suelta**: el cerrojo queda a 1 sin dueño. Es exactamente vuestro síntoma.
2. **Corchete de interrupciones.** Todos los `Interlocked*` de TU8 son `mfmsr rX; mtmsrd r13,1; lwarx …; stwcx. …; mtmsrd rX,1` (`sub_8284E5C0`…`E668`, y en línea en las 37 funciones de TU8 que tienen `stwcx.`). `mfmsr` es privilegiada y `mtmsrd` es privilegiada y de 64 bits. Si eso hace que una función se **interprete** y el intérprete simula la reserva (variable en el contexto + `stw` normal), mientras otro hilo ejecuta en nativo el `lwarx/stwcx.` real sobre la misma palabra, **no hay atomicidad entre ambos**. Que «siempre intervenga el hilo 8» encaja con un hilo que pasa por el camino interpretado.
3. Los atómicos de GCC (`__sync_*`, `__atomic_*`, C11) en devkitPPC generan `lwarx/stwcx.` **sin** `dcbst`. Cualquier cerrojo del núcleo hecho así también está expuesto.

**Propuesta.**
- `ppcscan.py atomicos --funciones out/funciones.csv --sitios out/sitios_stwcx.txt`: lista todos los `lwarx/stwcx.` y escribe, para cada `stwcx.`, el `dcbst` que hay que poner delante (en TU8 son 59 `stwcx.` y 48 `lwarx`). En `monta4.py`, cada `stwcx. rS,rA,rB` → `b sitio_N`, con `sitio_N: dcbst rA,rB ; stwcx. rS,rA,rB ; b vuelta`. El salto no rompe la reserva y CR0 lo pone la propia `stwcx.`.
- Corchetes: `mfmsr rX` → `li rX,0`, y los dos `mtmsrd` → `nop`. La atomicidad la dan `lwarx/stwcx.`. Así esas funciones dejan de interpretarse por este motivo.
- Intérprete y AOT: `lwarx` guarda (ea, valor) y `stwcx.` hace **CAS real** con `dcbst` (`codigo/atomicos_espresso.h`: `interp_lwarx`/`interp_stwcx`). Es la semántica de Xenia y XenonRecomp; el ABA no afecta a cerrojos ni contadores.
- Núcleo: todos los `Ke*SpinLock*`, `Rtl*CriticalSection`, `Interlocked*SList` con `cas32`/`xchg32`/`xadd32` del mismo fichero. Las SList de 64 bits de la 360 se protegen con un cerrojo propio: como son exportaciones del núcleo, todos los accesos pasan por vosotros.
- Quitad el «remedio que los suelta a mano» después del arreglo, para comprobarlo.

**Cómo comprobarlo.**
- Contador de sueltas forzadas por el remedio: debe quedarse en 0 tras el parche.
- En la prueba de 15 min, los cerrojos de 0x83130590 y 0x8312F23C con `PEEK` periódico: nunca a 1 durante más de N ms sin dueño.
- Registrad qué función ejecuta el hilo 8 cuando coge y suelta esos cerrojos (con el muestreador o con `HILOS`) y si está en `mapa.bin` como interpretada.
- Desensamblad `OSCompareAndSwapAtomic` de coreinit en consola (`PEEK` de su dirección) para ver si Nintendo pone `dcbst`. Si lo pone, es una confirmación extra del erratum.

**Riesgos.** Ninguno funcional: `dcbst` solo escribe la línea a memoria. El coste es de unos pocos ciclos por atómico.

#### P7b. Cuelgue intermitente al arrancar en «leyendo FFEA0D00» (al tomar SR6/SR7)

**Diagnóstico (baja-media; sin el código no puedo afinar).**
- MemoryMappingModule (Aroma), que mapea 0x80000000+:
  1. lee los SR con `KernelReadSRs` y escribe los nuevos **en los 3 núcleos** (`runOnAllCores(writeSegmentRegister…)`, `memory_mapping.cpp` l. 533);
  2. **anula con NOP las escrituras de SR del kernel** para ese segmento (l. 172–180: 0xfff1d734 + 4·sr, 0xfff1d604/0xfff1d648, 0xffe00618 + 4·sr). Si no, el kernel los reescribe al cambiar de contexto;
  3. sustituye `KiEffectiveToPhysical` y compañía (`function_replacements.cpp` l. 121–128) para que `OSEffectiveToPhysical`, y por tanto GX2, conozca su región.
- Si vuestro método lee una estructura del kernel (FFEA0D00) con `KernelCopyData` mientras otro núcleo cambia de contexto, o solo actúa en el núcleo actual, aparecen fallos intermitentes: SR distintos por núcleo, o lecturas de un estado intermedio.

**Propuesta.** Copiad el procedimiento de MemoryMappingModule para SR6/SR7:
- `KernelReadSRs` → modificar → `KernelWriteSRs` en los 3 núcleos con un hilo fijado a cada uno;
- NOPs en los mismos puntos del kernel para sr = 6 y 7;
- antes de crear ningún hilo del juego.

Mejor aún: pedid las regiones a MemoryMappingModule si su API lo permite. Así la memoria «física» de la 360 tiene dirección física válida para GX2, y P5 puede leer vértices sin copia.

**Cómo comprobarlo.** Registro antes y después de cada llamada al kernel con el núcleo actual (`OSGetCoreId`). Imprimid los SR leídos en los 3 núcleos al final del arranque y en `HILOS`: deben coincidir.

#### P7c. Cierres al menú de la Wii U sin registro (p. ej. t≈695 s)

**Diagnóstico (media), causas candidatas.**
1. **Salida limpia por `abort`/`exit`.** Un `assert`, una excepción de C++ o `std::terminate` en el port, wut o newlib terminan en `exit`, y bajo Aroma eso **vuelve al menú sin mensaje**. Si el registro por red es asíncrono, las últimas líneas se pierden.
2. **Escritura de la GPU fuera de rango** en resolves (P1, alto de la copia): corrompe la memoria y el fallo aparece más tarde en otro sitio.
3. **Pila desbordada**: las pilas de los hilos tienen el tamaño que pide el juego, pero el núcleo, el intérprete y el AOT usan más.
4. **UAF conocido de RAGE**: el arena de 1 MB de listas de dibujo (TU8 `sub_821BB3D8`) da la vuelta y quedan punteros colgantes, rellenos con 0xE1E1E1E1, **tras un cambio de pantalla de carga**. LibertyRecomp lo documenta (`docs/uaf-handle-investigation/15-synthesis.md`). Con otra temporización puede convertirse en DSI.
5. (Baja, especulativa) 695 s está cerca de 2^35 / 50 MHz = 687 s: revisad las conversiones de base de tiempos 360 ↔ Wii U (62,156 MHz) por desbordamientos.

**Propuesta.**
- Definid en el port `abort()`, `__assert_func()`, `_exit()` y un `atexit()` que escriban el motivo y una traza de la pila (recorriendo r1 → LR guardado) **de forma síncrona**. En lugar de volver al menú, se quedan en bucle infinito.
- `OSSetExceptionCallbackEx(OS_EXCEPTION_MODE_GLOBAL_ALL_CORES, …)` para DSI, ISI, PROGRAM y ALIGNMENT: escribe `OSContext` (srr0, lr, dar, dsisr, gpr) en un búfer fijo y lo pinta en la tele con `OSScreenPutFontEx`. Lo capturáis con `tele.sh`. No dependáis de la red dentro del manejador.
- `HILOS`: añadid `OSCheckThreadStackUsage(hilo)` frente al tamaño de la pila, y pilas de al menos max(2 × lo pedido, 256 KB).
- Registrad la memoria libre de los montones cada 20 s.

#### P7d. Congelaciones totales (GPU colgada)

**Diagnóstico (media).** Causas probables:
- **bucles del sombreador**: el `do{switch}` del traductor más `loop_consts`/`bool_consts` a 0 o basura por el modo de uniformes (P4), o un `pc` que nunca llega a 0xFFFF;
- lecturas o escrituras de la GPU fuera de memoria válida (resolves P1; punteros de textura o vértices de una región sin dirección física válida, P7b);
- desbordamiento del búfer de órdenes.

**Propuesta: migas de GPU.**
- Antes de cada dibujo, `GX2SubmitUserTimeStamp(&migas[i & 1023], (trama << 32) | i, GX2_PIPE_EVENT_BOTTOM /*1*/, FALSE)`. La función se exporta pero no está declarada en wut; la firma está en decaf `gx2_cbpool.cpp` l. 60–63.
- Cuando `VIGILA` detecte que `GX2GetRetiredTimeStamp()` no avanza, la última miga escrita dice qué dibujo terminó. Volcad el estado y los sombreadores del siguiente.
- `GX2SetGPUTimeout` (exportada) para que GX2 avise antes.
- En el traductor, un tope de iteraciones en el `do{switch}` (p. ej. 4096) que escriba un color de error. Una congelación se convierte así en un píxel magenta.

**Fuentes P7.** NetBSD `sys/arch/powerpc/include/asm.h`, `include/lock.h`, `powerpc/trap.c` (`fix_stwcx`), `common/lib/libc/arch/powerpc/atomic/atomic_op_asm.h`, `evbppc/conf/NINTENDO.MP` («The workaround for Espresso's stwcx. errata is not compatible with PaX MPROTECT»); TU8 `sub_8284E5C0`… (`referencia-tu8.md`); wiiu-env MemoryMappingModule y libkernel; LibertyRecomp `docs/uaf-handle-investigation/15-synthesis.md`; decaf-emu `gx2_cbpool.cpp`, `gx2_query.h`, `gx2_state.h`; wut `coreinit/exception.h`, `coreinit/thread.h`, `coreinit/screen.h`.

---

## 3. Rendimiento (P5): palancas y estimación

Punto de partida: **≈6 s por imagen en la ciudad** (4 s de pintor + ~2 s de juego, que incluye D3D de la 360 y espera), unos 4400 dibujos, GPU 0,2 s.

| # | Palanca | Ganancia estimada | Supuestos |
|---|---|---|---|
| 1 | P1 una franja | −2200 dibujos (−50 %); pintor −2 s/imagen; GPU −25–35 % | 3 × ~1100 dibujos de G-buffer; coste ∝ dibujos (~0,9 ms) |
| 2 | Pintor: constantes con rangos sucios y bloque compartido | coste por dibujo ÷3 a ÷10 | si hoy se suben 512 vec4 por dibujo (lo más probable de los 3,5 s sin medir) |
| 3 | Pintor: vértices e índices sin copia (endian de GX2, índices BE) | 77 → ~10 ms | memoria invitada con dirección física válida (P7b) |
| 4 | Pintor: memo de texturas por trama | 157 → ~20 ms | pocas texturas cambian por trama |
| 5 | Pintor: estado con bits sucios y sin esperas | 275 → ~50 ms | — |
| 6 | Población ×0,3 y distancia ×0,6 | −25–40 % de dibujos y de CPU de juego (IA, física, animación) | dibujos ∝ objetos visibles; distancia ∝ área ≈ d² en ciudad plana |
| 7 | Reflejos fuera (agua/espejo 320×180 y paraboloide) | −800 a −1000 dibujos (−18–22 %) y su CPU de *culling* | hipótesis: el «prepaso» de 802 dibujos a 320×180 es el reflejo del agua o el de los espejos (son las dos únicas superficies de 320×180 que crea el juego) |
| 8 | Atómicos nativos (quitar `mfmsr/mtmsrd`) + modismos de 64 bits → 32 bits | sacar del intérprete gran parte del 26,6 % «64 bits sin VMX» | análisis de consumidores de 32 bits |
| 9 | VMX → *paired singles* en las ~200 funciones VMX más calientes | ×2–×4 en ellas | perfil del muestreador |
| 10 | AOT con registros locales (XenonRecomp) | −20–40 % en código AOT | quitar `__savegprlr`/contexto en memoria |
| 11 | Pintor en otro núcleo, en tubería con el juego | imagen = máx(juego, pintor) | GX2 en su núcleo; una trama de latencia |
| 12 | Superficies EDRAM en MEM1 | GPU −20–40 % | 17,6 MB con P1 |
| 13 | Sombreadores lineales (sin `do{switch}`) | GPU ×2–×5 en esos PS | la mayoría de los PS de Xenos no tienen saltos |
| 14 | Resolución 960×540 | −44 % de píxeles | solo útil cuando la GPU sea el cuello |

**Modelo del techo (supuestos explícitos).**
- *Juego.* Supongamos que de los ~2 s de juego, el 43,7 % de instrucciones interpretadas va de 20× a 50× más lento que nativo. Entonces T_juego = T_nativo × (0,563 + 0,437 × k), con k = 20…50, y despejando, **T_nativo ≈ 90–215 ms** con la densidad actual. Con el intérprete sustituido por AOT a ~3× (VMX escalar), T ≈ 1,9 × T_nativo ≈ 170–400 ms. Con VMX en PS (~1,5×) y población y distancia reducidas (−30 %), **≈ 80–200 ms**.
- *Cruce con la 360.* GTA IV en la 360 va a unos 25–30 fps (33–40 ms) en 3 núcleos de 3,2 GHz con VMX128. Un núcleo de Espresso a 1,24 GHz rinde por reloj más que Xenon en código escalar (estimaciones de foro: ~2,9 frente a ~1,9 DMIPS/MHz, sin metodología), pero tiene la mitad de ancho SIMD (2 floats) y 2,6× menos reloj. El cálculo da 50–70 ms por imagen solo para el hilo principal en un port nativo perfecto, coherente con lo anterior.
- *Pintor.* Bien hecho, 1500–2200 dibujos × 15–30 µs = 25–65 ms, en otro núcleo.
- *GPU.* Con P1, MEM1 y sombreadores lineales, 40–80 ms a 720p; a 960×540, 25–50 ms.

**Estimación final honesta.**
- Corto plazo (P1 + pintor ×10 + atómicos): de 0,2 a **~0,4–0,6 fps** (el juego pasa a ser el cuello, ~2 s).
- Medio plazo (+ AOT amplio + 64→32 + contenido reducido): **2–5 fps** en la ciudad.
- Muy largo plazo (+ VMX→PS en todo lo caliente + tubería de núcleos + sombreadores lineales): **5–12 fps** en la ciudad con densidad 0,3 y distancia 0,6; **15–20 fps** en interiores y cinemáticas.
- **30 fps en la ciudad no son alcanzables** con este enfoque en Espresso: el hilo principal nativo perfecto ya cuesta 50–70 ms. Haría falta recortar el juego (IA, física, población cero) más allá de lo razonable.

---

## 4. Preguntas abiertas (lo que necesito que mida el agente con la consola)

1. **Desglose del pintor** (los 3,5 s sin medir): temporizadores por fase (análisis PM4, constantes, sombreadores, estado, texturas, vértices, llamadas GX2, esperas) en una imagen de la ciudad. ¿Cómo se suben hoy las constantes (registro o bloque) y cuántos bytes por dibujo?
2. **Muestreador** (`codigo/muestreador.c`): top 40 por núcleo en la ciudad. ¿Cuánto es pintor, juego nativo, intérprete, AOT u ocioso?
3. **Franjas**: `BIN_MASK`/`BIN_SELECT` por franja; ¿hay paquetes predicados y `EVENT_WRITE_EXT`?; rectángulos de `vf0` y alto de `RB_COPY_DEST_PITCH` en los 3 resolves de cada destino; bits 8/9 de `RB_COPY_CONTROL`; ¿cómo se borra hoy?
4. **Profundidad**: `RB_DEPTH_INFO` (bit 16), `RB_DEPTH_CLEAR`, `PA_CL_CLIP_CNTL`; ¿se llama a `GX2SetRasterizerClipControlEx` con `halfZ`?; formato del búfer de profundidad GX2; formato y swizzle de las búsquedas que leen 1B192000.
5. **P4**: valor de `ps_param_gen` que se pasa; `SQ_PROGRAM_CNTL`/`SQ_CONTEXT_MISC` en los dibujos del G-buffer; modo de uniformes de cada par VS/PS; resultado de la textura de diagnóstico.
6. **P3**: `PEEK 83137BC0`, `83137644`, `83137D18` (¿encaja el desfase 0x9D780?); salida de `busca_tu8.py` para 82145770, 82145420, 82145820, 821B7048, 821B7250, 821B5C90, 82144188; resultado de la prueba A; estado de 82130B88 en el tiempo y llamadas Xam* con resultados.
7. **P5 contenido**: direcciones de los nativos `*DENSITY*` y `*BUDGET*` en `nativos.txt`; resultado de `busca_tu8.py 821DFFE8` y efecto de `POKE` del multiplicador a 0,6; cadenas `CRenderPhase*Reflection` en `imagen.bin`.
8. **P7a**: salida de `ppcscan.py atomicos` (cuántos y en qué funciones); ¿cuáles están en `mapa.bin` como interpretadas?; ¿cómo implementan hoy `lwarx/stwcx.` el intérprete, el AOT y los sitios?; quién es el hilo 8 (nombre, entrada, prioridad, afinidad) y qué función coge y suelta 0x83130590 / 0x8312F23C.
9. **P7b**: código exacto que lee FFEA0D00 (¿`KernelCopyData`?, ¿en qué núcleo?, ¿con otros hilos ya creados?); SR 6/7 leídos en los 3 núcleos tras el arranque.
10. **P7c**: uso de pila por hilo (`OSCheckThreadStackUsage`) y memoria libre cada 20 s hasta el cierre; ¿hay un manejador de excepciones instalado en todos los núcleos?; ¿vuelve al menú también con `SEGUNDOS` corto y sin `VIGILA`?
11. **Hilos y núcleos**: ¿en qué núcleo se llama a `GX2Init`?; afinidad y prioridad de cada hilo del juego; ¿dónde están las superficies EDRAM (MEM1 o MEM2) y cuántas hay?
12. **XMA**: número máximo de contextos activos a la vez, canales y frecuencia de muestreo en la cinemática y en la ciudad.

---

## 5. Bibliografía

**Repositorios leídos** (clonados el 16-oct-2026; commit entre paréntesis):
- xenia-canary (`583528e8e3d7`): https://github.com/xenia-canary/xenia-canary
  - `src/xenia/gpu/pm4_command_processor_implement.h` (predicado l. 408–418; BIN_MASK/SELECT l. 511–545; EVENT_WRITE_EXT l. 943–976)
  - `src/xenia/gpu/draw_util.cc` (`GetWindowOffsetTiles` l. 925–1010; `GetResolveInfo` l. 1240–1640)
  - `src/xenia/gpu/xenos.h` (opcodes PM4 l. 1638–1694; formatos de profundidad l. 403–407; resolves l. 955+; `EdramMode`; `xe_gpu_texture_fetch_t` l. 1245+), `xenos.cc` (`Float20e4To32`)
  - `src/xenia/gpu/registers.h`, `register_table.inc`
  - `src/xenia/gpu/vulkan/vulkan_texture_cache.cc`, `shaders/texture_load_depth_unorm.cs.xesl`, `texture_load_depth_float.cs.xesl`
  - `src/xenia/apu/xma_context.h`, `xma_context.cc`, `xma_context_new.cc`
- xenia (historial antiguo): https://github.com/xenia-project/xenia — `src/xenia/gpu/glsl_shader_translator.cc` (`66a9c9d8`, 2019) y `src/xenia/gpu/gl4/gl4_command_processor.cc` (`083f936b`, 2015)
- PR #1262 «[GPU] Put tiling window offsets in EDRAM bases»: https://github.com/xenia-canary/xenia-canary/pull/1262
- PR #1271 «[GPU] Xenos sample positions; UE3 tiling fix»: https://github.com/xenia-canary/xenia-canary/pull/1271
- LibertyRecomp (OZORDI, `1922957d9f5a`): https://github.com/OZORDI/LibertyRecomp
  - `glue/rexglue-sdk-main/gta4-recomp/src/` (`gta4_transition_hooks.cpp`, `gta4_loading_parallax_fix.cpp`, `gta4_draw_distance_policy.h`, `gta4_draw_distance_guest.inc`, `gta4_native_hooks.cpp`, `gta4_population_hooks.cpp`, `gta4_population_policy.h`)
  - `glue/rexglue-sdk-main/gta4-recomp/generated/` (código TU8 generado)
  - `glue/rexglue-sdk-main/src/graphics/gta4_native/` (`packed_depth_alias_ps.glsl`, `scene_depth_handoff_ps.glsl`, `native_clip_control.h`, `graphics_system.cpp`)
  - `glue/rexglue-sdk-main/src/audio/xma_context.cpp`
  - `docs/reflection-rendering-study/README.md`, `docs/uaf-handle-investigation/15-synthesis.md`
- LibertyRecomp (UnknownLITE, versión antigua): https://github.com/UnknownLITE/LibertyRecomp
- XenonRecomp: https://github.com/hedge-dev/XenonRecomp (README, «Optimizations»)
- FFmpeg con `xmaframes`: https://github.com/has207/FFmpeg/tree/xmaframes (`libavcodec/wmaprodec.c`); FFmpeg `wmaprodec.c`: https://ffmpeg.org/doxygen/trunk/wmaprodec_8c_source.html
- devkitPro pacman-packages: https://github.com/devkitPro/pacman-packages (`switch/ffmpeg/PKGBUILD`)
- wut: https://github.com/devkitPro/wut (`include/gx2/*.h`, `include/coreinit/*.h`, `include/sndcore2/*.h`, `cafe/gx2.def`, `cafe/coreinit.def`)
- CafeGLSL: https://github.com/Exzap/CafeGLSL (README: modos de uniformes)
- decaf-emu: https://github.com/decaf-emu/decaf-emu (`src/libdecaf/src/cafe/libraries/gx2/gx2_cbpool.cpp`, `gx2_query.h`, `gx2_state.h`, `gx2_enum.h`)
- Cemu: https://github.com/cemu-project/Cemu (`src/Cafe/OS/libs/gx2/GX2_Surface_Copy.cpp`)
- wiiu-env MemoryMappingModule: https://github.com/wiiu-env/MemoryMappingModule (`source/memory_mapping.cpp`, `source/function_replacements.cpp`)
- wiiu-env libkernel: https://github.com/wiiu-env/libkernel
- NetBSD src (trunk): https://github.com/NetBSD/src
  - `sys/arch/powerpc/include/asm.h`, `sys/arch/powerpc/include/lock.h`
  - `sys/arch/powerpc/powerpc/trap.c` (`fix_stwcx`)
  - `common/lib/libc/arch/powerpc/atomic/atomic_op_asm.h`
  - `sys/arch/evbppc/conf/NINTENDO.MP`, `sys/arch/powerpc/include/oea/spr.h`, `sys/arch/powerpc/oea/cpu_subr.c`

**Otras referencias:**
- Compatibilidad de GTA IV en Xenia: https://github.com/xenia-project/game-compatibility/issues/427
- Xenia, caché de destinos: https://xenia.jp/updates/2021/04/27/leaving-no-pixel-behind-new-render-target-cache-3x3-resolution-scaling.html
- UnleashedRecomp: https://github.com/hedge-dev/UnleashedRecomp
- xma-rs: https://github.com/HaydnTrigg/xma-rs
- Patente de *predicated tiling* citada por Xenia: https://www.google.com/patents/US20060055701
- Relojes de Espresso y Xenon: https://www.tomshardware.com/news/wiiu-xbox360-ps3,19444.html
- Microarquitectura de Espresso (foros, sin metodología): https://forums.anandtech.com/threads/wii-u-hardware-investigation.2345436
- Rendimiento de GTA IV (retrocompatibilidad frente a 360): https://cinemablend.com/games/1624089/how-gta-4-is-performing-on-xbox-one
- Coste de WMA en Rockbox (Hydrogenaudio): https://hydrogenaud.io/index.php/topic,82125.0.html
- Paired singles en GCC y libogc: https://gnu.googlesource.com/gcc/+/320cafa844c4eb9c23396727682c306cdaeb628c/gcc/config/rs6000/paired.md · https://devkitpro.org/viewtopic.php?p=4274
