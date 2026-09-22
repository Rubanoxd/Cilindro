package cat.junipero.cilindro;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Ejecuta efectos de Cylinder (Lua) sobre el escritorio del Pixel Launcher. */
public class Modulo implements IXposedHookLoadPackage {

    private static final String LAUNCHER = "com.google.android.apps.nexuslauncher";
    private static final String YO = "cat.junipero.cilindro";
    private static final String ETIQUETA = "[Cilindro] ";
    private static final String MARCA = "@MARCA@";

    private static boolean puesto = false;
    private static Globals lua = null;
    private static LuaValue efecto = null;
    private static String efectoActual = null;
    private static boolean roto = false;
    private static int diag = 0;
    private static final Map<View, Vista> envoltorios = new HashMap<View, Vista>();

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!LAUNCHER.equals(lpparam.packageName)) return;
        XposedBridge.log(ETIQUETA + "enganchado, compilacion " + MARCA);

        XposedHelpers.findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Activity a = (Activity) param.thisObject;
                if (a == null || !LAUNCHER.equals(a.getPackageName())) return;

                // en cada vuelta al escritorio se mira si has cambiado de efecto
                String elegido = preguntarElegido(a);
                if (elegido != null && !elegido.equals(efectoActual)) {
                    if (cargarEfecto(a, elegido)) {
                        efectoActual = elegido;
                        roto = false;
                        XposedBridge.log(ETIQUETA + "efecto activo: " + elegido);
                    }
                }
                if (!puesto) instalar(a);
            }
        });
    }

    /** Pregunta a la app cual has elegido. Entre procesos distintos es la via limpia. */
    private String preguntarElegido(Context ctx) {
        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(Ajustes.URI, null, null, null, null);
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Throwable e) {
            XposedBridge.log(ETIQUETA + "no pude preguntar el efecto: " + e);
        } finally {
            if (c != null) try { c.close(); } catch (Throwable ignorado) {}
        }
        return Ajustes.POR_DEFECTO;
    }

    private static String leerAsset(Context mio, String ruta) throws Exception {
        InputStream in = mio.getAssets().open(ruta);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        in.close();
        return bos.toString("UTF-8");
    }

    private boolean cargarEfecto(Context ctx, final String nombre) {
        try {
            final Context mio = ctx.createPackageContext(YO, Context.CONTEXT_IGNORE_SECURITY);
            final String carpeta = "efectos/" + nombre + "/";
            String fuente = leerAsset(mio, carpeta + "efecto.lua");

            lua = JsePlatform.standardGlobals();

            // dofile se resuelve DENTRO de la carpeta del efecto: cada autor trae
            // sus propios include/ y con nombres repetidos (fade.lua, por ejemplo).
            lua.set("dofile", new VarArgFunction() {
                public Varargs invoke(Varargs a) {
                    String ruta = a.checkjstring(1);
                    try {
                        return lua.load(leerAsset(mio, carpeta + ruta), ruta).call();
                    } catch (Exception e) {
                        XposedBridge.log(ETIQUETA + "dofile fallo con " + ruta + ": " + e);
                        return NIL;
                    }
                }
            });

            lua.set("subviews", new VarArgFunction() {
                public Varargs invoke(Varargs a) {
                    LuaValue lista = a.arg1().get("subviews");
                    return varargsOf(new LuaValue[]{
                            lua.get("ipairs").call(lista).arg1(), lista, ZERO });
                }
            });

            // Cylinder los ofrece; sin ellos, algunos scripts revientan.
            lua.set("print", new VarArgFunction() {
                public Varargs invoke(Varargs a) {
                    XposedBridge.log(ETIQUETA + "lua: " + a.tojstring(1));
                    return NONE;
                }
            });
            lua.set("popup", lua.get("print"));
            lua.set("PERSPECTIVE_DISTANCE", LuaValue.valueOf(1000));

            efecto = lua.load(fuente, nombre).call();
            return true;
        } catch (Throwable e) {
            XposedBridge.log(ETIQUETA + "no pude cargar '" + nombre + "': " + e);
            return false;
        }
    }

    private void instalar(Activity a) {
        try {
            int id = a.getResources().getIdentifier("workspace", "id", a.getPackageName());
            if (id == 0) { XposedBridge.log(ETIQUETA + "sin recurso workspace"); return; }
            View v = a.findViewById(id);
            if (!(v instanceof ViewGroup)) return;
            final ViewGroup escritorio = (ViewGroup) v;

            escritorio.getViewTreeObserver().addOnPreDrawListener(
                    new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    if (!roto && efecto != null) aplicar(escritorio);
                    return true;
                }
            });
            puesto = true;
            XposedBridge.log(ETIQUETA + "oyente instalado");
        } catch (Throwable e) {
            XposedBridge.log(ETIQUETA + "fallo al instalar: " + e);
        }
    }

    private void aplicar(ViewGroup escritorio) {
        try {
            int scroll = escritorio.getScrollX();
            int anchoPantalla = escritorio.getWidth();
            int altoPantalla = escritorio.getHeight();
            if (anchoPantalla <= 0) return;

            // paso entre paginas: lo que de verdad separa una de otra.
            // Medido aqui: paginas de 1038 px con los left en 21 y 1087, o sea
            // un paso de 1066. Usar 1038 como arista del cubo deja hueco.
            int paso = 0;
            if (escritorio.getChildCount() >= 2) {
                paso = escritorio.getChildAt(1).getLeft() - escritorio.getChildAt(0).getLeft();
            }

            for (int p = 0; p < escritorio.getChildCount(); p++) {
                View paginaV = escritorio.getChildAt(p);
                if (!(paginaV instanceof ViewGroup)) continue;

                // el escritorio tiene margen interno: sin restarlo, en reposo
                // sale offset != 0 y el efecto se aplica con el movil quieto
                int offset = scroll - paginaV.getLeft() + escritorio.getPaddingLeft();
                if (Math.abs(offset) > anchoPantalla) continue;

                if (diag < 2) {
                    diag++;
                    StringBuilder sb = new StringBuilder(ETIQUETA + "geometria:");
                    sb.append(" escritorio=").append(anchoPantalla);
                    sb.append(" margen=").append(escritorio.getPaddingLeft());
                    for (int k = 0; k < escritorio.getChildCount(); k++) {
                        View pv = escritorio.getChildAt(k);
                        sb.append(" | pag").append(k).append(" left=").append(pv.getLeft())
                          .append(" ancho=").append(pv.getWidth());
                    }
                    XposedBridge.log(sb.toString());
                }

                Vista pagina = envolver(paginaV);
                pagina.refrescar(paso);

                ViewGroup cont = (ViewGroup) paginaV;
                for (int i = 0; i < cont.getChildCount(); i++) {
                    if (cont.getChildAt(i) instanceof ViewGroup) {
                        cont = (ViewGroup) cont.getChildAt(i);
                        break;
                    }
                }

                LuaTable iconos = new LuaTable();
                for (int i = 0; i < cont.getChildCount(); i++) {
                    Vista ic = envolver(cont.getChildAt(i));
                    ic.refrescar();
                    iconos.set(i + 1, ic);
                    pagina.rawset(LuaValue.valueOf(i + 1), ic);
                }
                pagina.rawset(LuaValue.valueOf("subviews"), iconos);

                efecto.invoke(LuaValue.varargsOf(new LuaValue[]{
                        pagina, LuaValue.valueOf(offset),
                        LuaValue.valueOf(anchoPantalla), LuaValue.valueOf(altoPantalla) }));
            }
        } catch (Throwable e) {
            roto = true;
            XposedBridge.log(ETIQUETA + "el efecto ha fallado, lo desactivo: " + e);
        }
    }

    private Vista envolver(View v) {
        Vista w = envoltorios.get(v);
        if (w == null) { w = new Vista(v); envoltorios.put(v, w); }
        return w;
    }
}
