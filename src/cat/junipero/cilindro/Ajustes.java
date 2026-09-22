package cat.junipero.cilindro;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/**
 * Deja leer el efecto elegido desde FUERA de esta app.
 * Hace falta porque el modulo corre dentro del proceso del launcher y no
 * puede leer las preferencias de otra aplicacion.
 */
public class Ajustes extends ContentProvider {

    public static final String AUTORIDAD = "cat.junipero.cilindro.ajustes";
    public static final Uri URI = Uri.parse("content://" + AUTORIDAD + "/efecto");
    private static final String FICHERO = "cilindro";
    private static final String CLAVE = "efecto";
    public static final String POR_DEFECTO = "Ant Lines (Horizontal)";

    public static String leer(Context c) {
        SharedPreferences p = c.getSharedPreferences(FICHERO, Context.MODE_PRIVATE);
        return p.getString(CLAVE, POR_DEFECTO);
    }

    public static void guardar(Context c, String nombre) {
        c.getSharedPreferences(FICHERO, Context.MODE_PRIVATE)
                .edit().putString(CLAVE, nombre).apply();
    }

    @Override public boolean onCreate() { return true; }

    @Override
    public Cursor query(Uri uri, String[] p, String s, String[] a, String o) {
        MatrixCursor c = new MatrixCursor(new String[]{"nombre"});
        c.addRow(new Object[]{ leer(getContext()) });
        return c;
    }

    @Override public String getType(Uri uri) { return "vnd.android.cursor.item/efecto"; }
    @Override public Uri insert(Uri uri, ContentValues v) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
