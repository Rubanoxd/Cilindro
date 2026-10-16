#version 330
// resolve_profundidad.frag - P2: empaqueta la profundidad GX2 en el formato de un
// resolve de profundidad de Xenos, para que el juego lo lea como A8R8G8B8.
//
// GTA IV usa D24FS8 (profundidad float 20e4 de 24 bits + stencil de 8) y crea una
// textura A8R8G8B8 sobre la memoria del resolve de profundidad (TU8 sub_828D9768,
// D3DFMT 0x18280186). Sus sombreadores de luces decodifican esos bytes a mano, así que
// el resolve tiene que reproducir los bytes de Xenos:
//     palabra32 = (profundidad24 << 8) | stencil8
// (Xenia: draw_util.cc GetResolveInfo + texture_load_depth_*.cs.xesl; LibertyRecomp:
//  src/graphics/gta4_native/packed_depth_alias_ps.glsl, del que sale depth20e4).
//
// Entradas:
//   prof: textura obtenida con GX2ConvertDepthBufferToTextureSurface desde el
//         GX2DepthBuffer del destino EDRAM (formato FLOAT_R32 si el búfer es D32F,
//         UNORM_R24_X8 si es D24S8). Debe dar z/w en [0,1] (ver halfZ en P2).
//   sten: misma memoria vista como UINT_X24_G8 (D24S8) o UINT_G8_X24 (D32F_S8).
//         Si el port no tiene stencil, pasar 0.
//   u_formato: 1 = D24FS8 (RB_DEPTH_INFO bit 16), 0 = D24S8.
//   u_origen:  esquina del rectángulo del resolve en la superficie (píxeles).
//
// Salida: color RGBA8 en una superficie registrada en RB_COPY_DEST_BASE, igual que un
// resolve de color. ORDEN DE CANALES: aquí R = bits 0..7 de la palabra (stencil),
// G = 8..15, B = 16..23, A = 24..31 (como Xenia/LibertyRecomp). Comprobar que el port
// usa ese mismo convenio para las texturas k_8_8_8_8 cargadas de memoria; si no,
// permutar aquí (una sola vez).
//
// Requiere GLSL con enteros sin signo (CafeGLSL compila 3.30); sin probar en consola.

uniform sampler2D prof;
uniform usampler2D sten;
uniform int u_formato;
uniform ivec2 u_origen;

layout(location = 0) out vec4 o_color;

// float32 -> 20e4 (4 bits de exponente, 20 de mantisa, rango [0,2)), truncando como
// Xenos (Xenia xenos.cc Float32To20e4).
uint f32_a_20e4(float d)
{
    if (!(d > 0.0)) return 0u;
    uint b = floatBitsToUint(d);
    if (b >= 0x3FFFFFF8u) return 0xFFFFFFu;
    if (b < 0x38800000u) {
        uint despl = min(113u - (b >> 23u), 24u);
        b = (0x800000u | (b & 0x7FFFFFu)) >> despl;
    } else {
        b += 0xC8000000u;
    }
    return (b >> 3u) & 0xFFFFFFu;
}

void main()
{
    ivec2 p = ivec2(gl_FragCoord.xy) + u_origen;
    float d = texelFetch(prof, p, 0).r;
    uint s = texelFetch(sten, p, 0).g & 255u;
    uint d24 = (u_formato != 0) ? f32_a_20e4(d)
                                : uint(clamp(d, 0.0, 1.0) * 16777215.0 + 0.5);
    uint w = (d24 << 8u) | s;
    o_color = vec4(float(w & 255u), float((w >> 8u) & 255u),
                   float((w >> 16u) & 255u), float(w >> 24u)) / 255.0;
}
