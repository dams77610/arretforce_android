package fr.outil.nettoyage;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

/**
 * V0.2 : inventaire visible, choix manuel uniquement, protection stricte.
 * Un processus présent ne signifie pas qu'il consomme inutilement la RAM/batterie.
 */
public final class MainActivity extends Activity {
  private static final int SHIZUKU_PERMISSION = 3102;
  private static final int DARK = Color.rgb(25, 54, 53);
  private static final int TEAL = Color.rgb(0, 123, 114);
  private static final int MUTED = Color.rgb(106, 119, 117);
  private static final int BG = Color.rgb(245, 248, 247);

  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final List<Entry> entries = new ArrayList<>();
  private final List<Entry> filtered = new ArrayList<>();
  private final Set<String> selected = new HashSet<>();
  private final Set<String> acceptedCautions = new HashSet<>();

  private TextView status, overview, empty, chosen, report;
  private Button scanButton, stopButton, runningTab, allTab, protectedTab;
  private EditText search;
  private ListView list;
  private EntryAdapter adapter;
  private ICleanerService cleaner;
  private Shizuku.UserServiceArgs userArgs;
  private boolean busy = false, binding = false, bound = false, snapshotValid = false, loaded = false;
  private int tab = 0; // 0 processus présents, 1 toutes installées, 2 protégées
  private String lastStopReport = "";

