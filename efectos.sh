#!/bin/sh
# Descarga efectos de Cylinder de sus repositorios ORIGINALES y los deja en
# efectos/<origen>/<nombre>/efecto.lua, con sus include/ resueltos.
#
#   ./efectos.sh --lista                  todos los que hay, con su origen
#   ./efectos.sh --gpl                    los de supermamon (GPL-2.0): los que
#                                         van en este repositorio
#   ./efectos.sh --rweichler              los del Cylinder original (sin
#                                         licencia): se descargan, no se
#                                         redistribuyen; ver CREDITOS.md
#   ./efectos.sh "Vortex" "Cube (inside)" solo esos, del origen donde esten
#
# Si un efecto esta en los dos repos, gana la copia GPL de supermamon.
# Algunos includes de supermamon (libCountIcons.lua) no estan sueltos en su
# repo sino dentro de su propio zip, tambien GPL: se sacan de ahi.
# Un efecto que incluya codigo de OTRO repo (Tornado pide
# ../rweichler/include/cube.lua) no se descarga con --gpl.
set -e
cd "$(dirname "$0")"
CACHE=.cache; mkdir -p "$CACHE"
A_RW=$CACHE/arbol-rweichler.json
A_SM=$CACHE/arbol-supermamon.json
R_RW=https://raw.githubusercontent.com/rweichler/cylinder/master
R_SM=https://raw.githubusercontent.com/supermamon/cylinder-scripts/master
ZIP_SM=$CACHE/supermamon-cylinder-scripts.zip
PY=${PYTHON:-python3}

[ -s "$A_RW" ] || curl -fsSL "https://api.github.com/repos/rweichler/cylinder/git/trees/master?recursive=1" -o "$A_RW"
[ -s "$A_SM" ] || curl -fsSL "https://api.github.com/repos/supermamon/cylinder-scripts/git/trees/master?recursive=1" -o "$A_SM"

# Imprime "origen<TAB>ruta<TAB>nombre" de cada efecto (sin includes ni ejemplos)
catalogo() {
  $PY - "$A_SM" "$A_RW" <<'PYEOF'
import json, os, sys
visto = set()
for fichero, origen, pre in ((sys.argv[1], 'supermamon', ''), (sys.argv[2], 'rweichler', 'src/scripts/')):
    for x in json.load(open(fichero)).get('tree', []):
        p = x['path']
        if not p.endswith('.lua') or '/include/' in p or 'EXAMPLE' in p or not p.startswith(pre):
            continue
        nombre = os.path.basename(p)[:-4]
        if nombre in visto:
            continue          # ya estaba en supermamon: gana la copia GPL
        visto.add(nombre)
        print(f'{origen}\t{p}\t{nombre}')
PYEOF
}

bajar() {  # $1=origen $2=ruta $3=nombre
  raiz=$R_RW; [ "$1" = supermamon ] && raiz=$R_SM
  dest="efectos/$1/$3"; carpeta=$(dirname "$2")
  mkdir -p "$dest"
  q() { $PY -c "import urllib.parse,sys; print(urllib.parse.quote(sys.argv[1]))" "$1"; }
  curl -fsSL -o "$dest/efecto.lua" "$raiz/$(q "$2")"
  # IFS de linea: los nombres de include no llevan espacios, pero por si acaso
  grep -oE 'dofile\("[^"]+"\)' "$dest/efecto.lua" | sed 's/dofile("//; s/")//' | while read -r inc; do
    case "$inc" in
      ../*) if [ "$1" = supermamon ] && [ -n "$SOLO_PROPIOS" ]; then
              rm -rf "$dest"; echo "  (saltado $3: incluye $inc, de otro autor)"; exit 0
            fi ;;
    esac
    mkdir -p "$dest/$(dirname "$inc")"
    if ! curl -fsSL -o "$dest/$inc" "$raiz/$(q "$carpeta/$inc")" 2>/dev/null; then
      # no esta suelto en el repo: se busca en el zip GPL de supermamon
      [ "$1" = supermamon ] || { echo "!! $3: falta $inc"; exit 1; }
      [ -s "$ZIP_SM" ] || curl -fsSL "$R_SM/supermamon-cylinder-scripts.zip" -o "$ZIP_SM"
      unzip -p "$ZIP_SM" "supermamon/$inc" > "$dest/$inc"
      [ -s "$dest/$inc" ] || { echo "!! $3: $inc no esta ni en el repo ni en el zip"; exit 1; }
    fi
  done
  [ -d "$dest" ] && echo "  $1: $3"
}

case "$1" in
  --lista) catalogo | awk -F'\t' '{printf "%-11s %s\n", $1, $3}' | sort ;;
  --gpl|--rweichler)
    o=supermamon; [ "$1" = --rweichler ] && o=rweichler
    [ "$1" = --gpl ] && export SOLO_PROPIOS=1
    catalogo | awk -F'\t' -v o="$o" '$1==o' | while IFS="$(printf '\t')" read -r origen ruta nombre; do
      bajar "$origen" "$ruta" "$nombre"
    done ;;
  ""|-h|--help) sed -n '2,15p' "$0" ;;
  *)
    for pedido in "$@"; do
      linea=$(catalogo | awk -F'\t' -v n="$pedido" 'tolower($3)==tolower(n)' | head -1)
      [ -n "$linea" ] || { echo "!! no existe: $pedido"; continue; }
      bajar "$(echo "$linea" | cut -f1)" "$(echo "$linea" | cut -f2)" "$(echo "$linea" | cut -f3)"
    done ;;
esac
