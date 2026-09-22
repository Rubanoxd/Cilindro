# Créditos y licencias

Cilindro es un **clon** del motor de Cylinder para Android. La idea, la API de Lua que imita y casi
todos los efectos que existen son de otras personas. Esto es lo que es de cada uno.

## Cylinder — Reed Weichler (rweichler)

[rweichler/cylinder](https://github.com/rweichler/cylinder) es el tweak original para iOS con jailbreak,
y define la API de Lua (`view:translate`, `view:rotate`, `page.subviews`, `view.layer`, …) que
Cilindro reimplementa sobre Android.

**Su repositorio no tiene licencia**, lo que en GitHub significa "todos los derechos reservados".
Por eso **ninguno de sus scripts está en este repositorio**. `./efectos.sh --rweichler` los descarga
directamente de su repositorio para uso personal, y es cosa de quien compila. Si en algún momento
Reed Weichler añade una licencia que lo permita, se podrán incluir.

Cilindro **no contiene código de Cylinder**: es una implementación nueva, en Java, de la misma API.

## Efectos de supermamon

[supermamon/cylinder-scripts](https://github.com/supermamon/cylinder-scripts), **GNU GPL v2**.
Los 12 efectos de `efectos/supermamon/` son suyos y van **sin modificar**; ver su
[LEEME](efectos/supermamon/LEEME.md).

## LuaJ

[LuaJ](https://github.com/luaj/luaj) 3.0.1 (`org.luaj:luaj-jse`), intérprete de Lua 5.2 en Java puro,
**licencia MIT**. No va en el repositorio: `construye.sh` lo descarga de Maven Central y comprueba
su huella SHA-1 antes de usarlo. Se incluye dentro del APK compilado.

## API de Xposed

`stubs/` contiene solo las **firmas** de las clases de la API de Xposed que usa el módulo, para poder
compilar sin Gradle. No entran en el APK: en el teléfono las pone el framework (LSPosed/Vector).
La API original es de rovo89 y se publica bajo **Apache 2.0**.

## Cilindro

El código de Cilindro (`src/`, `construye.sh`, `efectos.sh`, `herramientas/`) se publica bajo la
**GNU GPL v2 o, a tu elección, cualquier versión posterior** (ver `LICENSE`), compatible con los
efectos de supermamon y con LuaJ.
