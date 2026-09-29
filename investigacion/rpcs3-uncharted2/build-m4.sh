#!/bin/sh -ex
# Compila RPCS3 en el propio Mac (M4), igual que la build oficial pero con:
#   - USE_NATIVE_INSTRUCTIONS=ON  (la oficial usa -march=armv8.4-a genérico)
#   - opcional (PATCH=1): todos los parches 00NN-*.patch de esta carpeta
#       0001 QoS de hilos compiladores (núcleos P para la emulación)
#       0002 "ZCull Fake ZPass Value": con consultas ZCull desactivadas, informar píxeles visibles en vez de 0
#       0003 "Relaxed Back-End Semaphores": escribir etiquetas del RSX sin esperar a la GPU entera
#       0004 "SPU Heuristics Host Thread Count": las heurísticas de espera SPU asumen >=12 hilos (M4 base = 10)
#       0005 Fix: el intérprete de shaders no declaraba el binding del renderizado condicional emulado (Relaxed ZCULL en MoltenVK)
#       0006 "Emulated Conditional Rendering": cond. render en GPU (predicado en shader) sin relajar los informes ZCULL
#       0007 GPU Apple: cerrar render pass antes de copiar resultados de consultas (si no, llegan a 0) + predicado inicial "visible"
#       0008 ARM64: barreras de memoria en las DMA del SPU (x86 ordena solo, ARM no) — JIT + C++
#       0009 Caché persistente de objetos SPU (RPCS3 solo la guarda con SPU Debug): arranques sin recompilar ~9k funciones
#       0010 GETLLAR del SPU: barrera acquire en la lectura tipo seqlock (ARM puede aceptar líneas de 128 B a medio escribir)
#       0011 "Relaxed NV406E Sync": semáforo NV406E y set_reference sin sync completo de GPU (latencia MoltenVK → timeouts RsxKick del juego)
# Reutiliza los scripts de CI de RPCS3 (.ci/build-mac.sh + .ci/deploy-mac.sh), que ya meten
# MoltenVK 1.4.2 "privateapi" dentro de la app, como la build oficial.
#
# Requisitos: Xcode Command Line Tools, MacPorts, ~15 GB libres, 30-60 min.
# Uso:   PATCH=1 REV=master sh build-m4.sh
# Salida: /Applications/U2M4.app  (build "U2M4" = RPCS3 + parches para Uncharted 2 en M4)

SRC="${SRC:-$HOME/rpcs3-src}"
REV="${REV:-master}"
HERE="$(cd "$(dirname "$0")" && pwd)"

sudo port -N install clang-22 abseil ccache gtest opencv4 sdl3 vulkan-headers vulkan-loader MoltenVK cmake ninja p7zip wget

if [ ! -d "$SRC/.git" ]; then
  git clone https://github.com/RPCS3/rpcs3 "$SRC"
fi
cd "$SRC"
git fetch origin
git checkout -f "$REV"
git reset --hard

if [ "${PATCH:-0}" = "1" ]; then
  for p in "$HERE"/00[0-9][0-9]-*.patch; do git apply "$p"; echo "applied $p"; done
fi

# Instrucciones nativas del M4 en vez de armv8.4-a genérico
sed -i '' 's/-DUSE_NATIVE_INSTRUCTIONS=OFF/-DUSE_NATIVE_INSTRUCTIONS=ON/' .ci/build-mac.sh
rm -rf build

export LLVM_COMPILER_VER=22 QT_VER=6.11.2 QT_VER_MAIN=6 RUN_UNIT_TESTS=OFF
export CCACHE_DIR="$HOME/.ccache-rpcs3"
export BUILD_ARTIFACTSTAGINGDIRECTORY="$SRC/artifacts"
export RELEASE_MESSAGE="../GitHubReleaseMessage.txt"
mkdir -p "$BUILD_ARTIFACTSTAGINGDIRECTORY"

# SDK 27: protobuf necesita float.h (FLT_DIG/FLT_EPSILON)
export CXXFLAGS="-include float.h"
.ci/build-mac.sh

# Comprobar que de verdad se compiló con -march=native (si sale armv8.4-a, el compilador no lo aceptó)
grep -o -m1 'march=[a-z0-9.+-]*' build/build.ninja || true

# Marca U2M4: RPCS3 afinado para Uncharted 2 en el M4
APP=/Applications/U2M4.app
rm -rf "$APP"
cp -R build/bin/RPCS3.app "$APP"
# deploy-mac.sh puede no incluir MoltenVK con USE_SYSTEM_MVK: garantizarlo
[ -f "$APP/Contents/Frameworks/libMoltenVK.dylib" ] || cp build/bin/MoltenVK/MoltenVK/dynamic/dylib/macOS/libMoltenVK.dylib "$APP/Contents/Frameworks/"
plutil -replace CFBundleName -string "U2M4" "$APP/Contents/Info.plist"
plutil -replace CFBundleDisplayName -string "U2M4" "$APP/Contents/Info.plist" 2>/dev/null || plutil -insert CFBundleDisplayName -string "U2M4" "$APP/Contents/Info.plist"
# Icono U2M4 (PNG 1024 sin fondo -> .icns)
ICONSET="$(mktemp -d)/U2M4.iconset"; mkdir -p "$ICONSET"
for sz in 16 32 128 256 512; do
  sips -z $sz $sz "$HERE/icono/U2M4-1024.png" --out "$ICONSET/icon_${sz}x${sz}.png" >/dev/null
  sips -z $((sz*2)) $((sz*2)) "$HERE/icono/U2M4-1024.png" --out "$ICONSET/icon_${sz}x${sz}@2x.png" >/dev/null
done
iconutil -c icns "$ICONSET" -o "$APP/Contents/Resources/U2M4.icns"
plutil -replace CFBundleIconFile -string "U2M4.icns" "$APP/Contents/Info.plist"
plutil -remove CFBundleIconName "$APP/Contents/Info.plist" 2>/dev/null || true

U2M4_VER="$(cat "$HERE/VERSION")"
plutil -replace CFBundleShortVersionString -string "$U2M4_VER" "$APP/Contents/Info.plist"
codesign --force --deep --sign - "$APP"
echo "OK: $APP  U2M4 v$U2M4_VER  ($(git rev-parse --short=8 HEAD), PATCH=${PATCH:-0})"
