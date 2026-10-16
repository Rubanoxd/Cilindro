# ¿GTA IV a 30 fps en Wii U recompilando o rehaciendo partes? — respuesta

*16-oct-2026. Investigación en la nube, sin vuestro código, discos ni consola.*

Etiquetas: **[D]** dato con fuente · **[E]** estimación con supuestos explícitos · **[H]** hipótesis por verificar. No doy direcciones de vuestro XEX; las de TU8 salen del código generado de LibertyRecomp (OZORDI, commit `1922957d9f5a`), que he analizado aquí.

---

## 1. Veredicto en 10 líneas

1. **30 fps estables en la ciudad conduciendo: no puedo prometerlo.** Ningún port profesional comparable a Wii U igualó a la 360, y GTA IV ya bajaba de 30 en la 360. **«Casi 30» (25–30 a pie, 20–25 conduciendo rápido) sí es alcanzable [E]** con todo lo de abajo. 30 sólidos en interiores, cinemáticas y a pie con densidad baja también **[E]**.
2. La estimación anterior (hilo principal nativo en 50–70 ms) era **pesimista**. Desde el punto de vista del rendimiento, la clave es que **el Xenon y el Espresso hablan casi el mismo PowerPC**. En TU8 **solo el 5,7 % de las instrucciones son VMX [D]**: el resto puede correr en nativo, y no hace falta pasar por C.
3. Así que **no recomiendo una recompilación «tipo XenonRecomp» a C** para todo el juego. Recomiendo una **reescritura binaria PPC→PPC estática** que deja intacto el ≥90 % nativo y sustituye solo VMX, 64 bits y privilegiadas por secuencias de 750. Ese es el «sin intérprete» de mayor techo.
4. VMX128 → *paired singles* va bien en TU8: **el `vperm` genérico es el 0,2 % y `vpkd3d`/`vupkd3d` no llega a 25 usos de 135 652 [D]**. El 85–90 % se traduce a 1–3 instrucciones PS **[E]**.
5. **El segundo muro es el pintor.** Hay que interceptar RAGE en la capa `grc` y emitir GX2 directo, con 1132 sombreadores (79 FXC) [D]: unos 2 de cada 3 por traducción automática y los ~20 calientes a mano.
6. Recortes necesarios **[E]**: población 0,4–0,5 · distancia 0,6–0,7 · sin reflejos de agua ni espejo (−800 dibujos) · Euphoria limitada a 2 personajes · resolución 960×540 (o dinámica 540–720p) · luces ≤ 40 · transparentes y partículas a la mitad.
7. Arquitectura: núcleo 1 (2 MB de L2) con el hilo principal ≤ 28 ms; núcleo 2 con el pintor; núcleo 0 con *streaming* (descompresión), audio y trabajos de animación y *culling*.
8. Trabajo **[E]**: **8–14 meses de una persona con ayuda de IA** hasta «casi 30» en ciudad. Primeros 10–15 fps en interior a los 2–4 meses.
9. *Table Tennis* de Wii (RAGE 2006 en PPC 750) es una buena **ayuda de ingeniería inversa** (nombres de funciones del motor), no un atajo de rendimiento. TCRF indica que la versión **EUR de Wii conserva símbolos de depuración** [D, a confirmar].
10. **Medida decisiva** antes de comprometer meses: el coste del hilo principal *solo en código nativo* (con el muestreador del informe anterior), extrapolado a «0 % interpretado».

---

## 2. Precedentes

### 2.1 Ports profesionales de 360/PS3 a Wii U

| Juego | Wii U: resolución / fps | 360 | Recortes o notas | Fuente |
|---|---|---|---|---|
| CoD Black Ops II (2012) | 880×720, 2×MSAA; ~32 fps de media en escena tranquila; mínimos ~26 | ~52 de media (mismo tramo); PS3 ~46 | «high levels of on-screen characters clearly cause issues» | [D] DF vía Nintendo Life / MyNintendoNews |
| Batman Arkham City Armoured Ed. (2012) | ~22 fps de media | 30 estables | v-sync | [D] DF vía Multiplayer.it |
| Assassin's Creed IV (2013) | 720p, mismo AA | >25 estable | **6–10 fps por debajo de 360/PS3**, mínimos de ~18 | [D] DF vía MyNintendoNews / Nintenderos |
| Assassin's Creed III (2012) | 720p; caídas en multitudes y persecuciones | también caía | — | [D] reseña GodIsAGeek (sin cifras de DF) |
| Watch Dogs (2014) | 1152×648, tope de 30; **~20 conduciendo por la ciudad**, <25 con física | (360 similar o mejor) | menos NPC, sin hierba ni farolas de PS4 | [D] DF vía Nintenderos / Nintendo Life |
| NFS Most Wanted U (2013) | 30 fps **más estables que 360 y PS3**; 704p según Cubed3 | 30 con caídas | Criterion partió de la versión de PC y usó la RAM extra; online de 6 en vez de 8 | [D] DF vía Cubed3 / Nintendo Life |
| Xenoblade Chronicles X (2015, nativo) | 720p, ~30 casi sólidos en mundo abierto enorme | — | «visual sacrifices» | [D] DF vía Nintendo Life |
| Zelda BotW (2017, nativo) | 720p, 30 con caídas a 20 (bloqueo a 20) | — | — | [D] DF vía Game Informer / Kotaku |
| Mass Effect 3, Darksiders II, Splinter Cell Blacklist, Arkham Origins | sin cifras fiables encontradas | — | — | — |