  private final Shizuku.OnBinderReceivedListener received = () ->
      runOnUiThread(() -> { if (!busy && !binding) requestScan(false); });
  private final Shizuku.OnBinderDeadListener dead = () -> runOnUiThread(() -> {
    cleaner = null;
    bound = false;
    binding = false;
    snapshotValid = false;
    report.setText("Shizuku s'est arrêté. La sélection est conservée, mais les arrêts sont désactivés.");
    refreshUi("Shizuku arrêté");
  });
  private final Shizuku.OnRequestPermissionResultListener permission = (requestCode, grant) -> {
    if (requestCode != SHIZUKU_PERMISSION) return;
    runOnUiThread(() -> {
      if (grant == PackageManager.PERMISSION_GRANTED) requestScan(false);
      else {
        report.setText("Autorisation Shizuku refusée. Aucune application ne sera arrêtée.");
        loadEntries(null);
      }
    });
  };
  private final ServiceConnection connection = new ServiceConnection() {
    @Override public void onServiceConnected(ComponentName name, IBinder binder) {
      runOnUiThread(() -> {
        cleaner = ICleanerService.Stub.asInterface(binder);
        binding = false;
        bound = true;
        loadEntries(cleaner);
      });
    }
    @Override public void onServiceDisconnected(ComponentName name) {
      runOnUiThread(() -> {
        cleaner = null;
        binding = false;
        bound = false;
        snapshotValid = false;
        selected.clear();
        report.setText("Connexion à Shizuku interrompue.");
        refreshUi("Connexion interrompue");
      });
    }
  };

  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    createUi();
    Shizuku.addBinderReceivedListenerSticky(received);
    Shizuku.addBinderDeadListener(dead);
    Shizuku.addRequestPermissionResultListener(permission);
    if (!busy && !binding && !loaded) requestScan(false);
  }

  @Override protected void onResume() {
    super.onResume();
    if (loaded && !busy && !binding) requestScan(false);
  }

  private void createUi() {
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(BG);
    root.setPadding(dp(17), dp(19), dp(17), dp(12));
    root.setOnApplyWindowInsetsListener((view, insets) -> {
      android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
      view.setPadding(dp(17), dp(19) + bars.top, dp(17), dp(12) + bars.bottom);
      return insets;
    });
    setContentView(root);

    TextView eyebrow = text("ENTRETIEN ANDROID  ·  V0.2", 12, TEAL, true);
    root.addView(eyebrow);
    root.addView(text("Nettoyage sûr", 29, DARK, true), margins(0, 2, 0, 0));

    LinearLayout top = new LinearLayout(this);
    top.setGravity(Gravity.CENTER_VERTICAL);
    root.addView(top, margins(0, 12, 0, 5));
    status = text("Préparation de l'inventaire…", 13, TEAL, true);
    top.addView(status, new LinearLayout.LayoutParams(0, dp(48), 1));
    status.setGravity(Gravity.CENTER_VERTICAL);

    scanButton = new Button(this);
    scanButton.setText("Actualiser");
    scanButton.setTextSize(13);
    scanButton.setAllCaps(false);
    scanButton.setTextColor(TEAL);
    scanButton.setBackground(background(Color.WHITE, 13));
    scanButton.setOnClickListener(v -> requestScan(true));
    top.addView(scanButton, new LinearLayout.LayoutParams(dp(106), dp(48)));

    overview = text("Chargement…", 13, MUTED, false);
    root.addView(overview, margins(0, 0, 0, 11));

    LinearLayout tabs = new LinearLayout(this);
    tabs.setOrientation(LinearLayout.HORIZONTAL);
    root.addView(tabs, margins(0, 0, 0, 9));
    runningTab = tabButton(tabs, "En cours", 0);
    allTab = tabButton(tabs, "Toutes", 1);
    protectedTab = tabButton(tabs, "Protégées", 2);

    search = new EditText(this);
    search.setSingleLine(true);
    search.setTextSize(15);
    search.setTextColor(DARK);
    search.setHintTextColor(MUTED);
    search.setHint("Rechercher une application");
    search.setPadding(dp(13), 0, dp(13), 0);
    search.setBackground(background(Color.WHITE, 12));
    root.addView(search, new LinearLayout.LayoutParams(-1, dp(48)));
    search.addTextChangedListener(new TextWatcher() {
      @Override public void beforeTextChanged(CharSequence s, int a, int count, int after) {}
      @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
        filterEntries();
      }
      @Override public void afterTextChanged(Editable s) {}
    });

    list = new ListView(this);
    list.setDivider(new android.graphics.drawable.ColorDrawable(BG));
    list.setDividerHeight(dp(5));
    list.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
    list.setCacheColorHint(Color.TRANSPARENT);
    adapter = new EntryAdapter();
    list.setAdapter(adapter);
    root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));

    empty = text("Aucune application dans cette catégorie.\nConsulte « Toutes ».", 14, MUTED, false);
    empty.setGravity(Gravity.CENTER);
    root.addView(empty, new LinearLayout.LayoutParams(-1, 0, 1));
    list.setEmptyView(empty);

    chosen = text("0 application sélectionnée", 14, DARK, true);
    root.addView(chosen, margins(0, 10, 0, 7));

    stopButton = new Button(this);
    stopButton.setText("Sélectionner des applications");
    stopButton.setAllCaps(false);
    stopButton.setTextSize(16);
    stopButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    stopButton.setTextColor(Color.WHITE);
    stopButton.setBackground(background(TEAL, 16));
    stopButton.setOnClickListener(v -> stopSelected());
    root.addView(stopButton, new LinearLayout.LayoutParams(-1, dp(58)));

    report = text("Aucun arrêt automatique. Les cases sont décochées par défaut.", 12, MUTED, false);
    root.addView(report, margins(0, 10, 0, 4));

    refreshUi("Préparation…");
  }

  private Button tabButton(LinearLayout parent, String title, int value) {
    Button b = new Button(this);
    b.setAllCaps(false);
    b.setTextSize(13);
    b.setText(title);
    b.setMinHeight(0);
    b.setMinimumHeight(0);
    b.setPadding(0, 0, 0, 0);
    b.setOnClickListener(v -> {
      tab = value;
      filterEntries();
      paintTabs();
    });
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(43), 1);
    p.setMargins(0, 0, dp(value == 2 ? 0 : 5), 0);
    parent.addView(b, p);
    return b;
  }

  private void paintTabs() {
    Button[] tabs = {runningTab, allTab, protectedTab};
    for (int i = 0; i < tabs.length; i++) {
      tabs[i].setBackground(background(i == tab ? TEAL : Color.WHITE, 12));
      tabs[i].setTextColor(i == tab ? Color.WHITE : DARK);
    }
  }

  private void requestScan(boolean explicit) {
    if (busy) return;
    try {
      if (!Shizuku.pingBinder()) {
        cleaner = null;
        snapshotValid = false;
        if (explicit) openShizuku();
        loadEntries(null);
        return;
      }
      if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
        if (explicit) Shizuku.requestPermission(SHIZUKU_PERMISSION);
        else loadEntries(null);
        return;
      }
      if (cleaner != null) {
        loadEntries(cleaner);
      } else if (!binding) {
        binding = true;
        status.setText("Connexion à Shizuku…");
        userArgs = new Shizuku.UserServiceArgs(new ComponentName(this, CleanerService.class))
            .processNameSuffix("cleaner").daemon(false).version(2).debuggable(BuildConfig.DEBUG);
        Shizuku.bindUserService(userArgs, connection);
      }
    } catch (Exception e) {
      binding = false;
      report.setText("Connexion Shizuku impossible : " + e.getClass().getSimpleName());
      loadEntries(null);
    }
  }

  private void openShizuku() {
    Intent launch = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
    if (launch != null) startActivity(launch);
  }

  private boolean shizukuReady() {
    try {
      return cleaner != null && Shizuku.pingBinder() &&
          Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
    } catch (Exception e) { return false; }
  }

  private void loadEntries(ICleanerService service) {
    if (busy) return;
    busy = true;
    scanButton.setEnabled(false);
    snapshotValid = false;
    // Sélection maintenue lors de l'actualisation.
    refreshUi("Analyse des applications…");
    worker.execute(() -> {
      String snapshot = null;
      if (service != null) {
        try { snapshot = service.getProcessSnapshot(); }
        catch (Exception ignored) {}
      }
      final String snapshotText = snapshot;
      final boolean valid = snapshotText != null && !snapshotText.trim().isEmpty() && !snapshotText.startsWith("#ERROR:");
      final Set<String> running = valid ?
          ProcessParser.runningPackages(snapshotText) : Collections.emptySet();
      final ArrayList<Entry> inventory = new ArrayList<>();
      try {
        PackageManager pm = getPackageManager();
        SafetyPolicy policy = new SafetyPolicy(this);
        for (PackageInfo pkg : pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)) {
          if (pkg.applicationInfo == null) continue;
          String title = String.valueOf(pm.getApplicationLabel(pkg.applicationInfo));
          boolean hard = policy.isHardProtected(pkg);
          boolean caution = !hard && policy.isCaution(pkg);
          boolean system = (pkg.applicationInfo.flags &
              (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
          inventory.add(new Entry(pkg.packageName, title, system, hard, caution,
              running.contains(pkg.packageName)));
        }
        Collections.sort(inventory, (a, b) -> a.title.compareToIgnoreCase(b.title));
        final boolean canInspect = valid && !running.isEmpty();
        runOnUiThread(() -> {
          entries.clear();
          entries.addAll(inventory);
          Set<String> stillAllowed = new HashSet<>();
          for (Entry item : inventory)
            if (!item.hardProtected && (!item.caution || acceptedCautions.contains(item.pkg)))
              stillAllowed.add(item.pkg);
          selected.retainAll(stillAllowed);
          acceptedCautions.retainAll(stillAllowed);
          loaded = true;
          snapshotValid = canInspect;
          busy = false;
          scanButton.setEnabled(true);
          if (!canInspect) tab = 1; // Toujours montrer les applications, même sans Shizuku.
          filterEntries();
          refreshUi(canInspect ? "Inventaire actualisé" :
              (shizukuReady() ? "Shizuku connecté · activité inconnue"
                              : "Shizuku arrêté ou non autorisé"));
          if (!canInspect) {
            if (!shizukuReady()) report.setText("Shizuku non connecté ou non autorisé. Vérifie Shizuku puis Actualiser. Aucun arrêt possible.");
            else report.setText("Shizuku connecté mais activité non détectée. Sélection manuelle des applications tierces possible." +
                (snapshotText != null && snapshotText.startsWith("#ERROR:") ? " Diagnostic : " + snapshotText : ""));
          } else report.setText(lastStopReport.isEmpty()
              ? "Choisis les applications souhaitées. Aucun arrêt automatique."
              : lastStopReport);
        });
      } catch (Exception e) {
        runOnUiThread(() -> {
          busy = false;
          scanButton.setEnabled(true);
          refreshUi("Inventaire impossible");
          report.setText("Erreur pendant l'inventaire : " + e.getClass().getSimpleName());
        });
      }
    });
  }

  private void filterEntries() {
    if (adapter == null) return;
    String q = search.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
    filtered.clear();
    for (Entry item : entries) {
      if (tab == 0 && !item.running) continue;
      if (tab == 2 && !item.hardProtected && !item.caution) continue;
      if (!q.isEmpty() &&
          !item.title.toLowerCase(java.util.Locale.ROOT).contains(q) &&
          !item.pkg.toLowerCase(java.util.Locale.ROOT).contains(q)) continue;
      filtered.add(item);
    }
    adapter.notifyDataSetChanged();
    paintTabs();
    updateSelection();
  }

  private void toggle(Entry item) {
    if (busy || item.hardProtected) return;
    if (selected.remove(item.pkg)) {
      acceptedCautions.remove(item.pkg);
      adapter.notifyDataSetChanged();
      updateSelection();
      return;
    }
    if (item.caution) {
      new AlertDialog.Builder(this)
          .setTitle("Protection conseillée")
          .setMessage(item.title + " peut assurer des notifications, des alarmes ou un service utile." +
              "\\n\\nSon arrêt forcé peut perturber ces fonctions jusqu'à sa réouverture." +
              "\\n\\nVeux-tu vraiment la sélectionner ?")
          .setNegativeButton("Annuler", (d, which) -> adapter.notifyDataSetChanged())
          .setPositiveButton("Choisir quand même", (d, which) -> {
            if (!busy) {
              acceptedCautions.add(item.pkg);
              selected.add(item.pkg);
              adapter.notifyDataSetChanged();
              updateSelection();
            }
          }).show();
      return;
    }
    selected.add(item.pkg);
    adapter.notifyDataSetChanged();
    updateSelection();
  }

  private void updateSelection() {
    int n = selected.size();
    chosen.setText(n + (n > 1 ? " applications sélectionnées" : " application sélectionnée"));
    stopButton.setText(n == 0 ? "Sélectionne les applications à arrêter" :
        (shizukuReady() ? "Forcer l'arrêt (" + n + ")" : "Activer Shizuku pour arrêter (" + n + ")"));
    stopButton.setEnabled(!busy && n > 0);
    stopButton.setAlpha(stopButton.isEnabled() ? 1f : .5f);
  }

  private void refreshUi(String message) {
    if (status == null) return;
    status.setText(message);
    int running = 0, selectable = 0, protectedCount = 0;
    for (Entry e : entries) {
      if (e.running) running++;
      if (!e.hardProtected) selectable++;
      if (e.hardProtected) protectedCount++;
    }
    overview.setText(entries.size() + " installées  ·  " +
        (snapshotValid ? running + " processus présents" : "activité inconnue") +
        "  ·  " + (shizukuReady() ? selectable + " choix possibles" : "Shizuku non connecté"));
    updateSelection();
  }

  private void stopSelected() {
    if (busy || selected.isEmpty()) return;
    if (!shizukuReady()) {
      report.setText("Shizuku doit être actif et autorisé avant l'arrêt. Ta sélection sera conservée.");
      requestScan(true);
      return;
    }
    final Set<String> requested = new HashSet<>(selected);
    final Set<String> confirmed = new HashSet<>(acceptedCautions);
    busy = true;
    scanButton.setEnabled(false);
    refreshUi("Vérification avant arrêt…");
    worker.execute(() -> {
      try {
        ICleanerService service = cleaner;
        if (service == null) throw new IllegalStateException("Shizuku absent");
        String beforeRaw = service.getProcessSnapshot();
        Set<String> before = ProcessParser.runningPackages(beforeRaw);
        boolean observed = beforeRaw != null && !beforeRaw.startsWith("#ERROR:") && !before.isEmpty();
        SafetyPolicy safety = new SafetyPolicy(this);
        PackageManager pm = getPackageManager();
        ArrayList<String> targets = new ArrayList<>();
        Map<String, String> names = new HashMap<>();
        for (String name : requested) {
          try {
            PackageInfo pkg = pm.getPackageInfo(name, PackageManager.GET_PERMISSIONS);
            if (safety.canStop(pkg, confirmed.contains(name))) {
              targets.add(name);
              names.put(name, String.valueOf(pm.getApplicationLabel(pkg.applicationInfo)));
            }
          } catch (PackageManager.NameNotFoundException ignored) {}
        }
        Collections.sort(targets);
        if (targets.isEmpty()) {
          runOnUiThread(() -> {
            busy = false;
            scanButton.setEnabled(true);
            selected.clear();
            acceptedCautions.clear();
            refreshUi("Aucune application encore éligible");
            report.setText("Aucun arrêt : les applications sélectionnées sont fermées ou protégées.");
            requestScan(false);
          });
          return;
        }
        String response = service.stopPackages(targets.toArray(new String[0]));
        Set<String> accepted = new HashSet<>();
        for (String line : response.split("\\R")) {
          String[] parts = line.split("\\t", 2);
          if (parts.length == 2 && "OK".equals(parts[0]) && names.containsKey(parts[1]))
            accepted.add(parts[1]);
        }
        String afterRaw = service.getProcessSnapshot();
        Set<String> after = ProcessParser.runningPackages(afterRaw);
        boolean canVerify = afterRaw != null && !afterRaw.trim().isEmpty() && !after.isEmpty();
        List<String> stopped = new ArrayList<>();
        for (String pkg : accepted)
          if (observed && before.contains(pkg) && canVerify && !after.contains(pkg))
            stopped.add(names.get(pkg));
        Collections.sort(stopped);
        final int acceptedCount = accepted.size(), verified = stopped.size(), attempted = targets.size();
        runOnUiThread(() -> {
          busy = false;
          selected.clear();
          acceptedCautions.clear();
          scanButton.setEnabled(true);
          lastStopReport = attempted + " arrêt(s) demandé(s), " + acceptedCount +
              " commande(s) acceptée(s), " + verified + " arrêt(s) vérifié(s)." +
              (stopped.isEmpty() ? "" : "\n" + android.text.TextUtils.join(", ", stopped)) +
              "\nNote : notifications et tâches peuvent être interrompues jusqu'à la réouverture.";
          report.setText(lastStopReport);
          requestScan(false);
        });
      } catch (Exception error) {
        runOnUiThread(() -> {
          busy = false;
          scanButton.setEnabled(true);
          refreshUi("Arrêt interrompu");
          report.setText("Aucun résultat confirmé : " + error.getClass().getSimpleName());
        });
      }
    });
  }

  private final class EntryAdapter extends BaseAdapter {
    @Override public int getCount() { return filtered.size(); }
    @Override public Entry getItem(int position) { return filtered.get(position); }
    @Override public long getItemId(int position) { return position; }

    @Override public View getView(int position, View oldView, ViewGroup parent) {
      Entry item = getItem(position);
      LinearLayout row = new LinearLayout(MainActivity.this);
      row.setPadding(dp(10), dp(10), dp(9), dp(10));
      row.setGravity(Gravity.CENTER_VERTICAL);
      row.setBackground(background(Color.WHITE, 12));

      ImageView icon = new ImageView(MainActivity.this);
      try {
        Drawable drawable = getPackageManager().getApplicationIcon(item.pkg);
        icon.setImageDrawable(drawable);
      } catch (Exception ignored) {
        icon.setImageResource(android.R.drawable.sym_def_app_icon);
      }
      row.addView(icon, new LinearLayout.LayoutParams(dp(39), dp(39)));

      LinearLayout labels = new LinearLayout(MainActivity.this);
      labels.setOrientation(LinearLayout.VERTICAL);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
      lp.setMargins(dp(12), 0, dp(6), 0);
      row.addView(labels, lp);
      TextView name = text(item.title, 14, DARK, true);
      name.setMaxLines(1);
      name.setEllipsize(android.text.TextUtils.TruncateAt.END);
      labels.addView(name);
      String state;
      if (item.hardProtected)
        state = item.system ? "Système · arrêt désactivé" : "Fonction essentielle · arrêt désactivé";
      else if (item.caution)
        state = selected.contains(item.pkg) ? "Attention · sélection confirmée" :
            "Attention · protection conseillée, choix possible";
      else if (!snapshotValid)
        state = "Activité inconnue · choix manuel possible";
      else
        state = item.running ? "Processus détecté · sélectionnable" : "Pas de processus détecté";
      labels.addView(text(state, 11, item.hardProtected ? MUTED : TEAL, false));

      if (item.hardProtected) {
        TextView lock = text("🔒", 19, MUTED, false);
        row.addView(lock);
      } else {
        CheckBox checkbox = new CheckBox(MainActivity.this);
        checkbox.setButtonTintList(ColorStateList.valueOf(TEAL));
        checkbox.setChecked(selected.contains(item.pkg));
        checkbox.setEnabled(!busy);
        checkbox.setOnClickListener(v -> toggle(item));
        row.addView(checkbox, new LinearLayout.LayoutParams(dp(44), dp(48)));
      }
      row.setOnClickListener(v -> toggle(item));
      return row;
    }
  }

  private static final class Entry {
    final String pkg, title;
    final boolean system, hardProtected, caution, running;
    Entry(String pkg, String title, boolean system, boolean hardProtected,
          boolean caution, boolean running) {
      this.pkg = pkg;
      this.title = title;
      this.system = system;
      this.hardProtected = hardProtected;
      this.caution = caution;
      this.running = running;
    }
  }

  @Override protected void onDestroy() {
    Shizuku.removeBinderReceivedListener(received);
    Shizuku.removeBinderDeadListener(dead);
    Shizuku.removeRequestPermissionResultListener(permission);
    worker.shutdownNow();
    if (bound && userArgs != null) {
      try { Shizuku.unbindUserService(userArgs, connection, true); }
      catch (RuntimeException ignored) {}
    }
    super.onDestroy();
  }

  private TextView text(String value, int sp, int color, boolean heavy) {
    TextView view = new TextView(this);
    view.setText(value);
    view.setTextSize(sp);
    view.setTextColor(color);
    if (heavy) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    return view;
  }

  private GradientDrawable background(int color, int radius) {
    GradientDrawable drawable = new GradientDrawable();
    drawable.setColor(color);
    drawable.setCornerRadius(dp(radius));
    return drawable;
  }

  private LinearLayout.LayoutParams margins(int left, int top, int right, int bottom) {
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.setMargins(dp(left), dp(top), dp(right), dp(bottom));
    return p;
  }

  private int dp(float n) {
    return (int) (n * getResources().getDisplayMetrics().density + .5f);
  }
}
