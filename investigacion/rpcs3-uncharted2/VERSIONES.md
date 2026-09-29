# U2M4 — versiones

Se usa el esquema `0.N`: cada build nueva suma 1 a N (0.1, 0.2, … 0.11, … 0.1000). Cuando Uncharted 2 sea jugable, la build pasa a `1.0`.
La versión actual está en `VERSION`; `build-m4.sh` la graba en la app (Acerca de / Info.plist).

| Versión | Base RPCS3 | Parches | Resultado |
|---|---|---|---|
| 0.1 | 105c4988 (0.0.42-20073) | 0001–0006 | sin esperas RsxKick (20 FPS en menú), pero sin geometría: resultados de consultas a 0 en GPU Apple |
| 0.2 | 105c4988 | 0001–0007 | 3D en menú y cinemática; cuelgue al empezar a jugar (con SPU Debug llega a jugar ~10 s) |
| 0.3 | 105c4988 | 0001–0008 | arranque en frío falla (assert VDEC por compilar ~9k funciones SPU en vivo), con y sin fence |
| 0.4 | 105c4988 | 0001–0009 | con caché caliente llega a JUGAR 3/3, se congela 3/3 (RsxKick → SPU-PM too many flags) |
| 0.5 | 105c4988 | 0001–0010 | juega 1-2 min y se congela (RsxKick → too many flags); x86+Rosetta falla igual pero más tarde → no es (solo) orden de memoria |
| 0.6 | 105c4988 | 0001–0011 | en pruebas (ORDEN #26) |