**Opiniones de desarrolladores [D]:**
- 4A Games (Metro): CPU «horrible, slow».
- Omega Force (Warriors): la CPU es algo inferior a PS3/360 y el rendimiento cae «when many enemies are onscreen».
- Shin'en: la eDRAM de 32 MB es como la de 360 pero cabe 1080p en una pasada; la usan para *framebuffers*, capturas intermedias y memoria rápida de CPU. Nano Assault Neo usaba **un solo núcleo**.

**Lectura [E].** Los ports de juegos con mucha CPU, multitudes o física van **20–40 % por debajo de la 360** cuando se portaron con prisa y presupuesto de lanzamiento. Solo hay dos casos que igualan o superan a la 360:
- **NFS MW U**: equipo con experiencia, código fuente y meses de optimización;
- juegos **nativos** diseñados para Espresso (XCX, BotW), y aun así a 720p/30 con caídas a 20.

GTA IV en la 360 ya va a 25–30 con caídas. Un port «profesional medio» lo dejaría en 18–24. Superar eso exige **cambiar contenido** (menos peatones y coches) y optimizar más que esos estudios. Eso es lo que permite el plazo de meses.

### 2.2 Recompilaciones estáticas de 360

| Proyecto | Resultado | Fuente |
|---|---|---|
| UnleashedRecomp | Pintor nuevo «written from scratch»; Steam Deck a **60 fps sólidos** con 13–14 W | [D] SteamDeckHQ |
| LibertyRecomp (GTA IV TU8) | Recompilación con ReXGlue + pintor nativo RAGE (`gta4_native`) en Vulkan/Metal; sin cifras públicas | [D] repo (docs, código) |
| ReXGlue | SDK de recompilación estática 360 → C++ | [D] Habr / repo |
| GTA IV a Android/Switch con ReXGlue (modder brasileño) | anunciado; sin cifras | [D] GTABoom |
| Recompilaciones a **PowerPC** | **ninguna encontrada** | — |

**Lectura.** Todas apuntan a CPUs modernas (x86/ARM, ×5–×20 más rápidas que un Xenon) y ganan sobre todo por el **pintor nuevo**. En Wii U la CPU no sobra: la ventaja de recompilar no viene de «C rápido», sino de **no interpretar**. Y el Espresso puede ejecutar casi todo el código original tal cual.

### 2.3 Homebrew

- **re3** (GTA III con motor reescrito) tiene port a Wii U [D, repo]. Sobre esa base se hizo uno a Wii (Broadway, GX) [D, GTABoom]. **No encontré cifras de fps en Wii U.** Es la prueba más cercana de «GTA en PPC 750 con código propio», pero RenderWare de 2001 no es comparable en carga a RAGE 2008.
- Datos útiles de Linux en Wii U: `linux-wiiu/smp-patches` existe para «SMP bugs in the Espresso processor» [D]. Encaja con el erratum de `stwcx.` ya tratado.

---

## 3. Presupuesto a 33 ms

### 3.1 Relación Espresso / Xenon por tipo de código [E]

| Tipo de código | Supuesto | Rendimiento de 1 núcleo Espresso frente a **1 hilo hardware** de Xenon |
|---|---|---|
| Escalar entero con ramas (IA, scripts, gestión) | Xenon en orden, 2 de ancho, con penalizaciones *load-hit-store* de hasta ~80 ciclos [D, blog GameDev.net]: IPC efectivo por hilo 0,3–0,5. 750 fuera de orden, cadena corta: IPC 0,8–1,0 | **0,7–1,2×** (≈ paridad) |
| Coma flotante escalar | 750: `fmadds` encadenable a 1/ciclo; Xenon: FPU compartida por 2 hilos a 3,2 GHz | **0,5–0,8×** |
| SIMD 4×float | VMX128 de 4 carriles frente a PS de 2; más cargas y barajados | **0,25–0,4×** con buena traducción; 0,1× escalar |
| Fallos de caché a RAM | Latencias del mismo orden en ns; el 750 a 1,24 GHz pierde menos ciclos por fallo; L2 de 2 MB privada (núcleo 1) frente a 1 MB compartido por 6 hilos | **1,0–1,5×** |

Consecuencia: **un núcleo de Espresso ≈ un hilo hardware de Xenon** en código general, y bastante menos en SIMD. La 360 tiene 6 hilos y la Wii U 3 núcleos: **la Wii U tiene ~la mitad de CPU total** **[E]**.

### 3.2 GPU y memoria [D/E]

- **Latte** [D]: 320 SP, 16 TMU, 8 ROP, 550 MHz (TechPowerUp, DF vía Nintenderos; reloj medido 549,999 MHz por fail0verflow); ≈ **352 GFLOPS** (320 × 2 × 0,55, cálculo mío).
- **Xenos** [D]: 48 ALU unificadas a 500 MHz (≈ 240 GFLOPS, cálculo), 10 MB de EDRAM a 256 GB/s, GDDR3 a 22,4 GB/s (Hot Chips 17).
- **MEM2**: DDR3-1600 en 64 bits = 12,8 GB/s [D, WiiUBrew]. **MEM1**: 32 MB de eDRAM; ancho de banda no publicado [H: varias decenas de GB/s].
- **G-buffer en MEM1** (4 MRT de 32 bpp + D24S8):

