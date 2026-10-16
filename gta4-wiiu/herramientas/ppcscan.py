#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ppcscan.py - buscador para out/imagen.bin (XEX de GTA IV mapeado en 0x82000000).

Python 3 puro, sin dependencias (capstone es opcional, solo para 'desen').

Órdenes:
  refs  DIR [DIR ...]     cargas/almacenamientos cuya dirección efectiva (lis/addi/ori
                          propagados en línea) cae en DIR o en DIR..DIR+n (--ancho n)
  inm   VALOR [...]       sitios donde se materializa VALOR en un registro (p. ej. un
                          puntero a función que se guarda como delegado)
  llamadas DIR [...]      todos los b/bl que saltan a DIR
  atomicos                todos los lwarx/stwcx./ldarx/stdcx. (para el remedio del
                          erratum de Espresso: dcbst antes de stwcx.)
  patron "W W ?? W"       busca palabras de instrucción; '??' = cualquiera; dentro de
                          una palabra, 'x' = nibble comodín (p. ej. C01Fxxxx)
  desen DIR [N]           desensambla N instrucciones (necesita 'pip install capstone')

Opciones:
  --imagen out/imagen.bin   --base 0x82000000   --funciones out/funciones.csv
  --ancho N  (refs: acepta EA en [DIR, DIR+N) )

