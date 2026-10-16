#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
busca_tu8.py - encuentra en NUESTRO XEX (out/imagen.bin) las funciones equivalentes a
funciones conocidas de GTA IV TU8 (LibertyRecomp), comparando estructura y no direcciones.

Las firmas (firmas_tu8.json) son las instrucciones de TU8 reensambladas con máscaras:
  - b/bl/bc: se ignora el desplazamiento (solo opcode, BO/BI, LK)
  - lis y los addi/ori/carga/almacenamiento que usan la base de un lis: se ignora el
    inmediato (las direcciones globales cambian entre versiones)
  - el resto (desplazamientos de estructura, registros, constantes) se compara entero
  - instrucciones VMX128 que no se pudieron reensamblar: comodín

Búsqueda: anclas raras (palabras exactas poco frecuentes) con bytes.find y luego
puntuación con tolerancia a pequeñas inserciones/borrados (+-2 instrucciones).

Uso:
  python3 busca_tu8.py --imagen out/imagen.bin                 # todas las firmas
  python3 busca_tu8.py --imagen out/imagen.bin 82145770 821B7048
  python3 busca_tu8.py --lista                                 # qué firmas hay
Salida: candidatos ordenados por puntuación (1.00 = idéntica salvo direcciones).
Una puntuación >= 0.80 con un segundo candidato claramente peor es casi segura;
0.55-0.80 es "parecida" (mirar a mano con ppcscan.py desen).
"""
import argparse, json, os, struct, sys

def cargar(ruta):
    d = open(ruta, 'rb').read()
    n = len(d) // 4
    return d, struct.unpack('>%dI' % n, d[:n * 4])

def puntuar(words, ini, sig_w, sig_m):
    n = len(sig_w); desp = 0; pts = 0.0; ok_exactas = 0
    nw = len(words)
    for i in range(n):
        m = sig_m[i]
        if m == 0:
            pts += 0.5; continue
        mejor = None
        for d in (0, 1, -1, 2, -2):
            j = ini + i + desp + d
            if 0 <= j < nw and (words[j] & m) == (sig_w[i] & m):
                mejor = d; break
        if mejor is not None:
            desp += mejor; pts += 1.0 if mejor == 0 else 0.9; ok_exactas += 1
        else:
            j = ini + i + desp
            if 0 <= j < nw and (words[j] >> 26) == (sig_w[i] >> 26):
                pts += 0.3
    return pts / n, ok_exactas

def buscar(datos, words, base, firma, maximo=5):
    sig_w = [int(x, 16) for x in firma['w']]
    sig_m = [int(x, 16) for x in firma['m']]
    cands = set()
    for a in firma['anclas']:
        patron = struct.pack('>I', sig_w[a])
        pos = datos.find(patron)
        vistos = 0
        while pos != -1 and vistos < 20000:
            if pos % 4 == 0:
                cands.add(pos // 4 - a)
            pos = datos.find(patron, pos + 1)
            vistos += 1
    res = []
    for c in cands:
        if c < 0: continue
        s, ex = puntuar(words, c, sig_w, sig_m)
        res.append((s, ex, c))
    res.sort(reverse=True)
    # refinar el inicio de los mejores (las anclas pueden caer tras una inserción)
    ref = []
    for s0, ex0, c0 in res[:12]:
        mejor = (s0, ex0, c0)
        for dlt in range(-12, 13):
            if dlt == 0 or c0 + dlt < 0: continue
            s1, ex1 = puntuar(words, c0 + dlt, sig_w, sig_m)
            if s1 > mejor[0] + 1e-9: mejor = (s1, ex1, c0 + dlt)
        ref.append(mejor)
    res = sorted(set(ref + res[12:]), reverse=True)
    # quitar duplicados cercanos
    out = []
    for s, ex, c in res:
        if all(abs(c - c2) > 4 for _, _, c2 in out):
            out.append((s, ex, c))
        if len(out) >= maximo: break
    return [(s, ex, base + 4 * c) for s, ex, c in out]

def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('funciones', nargs='*', help='direcciones TU8 (p. ej. 82145770); vacío = todas')
    ap.add_argument('--imagen', default='out/imagen.bin')
    ap.add_argument('--base', default='0x82000000')
    ap.add_argument('--firmas', default=os.path.join(os.path.dirname(os.path.abspath(__file__)), 'firmas_tu8.json'))
    ap.add_argument('--lista', action='store_true')
    ap.add_argument('-n', type=int, default=3, help='candidatos por función')
    a = ap.parse_args()
    F = json.load(open(a.firmas))['funciones']
    if a.lista:
        for k, v in F.items(): print(f"{k}  ({v['n']:4d} instr)  {v['desc']}")
        return
    datos, words = cargar(a.imagen)
    base = int(a.base, 16)
    claves = [x.upper().replace('SUB_', '') for x in a.funciones] or list(F.keys())
    for k in claves:
        if k not in F: print(f'{k}: no hay firma'); continue
        f = F[k]
        r = buscar(datos, words, base, f, a.n)
        print(f"TU8 {k} ({f['n']} instr) {f['desc']}")
        if not r: print('    sin candidatos (ninguna ancla aparece)'); continue
        for s, ex, d in r:
            print(f"    {d:08X}  puntuación {s:.2f}  ({ex}/{f['n']} exactas)")

if __name__ == '__main__':
    main()