| Resolución | Tamaño | ¿Cabe en 32 MB? |
|---|---|---|
| 1280×720 | 4 × 3,52 + 3,52 = **17,6 MiB** | sí, pero con acumulación de luz RGBA16F (7 MiB) y destinos de posproceso queda justo (~26–30 MiB) |
| 960×540 | 9,9 MiB | sobra |
| 640×360 | 4,4 MiB | sobra |

  Los búferes de pantalla de la tele, en MEM2.
- **Tráfico del diferido a 720p** [E]: escribir el G-buffer ~37 MB (sobredibujo 2×), luces ~40 MB, composición y posproceso ~30 MB: **~110 MB/imagen ≈ 3,3 GB/s a 30 fps**. Desde MEM1 no es problema; en MEM2, compartido con texturas y CPU, sería el cuello.
- **GPU [E]**: con sombreadores bien traducidos, Latte (×1,47 ALU, relleno parecido) haría el diferido de GTA IV en **20–28 ms a 720p** y **12–17 ms a 960×540**.

### 3.3 Tabla de presupuesto (ciudad, 30 fps, con los recortes del §1.6) [E]

La línea base de la 360 es una estimación: no hay perfil público del reparto de GTA IV. Los foros de PC coinciden en que el juego se ata a un hilo principal y a la IA/física de peatones y coches [D cualitativo].

| Subsistema | 360 (ms en su hilo) | Factor Wii U | Wii U sin recortes | Wii U con recortes | Núcleo |
|---|---|---|---|---|---|
| Scripts + juego (misiones, jugador) | 2–3 | 1,0 | 2–3 | 2–3 | 1 |
| IA y población (peatones, coches, rutas) | 5–7 | 1,0 | 5–7 | **3–4** (población 0,45) | 1 |
| Física RAGE (cuerpos, vehículos) | 4–6 | 0,6 (FP + algo de VMX) | 7–10 | **4–6** | 1 |
| Euphoria (personajes NM activos) | 1–3 | 0,4 (FP/VMX) | 3–7 | **1–2** (tope de 2) | 1 |
| Animación (huesos, mezcla) | 3–4 | 0,4 | 8–10 | **4–5** → trabajos | 0 |
| *Culling* y visibilidad | 2–3 | 0,5 | 4–6 | **2–3** (distancia 0,65) → trabajos | 0 |
| Construir listas de dibujo (DC de RAGE) | 3–5 | 0,8 | 4–6 | **3–4** | 1 o 2 |
| Gestión de *streaming* | 0,5–1 | 1,0 | 1 | 1 | 1 |
| **Hilo principal (núcleo 1)** | **~20–29** | | **~31–43** | **~17–24** | 1 |
| Pintor nativo GX2 (≈2000 dibujos × 6–10 µs) + recorrido `grc` | (hilo de render de la 360: 8–12) | — | 25–35 | **15–22** | 2 |
| *Streaming*: descompresión LZX/zlib conduciendo | 2–5 | 0,7 | 3–7 | **3–5** (o ~1 con LZ4, §6) | 0 |
| Audio (XMA ~10–20 voces + mezcla) | 3–5 (hardware XMA) | — | 6–12 | **4–6** (tope de voces) | 0 |
| **Núcleo 0 total** | | | | **~12–20** | 0 |
| **GPU** | ~25–30 | — | 20–28 a 720p | **12–17** a 960×540 | Latte |

Lectura: **con recortes, los tres núcleos y la GPU caben en 33 ms con poco margen**. El núcleo 1 (17–24 ms) es el más incierto: si la línea base de la 360 fuera 30+ ms, no cabe. Sin recortes, el hilo principal (31–43 ms) **no cabe**, y eso coincide con el informe anterior.

---

## 4. Palancas

Coste en semanas de una persona con ayuda de IA **[E]**.

