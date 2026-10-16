/*
 * muestreador.c - perfil estadístico por núcleo para Cafe OS (wut).
 *
 * Idea: un hilo de prioridad 0 fijado a cada núcleo se despierta cada ~1 ms. Al
 * despertarse expulsa al hilo invitado que corría en ese núcleo; Cafe OS guarda el
 * contexto de ese hilo en su OSThread (context.srr0 = PC donde se interrumpió). El
 * muestreador lee ese PC (y el PC invitado del intérprete, si el hilo estaba
 * interpretando) y lo cuenta en un histograma. Cada 20 s se manda el top por el
 * registro de red.
 *
 * Coste: ~1 cambio de contexto por ms y núcleo (<1 %).
 *
 * Integración (plat5.c / etapa5.c):
 *   - el núcleo ya crea los hilos del juego: registrar cada OSThread* con
 *     muestreador_registra(hilo, &ctx_invitado->pc_interp) (puntero al PC invitado
 *     actual del intérprete/AOT para ese hilo, o NULL).
 *   - llamar a muestreador_arranca() tras crear los hilos.
 *   - orden UDP "PERFIL" -> muestreador_vuelca(); "PERFIL0" -> muestreador_borra().
 *
 * Interpretación de los PC:
 *   FFFFFFF0                  -> ningún hilo invitado listo (núcleo ocioso o todos esperando)
 *   0x82000000..fin de imagen -> código del juego nativo  -> función con funciones.csv
 *   dentro de interp.c        -> usar el PC invitado (pc_interp) -> función invitada
 *   dentro de aot.bin         -> tabla aot -> función invitada
 *   resto (gx2r.c, nucleo.c)  -> símbolos del .elf (powerpc-eabi-addr2line)
 *
 * Sin probar en consola: los offsets de OSThread son los de wut (context en 0x0,
 * state en 0x324).
 */
#include <coreinit/thread.h>
#include <coreinit/time.h>
#include <coreinit/core.h>
#include <string.h>
#include <stdint.h>

#define MAX_HILOS     64
#define TAM_TABLA     8192          /* potencia de 2; hash abierto */
#define PERIODO_US    997           /* primo: evita sincronizarse con el juego */

void registra(const char *fmt, ...);   /* el registro por red del port */

typedef struct { uint32_t pc; uint32_t n; } Cubo;

static struct {
    OSThread *hilo;
    volatile uint32_t *pc_interp;   /* PC invitado del intérprete o NULL */
    uint32_t nucleo;
} g_hilos[MAX_HILOS];
static volatile int g_n_hilos;

static Cubo g_tabla[3][TAM_TABLA];
static uint32_t g_total[3], g_perdidas[3];
static OSThread g_muestreadores[3];
static uint8_t g_pilas[3][16 * 1024] __attribute__((aligned(16)));
static volatile int g_activo = 1;

void muestreador_registra(OSThread *h, volatile uint32_t *pc_interp, uint32_t nucleo)
{
    int i = g_n_hilos;
    if (i >= MAX_HILOS) return;
    g_hilos[i].hilo = h; g_hilos[i].pc_interp = pc_interp; g_hilos[i].nucleo = nucleo;
    g_n_hilos = i + 1;
}

static void cuenta(int nucleo, uint32_t pc)
{
    uint32_t h = (pc >> 2) * 2654435761u;
    for (int k = 0; k < 16; k++) {
        Cubo *c = &g_tabla[nucleo][(h + k) & (TAM_TABLA - 1)];
        if (c->pc == pc) { c->n++; return; }
        if (c->pc == 0) { c->pc = pc; c->n = 1; return; }
    }
    g_perdidas[nucleo]++;
}

static int bucle(int argc, const char **argv)
{
    int nucleo = OSGetCoreId();
    (void)argc; (void)argv;
    while (g_activo) {
        OSSleepTicks(OSMicrosecondsToTicks(PERIODO_US));
        /* El hilo que corría en este núcleo queda READY con su contexto guardado.
           Cafe OS siempre ejecuta el READY de mayor prioridad (número menor), así que
           el expulsado es el READY de prioridad más alta; si hay empate, se cuentan
           todos (ambigüedad pequeña; fijar prioridades distintas por núcleo la quita). */
        int mejor_prio = 1000;
        for (int i = 0; i < g_n_hilos; i++) {
            OSThread *h = g_hilos[i].hilo;
            if (g_hilos[i].nucleo == (uint32_t)nucleo && h->state == OS_THREAD_STATE_READY &&
                h->priority < mejor_prio)
                mejor_prio = h->priority;
        }
        if (mejor_prio == 1000) { cuenta(nucleo, 0xFFFFFFF0u); g_total[nucleo]++; continue; } /* ocioso/esperando */
        for (int i = 0; i < g_n_hilos; i++) {
            if (g_hilos[i].nucleo != (uint32_t)nucleo) continue;
            OSThread *h = g_hilos[i].hilo;
            if (h->state != OS_THREAD_STATE_READY || h->priority != mejor_prio) continue;
            uint32_t pc = h->context.srr0;
            if (g_hilos[i].pc_interp) {
                uint32_t gpc = *g_hilos[i].pc_interp;
                if (gpc) pc = gpc | 1;      /* bit 0 = "interpretado" */
            }
            cuenta(nucleo, pc);
            g_total[nucleo]++;
        }
    }
    return 0;
}

void muestreador_arranca(void)
{
    static const uint32_t af[3] = { OS_THREAD_ATTRIB_AFFINITY_CPU0,
                                    OS_THREAD_ATTRIB_AFFINITY_CPU1,
                                    OS_THREAD_ATTRIB_AFFINITY_CPU2 };
    for (int c = 0; c < 3; c++) {
        if (!OSCreateThread(&g_muestreadores[c], bucle, 0, NULL,
                            g_pilas[c] + sizeof(g_pilas[c]), sizeof(g_pilas[c]),
                            0 /* prioridad máxima */, af[c]))
            continue;
        OSSetThreadName(&g_muestreadores[c], "muestreador");
        OSResumeThread(&g_muestreadores[c]);
    }
}

void muestreador_borra(void)
{
    memset(g_tabla, 0, sizeof g_tabla);
    memset(g_total, 0, sizeof g_total);
    memset(g_perdidas, 0, sizeof g_perdidas);
}

/* Vuelca el top 40 de cada núcleo, en trozos (el registro corta líneas largas). */
void muestreador_vuelca(void)
{
    for (int c = 0; c < 3; c++) {
        registra("perfil nucleo %d: %u muestras (%u perdidas)", c, g_total[c], g_perdidas[c]);
        for (int k = 0; k < 40; k++) {
            uint32_t mejor = 0, idx = 0;
            for (uint32_t i = 0; i < TAM_TABLA; i++)
                if (g_tabla[c][i].n > mejor) { mejor = g_tabla[c][i].n; idx = i; }
            if (!mejor) break;
            uint32_t pc = g_tabla[c][idx].pc;
            registra("  %08X%s %5u  %4.1f%%", pc & ~1u, (pc & 1) ? " (interp)" : "",
                     mejor, 100.0f * mejor / (g_total[c] ? g_total[c] : 1));
            g_tabla[c][idx].n = 0;   /* destructivo: vuelve a llamar a borra() */
        }
    }
}
