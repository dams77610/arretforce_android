package fr.outil.nettoyage;

import android.app.admin.DevicePolicyManager;
import android.telecom.TelecomManager;
import android.provider.Telephony;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.view.inputmethod.InputMethod;
import android.accessibilityservice.AccessibilityService;
import android.service.notification.NotificationListenerService;
import android.net.VpnService;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Ne jamais inférer « système » du simple nom de paquet ou de permissions.
 * Les rôles indispensables ACTUELLEMENT actifs sont verrouillés.
 * Les autres services tiers sont sélectionnables après avertissement.
 */
public final class SafetyPolicy {
  private static final Set<String> RISK_PERMISSIONS = new HashSet<>(Arrays.asList(
      "android.permission.SCHEDULE_EXACT_ALARM",
      "android.permission.USE_EXACT_ALARM",
      "android.permission.BIND_ACCESSIBILITY_SERVICE",
      "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
      "android.permission.BIND_VPN_SERVICE",
      "android.permission.BIND_DEVICE_ADMIN"
  ));

  private final Context context;
  private final PackageManager pm;
  private final Map<String, String> hardRoles = new HashMap<>();
  private final Map<String, String> cautionRoles = new HashMap<>();

  public SafetyPolicy(Context context) {
    this.context = context;
    this.pm = context.getPackageManager();
    putHard(context.getPackageName(), "Application Nettoyage sûr en cours d'utilisation");
    putHard("moe.shizuku.privileged.api", "Shizuku, nécessaire pour exécuter les commandes");
    putHard("rikka.shizuku", "Service Shizuku");
    protectActiveRoles();
    protectSecureComponents(Settings.Secure.DEFAULT_INPUT_METHOD,
        "Clavier actuellement utilisé", true);
    protectSecureComponents("enabled_accessibility_services",
        "Service d'accessibilité activé", false);
    protectSecureComponents("enabled_notification_listeners",
        "Accès aux notifications activé", false);
    protectSecureComponents("assistant", "Assistant vocal choisi", false);
    protectActiveDeviceAdmins();
    cautionServices(InputMethod.SERVICE_INTERFACE, "Propose un clavier");
    cautionServices(AccessibilityService.SERVICE_INTERFACE,
        "Fournit un service d'accessibilité");
    cautionServices(NotificationListenerService.SERVICE_INTERFACE,
        "Fournit un service de notification");
    cautionServices(VpnService.SERVICE_INTERFACE, "Fournit un service VPN");
  }

  private void putHard(String pkg, String reason) {
    if (pkg != null && !pkg.isEmpty()) hardRoles.put(pkg, reason);
  }
  private void putCaution(String pkg, String reason) {
    if (pkg != null && !pkg.isEmpty() && !hardRoles.containsKey(pkg))
      cautionRoles.put(pkg, reason);
  }