| # | Palanca | Ganancia esperada | Coste | Riesgo |
|---|---|---|---|---|
| 1 | **Reescritura binaria PPC→PPC** sin intérprete: VMX→PS, 64→32, privilegiadas, atómicos (§5.1) | La parte de juego pasa de ~2 s a **~40–60 ms** por imagen (hoy 43,7 % interpretado a ×20–50) | 12–20 sem. | Medio: casos raros de VMX, ramas a mitad de bloque |
| 1b | XenonRecomp completo a C → GCC 750 (lo que pregunta el encargo) | Algo menos que la 1 para el 94 % escalar: contexto en memoria, CR emulado, sin `bl` nativos. Las optimizaciones de XenonRecomp lo compensan solo en parte | 16–24 sem. | Medio. **No recomendado** como base; sí para funciones sueltas muy VMX |
| 2 | **Pintor GX2 nativo en la frontera `grc`** | Pintor de ~4 s (o 40–80 ms optimizando PM4) a **15–22 ms** | 16–30 sem. (con sombreadores) | Alto: superficie grande (dispositivo, efectos, texturas, destinos, consultas) |
| 2b | Optimizar la traducción PM4 (informe anterior) | 4 s → 40–80 ms | 4–8 sem. | Bajo. **Hacerlo primero**: es la red de seguridad |
| 3 | Sombreadores: automático para los lineales + a mano los ~20 calientes (diferido ×4, G-buffer común, agua, posproceso) | GPU ×2–×4 respecto al traductor antiguo con `do{switch}` | 6–10 sem. | Medio |
| 4a | Población ×0,45 (globales de densidad) | Hilo principal −3 a −6 ms; −15–25 % dibujos | 1 sem. | Bajo |
| 4b | Distancia ×0,65 (multiplicador TU8 `flt_82A931BC`) | −20–30 % dibujos y *culling* (~ d² en ciudad) | 1 sem. | Bajo (más *pop-in*) |
| 4c | Sin reflejo de agua ni espejos (`CRenderPhaseWaterReflection` / `MirrorReflection`) | −800 dibujos (≈ −18 %), −2–3 ms de CPU | 1–2 sem. | Bajo |
| 4d | Paraboloide 256→128 o cada 2 imágenes | −1–2 ms de GPU, −100–300 dibujos [H] | 1–2 sem. | Bajo |
| 4e | Tope de Euphoria (2 agentes; el resto, *ragdoll* simple o animación) | −1–4 ms en peleas y atropellos | 2–4 sem. | Medio (hay que encontrar el gestor NM) |
| 4f | 960×540 o resolución dinámica | GPU −44 % | 2–4 sem. (todos los destinos y resolves escalados) | Medio |
| 4g | Luces ≤ 40, partículas y transparentes ×0,5 | GPU −2–5 ms; −400 dibujos | 2–3 sem. | Bajo-medio |
| 4h | IA y física lejanas a 15 Hz | Hilo principal −2–4 ms | 4–8 sem. | **Alto** (deriva y estados de misión) |
| 5 | PS a mano en el *top*-N (matrices, *culling* esfera/frustum, mezcla de animación, vehículo) | Esas funciones ×1,5–×3; hilo principal −10–20 % | 4–8 sem. | Bajo-medio |
| 6 | Tubería de 3 núcleos (juego n, pintor n−1, trabajos en el 0) | Imagen = máx en vez de suma: ×1,6–×2 | 4–8 sem. | Medio (RAGE ya separa el hilo de render) |
| 7 | *Streaming*: RPF descomprimidos o recomprimidos en LZ4 al instalar | −3–5 ms conduciendo en el núcleo 0 | 2–3 sem. | Bajo (más disco) |

---

## 5. Arquitectura recomendada y hoja de ruta

### 5.1 CPU: reescritura binaria PPC→PPC estática (no «a C»)

Datos de partida [D, TU8]: 2 398 670 instrucciones; 135 652 VMX (**5,66 %**); funciones limpias de 32 bits: 54,6 % (35,8 % de instrucciones).

Las 64 bits más frecuentes:

| Instrucción | Usos |
|---|---|
| `std` | 24 882 |
| `ld` | 22 304 |
| `fcfid` | 4245 |
| `extsw` | 2515 |
| `fctidz` | 753 |
| `rldicr` | 695 |
| `clrldi` | 522 |
| `rldicl` | 419 |
| `mulld` | 309 |
| `mtmsrd` | 113 |

**Idea.** Una herramienta (extensión de `monta4.py`/`aot.py`) recorre cada función de `.pdata`, la copia a una nueva sección de código y en la copia:
1. deja tal cual cada instrucción que el 750 ejecuta igual (≥ 90 %);
2. sustituye cada «sucia» por una **secuencia de 750 en línea**, sin trampolines: así el resto del bloque no se toca. Los saltos se recalculan, porque la función crece;
3. los saltos a funciones van a sus copias (o a stubs del núcleo, para las importaciones).

Así no queda intérprete ni contexto en memoria para los GPR: los registros del juego son los registros del 750, como hoy en el código nativo.

**Banco VMX.** VMX128 tiene 128 registros vectoriales y el 750 32 FPR (pares PS). El banco va en memoria por hilo, apuntado de forma fija desde el PCR de la 360: el núcleo ya da a cada hilo su r13, así que basta reservar un bloque en `r13 + K` [H: comprobad cómo se pone hoy r13]. Dentro de cada bloque básico con VMX, un asignador de registros carga los v-registros vivos en pares de FPR muertos (análisis de vida de FPR, ya que el juego también usa la FPU escalar), opera y escribe al salir. Ojo: **`stfd` guarda solo ps0**; para mover pares hay que usar `psq_st`/`psq_l` con GQR 0.

**Tabla de traducción VMX → PS en TU8 [D recuento / E traducción].**

