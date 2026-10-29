package fr.outil.nettoyage;

import android.Manifest;
import android.app.admin.DevicePolicyManager;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
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
  private static final Set<String> RISK_PERMISSIONS = new HashSet<>(Arrays.asList(
      Manifest.permission.RECEIVE_BOOT_COMPLETED,
      "android.permission.FOREGROUND_SERVICE",
      "android.permission.POST_NOTIFICATIONS",
      "android.permission.SCHEDULE_EXACT_ALARM",
      "android.permission.USE_EXACT_ALARM",
      "android.permission.ACCESS_BACKGROUND_LOCATION",
      "android.permission.SYSTEM_ALERT_WINDOW",
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
    protectServices(AccessibilityService.SERVICE_INTERFACE);
    protectServices(NotificationListenerService.SERVICE_INTERFACE);
    protectServices(VpnService.SERVICE_INTERFACE);
    protectWidgetProviders();
  }

  public boolean canStop(PackageInfo info) {
    if (info == null || info.applicationInfo == null) return false;
    String pkg = info.packageName;
    ApplicationInfo app = info.applicationInfo;
    if (!ProcessParser.isValidPackage(pkg)) return false;
    if (protectedPackages.contains(pkg)) return false;
    if (app.uid < android.os.Process.FIRST_APPLICATION_UID) return false;
    if ((app.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0) return false;
    if (!app.enabled || isCriticalName(pkg)) return false;
    if (pm.getLaunchIntentForPackage(pkg) == null) return false;
    if (info.requestedPermissions != null) {
      for (String perm : info.requestedPermissions) {
        if (RISK_PERMISSIONS.contains(perm) || perm.startsWith("android.permission.FOREGROUND_SERVICE_"))
          return false;
      }
    }
    if (info.activities != null) {
      for (ActivityInfo a : info.activities) {
        if ((a.flags & ActivityInfo.FLAG_SUPPORTS_PICTURE_IN_PICTURE) != 0) return false;
      }
    }
    return true;
  }

  private static boolean isCriticalName(String pkg) {
    String lower = pkg.toLowerCase(Locale.ROOT);
    for (String prefix : OS_PREFIXES) {
      if (pkg.equals(prefix) || pkg.startsWith(prefix)) return true;
    }
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
    if (Build.VERSION.SDK_INT < 29) return;
    try {
      RoleManager manager = context.getSystemService(RoleManager.class);
      if (manager == null) return;
      String[] roles = {RoleManager.ROLE_HOME, RoleManager.ROLE_DIALER,
          RoleManager.ROLE_SMS, RoleManager.ROLE_ASSISTANT,
          RoleManager.ROLE_CALL_SCREENING, RoleManager.ROLE_EMERGENCY,
          RoleManager.ROLE_BROWSER};
      for (String role : roles) {
        try {
          if (manager.isRoleAvailable(role)) protectedPackages.addAll(manager.getRoleHolders(role));
        } catch (RuntimeException ignored) {}
      }
    } catch (RuntimeException ignored) {}
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
