#!/bin/sh
# Construye Cilindro.apk sin Gradle: aapt2 + javac + d8 + apksigner.
#
# Necesita un JDK 17 o posterior y el Android SDK con build-tools y una
# plataforma >= 29. Los busca solo; si no, se le dicen con variables:
#
#   ANDROID_HOME=/ruta/al/sdk  JAVA_HOME=/ruta/al/jdk  ./construye.sh
#
# Otras variables: KEYSTORE (por defecto ./cilindro.keystore, se crea si no
# existe) y KS_PASS (su contrasena). La clave es TUYA: no la subas nunca.
#
# Salida: build/Cilindro.apk
set -e
cd "$(dirname "$0")"
RAIZ=$(pwd)
OBRA=build/obra
CACHE=.cache

falla() { echo "!! $*" >&2; exit 1; }

# ---- herramientas --------------------------------------------------------
if [ -z "$ANDROID_HOME" ]; then
  for d in "$ANDROID_SDK_ROOT" "$HOME/Library/Android/sdk" "$HOME/Android/Sdk" \
           /opt/homebrew/share/android-commandlinetools /usr/local/share/android-commandlinetools; do
    [ -n "$d" ] && [ -d "$d/build-tools" ] && { ANDROID_HOME=$d; break; }
  done
fi
[ -d "$ANDROID_HOME/build-tools" ] || falla "no encuentro el Android SDK: define ANDROID_HOME"
BT=$(ls -d "$ANDROID_HOME"/build-tools/*/ | sort -V | tail -1); BT=${BT%/}
JAR=$(ls "$ANDROID_HOME"/platforms/android-*/android.jar 2>/dev/null | sort -V | tail -1)
[ -f "$JAR" ] || falla "no hay ninguna plataforma en $ANDROID_HOME/platforms"

if [ -n "$JAVA_HOME" ]; then JAVAC=$JAVA_HOME/bin/javac; KEYTOOL=$JAVA_HOME/bin/keytool
else JAVAC=$(command -v javac); KEYTOOL=$(command -v keytool); fi
[ -x "$JAVAC" ] || falla "no encuentro javac: instala un JDK 17+ o define JAVA_HOME"
# apksigner y d8 son scripts de Java: necesitan JAVA_HOME o java en el PATH
[ -n "$JAVA_HOME" ] && export JAVA_HOME && export PATH="$JAVA_HOME/bin:$PATH"

echo "### SDK:         $ANDROID_HOME"
echo "### build-tools: $(basename "$BT")   plataforma: $(basename "$(dirname "$JAR")")"

# ---- LuaJ (MIT) desde Maven Central, comprobando su huella -------------------
mkdir -p "$CACHE"
LUAJ=$CACHE/luaj-jse-3.0.1.jar
LUAJ_SHA1=99245b2df284805e1cb835e9be47c243f9717511
if [ ! -s "$LUAJ" ]; then
  echo "### descargando LuaJ 3.0.1"
  curl -fsSL -o "$LUAJ" https://repo1.maven.org/maven2/org/luaj/luaj-jse/3.0.1/luaj-jse-3.0.1.jar
fi
[ "$(shasum -a 1 "$LUAJ" | cut -d' ' -f1)" = "$LUAJ_SHA1" ] || { rm -f "$LUAJ"; falla "LuaJ con huella distinta: borrado, vuelve a lanzar"; }

# ---- comprobaciones ------------------------------------------------------
# Sin assets/xposed_init el modulo DEJA DE EXISTIR para LSPosed/Vector sin
# ningun aviso: activarlo sigue diciendo que si. Nos costo una hora.
[ -f assets/xposed_init ] || falla "falta assets/xposed_init"
ls efectos/*/*/efecto.lua >/dev/null 2>&1 || falla "no hay efectos: lanza ./efectos.sh --gpl"

# ---- clave de firma --------------------------------------------------------
KEYSTORE=${KEYSTORE:-$RAIZ/cilindro.keystore}
KS_PASS=${KS_PASS:-cilindro}
if [ ! -f "$KEYSTORE" ]; then
  echo "### creando una clave de firma nueva en $KEYSTORE"
  "$KEYTOOL" -genkeypair -keystore "$KEYSTORE" -alias cilindro -keyalg RSA -keysize 4096 \
    -validity 10950 -storepass "$KS_PASS" -keypass "$KS_PASS" -dname "CN=Cilindro" >/dev/null
fi

# ---- area de trabajo: se compila sobre COPIAS, las fuentes no se tocan ------
rm -rf build; mkdir -p "$OBRA/clases" "$OBRA/assets/efectos"
cp -R src stubs res AndroidManifest.xml "$OBRA/"
cp assets/* "$OBRA/assets/"
# Los efectos se aplanan en assets/efectos/<nombre>/, venga de donde venga.
# Si el mismo nombre esta en dos origenes, gana supermamon (GPL).
for origen in rweichler supermamon; do
  [ -d "efectos/$origen" ] && cp -R "efectos/$origen/." "$OBRA/assets/efectos/"
done
echo "### efectos: $(ls "$OBRA/assets/efectos" | wc -l | tr -d ' ')"

# versionCode nuevo en cada compilacion: con el mismo, Vector sigue ejecutando
# la copia cacheada del modulo y los cambios no se ven.
VER=$(( $(cat "$CACHE/version" 2>/dev/null || echo 0) + 1 )); echo "$VER" > "$CACHE/version"
sed "s/android:versionCode=\"[0-9]*\"/android:versionCode=\"$VER\"/" AndroidManifest.xml > "$OBRA/AndroidManifest.xml"
# El sello aparece en el registro de Xposed: asi se sabe que corre ESTA compilacion.
sed "s/@MARCA@/$VER/" src/cat/junipero/cilindro/Modulo.java > "$OBRA/src/cat/junipero/cilindro/Modulo.java"
echo "### version $VER"

cd "$OBRA"
echo "### 1/5 recursos";   "$BT/aapt2" compile --dir res -o res.zip
echo "### 2/5 enlazado";   "$BT/aapt2" link -o base.apk -I "$JAR" --manifest AndroidManifest.xml -A assets \
                             --min-sdk-version 30 --target-sdk-version 36 res.zip
echo "### 3/5 javac";      "$JAVAC" -source 17 -target 17 -nowarn -classpath "$JAR:$RAIZ/$LUAJ" -d clases \
                             $(find stubs src -name "*.java")
# Los stubs de Xposed solo sirven para compilar: en el movil pone las clases
# el propio framework, asi que NO entran en el dex.
echo "### 4/5 d8";         "$BT/d8" --lib "$JAR" --classpath clases --min-api 30 --output . \
                             "$RAIZ/$LUAJ" $(find clases/cat -name "*.class")
zip -q -u base.apk classes.dex
echo "### 5/5 firma";      "$BT/zipalign" -f -p 4 base.apk alineado.apk
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
  --out "$RAIZ/build/Cilindro.apk" alineado.apk
cd "$RAIZ"; rm -rf "$OBRA" build/Cilindro.apk.idsig

echo "### LISTO: build/Cilindro.apk ($(du -h build/Cilindro.apk | cut -f1))"