| Clase | % de las VMX | Traducción |
|---|---|---|
| Cargas y almacenamientos (`lvx128`, `stvx128`, `lvlx`, `lvrx`…) | 51,0 | 2 × `psq_l`/`psq_st`. `lvlx`/`lvrx` (3,4 %) desalineados: palabra a palabra |
| Aritmética float por carril (`vmulfp128`, `vmaddfp`, `vsubfp`, `vaddfp`, `vnmsubfp`, `vmax/minfp`…) | 16,2 | 2 × `ps_mul`/`ps_madd`/`ps_sub`/`ps_add`/`ps_nmsub`; `max`/`min` con `ps_sub` + `ps_sel` |
| Barajado de palabras (`vmrghw/lw`, `vspltw`, `vpermwi128`, `vrlimi128`, `vsldoi`) | 17,6 | **1 `ps_merge` por par de salida**: cualquier `vpermwi` son 2 `ps_merge` (cada par de salida toma una palabra de un par y otra de otro). `vrlimi` es inserción por carriles |
| Producto escalar (`vmsum3fp128`, `vmsum4fp128`) | 4,8 | `ps_mul` + `ps_madd` + `ps_sum0` (+ `ps_merge00` para repartir) |
| Lógicas, mover, selección (`vor`, `vand`, `vandc`, `vsel`) | 5,9 | `vor vD,vA,vA` → 2 `fmr`. `vsel` tras `vcmp*fp` → `ps_sel` (reconocer el patrón). Máscaras de signo → `ps_abs`/`ps_neg`. Bit a bit general → por GPR (lento) |
| Estimaciones (`vrsqrtefp` 995, `vrefp` 282, `vlogefp` 140, `vexptefp` 16) | 1,1 | `ps_rsqrte`/`ps_res` (+1 Newton-Raphson si hace falta precisión); `log`/`exp` en escalar |
| Comparaciones float (`vcmp*fp`, con o sin `.`) | 1,3 | `ps_cmpo0/1` o `ps_sub` + `ps_sel`; las de punto ponen CR6, en escalar |
| Conversiones int↔float | 0,7 | escalar (`fctiwz`, secuencia 0x4330) |
| Enteros VMX y (des)empaquetado entero | 1,1 | escalar por GPR |
| `vpkd3d128`/`vupkd3d128` | 0,02 (22 usos) | escalar; `psq_l` con GQR cuantizado para los u8/s8/u16/s16 |
| `vperm` genérico | **0,2** (314 usos) | por bytes (lento, pero irrelevante) |

**Cobertura [E]**: ~85–90 % de las VMX en 1–3 instrucciones PS; ~8–12 % por patrones o caminos lentos; <1 % realmente caro.

**Precisión [H].**
- `vrefp`/`vrsqrtefp` dan ~12 bits (especificación AltiVec). `ps_res`/`ps_rsqrte` del 750CL son también estimaciones; para igualarlos, una iteración de Newton (2–3 instrucciones).
- Denormales: VMX los vacía; la FPU del 750 en modo IEEE no, salvo con `FPSCR[NI]=1`. Propongo activar NI solo dentro de los bloques VMX (XenonRecomp hace lo equivalente por instrucción) e ignorarlo en el resto.

### 5.2 GPU: pintor nativo en la frontera `grc`

- **Frontera [D].** LibertyRecomp ya lo hizo en TU8: `gta4_native` intercepta el dispositivo de RAGE y crea recursos nativos.
  - Sus documentos recuperan fases (`CRenderPhase*`), recursos con nombre (`MIRROR_RT`, `REFLECTION_MAP_COLOUR`…), el arena de listas de dibujo (`sub_821BB3D8`) y la ejecución diferida (`sub_821BB2D0`).
  - Los nombres de clases salen del **RTTI del XEX retail** (`method_symbols.txt`, `vtables_with_addrs.txt` en sus documentos).
  - UnleashedRecomp hace lo mismo en `video.cpp`: hooks a `Draw*/Set*/Lock*` que meten órdenes en una cola hacia un hilo de render propio.
- **Plan [E].**
  1. Interceptar `grcDevice::Draw*`, `SetVertexDeclaration/Stream`, `grcEffect::Bind/BeginPass`, `grcTexture`, `grcRenderTarget` y `Lock/Unlock`. Se encuentran por RTTI + vtables, o con `busca_tu8.py` partiendo de TU8.
  2. Emitir GX2 en un hilo del núcleo 2, con una cola como UnleashedRecomp.
  3. Las texturas, convertidas una vez al cargar (des-mosaico Xenos → mosaico GX2), sin hash por imagen.
  4. Destinos en MEM1 a resolución elegida. El G-buffer se pinta una sola vez, sin franjas: la Wii U no las necesita.
- **Sombreadores [D].** GTA IV trae **79 FXC con 1132 sombreadores (~600 VS, ~530 PS)**; 68 FXC los usan 131 *presets* SPS y 21 son de sistema (diferido, agua, posproceso, sombras) (LibertyRecomp `docs/SHADER_PIPELINE.md`).
  - Ruta [E]: microcódigo Xenos → traductor automático moderno → GLSL → CafeGLSL. Por ejemplo, XenosRecomp (HLSL) o Xenia SPIR-V → SPIRV-Cross → GLSL. Emitid **GLSL lineal** cuando el control de flujo de Xenos no tenga saltos, que es la mayoría.
  - A mano en GLSL, los ~20 calientes: `deferred_lighting` ×4, G-buffer de edificios/vehículos/peatones, agua, *bloom*, DoF y tonos.
  - Medid con la GPU qué 20 pesan más antes de reescribir.

### 5.3 Hoja de ruta por fases (hitos medibles)

