package fr.outil.nettoyage;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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

/** Un bouton unique. Aucune exécution sans action de l'utilisateur. */
public final class MainActivity extends Activity {
  private static final int REQUEST_SHIZUKU = 3102;
  private static final int DARK = Color.rgb(27,54,53);
  private static final int TEAL = Color.rgb(32,119,111);
  private static final int MUTED = Color.rgb(100,117,114);
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private TextView status, detail, total, result;
  private Button button;
  private boolean busy, pendingCleanup, bound;
  private ICleanerService cleaner;
  private Shizuku.UserServiceArgs serviceArgs;

  private final Shizuku.OnBinderReceivedListener received = () -> runOnUiThread(this::refresh);
  private final Shizuku.OnBinderDeadListener dead = () -> runOnUiThread(() -> {
    cleaner = null;
    bound = false;
    showFailure("Shizuku s'est arrêté. Relance Shizuku pour continuer.");
  });
  private final Shizuku.OnRequestPermissionResultListener permission = (code, grant) -> {
    if (code != REQUEST_SHIZUKU) return;
    runOnUiThread(() -> {
      if (grant == PackageManager.PERMISSION_GRANTED && pendingCleanup) bindAndClean();
      else showFailure("Autorisation Shizuku refusée. Aucun arrêt effectué.");
    });
  };
  private final ServiceConnection connection = new ServiceConnection() {
    @Override public void onServiceConnected(ComponentName name, IBinder binder) {
      runOnUiThread(() -> {
        cleaner = ICleanerService.Stub.asInterface(binder);
        bound = true;
        if (pendingCleanup) runCleanup();
      });
    }
    @Override public void onServiceDisconnected(ComponentName name) {
      runOnUiThread(() -> {
        cleaner = null;
        bound = false;
        if (busy) showFailure("Connexion à Shizuku interrompue.");
      });
    }
  };

  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    createUi();
    Shizuku.addBinderReceivedListenerSticky(received);
    Shizuku.addBinderDeadListener(dead);
    Shizuku.addRequestPermissionResultListener(permission);
    refresh();
  }

  private void createUi() {
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setBackgroundColor(Color.rgb(244,247,246));
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(dp(22),dp(34),dp(22),dp(30));
    scroll.addView(root);
    TextView eyebrow = label("ENTRETIEN ANDROID",12,TEAL,true);
    root.addView(eyebrow);
    root.addView(label("Nettoyage sûr",32,DARK,true), margin(0,10,0,8));
    root.addView(label("Arrêter les applications non essentielles détectées en arrière-plan.",15,MUTED,false));
    LinearLayout card = new LinearLayout(this);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setPadding(dp(22),dp(26),dp(22),dp(25));
    card.setBackground(shape(Color.WHITE,26));
    root.addView(card,margin(0,26,0,0));
    status = label("Vérification de Shizuku…",14,TEAL,true);
    card.addView(status);
    total = label("—",70,DARK,true);
    total.setGravity(Gravity.CENTER);
    card.addView(total,margin(0,20,0,0));
    TextView countLabel = label("arrêts vérifiés au dernier nettoyage",13,MUTED,false);
    countLabel.setGravity(Gravity.CENTER);
    card.addView(countLabel);
    button = new Button(this);
    button.setAllCaps(false);
    button.setText("Nettoyer maintenant");
    button.setTextSize(16);
    button.setTextColor(Color.WHITE);
    button.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
    button.setBackground(shape(TEAL,17));
    button.setMinHeight(dp(64));
    button.setOnClickListener(v -> action());
    card.addView(button,margin(0,26,0,0));
    detail = label("Aucune action sans ton accord.",13,MUTED,false);
    detail.setGravity(Gravity.CENTER);
    card.addView(detail,margin(0,15,0,0));
    root.addView(label("Dernier bilan",18,DARK,true),margin(0,28,0,12));
    result = label("Aucune analyse effectuée.",14,MUTED,false);
    result.setPadding(dp(17),dp(19),dp(17),dp(19));
    result.setBackground(shape(Color.WHITE,17));
    root.addView(result);
    root.addView(label("Protection stricte des applications système et sensibles. Un arrêt forcé peut interrompre les notifications, tâches et synchronisations d'une application jusqu'à sa réouverture. Aucune amélioration de batterie ou de RAM n'est garantie.",12,MUTED,false),margin(0,25,0,0));
    setContentView(scroll);
  }

  private void refresh() {
    if (busy) return;
    try {
      if (!Shizuku.pingBinder()) {
        status.setText("● Shizuku inactif");
        button.setText("Ouvrir Shizuku");
        detail.setText("Démarre Shizuku avant de nettoyer.");
      } else if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
        status.setText("● Autorisation nécessaire");
        button.setText("Autoriser le nettoyage");
        detail.setText("Une autorisation Shizuku est nécessaire.");
      } else {
        status.setText("● Prêt à nettoyer");
        button.setText("Nettoyer maintenant");
        detail.setText("Un appui. Aucune configuration à choisir.");
      }
    } catch (RuntimeException ignored) {
      status.setText("● Shizuku indisponible");
      button.setText("Ouvrir Shizuku");
    }
  }

  private void action() {
    if (busy) return;
    try {
      if (!Shizuku.pingBinder()) {
        Intent launch = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
        if (launch != null) startActivity(launch);
        else startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")));
        return;
      }
      pendingCleanup = true;
      if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
        Shizuku.requestPermission(REQUEST_SHIZUKU);
        return;
      }
      bindAndClean();
    } catch (RuntimeException ex) {
      showFailure("Shizuku inaccessible. Vérifie son état.");
    }
  }

  private void bindAndClean() {
    if (cleaner != null) { runCleanup(); return; }
    busy = true;
    button.setEnabled(false);
    status.setText("● Connexion sécurisée…");
    serviceArgs = new Shizuku.UserServiceArgs(new ComponentName(this,CleanerService.class))
        .processNameSuffix("cleaner").daemon(false).version(1).debuggable(BuildConfig.DEBUG);
    try {
      Shizuku.bindUserService(serviceArgs,connection);
    } catch (RuntimeException ex) {
      showFailure("Impossible de connecter le service Shizuku.");
    }
  }

  private void runCleanup() {
    if (cleaner == null) return;
    busy = true;
    button.setEnabled(false);
    status.setText("● Analyse en cours…");
    detail.setText("Identification des applications et protections.");
    executor.execute(() -> {
      try {
        ICleanerService remote = cleaner;
        if (remote == null) throw new RemoteException("Service indisponible");
        Set<String> running = ProcessParser.runningPackages(remote.getProcessSnapshot());
        // Refus de nettoyer en cas d'analyse absente ou non reconnue.
        if (running.isEmpty()) {
          runOnUiThread(() -> showFailure("Impossible de lire les processus. Aucun arrêt."));
          return;
        }
        PackageManager pm = getPackageManager();
        SafetyPolicy policy = new SafetyPolicy(this);
        List<PackageInfo> apps = pm.getInstalledPackages(
            PackageManager.GET_PERMISSIONS | PackageManager.GET_ACTIVITIES);
        List<PackageInfo> eligible = new ArrayList<>();
        int protectedCount = 0;
        for (PackageInfo app : apps) {
          if (!running.contains(app.packageName)) continue;
          if (policy.canStop(app)) eligible.add(app);
          else protectedCount++;
        }
        final int ignored = protectedCount;
        if (eligible.isEmpty()) {
          runOnUiThread(() -> display(0,ignored,Collections.emptyList(),0,0));
          return;
        }
        Collections.sort(eligible,(a,b) -> a.packageName.compareTo(b.packageName));
        // Nombre maximal conservateur par passage.
        int count = Math.min(eligible.size(),100);
        String[] targets = new String[count];
        Map<String,String> labels = new HashMap<>();
        for (int i = 0; i < count; i++) {
          PackageInfo p = eligible.get(i);
          targets[i] = p.packageName;
          labels.put(p.packageName,String.valueOf(pm.getApplicationLabel(p.applicationInfo)));
        }
        runOnUiThread(() -> status.setText("● Arrêt des applications éligibles…"));
        String response = remote.stopPackages(targets);
        Set<String> reported = new HashSet<>();
        Set<String> accepted = new HashSet<>();
        int errors = 0;
        for (String line : response.split("\\R")) {
          String[] parts = line.split("\\t",2);
          if (parts.length != 2 || !labels.containsKey(parts[1]) || !reported.add(parts[1])) continue;
          if ("OK".equals(parts[0])) accepted.add(parts[1]);
          else errors++;
        }
        errors += Math.max(0,count-reported.size());
        Set<String> after = ProcessParser.runningPackages(remote.getProcessSnapshot());
        List<String> verified = new ArrayList<>();
        int uncertain = 0;
        for (String pkg : accepted) {
          if (!after.isEmpty() && !after.contains(pkg)) verified.add(labels.get(pkg));
          else uncertain++;
        }
        final int finalErrors = errors, finalUncertain = uncertain;
        runOnUiThread(() -> display(verified.size(),ignored,verified,finalErrors,finalUncertain));
      } catch (Exception e) {
        runOnUiThread(() -> showFailure("Analyse interrompue : " + e.getClass().getSimpleName()));
      }
    });
  }

  private void display(int stopped,int protectedCount,List<String> names,int errors,int unconfirmed) {
    busy = false;
    pendingCleanup = false;
    button.setEnabled(true);
    status.setText("● Nettoyage terminé");
    button.setText("Nettoyer maintenant");
    total.setText(String.valueOf(stopped));
    detail.setText(stopped == 0 ? "Aucun arrêt confirmé." : stopped + " application(s) arrêtée(s) et vérifiée(s).");
    StringBuilder text = new StringBuilder();
    text.append(stopped).append(" arrêt(s) vérifiés\n");
    text.append(protectedCount).append(" application(s) préservée(s) par sécurité");
    if (errors != 0) text.append("\n").append(errors).append(" échec(s)");
    if (unconfirmed != 0) text.append("\n").append(unconfirmed).append(" arrêt(s) non confirmés");
    if (!names.isEmpty()) {
      Collections.sort(names);
      text.append("\n\nApplications concernées :");
      for (String name : names) text.append("\n• ").append(name);
    }
    result.setText(text.toString());
  }

  private void showFailure(String message) {
    busy = false;
    pendingCleanup = false;
    if (button != null) button.setEnabled(true);
    if (detail != null) detail.setText(message);
    if (result != null) result.setText(message);
    refresh();
  }

  @Override protected void onDestroy() {
    Shizuku.removeBinderReceivedListener(received);
    Shizuku.removeBinderDeadListener(dead);
    Shizuku.removeRequestPermissionResultListener(permission);
    executor.shutdownNow();
    if (bound && serviceArgs != null) {
      try { Shizuku.unbindUserService(serviceArgs,connection,true); }
      catch (RuntimeException ignored) {}
    }
    super.onDestroy();
  }

  private TextView label(String content,int size,int color,boolean bold) {
    TextView view = new TextView(this);
    view.setText(content);
    view.setTextSize(size);
    view.setTextColor(color);
    if (bold) view.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
    view.setLineSpacing(dp(3),1f);
    return view;
  }

  private GradientDrawable shape(int color,int radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(radius));
    return d;
  }

  private LinearLayout.LayoutParams margin(int left,int top,int right,int bottom) {
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2);
    p.setMargins(dp(left),dp(top),dp(right),dp(bottom));
    return p;
  }

  private int dp(float size) {
    return (int)(size*getResources().getDisplayMetrics().density+0.5f);
  }
}
