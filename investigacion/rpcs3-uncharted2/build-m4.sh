#!/bin/sh -ex
# Compila RPCS3 en el propio Mac (M4), igual que la build oficial pero con:
#   - USE_NATIVE_INSTRUCTIONS=ON  (la oficial usa -march=armv8.4-a genérico)
#   - opcional (PATCH=1): todos los parches 000*.patch de esta carpeta
#       0001 QoS de hilos compiladores (núcleos P para la emulación)
#       0002 "ZCull Fake ZPass Value": con consultas ZCull desactivadas, informar píxeles visibles en vez de 0
#       0003 "Relaxed Back-End Semaphores": escribir etiquetas del RSX sin esperar a la GPU entera
# Reutiliza los scripts de CI de RPCS3 (.ci/build-mac.sh + .ci/deploy-mac.sh), que ya meten
# MoltenVK 1.4.2 "privateapi" dentro de la app, como la build oficial.
#
# Requisitos: Xcode Command Line Tools, MacPorts, ~15 GB libres, 30-60 min.
# Uso:   PATCH=1 REV=master sh build-m4.sh
# Salida: ~/rpcs3-src/build/bin/RPCS3.app  -> se copia a /Applications/RPCS3-m4.app

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
  for p in "$HERE"/000*.patch; do git apply "$p"; echo "applied $p"; done
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

rm -rf /Applications/RPCS3-m4.app
cp -R build/bin/RPCS3.app /Applications/RPCS3-m4.app
echo "OK: /Applications/RPCS3-m4.app  ($(git rev-parse --short=8 HEAD), PATCH=${PATCH:-0})"