| Fase | Meses (acumulados) | Contenido | Hito |
|---|---|---|---|
| F0 | 0–1 | Lo del informe anterior: atómicos, una franja, optimizar el pintor PM4, muestreador, quitar la ilustración | Partida visible; **2–5 fps en interior**; reparto de CPU por hilo medido |
| F1 | 1–4 | Reescritura binaria PPC→PPC: VMX→PS, 64→32, privilegiadas. **0 % interpretado** | **Interior 10–15 fps**, cinemática 12–18 (con el pintor PM4 optimizado) |
| F2 | 3–8 (en paralelo a F1) | Pintor nativo `grc` + MEM1 + sombreadores automáticos lineales + ~20 a mano | Pintor < 25 ms; **cinemática 25–30**, interior 20–25 |
| F3 | 6–10 | Tubería de 3 núcleos, recortes con ajustes UDP, PS a mano en el *top*-N, tope de Euphoria | **Ciudad a pie 25–30**, conduciendo 20–25 |
| F4 | 9–14 | *Streaming* (LZ4), audio XMA real, resolución dinámica, ritmo de imágenes | Conduciendo rápido ≥ 25 con caídas cortas; «casi 30» estable a pie |

Puntos de decisión:
- al final de F1, si el hilo principal nativo en interior pasa de 25 ms, rebajad expectativas a 20–25 fps en ciudad;
- al final de F2, si la GPU a 960×540 pasa de 20 ms, priorizad sombreadores sobre CPU.

---

## 6. Riesgos técnicos

- **VMX → PS.**
  - Lo caro no son las operaciones, sino las **cargas y almacenamientos (51 %)** y la presión de registros: 128 VR del juego frente a 32 FPR compartidos con la FPU escalar.
  - Sin un asignador por bloque, cada operación VMX cuesta 4–6 instrucciones de memoria y la ganancia se queda en ×1,5.
  - Riesgo de fallos sutiles: NaN/denormales, `vcmp*.` que ponen CR6, `vsel` con máscaras de bits arbitrarias.
  - Mitigación: verificador diferencial que ejecute cada función VMX traducida contra el intérprete actual con entradas aleatorias (se puede hacer en la consola o en el Mac con un emulador).
- **Sombreadores.** 1132 son muchos para hacerlos a mano: hay que tener la traducción automática buena primero. El traductor GLSL antiguo de Xenia (`do{switch}`, `r[64]`) no vale para 30 fps. Los PS que usan VPOS/`ps_param_gen`, las texturas de profundidad empaquetadas (D24FS8 leído como A8R8G8B8, informe anterior) y las 4 variantes del diferido son los más delicados.
- **Memoria.**
  - La 360 tenía 512 MB unificados; la Wii U da 1 GB de MEM2 a la aplicación [D, VGleaks vía PureNintendo].
  - GTA IV cabe con margen. El *streaming* tiene dos límites por defecto de **50 MiB** (virtual y físico) y lee en bloques de 32 KiB con 8 ranuras [D, LibertyRecomp `GTA_IV_ASSET_STREAMING_SYSTEM.md`].
  - Con 1 GB podéis **subir esos límites**: menos *pop-in* sin coste de CPU.
  - MEM1: G-buffer de 720p 17,6 MiB (justo con extras); de 960×540, 9,9 MiB.
- **Streaming.**
  - USB 2.0 da 24–42 MB/s reales, el DVD de la Wii U ~22,5 MB/s [D, foros] y el DVD 12× de la 360 era similar o menos. **La E/S no es el cuello** si los RPF van en USB (mejor SSD, por las búsquedas).
  - **El cuello probable es la CPU de descompresión**: LZX/zlib de los RSC; LibertyRecomp tiene `gta4_lzx_hooks.cpp`. Conduciendo rápido, 5–10 MB/s comprimidos son 3–7 ms por imagen en un núcleo de 750 [H].
  - Remedio: al instalar, recomprimir en LZ4 (descompresión ~×5 más rápida) o descomprimir del todo si el disco lo aguanta.
- **Euphoria.** No hay cifras públicas de coste por personaje [búsqueda sin resultados]. En GTA IV solo actúa en reacciones (golpes, caídas, atropellos), con pocos agentes a la vez [D cualitativo].
  - Estimación [E]: 0,5–1,5 ms por agente en 360 y ×2–3 en Espresso.
  - Remedio: tope global de agentes NM (buscar el gestor y su *pool* por cadenas `NmRs*`/`fragInstNM*` [H]) y *ragdoll* simple para los lejanos.
- **Base de la 360 desconocida.** Todo el §3 descansa en un reparto estimado. Si el hilo principal de la 360 ya iba a 30+ ms (GTA IV cae a 20–25), el techo en ciudad baja a 20–25 fps aunque todo lo demás salga bien.

---

## 7. Preguntas abiertas (lo que debe medir el agente con la consola)