La propagación es lineal dentro de cada función (si hay funciones.csv) y no hace
uniones en los destinos de salto: puede dar algún falso positivo; nunca inventa una
dirección que no se haya construido con lis/addi/ori/mr. Verifica siempre a mano.
"""
import argparse, os, re, struct, sys

# ---------------------------------------------------------------- utilidades
def s16(v): return v - 0x10000 if v & 0x8000 else v
def u32(v): return v & 0xFFFFFFFF

D_LOADS = {32: 'lwz', 33: 'lwzu', 34: 'lbz', 35: 'lbzu', 40: 'lhz', 41: 'lhzu',
           42: 'lha', 43: 'lhau', 46: 'lmw', 48: 'lfs', 49: 'lfsu', 50: 'lfd', 51: 'lfdu'}
D_STORES = {36: 'stw', 37: 'stwu', 38: 'stb', 39: 'stbu', 44: 'sth', 45: 'sthu',
            47: 'stmw', 52: 'stfs', 53: 'stfsu', 54: 'stfd', 55: 'stfdu'}
FP_OPS = {48, 49, 50, 51, 52, 53, 54, 55}
UPDATE_OPS = {33, 35, 37, 39, 41, 43, 45, 49, 51, 53, 55}
# opcode 31 con destino en rA (lógicas / desplazamientos / extensiones)
X31_WRITES_RA = {28, 60, 124, 284, 316, 412, 444, 476, 24, 536, 792, 824, 26, 922, 954, 986,
                 27, 539, 794, 826, 827, 58}  # and andc nor eqv xor orc or nand slw srw sraw srawi
                                               # cntlzw extsh extsb extsw sld srd srad sradi cntlzd
X31_NO_DEST = {0, 32, 4, 68, 150, 214, 151, 183, 215, 247, 407, 439, 149, 181, 663, 695, 727,
               759, 983, 918, 662, 54, 86, 246, 278, 470, 1014, 982, 598, 854, 467, 144,
               146, 210, 242, 306, 370, 566, 725, 661, 135, 167, 199, 231, 487, 647, 679,
               775, 807}  # cmp/tw/st*x/dcb*/icbi/sync/eieio/mt*/stv*
X31_STORE_UPD = {183, 247, 439, 181, 695, 759}
X31_LOAD_UPD = {55, 119, 311, 375, 53, 373, 567, 631}

class Imagen:
    def __init__(self, ruta, base):
        with open(ruta, 'rb') as f:
            self.datos = f.read()
        self.base = base
        self.n = len(self.datos) // 4
    def palabra(self, dir_):
        i = (dir_ - self.base) >> 2
        if i < 0 or i >= self.n: return None
        return struct.unpack_from('>I', self.datos, i * 4)[0]
    def rango(self):
        return self.base, self.base + self.n * 4

def leer_funciones(ruta):
    """Acepta CSV/TXT con 2 números hex por línea: inicio,fin o inicio,longitud."""
    funcs = []
    if not ruta or not os.path.exists(ruta): return funcs
    for linea in open(ruta, errors='replace'):
        nums = re.findall(r'(?:0x)?([0-9A-Fa-f]{6,8})\b', linea)
        if len(nums) < 1: continue
        a = int(nums[0], 16)
        if len(nums) >= 2:
            b = int(nums[1], 16)
            fin = b if b > a else a + b
        else:
            fin = None
        funcs.append([a, fin])
    funcs.sort()
    for i, f in enumerate(funcs):  # sin longitud: hasta la siguiente
        if f[1] is None:
            f[1] = funcs[i + 1][0] if i + 1 < len(funcs) else f[0] + 4
    return [(a, b) for a, b in funcs if b > a]

def funcion_de(funcs, dir_):
    lo, hi = 0, len(funcs) - 1
    while lo <= hi:
        m = (lo + hi) // 2
        a, b = funcs[m]
        if dir_ < a: hi = m - 1
        elif dir_ >= b: lo = m + 1
        else: return a
    return None

def bloques(img, funcs):
    """Genera (inicio, fin) de los trozos a recorrer."""
    if funcs:
        lo, hi = img.rango()
        for a, b in funcs:
            if a >= lo and b <= hi: yield a, b
    else:
        yield img.rango()

# ---------------------------------------------------------- propagación
def recorrer(img, ini, fin, al_acceso=None, al_valor=None):
    """Propagación lineal de constantes en GPRs. Llama a:
       al_acceso(pc, op_nombre, ea, es_store, rt, d, ra)
       al_valor(pc, reg, valor)"""
    k = {}
    pc = ini
    while pc < fin:
        w = img.palabra(pc)
        if w is None: break
        op = w >> 26
        rd = (w >> 21) & 31; ra = (w >> 16) & 31; rb = (w >> 11) & 31
        imm = w & 0xFFFF
        if op == 15:  # addis / lis
            v = (s16(imm) << 16) if ra == 0 else (k[ra] + (s16(imm) << 16) if ra in k else None)
            if v is None: k.pop(rd, None)
            else:
                k[rd] = u32(v)
                if al_valor: al_valor(pc, rd, k[rd])
        elif op == 14:  # addi / li
            v = s16(imm) if ra == 0 else (k[ra] + s16(imm) if ra in k else None)
            if v is None: k.pop(rd, None)
            else:
                k[rd] = u32(v)
                if al_valor: al_valor(pc, rd, k[rd])
        elif op in (24, 25):  # ori / oris : rA = rS | imm
            rs, rA = rd, ra
            if rs in k:
                k[rA] = u32(k[rs] | (imm if op == 24 else imm << 16))
                if al_valor and w != 0x60000000: al_valor(pc, rA, k[rA])
            else: k.pop(rA, None)
        elif op in D_LOADS or op in D_STORES:
            es_store = op in D_STORES
            if ra != 0 and ra in k and al_acceso:
                al_acceso(pc, (D_LOADS.get(op) or D_STORES.get(op)), u32(k[ra] + s16(imm)),
                          es_store, rd, s16(imm), ra)
            if op in UPDATE_OPS and ra != 0:
                if ra in k: k[ra] = u32(k[ra] + s16(imm))
            if not es_store and op not in FP_OPS:
                k.pop(rd, None)
                if op == 46:  # lmw
                    for r in range(rd, 32): k.pop(r, None)
        elif op in (58, 62):  # ld/ldu/lwa  std/stdu (DS)
            ds = s16(imm & 0xFFFC); sub = w & 3
            es_store = op == 62
            nombre = (['ld', 'ldu', 'lwa', '?'] if op == 58 else ['std', 'stdu', '?', '?'])[sub]
            if ra != 0 and ra in k and al_acceso:
                al_acceso(pc, nombre, u32(k[ra] + ds), es_store, rd, ds, ra)
            if sub == 1 and ra in k: k[ra] = u32(k[ra] + ds)
            if not es_store: k.pop(rd, None)
        elif op == 18:  # b / bl
            if w & 1:
                for r in [0] + list(range(3, 13)): k.pop(r, None)
        elif op == 16:  # bc
            if w & 1:
                for r in [0] + list(range(3, 13)): k.pop(r, None)
        elif op == 19:
            xo = (w >> 1) & 0x3FF
            if xo in (16, 528) and (w & 1):  # bclrl / bcctrl
                for r in [0] + list(range(3, 13)): k.pop(r, None)
        elif op == 31:
            xo = (w >> 1) & 0x3FF
            if xo == 444 and rd == rb:  # mr rA,rS
                if rd in k: k[ra] = k[rd]
                else: k.pop(ra, None)
            elif xo in X31_WRITES_RA:
                k.pop(ra, None)
            elif xo in X31_NO_DEST:
                if xo in X31_STORE_UPD:  # st*ux: actualiza rA
                    k.pop(ra, None)
            else:
                k.pop(rd, None)
                if xo in X31_LOAD_UPD:  # l*ux: actualiza rA
                    k.pop(ra, None)
        elif op in (20, 21, 23, 26, 27, 28, 29, 30):  # rlw*, xori, xoris, andi., andis., rld*
            k.pop(ra, None)
        elif op in (10, 11, 3, 2, 4, 59, 63, 47, 56, 57, 60, 61, 1, 5, 6):
            pass  # cmpli/cmpi/twi/tdi/VMX/FPU/stmw...: no escriben GPR (salvo casos raros)
        else:
            k.pop(rd, None)
        pc += 4

# ------------------------------------------------------------------ órdenes
def cmd_refs(img, funcs, objetivos, ancho):
    res = []
    def acc(pc, nombre, ea, st, rt, d, ra):
        for t in objetivos:
            if t <= ea < t + ancho:
                res.append((pc, nombre, ea, rt, d, ra))
    for a, b in bloques(img, funcs):
        recorrer(img, a, b, al_acceso=acc)
    for pc, nombre, ea, rt, d, ra in sorted(set(res)):
        f = funcion_de(funcs, pc) if funcs else None
        reg = ('f' if nombre.startswith(('lf', 'stf')) else 'r') + str(rt)
        print(f"{pc:08X}  {nombre:6s} {reg},{d}(r{ra})   EA={ea:08X}" + (f"   func={f:08X}" if f else ''))
    print(f"# {len(set(res))} accesos", file=sys.stderr)

def cmd_inm(img, funcs, valores):
    vs = set(valores); res = []
    def val(pc, reg, v):
        if v in vs: res.append((pc, reg, v))
    for a, b in bloques(img, funcs):
        recorrer(img, a, b, al_valor=val)
    for pc, reg, v in sorted(set(res)):
        f = funcion_de(funcs, pc) if funcs else None
        print(f"{pc:08X}  r{reg} = {v:08X}" + (f"   func={f:08X}" if f else ''))

def cmd_llamadas(img, funcs, destinos):
    ds = set(destinos)
    lo, hi = img.rango()
    for pc in range(lo, hi, 4):
        w = img.palabra(pc)
        if w >> 26 != 18: continue
        li = w & 0x03FFFFFC
        if li & 0x02000000: li -= 0x04000000
        t = u32(li if (w & 2) else pc + li)
        if t in ds:
            f = funcion_de(funcs, pc) if funcs else None
            print(f"{pc:08X}  {'bl' if w & 1 else 'b '} {t:08X}" + (f"   func={f:08X}" if f else ''))

def cmd_atomicos(img, funcs, salida_sitios=None):
    lo, hi = img.rango()
    nombres = {20: 'lwarx', 84: 'ldarx', 150: 'stwcx.', 214: 'stdcx.'}
    cuenta = {}
    sitios = []
    for a, b in bloques(img, funcs):
        for pc in range(a, b, 4):
            w = img.palabra(pc)
            if w is None or w >> 26 != 31: continue
            xo = (w >> 1) & 0x3FF
            if xo not in nombres: continue
            if xo in (150, 214) and not (w & 1): continue
            rs = (w >> 21) & 31; ra = (w >> 16) & 31; rb = (w >> 11) & 31
            n = nombres[xo]; cuenta[n] = cuenta.get(n, 0) + 1
            f = funcion_de(funcs, pc) if funcs else None
            print(f"{pc:08X}  {n:7s} r{rs},r{ra},r{rb}" + (f"   func={f:08X}" if f else ''))
            if xo == 150:
                dcbst = 0x7C00006C | (ra << 16) | (rb << 11)
                sitios.append((pc, w, dcbst))
    print('# ' + ', '.join(f'{k}={v}' for k, v in sorted(cuenta.items())), file=sys.stderr)
    if salida_sitios:
        with open(salida_sitios, 'w') as fo:
            fo.write('# dir_stwcx  instr_original  dcbst_a_insertar_antes\n')
            for pc, w, d in sitios:
                fo.write(f'{pc:08X} {w:08X} {d:08X}\n')
        print(f'# {len(sitios)} sitios escritos en {salida_sitios}', file=sys.stderr)

def cmd_patron(img, funcs, patron):
    toks = patron.split()
    pats = []
    for t in toks:
        if t == '??': pats.append((0, 0)); continue
        t = t.lower().replace('0x', '')
        if len(t) != 8: sys.exit(f'palabra de patrón inválida: {t}')
        m = int(''.join('0' if c == 'x' else 'f' for c in t), 16)
        v = int(t.replace('x', '0'), 16)
        pats.append((m, v))
    lo, hi = img.rango()
    for pc in range(lo, hi - 4 * len(pats) + 4, 4):
        ok = True
        for i, (m, v) in enumerate(pats):
            w = img.palabra(pc + 4 * i)
            if (w & m) != v: ok = False; break
        if ok:
            f = funcion_de(funcs, pc) if funcs else None
            ws = ' '.join(f'{img.palabra(pc + 4 * i):08X}' for i in range(len(pats)))
            print(f'{pc:08X}  {ws}' + (f'   func={f:08X}' if f else ''))

def cmd_desen(img, dir_, n):
    try:
        import capstone
    except ImportError:
        sys.exit('desen necesita capstone:  pip install capstone')
    md = capstone.Cs(capstone.CS_ARCH_PPC, capstone.CS_MODE_32 | capstone.CS_MODE_BIG_ENDIAN)
    for i in range(n):
        pc = dir_ + 4 * i
        w = img.palabra(pc)
        if w is None: break
        ins = list(md.disasm(struct.pack('>I', w), pc))
        txt = f'{ins[0].mnemonic} {ins[0].op_str}' if ins else f'.long 0x{w:08X}'
        print(f'{pc:08X}  {w:08X}  {txt}')

def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('orden', choices=['refs', 'inm', 'llamadas', 'atomicos', 'patron', 'desen'])
    ap.add_argument('args', nargs='*')
    ap.add_argument('--imagen', default='out/imagen.bin')
    ap.add_argument('--base', default='0x82000000')
    ap.add_argument('--funciones', default=None)
    ap.add_argument('--ancho', type=int, default=1)
    ap.add_argument('--sitios', default=None, help='atomicos: escribe la lista de stwcx. a parchear')
    a = ap.parse_args()
    img = Imagen(a.imagen, int(a.base, 16))
    funcs = leer_funciones(a.funciones)
    if a.funciones: print(f'# {len(funcs)} funciones leídas', file=sys.stderr)
    h = lambda xs: [int(x, 16) for x in xs]
    if a.orden == 'refs': cmd_refs(img, funcs, h(a.args), a.ancho)
    elif a.orden == 'inm': cmd_inm(img, funcs, h(a.args))
    elif a.orden == 'llamadas': cmd_llamadas(img, funcs, h(a.args))
    elif a.orden == 'atomicos': cmd_atomicos(img, funcs, a.sitios)
    elif a.orden == 'patron': cmd_patron(img, funcs, ' '.join(a.args))
    elif a.orden == 'desen': cmd_desen(img, int(a.args[0], 16), int(a.args[1]) if len(a.args) > 1 else 40)

if __name__ == '__main__':
    main()
