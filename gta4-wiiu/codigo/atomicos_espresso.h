/*
 * atomicos_espresso.h - operaciones atómicas correctas en el Espresso (Wii U).
 *
 * El Espresso tiene un erratum en stwcx.: hay que ejecutar "dcbst" sobre la misma
 * dirección justo antes de cada stwcx. (NetBSD lo documenta y lo aplica en
 * sys/arch/powerpc/include/asm.h, POWERPC_STWCX_PRE(ra,rb) = dcbst ra,rb, y en
 * sys/arch/powerpc/include/lock.h). Sin eso, una stwcx. puede tener éxito aunque otro
 * núcleo haya escrito la línea: se pierden actualizaciones. Con un cerrojo de tipo
 * "intercambio" (InterlockedExchange(&l,1) para coger, stw 0 para soltar) el síntoma es
 * justo una suelta perdida.
 *
 * OJO: los __sync_* / __atomic_* de devkitPPC GCC generan lwarx/stwcx. SIN dcbst.
 * No usarlos en el núcleo ni en el intérprete; usar estas funciones (o las de coreinit,
 * OSCompareAndSwapAtomic etc., tras comprobar que llevan dcbst desensamblándolas).
 *
 * Uso previsto:
 *   - intérprete (interp/interp.c): lwarx -> guardar (ea, valor); stwcx. -> cas32()
 *     contra el valor guardado (semántica CAS; compatible con el código nativo que usa
 *     lwarx/stwcx. reales sobre la misma palabra).
 *   - AOT (tools/aot.py): emitir llamadas a estas funciones en lugar de una reserva
 *     simulada en el contexto.
 *   - núcleo (nucleo/nucleo.c): KeAcquireSpinLockAtRaisedIrql, KfAcquireSpinLock,
 *     KeReleaseSpinLockFromRaisedIrql, RtlEnterCriticalSection, InterlockedPushEntrySList...
 *
 * Sin probar en consola: compilar con -mcpu=750 y revisar el desensamblado.
 */
#ifndef ATOMICOS_ESPRESSO_H
#define ATOMICOS_ESPRESSO_H

#include <stdint.h>

/* CAS: si *p == esperado, escribe nuevo. Devuelve el valor que había. */
static inline uint32_t cas32_valor(volatile uint32_t *p, uint32_t esperado, uint32_t nuevo)
{
    uint32_t viejo;
    __asm__ __volatile__(
        "1:  lwarx   %0,0,%1      \n"
        "    cmpw    %0,%2        \n"
        "    bne-    2f           \n"
        "    dcbst   0,%1         \n"   /* erratum de Espresso */
        "    stwcx.  %3,0,%1      \n"
        "    bne-    1b           \n"
        "2:                       \n"
        : "=&r"(viejo)
        : "r"(p), "r"(esperado), "r"(nuevo)
        : "cr0", "memory");
    return viejo;
}

static inline int cas32(volatile uint32_t *p, uint32_t esperado, uint32_t nuevo)
{
    return cas32_valor(p, esperado, nuevo) == esperado;
}

/* Intercambio atómico (InterlockedExchange). */
static inline uint32_t xchg32(volatile uint32_t *p, uint32_t nuevo)
{
    uint32_t viejo;
    __asm__ __volatile__(
        "1:  lwarx   %0,0,%1      \n"
        "    dcbst   0,%1         \n"
        "    stwcx.  %2,0,%1      \n"
        "    bne-    1b           \n"
        : "=&r"(viejo)
        : "r"(p), "r"(nuevo)
        : "cr0", "memory");
    return viejo;
}

/* Suma atómica; devuelve el valor ANTERIOR (InterlockedExchangeAdd). */
static inline uint32_t xadd32(volatile uint32_t *p, int32_t d)
{
    uint32_t viejo, t;
    __asm__ __volatile__(
        "1:  lwarx   %0,0,%2      \n"
        "    add     %1,%0,%3     \n"
        "    dcbst   0,%2         \n"
        "    stwcx.  %1,0,%2      \n"
        "    bne-    1b           \n"
        : "=&r"(viejo), "=&r"(t)
        : "r"(p), "r"(d)
        : "cr0", "memory");
    return viejo;
}

/* Barreras: el 750 trata lwsync (sync 1) como sync; usar estas para el núcleo. */
static inline void barrera_completa(void) { __asm__ __volatile__("sync" ::: "memory"); }
static inline void barrera_adquirir(void) { __asm__ __volatile__("isync" ::: "memory"); }

/*
 * Reserva para el intérprete. Una por hilo invitado (en su contexto).
 *   lwarx rD,rA,rB:  ctx->res_ea = ea; ctx->res_val = *(uint32_t*)ea; rD = res_val;
 *                    ctx->res_ok = 1;
 *   stwcx. rS,rA,rB: ok = ctx->res_ok && ctx->res_ea == ea &&
 *                         cas32((uint32_t*)ea, ctx->res_val, rS);
 *                    ctx->res_ok = 0; CR0 = (ok ? EQ : 0) | XER.SO;
 * Cualquier excepción/cambio de contexto simulado debe poner res_ok = 0.
 * (Es la semántica CAS de Xenia/XenonRecomp; el problema ABA no afecta a cerrojos ni
 * contadores.)
 */
typedef struct {
    uint32_t res_ea;
    uint32_t res_val;
    uint32_t res_ok;
} ReservaInvitado;

static inline uint32_t interp_lwarx(ReservaInvitado *r, uint32_t ea)
{
    uint32_t v = *(volatile uint32_t *)(uintptr_t)ea;
    r->res_ea = ea; r->res_val = v; r->res_ok = 1;
    return v;
}

static inline int interp_stwcx(ReservaInvitado *r, uint32_t ea, uint32_t valor)
{
    int ok = r->res_ok && r->res_ea == ea &&
             cas32((volatile uint32_t *)(uintptr_t)ea, r->res_val, valor);
    r->res_ok = 0;
    return ok;   /* el llamador pone CR0.EQ = ok */
}

/*
 * Parche de los stwcx. NATIVOS del juego (sitios): sustituir cada
 *     stwcx. rS,rA,rB
 * por "b sitio_N" con
 *     sitio_N:  dcbst  rA,rB          (0x7C00006C | rA<<16 | rB<<11)
 *               stwcx. rS,rA,rB       (la instrucción original)
 *               b      dir_original+4
 * El salto no rompe la reserva y CR0 lo pone la propia stwcx. La lista de sitios sale de
 *     python3 gta4-wiiu/herramientas/ppcscan.py atomicos --funciones out/funciones.csv \
 *             --sitios out/sitios_stwcx.txt
 * (en TU8 hay 59 stwcx. y 48 lwarx: es un parche pequeño).
 *
 * Y los corchetes "mfmsr rX ; mtmsrd r13,1 ... mtmsrd rX,1" de los Interlocked* de la
 * 360 (deshabilitan interrupciones; en modo usuario de Cafe OS son privilegiados):
 * sustituir mfmsr rX por "li rX,0" y ambos mtmsrd por "nop". La atomicidad la da la
 * pareja lwarx/stwcx. (con su dcbst), no el corchete.
 */

#endif /* ATOMICOS_ESPRESSO_H */