1. **Reparto actual por hilo y núcleo** con el muestreador. Para el hilo principal: % en código nativo, en el intérprete y en el AOT, en interior y en ciudad. Con eso: tiempo nativo puro = tiempo en nativo + (interpretado / factor) → techo real de F1.
2. Lista de hilos del juego (`HILOS`): nombre, entrada, prioridad y afinidad pedidas a la 360 (`ExCreateThread`, `KeSetAffinityThread`). Así sabremos qué hilos hardware usaba GTA IV para render, *streaming*, audio y trabajos.
3. **Funciones VMX más calientes** (*top*-50 por tiempo) y qué clase de VMX usan: valida la cobertura del §5.1 en lo que importa, no en el recuento estático.
4. Cuántas de las 4938 unidades VMX caen en el *top*-50 caliente: si es poco, un AOT a mano basta y la reescritura completa puede esperar.
5. Dibujos por pasada y por tipo en la ciudad (con `TRAZA`), con población 0,45 y distancia 0,65 aplicadas por `POKE`: valida las ganancias de 4a/4b.
6. Tiempo de GPU por pasada (marcas de tiempo GX2 al inicio y al final de cada fase): ¿cuáles son los ~20 sombreadores caros?
7. Bytes leídos por segundo y tiempo de descompresión conduciendo rápido 60 s (instrumentar `pgStreamer` o `XFile` en el núcleo).
8. Número de agentes Euphoria simultáneos en una pelea o atropello, y su coste (muestreador).
9. Valores reales de `stream.ini` (`virtual`/`physical`) en vuestra versión y si subirlos reduce el *pop-in*.
10. Si tenéis la versión **EUR de Wii** de *Table Tennis*: comprobar si el disco trae `.map` o símbolos (Anexo A).

---

## 8. Bibliografía

**Precedentes y hardware:**
- DF Black Ops II Wii U (vía): https://www.nintendolife.com/news/2012/11/call_of_duty_black_ops_ii_suffers_from_reduced_frame_rate_on_wii_u · https://mynintendonews.com/2012/11/24/black-ops-2-on-wii-u-is-sub-hd-and-has-worst-frame-rate/
- DF Arkham City / BO2 / ME3 Wii U (vía): https://multiplayer.it/notizie/111659-call-of-duty-black-ops-ii-call-of-duty-black-ops-ii-e-batman-arkham-city-su-wii-u-sono-tecnicamente-inferiori-alle-versioni-ps3-e-xbox-360.html
- DF AC IV Wii U (vía): https://mynintendonews.com/2013/12/14/digital-foundry-assassins-creed-black-flag-wii-u-falls-6-10fps-behind-the-360-and-ps3/ · https://www.nintenderos.com/2013/12/assassins-creed-black-flag-comparativa-wii-u-vs-x360-vs-ps3/
- AC III Wii U: https://godisageek.com/2012/12/assassins-creed-iii-wii-analysis/
- DF Watch Dogs Wii U (vía): https://www.nintenderos.com/2014/11/comparativa-grafica-y-de-framerate-de-watchdogs-para-wii-u/ · https://www.nintendolife.com/news/2014/11/video_how_does_watch_dogs_wii_u_compare_to_the_xbox_360_and_ps4_versions
- DF NFS MW U (vía): https://cubed3.com/news/18387/1/digital-foundry-explores-need-for-speed-on-wii-u-compared-to-pc.html · https://www.nintendolife.com/news/2013/02/wii_u_need_for_speed_most_wanted_based_on_superior_pc_version
- DF Xenoblade X: https://www.nintendolife.com/news/2015/12/digital_foundry_gives_xenoblade_chronicles_x_a_thumbs_up
- DF BotW: https://Www.gameinformer.com/b/news/archive/2017/04/04/digital-foundry-dissects-breath-of-the-wilds-111-update.aspx · https://kotaku.com/zelda-breath-of-the-wilds-switch-and-wii-u-versions-c-1792968383
- 4A / THQ sobre la CPU: https://www.mcvuk.com/business-news/consoles/metro-last-light-dev-says-wii-u-has-a-horrible-slow-cpu/ · https://games.slashdot.org/story/12/11/24/1522248/thq-clarifies-claims-of-horrible-slow-wii-u-cpu
- Omega Force y otros: https://www.nintendolife.com/news/2012/11/talking_point_nintendo_and_the_third_party_challenge
- Shin'en sobre eDRAM: https://cubed3.com/news/19226/1/shinens-manfred-linzner-discuss-wii-u-shaders-eram-usage.html
- Latte: https://techpowerup.com/gpu-specs/wii-u-gpu.c1903 · https://www.nintenderos.com/2013/02/ultima-detalles-tecnicos-de-wii-u/ · https://fail0verflow.com/blog/2014/console-hacking-2013-omake/
- WiiUBrew Hardware (MEM2 12,8 GB/s): https://wiiubrew.org/wiki/Hardware
- VGleaks vía PureNintendo (MEM1/MEM2): https://purenintendo.com/?p=66399
- Xenos / 360 (Hot Chips 17): https://old.hotchips.org/wp-content/uploads/hc_archives/hc17/3_Tue/HC17.S8/HC17.S8T4.pdf · https://tomshw.it/hardware/svelata-nei-particolari-la-gpu-di-xbox-360
- Xenon LHS y en orden: https://gamedev.net/blogs/entry/2250696-memory-access-latency/ · https://faculty.cc.gatech.edu/~hyesoon/spr11/lec_xbox2.pdf
- Espresso y foros sobre IPC: https://forums.anandtech.com/threads/wii-u-hardware-investigation.2345436 · https://en.wikipedia.org/wiki/Espresso_(processor)
- linux-wiiu (parches SMP de Espresso): https://gitlab.com/linux-wiiu · https://gitlab.com/linux-wiiu/smp-patches
- USB 2.0 y disco en Wii U: https://www.cheapassgamer.com/threads/wii-u-storage-question-discussion.344242/post-12623588 · https://nintendolife.com/forums/wii-u/testing_internal_storage_vs_usb_flash_drive_performance
- GTA IV PC atado a CPU (foros): https://forums.anandtech.com/threads/gta-iv-wheres-the-bottleneck.285311/post-27796636
- Euphoria (contexto): https://en.wikipedia.org/wiki/Euphoria_(software)

