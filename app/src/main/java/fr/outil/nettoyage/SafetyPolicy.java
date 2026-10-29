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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Filtre conservateur : préserve systématiquement les applications système,
 * et de très nombreuses applications tierces pouvant effectuer des tâches critiques.
 * La protection n'est pas une garantie absolue contre les effets de force-stop.
 */
public final class SafetyPolicy {
  private static final String[] OS_PREFIXES = {
      "android", "com.android.", "com.google.android.", "com.google.gms.",
      "com.vivo.", "com.bbk.", "com.iqoo.", "com.qualcomm.", "com.mediatek.",
      "com.samsung.", "com.sec.", "com.miui.", "com.xiaomi.", "com.huawei.",
      "com.honor.", "com.oppo.", "com.coloros.", "com.oneplus.",
      "com.motorola.", "com.transsion.", "com.lenovo.", "com.sony.",
      "org.lineageos.", "moe.shizuku.", "rikka.shizuku."
  };
  // Ces permissions signalent des fonctions pour lesquelles l'arrêt est déconseillé.
  // RECEIVE_BOOT_COMPLETED, POST_NOTIFICATIONS et FOREGROUND_SERVICE seuls
  // ne prouvent PAS qu'une application est essentielle.
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
  private final Set<String> protectedPackages = new HashSet<>();

  public SafetyPolicy(Context context) {
    this.context = context;
    this.pm = context.getPackageManager();
    protectedPackages.add(context.getPackageName());
    protectRoles();
    protectSecureComponents(Settings.Secure.DEFAULT_INPUT_METHOD);
    protectSecureComponents("enabled_accessibility_services");
    protectSecureComponents("enabled_notification_listeners");
    protectDeviceAdmins();
    protectServices(InputMethod.SERVICE_INTERFACE);
    protectServices(VpnService.SERVICE_INTERFACE);
  }

  /** Jamais désactivable depuis l'interface, même sur demande. */
  public boolean isHardProtected(PackageInfo info) {
    if (info == null || info.applicationInfo == null) return true;
    String pkg = info.packageName;
    ApplicationInfo app = info.applicationInfo;
    if (!ProcessParser.isValidPackage(pkg)) return true;
    if (pkg.equals(context.getPackageName()) || protectedPackages.contains(pkg)) return true;
    if (app.uid < android.os.Process.FIRST_APPLICATION_UID) return true;
    if ((app.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0)
      return true;
    if (!app.enabled) return true;
    return isVendorCore(pkg);
  }

  /** Protection conseillée : l'utilisateur peut la lever pour une application tierce. */
  public boolean isCaution(PackageInfo info) {
    if (isHardProtected(info)) return false;
    if (isCriticalName(info.packageName)) return true;
    if (pm.getLaunchIntentForPackage(info.packageName) == null) return true;
    if (info.requestedPermissions != null) {
      for (String perm : info.requestedPermissions) {
        if (RISK_PERMISSIONS.contains(perm)) return true;
      }
    }
    return false;
  }

  public boolean canStop(PackageInfo info, boolean userConfirmedCaution) {
    return !isHardProtected(info) && (!isCaution(info) || userConfirmedCaution);
  }

  private static boolean isVendorCore(String pkg) {
    for (String prefix : OS_PREFIXES) {
      if (pkg.equals(prefix) || pkg.startsWith(prefix)) return true;
    }
    return false;
  }

  private static boolean isCriticalName(String pkg) {
    String lower = pkg.toLowerCase(Locale.ROOT);
    String[] terms = {
        "launcher", "keyboard", "inputmethod", "dialer", "telephony",
        "messaging", "messages", "alarm", "clock", "calendar",
        "wallet", "bank", "authenticator", "password", "passkey", "vpn",
        "security", "parental", "deviceadmin", "accessibility",
        "whatsapp", "telegram", "signal", "messenger", "discord",
        "teams", "outlook", "gmail", "protonmail", "mail",
        "bluetooth", "automate", "tasker", "homeassistant", "shizuku",
        "health", "medical", "hearing", "wear", "watch", "fitbit",
        "location", "tracking", "emergency", "sos", "maps"
    };
    for (String term : terms) if (lower.contains(term)) return true;
    return false;
  }

  private void protectRoles() {
    try {
      Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
      ResolveInfo launcher = pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
      if (launcher != null && launcher.activityInfo != null)
        protectedPackages.add(launcher.activityInfo.packageName);
    } catch (RuntimeException ignored) {}
    try {
      TelecomManager tel = (TelecomManager)context.getSystemService(Context.TELECOM_SERVICE);
      if (tel != null && tel.getDefaultDialerPackage() != null)
        protectedPackages.add(tel.getDefaultDialerPackage());
    } catch (RuntimeException ignored) {}
    try {
      String sms = Telephony.Sms.getDefaultSmsPackage(context);
      if (sms != null) protectedPackages.add(sms);
    } catch (RuntimeException ignored) {}
    protectSecureComponents("assistant");
  }

  private void protectSecureComponents(String key) {
    try {
      String value = Settings.Secure.getString(context.getContentResolver(), key);
      if (value == null) return;
      for (String v : value.split(":")) {
        ComponentName name = ComponentName.unflattenFromString(v);
        if (name != null) protectedPackages.add(name.getPackageName());
      }
    } catch (RuntimeException ignored) {}
  }

  private void protectDeviceAdmins() {
    try {
      DevicePolicyManager manager = (DevicePolicyManager)context.getSystemService(Context.DEVICE_POLICY_SERVICE);
      List<ComponentName> admins = manager == null ? null : manager.getActiveAdmins();
      if (admins != null) {
        for (ComponentName admin : admins) protectedPackages.add(admin.getPackageName());
      }
    } catch (RuntimeException ignored) {}
  }

  private void protectServices(String action) {
    try {
      for (ResolveInfo ri : pm.queryIntentServices(new Intent(action), 0)) {
        if (ri.serviceInfo != null) protectedPackages.add(ri.serviceInfo.packageName);
      }
    } catch (RuntimeException ignored) {}
  }

  private void protectWidgetProviders() {
    try {
      for (ResolveInfo ri : pm.queryBroadcastReceivers(
          new Intent("android.appwidget.action.APPWIDGET_UPDATE"), 0)) {
        if (ri.activityInfo != null) protectedPackages.add(ri.activityInfo.packageName);
      }
    } catch (RuntimeException ignored) {}
  }
}
