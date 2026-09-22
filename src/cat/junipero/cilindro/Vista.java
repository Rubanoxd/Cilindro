package cat.junipero.cilindro;

import android.graphics.Camera;
import android.graphics.Matrix;
import android.view.View;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/**
 * Envoltura Lua de una vista de Android con la API de Cylinder.
 *
 * Las transformaciones planas (giro en el eje Z, translacion en X/Y, escala)
 * van por los metodos normales de View, que son baratos.
 * En cuanto aparece 3D -- rotar en pitch/yaw o trasladar en Z -- se cambia a
 * android.graphics.Camera + View.setAnimationMatrix(): Camera SI traslada en
 * profundidad de verdad, cosa que setTranslationZ no hace (eso es elevacion
 * para sombras). setAnimationMatrix es API publica desde el nivel 29.
 */
public class Vista extends LuaTable {

    private static final double A_GRADOS = 180.0 / Math.PI;

    final View vista;
    private final Camera camara = new Camera();
    private final Matrix matriz = new Matrix();

    // Las operaciones se guardan EN EL ORDEN en que las pide el script:
    // translate-y-luego-rotate NO es lo mismo que rotate-y-luego-translate.
    // op: 0 = rotar (x,y,z en grados), 1 = trasladar (x,y,z en px)
    private final float[][] ops = new float[16][4];
    private int nOps;
    private boolean tres_d;
    /** page.layer.x/y: posicion de la capa, INDEPENDIENTE de la transformacion.
     *  El cubo la usa para clavar la pagina y que haga de bisagra; si se pierde,
     *  la pagina se va con el desplazamiento en vez de girar sobre su borde. */
    private float capaX, capaY;

    /** Cuantas veces el ancho de la pagina se aleja la camara.
     *  Cylinder usa ~1000 puntos sobre una pantalla de 375: unas 2,7 veces.
     *  Mas bajo = perspectiva mas exagerada y todo se encoge mas. */
    private static final float DISTANCIA = 2.7f;

