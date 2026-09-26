# Depuración y avisos al humano — Wiintosh GPU (Wii U + Miatoll)

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