**Recompilación y código:**
- LibertyRecomp (OZORDI): https://github.com/OZORDI/LibertyRecomp
  - `docs/SHADER_PIPELINE.md` (79 FXC / 1132 sombreadores)
  - `docs/GTA_IV_ASSET_STREAMING_SYSTEM.md` (50 MiB, 32 KiB, 8 ranuras)
  - `docs/reflection-rendering-study/README.md`
  - `docs/unleashed-rendering-study/01-architecture.md`
  - `gta4-recomp/generated/` (recuentos VMX/64 bits de este informe)
- UnleashedRecomp: https://github.com/hedge-dev/UnleashedRecomp · Steam Deck: https://steamdeckhq.com/news/sonic-unleashed-native-pc-port-steam-deck
- XenonRecomp: https://github.com/hedge-dev/XenonRecomp · ReXGlue: https://github.com/rexglue/rexglue-sdk · https://www.gtaboom.com/a-modder-is-trying-to-get-gta-iv-running-on-android-and-switch-7a5f
- re3 (incluye Wii U): https://www.github.com/halpz/re3 · port a Wii: https://www.gtaboom.com/gta-iii-now-runs-natively-on-the-original-wii-7302

**Table Tennis y RAGE:**
- https://en.wikipedia.org/wiki/Rockstar_Games_Presents_Table_Tennis
- https://en.wikipedia.org/wiki/Rockstar_Advanced_Game_Engine
- TCRF (símbolos de depuración de la EUR de Wii): https://tcrf.net/Rockstar_Games_Presents_Table_Tennis

---

## Anexo A. *Table Tennis* (Wii) como piedra de Rosetta de RAGE

**Qué se sabe [D].**
- Primer juego RAGE; 360 en mayo de 2006 y Wii en octubre de 2007 (Rockstar Leeds según una ficha; Rockstar San Diego el original).
- **TCRF documenta restos de depuración y dice textualmente que usa «the debug symbols present in the EUR Wii release»** para relacionar cadenas con una «DemoWorld». Lo he visto en los resúmenes del buscador; TCRF no era accesible desde aquí, así que confirmadlo vosotros.
- No he encontrado comunidades de *decomp* dedicadas ni fps medidos de ninguna versión.
- No hay rastro de símbolos públicos de GTA IV en PS3 ni de betas con símbolos.
- Existe la filtración del código fuente de GTA V (dic-2023). **No la uséis**: es material protegido y contaminaría el proyecto.
- *Bully* de Wii no es RAGE (lo confirma el propio encargo); no he encontrado otra cosa.

**Qué ganaríais [E].**
- *Nombres*: con símbolos, nombre de cada función compartida del motor de 2006 (`atArray`, `sysMem*`, `fiDevice`/`fiStream`, `pgBase`, `grcore`, `phBound*`, `crAnimation`, `rage::Vector3/Matrix34`). Al emparejarlas con GTA IV se pone nombre a quizá un 5–15 % de las funciones del XEX: las del núcleo del motor, que son las que rodean la frontera `grc` y el *streaming*.
- *Matemáticas en PS hechas por Rockstar*: cómo escribieron `Vector3/4`, `Matrix34`, cuaterniones y *skinning* para el 750. Útil como **plantilla y prueba de rendimiento**, pero **no** como tabla automática VMX→PS. En GTA IV esas matemáticas están **en línea** en miles de funciones (por eso el 51 % de las VMX son cargas y almacenamientos), así que no hay una función de 360 que sustituir por su equivalente de Wii.
- *Frontera `grc` sobre GX*: confirma qué clases abstrae RAGE (dispositivo, textura, efecto, destino). Es justo la frontera del §5.2.
- Ahorro realista: **2–6 semanas** de ingeniería inversa del motor. No cambia el techo de fps.

**Procedimiento de emparejado (con vuestras copias legales).**
1. Volcad vuestro disco EUR de Wii (CleanRip) y extraed `main.dol` y los RELs (Dolphin o `wit`). Buscad `.map` o `.sym` en el sistema de ficheros del disco; muchos juegos de Wii los traían. Si los símbolos van dentro del DOL, salen con Ghidra.
2. Cargadlo en Ghidra con el módulo Gekko/Broadway (*ghidra-gekko-broadway-lang*) e importad el mapa. Cargad el XEX de 360 de *Table Tennis* con XEXLoaderWV, que soporta VMX128.
3. **Primer puente, *Table Tennis* 360 ↔ Wii**: mismo código fuente, misma época. Emparejad por cadenas de *assert* y de depuración, constantes float singulares (`0x3F2AAAAB`…), tamaños de estructura en `mulli`/desplazamientos y grafo de llamadas. Así los nombres de Wii pasan a la 360.
4. **Segundo puente, *Table Tennis* 360 → GTA IV 360**: mismo compilador y misma ABI. Con `busca_tu8.py` (firmas enmascaradas) más las mismas cadenas y constantes, o con *Version Tracking* de Ghidra entre ambos XEX.
5. Volcad los nombres emparejados a un `nombres.csv` que usen `quees.py` y el muestreador.