    Vista(View v) {
        this.vista = v;

        set("translate", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                float x = (float) a.optdouble(2, 0);
                float y = (float) a.optdouble(3, 0);
                float z = (float) a.optdouble(4, 0);
                anotar(1, x, y, z);
                if (z != 0) tres_d = true;
                aplicar();
                return NONE;
            }
        });

        set("rotate", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                float ang = (float) (a.optdouble(2, 0) * A_GRADOS);
                if (a.narg() >= 5) {
                    float px = ang * (float) a.optdouble(3, 0);
                    float py = ang * (float) a.optdouble(4, 0);
                    float pz = ang * (float) a.optdouble(5, 0);
                    anotar(0, px, py, pz);
                    if (px != 0 || py != 0) tres_d = true;
                } else {
                    anotar(0, 0, 0, ang);   // rotate(a) es el giro plano
                }
                aplicar();
                return NONE;
            }
        });

        set("scale", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                float sx = (float) a.optdouble(2, 1);
                float sy = a.narg() >= 3 ? (float) a.optdouble(3, 1) : sx;
                vista.setScaleX(sx);
                vista.setScaleY(sy);
                return NONE;
            }
        });

        // page.layer.x / .y : en Cylinder es la posicion de la capa, aparte de
        // la transformacion. Aqui es la translacion normal de la vista.
        final Vista yo = this;
        LuaTable capa = new LuaTable() {
            @Override public void rawset(LuaValue k, LuaValue v) {
                String n = k.isstring() ? k.tojstring() : "";
                if ("x".equals(n)) { yo.capaX = (float) v.todouble(); yo.aplicar(); }
                else if ("y".equals(n)) { yo.capaY = (float) v.todouble(); yo.aplicar(); }
                super.rawset(k, v);
            }
        };
        set("layer", capa);
    }

    private void anotar(int tipo, float x, float y, float z) {
        if (nOps >= ops.length) return;
        ops[nOps][0] = tipo; ops[nOps][1] = x; ops[nOps][2] = y; ops[nOps][3] = z;
        nOps++;
    }

    private void aplicar() {
        if (!tres_d) {
            // camino barato: sin 3D no hace falta matriz
            float rz = 0, tx = 0, ty = 0;
            for (int i = 0; i < nOps; i++) {
                if (ops[i][0] == 0) rz += ops[i][3];
                else { tx += ops[i][1]; ty += ops[i][2]; }
            }
            vista.setAnimationMatrix(null);
            vista.setRotation(rz);
            vista.setTranslationX(tx + capaX);
            vista.setTranslationY(ty + capaY);
            return;
        }
        int w = vista.getWidth(), h = vista.getHeight();
        if (w <= 0) w = 1080;
        camara.save();
        // OJO: setLocation NO va en pixeles; su defecto -8 equivale a 576 px
        // porque multiplica por 72. Pasarle pixeles pone la camara a decenas
        // de miles y el efecto se ve lejisimos.
        camara.setLocation(0, 0, -(DISTANCIA * w) / 72f);
        // Core Animation concatena por la izquierda: la ultima operacion pedida
        // es la que se aplica ANTES al punto. Asi que se reproducen al reves.
        for (int i = nOps - 1; i >= 0; i--) {
            if (ops[i][0] == 0) {
                if (ops[i][1] != 0) camara.rotateX(ops[i][1]);
                if (ops[i][2] != 0) camara.rotateY(ops[i][2]);
                if (ops[i][3] != 0) camara.rotateZ(-ops[i][3]);
            } else {
                camara.translate(ops[i][1], -ops[i][2], ops[i][3]);
            }
        }
        camara.getMatrix(matriz);
        camara.restore();
        matriz.preTranslate(-w / 2f, -h / 2f);
        // la posicion de capa se suma DESPUES de la transformacion, que es
        // como la compone Core Animation: transformar y luego colocar
        matriz.postTranslate(w / 2f + capaX, h / 2f + capaY);
        vista.setRotation(0);
        vista.setTranslationX(0);
        vista.setTranslationY(0);
        vista.setAnimationMatrix(matriz);
    }

    void refrescar() { refrescar(0); }

    /**
     * Deja la vista como estaba y actualiza las medidas que lee el script.
     * anchoLogico != 0 sustituye al ancho medido: para las paginas hay que dar
     * el PASO entre ellas, no su ancho. Si no, el script del cubo calcula la
     * arista con un valor menor que la separacion real y las caras no se tocan.
     */
    void refrescar(int anchoLogico) {
        nOps = 0;
        tres_d = false;
        capaX = capaY = 0;
        vista.setAnimationMatrix(null);
        vista.setTranslationX(0);
        vista.setTranslationY(0);
        vista.setRotation(0);
        vista.setRotationX(0);
        vista.setRotationY(0);
        vista.setScaleX(1);
        vista.setScaleY(1);
        vista.setAlpha(1);

        rawset("x", LuaValue.valueOf(vista.getLeft()));
        rawset("y", LuaValue.valueOf(vista.getTop()));
        rawset("width", LuaValue.valueOf(anchoLogico > 0 ? anchoLogico : vista.getWidth()));
        rawset("height", LuaValue.valueOf(vista.getHeight()));
        rawset("alpha", LuaValue.valueOf(1));
        LuaValue capa = rawget("layer");
        if (capa instanceof LuaTable) {
            ((LuaTable) capa).rawset(LuaValue.valueOf("x"), LuaValue.valueOf(0));
            ((LuaTable) capa).rawset(LuaValue.valueOf("y"), LuaValue.valueOf(0));
        }
    }

    @Override
    public void rawset(LuaValue clave, LuaValue valor) {
        if (clave.isstring() && "alpha".equals(clave.tojstring())) {
            vista.setAlpha((float) valor.todouble());
        }
        super.rawset(clave, valor);
    }
}
