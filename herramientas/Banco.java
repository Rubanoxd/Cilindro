import org.luaj.vm2.*;
import org.luaj.vm2.lib.*;
import org.luaj.vm2.lib.jse.JsePlatform;

/** Mide cuantas veces por segundo se puede ejecutar un efecto al estilo Cylinder. */
public class Banco {

    // Una "vista" falsa: guarda lo que el script le pide, sin tocar Android.
    static class Vista extends LuaTable {
        float rot, tx, ty, alpha = 1f;
        final int x, y, w, h;
        Vista(final int x, final int y, final int w, final int h) {
            this.x = x; this.y = y; this.w = w; this.h = h;
            set("x", x); set("y", y); set("width", w); set("height", h);
            set("rotate", new VarArgFunction() {
                public Varargs invoke(Varargs a) { rot = (float) a.checkdouble(2); return NONE; }
            });
            set("translate", new VarArgFunction() {
                public Varargs invoke(Varargs a) {
                    tx = (float) a.checkdouble(2);
                    if (a.narg() > 2) ty = (float) a.checkdouble(3);
                    return NONE;
                }
            });
            set("scale", new VarArgFunction() { public Varargs invoke(Varargs a) { return NONE; } });
        }
        @Override public void rawset(LuaValue k, LuaValue v) {
            if (k.isstring() && "alpha".equals(k.tojstring())) alpha = (float) v.todouble();
            super.rawset(k, v);
        }
    }

    public static void main(String[] args) throws Exception {
        int iconos = args.length > 0 ? Integer.parseInt(args[0]) : 20;
        int vueltas = args.length > 1 ? Integer.parseInt(args[1]) : 20000;

        Globals g = JsePlatform.standardGlobals();

        // pagina con sus iconos
        final Vista pagina = new Vista(0, 0, 1080, 1800);
        final LuaTable hijos = new LuaTable();
        for (int i = 0; i < iconos; i++) {
            hijos.set(i + 1, new Vista((i % 4) * 270, (i / 4) * 300, 240, 240));
        }
        pagina.set("children", hijos);

        // subviews(page) -> iterador, como en Cylinder
        g.set("subviews", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                return varargsOf(new LuaValue[]{ g.get("ipairs").call(hijos).arg1(), hijos, ZERO });
            }
        });

        String script =
            "return function(page, offset, screen_width, screen_height)\n" +
            "  local percent = offset/page.width\n" +
            "  page:rotate(percent*math.pi*2)\n" +
            "  for i, icon in subviews(page) do\n" +
            "    icon.alpha = 1 - math.abs(percent)\n" +
            "  end\n" +
            "end\n";

        LuaValue efecto = g.load(script, "efecto").call();

        LuaValue anchoL = LuaValue.valueOf(1080);
        LuaValue altoL = LuaValue.valueOf(2400);

        // calentamiento
        for (int i = 0; i < 2000; i++) {
            efecto.invoke(LuaValue.varargsOf(new LuaValue[]{
                    pagina, LuaValue.valueOf(i % 1080), anchoL, altoL }));
        }

        long t0 = System.nanoTime();
        for (int i = 0; i < vueltas; i++) {
            efecto.invoke(LuaValue.varargsOf(new LuaValue[]{
                    pagina, LuaValue.valueOf(i % 1080), anchoL, altoL }));
        }
        long ns = System.nanoTime() - t0;

        double porLlamada = ns / (double) vueltas / 1000.0;         // microsegundos
        double porSegundo = 1e9 / (ns / (double) vueltas);
        System.out.println("iconos por pagina : " + iconos);
        System.out.println("llamadas medidas  : " + vueltas);
        System.out.printf ("por llamada       : %.1f microsegundos%n", porLlamada);
        System.out.printf ("llamadas/segundo  : %.0f%n", porSegundo);
        System.out.printf ("coste a 60 fps    : %.2f%% de un fotograma (2 paginas)%n",
                (porLlamada * 2 / 16666.0) * 100);
    }
}
