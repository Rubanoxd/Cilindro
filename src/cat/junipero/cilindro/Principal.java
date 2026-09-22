package cat.junipero.cilindro;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Lista de efectos, separados en probados y sin probar. */
public class Principal extends Activity {

    private static final String CAB_PROBADOS = "✓  PROBADOS";
    private static final String CAB_RESTO = "○  SIN PROBAR";

    private final List<String> filas = new ArrayList<String>();
    private final Set<Integer> cabeceras = new HashSet<Integer>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        Set<String> probados = new HashSet<String>();
        try {
            BufferedReader r = new BufferedReader(
                    new InputStreamReader(getAssets().open("probados.txt"), "UTF-8"));
            String l;
            while ((l = r.readLine()) != null) if (l.trim().length() > 0) probados.add(l.trim());
            r.close();
        } catch (Exception ignorado) {
        }

        List<String> si = new ArrayList<String>(), no = new ArrayList<String>();
        try {
            String[] e = getAssets().list("efectos");
            if (e != null) {
                Arrays.sort(e);
                for (String x : e) (probados.contains(x) ? si : no).add(x);
            }
        } catch (Exception ignorado) {
        }

        cabeceras.add(filas.size()); filas.add(CAB_PROBADOS);
        filas.addAll(si);
        cabeceras.add(filas.size()); filas.add(CAB_RESTO);
        filas.addAll(no);

        int pad = (int) (16 * getResources().getDisplayMetrics().density);

        LinearLayout raiz = new LinearLayout(this);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setBackgroundColor(Color.parseColor("#0E0E12"));
        raiz.setPadding(pad, pad * 2, pad, 0);

        TextView titulo = new TextView(this);
        titulo.setText("Cilindro");
        titulo.setTextColor(Color.WHITE);
        titulo.setTextSize(30);
        raiz.addView(titulo, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        final TextView actual = new TextView(this);
        actual.setTextColor(Color.parseColor("#9AA0B4"));
        actual.setTextSize(14);
        actual.setPadding(0, pad / 3, 0, pad / 2);
        actual.setText("Ahora: " + Ajustes.leer(this) + "   ·   "
                + si.size() + " probados, " + no.size() + " sin probar");
        raiz.addView(actual, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        ListView lista = new ListView(this);
        lista.setDivider(null);
        lista.setAdapter(new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, android.R.id.text1, filas) {
            @Override public boolean isEnabled(int pos) { return !cabeceras.contains(pos); }
            @Override public boolean areAllItemsEnabled() { return false; }
            @Override public View getView(int pos, View v, ViewGroup p) {
                View x = super.getView(pos, v, p);
                TextView t = (TextView) x.findViewById(android.R.id.text1);
                if (cabeceras.contains(pos)) {
                    t.setTextColor(Color.parseColor("#7C89FF"));
                    t.setTextSize(13);
                    t.setTypeface(Typeface.DEFAULT_BOLD);
                } else {
                    t.setTextColor(Color.WHITE);
                    t.setTextSize(17);
                    t.setTypeface(Typeface.DEFAULT);
                }
                return x;
            }
        });
        lista.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                if (cabeceras.contains(pos)) return;
                String nombre = filas.get(pos);
                Ajustes.guardar(Principal.this, nombre);
                actual.setText("Ahora: " + nombre + "   ·   vuelve al escritorio para verlo");
                Toast.makeText(Principal.this, nombre, Toast.LENGTH_SHORT).show();
            }
        });
        raiz.addView(lista, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(raiz);
    }
}