  /** L'origine de l'application est déterminée par Android, jamais par son nom. */
  public boolean isSystem(PackageInfo info) {
    if (info == null || info.applicationInfo == null) return false;
    ApplicationInfo app = info.applicationInfo;
    return (app.flags &
        (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
  }

  /** Raison précise d'un verrou non modifiable. Null si l'arrêt est autorisable. */
  public String hardReason(PackageInfo info) {
    if (info == null || info.applicationInfo == null) return "Application illisible";
    String pkg = info.packageName;
    ApplicationInfo app = info.applicationInfo;
    if (!ProcessParser.isValidPackage(pkg)) return "Identifiant Android invalide";
    if (pkg.equals(context.getPackageName())) return "Nettoyage sûr est en cours d'utilisation";
    if (app.uid < android.os.Process.FIRST_APPLICATION_UID)
      return "Composant exécuté avec un identifiant système";
    if (isSystem(info)) return "Application système ou préinstallée, selon Android";
    if (!app.enabled) return "Application déjà désactivée";
    String role = hardRoles.get(pkg);
    if (role != null) return role;
    return null;
  }

  /** Une application téléchargée peut être sensible sans être une application système. */
  public String cautionReason(PackageInfo info) {
    if (info == null || info.applicationInfo == null) return null;
    if (hardReason(info) != null) return null;
    String pkg = info.packageName;
    String role = cautionRoles.get(pkg);
    if (role != null) return role;
    if (isCriticalName(pkg))
      return "Cette application pourrait gérer un service important";
    if (info.requestedPermissions != null) {
      for (String perm : info.requestedPermissions)
        if (RISK_PERMISSIONS.contains(perm))
          return "Cette application demande des fonctions sensibles";
    }
    if (pm.getLaunchIntentForPackage(pkg) == null)
      return "Cette application ne possède pas d'écran de lancement standard";
    return null;
  }

  public boolean isHardProtected(PackageInfo info) {
    return hardReason(info) != null;
  }
  public boolean isCaution(PackageInfo info) {
    return cautionReason(info) != null;
  }
  public boolean canStop(PackageInfo info, boolean userConfirmedCaution) {
    return hardReason(info) == null &&
        (cautionReason(info) == null || userConfirmedCaution);
  }

  private boolean isCriticalName(String pkg) {
    String lower = pkg.toLowerCase(Locale.ROOT);
    String[] terms = {
        "launcher", "keyboard", "inputmethod", "dialer", "telephony",
        "messaging", "messages", "alarm", "clock", "calendar",
        "wallet", "bank", "authenticator", "password", "passkey", "vpn",
        "security", "parental", "deviceadmin", "accessibility",
        "whatsapp", "telegram", "signal", "messenger", "discord",
        "teams", "outlook", "gmail", "protonmail", "mail",
        "bluetooth", "automate", "tasker", "homeassistant", "shizuku",
        "health", "medical", "hearing", "wear", "watch",
        "location", "tracking", "emergency", "sos", "maps"
    };
    for (String term : terms) if (lower.contains(term)) return true;
    return false;
  }

  private void protectActiveRoles() {
    try {
      Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
      ResolveInfo launch = pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
      if (launch != null && launch.activityInfo != null)
        putHard(launch.activityInfo.packageName, "Écran d'accueil actuellement utilisé");
    } catch (RuntimeException ignored) {}
    try {
      TelecomManager telecom = (TelecomManager)
          context.getSystemService(Context.TELECOM_SERVICE);
      if (telecom != null) putHard(telecom.getDefaultDialerPackage(),
          "Application Téléphone par défaut");
    } catch (RuntimeException ignored) {}
    try {
      putHard(Telephony.Sms.getDefaultSmsPackage(context),
          "Application SMS par défaut");
    } catch (RuntimeException ignored) {}
  }

  private void protectSecureComponents(String key, String reason, boolean hard) {
    try {
      String value = Settings.Secure.getString(context.getContentResolver(), key);
      if (value == null) return;
      for (String v : value.split(":")) {
        ComponentName name = ComponentName.unflattenFromString(v);
        if (name == null) continue;
        if (hard) putHard(name.getPackageName(), reason);
        else putCaution(name.getPackageName(), reason);
      }
    } catch (RuntimeException ignored) {}
  }

  private void protectActiveDeviceAdmins() {
    try {
      DevicePolicyManager manager = (DevicePolicyManager)
          context.getSystemService(Context.DEVICE_POLICY_SERVICE);
      List<ComponentName> active = manager == null ? null : manager.getActiveAdmins();
      if (active != null)
        for (ComponentName admin : active)
          putHard(admin.getPackageName(), "Administrateur de l'appareil activé");
    } catch (RuntimeException ignored) {}
  }

  private void cautionServices(String action, String reason) {
    try {
      for (ResolveInfo ri : pm.queryIntentServices(new Intent(action), 0))
        if (ri.serviceInfo != null) putCaution(ri.serviceInfo.packageName, reason);
    } catch (RuntimeException ignored) {}
  }
}
