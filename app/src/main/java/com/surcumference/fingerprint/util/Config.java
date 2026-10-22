package com.surcumference.fingerprint.util;

import android.content.Context;
import android.content.SharedPreferences;
import com.surcumference.fingerprint.BuildConfig;
import com.surcumference.fingerprint.plugin.PluginApp;
import com.surcumference.fingerprint.storage.ConfigContract;
import com.surcumference.fingerprint.storage.ConfigStore;
import com.surcumference.fingerprint.storage.ConfigStores;
import com.hjq.toast.Toaster;
import org.json.JSONObject;
import java.util.Locale;

/**
 * Created by Jason on 2017/9/9.
 */
public final class Config {
    private static final java.util.Set<String> cleanedLegacyPackages = new java.util.HashSet<>();
    private final String pkg;
    private final ConfigStore store;
    public static Config from(Context context) { return new Config(context); }
    private Config(Context context) {
        pkg = ConfigContract.PACKAGES.contains(context.getPackageName()) ? context.getPackageName() : ConfigContract.PACKAGES.get(0);
        store = ConfigStores.forContext(context);
        cleanupLegacy(context);
    }
    private JSONObject request(String op) throws Exception {
        return new JSONObject().put("op", op).put("package", pkg).put("user", android.os.Process.myUid() / 100000);
    }
    private JSONObject read() throws Exception { return store.request(request("read")); }
    public JSONObject snapshot() throws Exception { return read(); }
    private static synchronized void cleanupLegacy(Context context) {
        String pkg = context.getPackageName();
        if (!ConfigContract.PACKAGES.contains(pkg) || cleanedLegacyPackages.contains(pkg)) return;
        try {
            String name = BuildConfig.APPLICATION_ID + ".settings";
            SharedPreferences old = context.getSharedPreferences(name, Context.MODE_PRIVATE);
            SharedPreferences.Editor edit = old.edit();
            for (String key : new String[]{"switch_on1", "password", "password_iv", "fingerprint_icon", "biometric_api",
                    "volume_down_monitor_enabled", "skip_version", "license_agree"}) edit.remove(key);
            if (!edit.commit()) return;
            boolean complete = true;
            if (old.getAll().isEmpty()) {
                if (android.os.Build.VERSION.SDK_INT >= 24) complete = context.deleteSharedPreferences(name);
                else {
                    java.io.File file = new java.io.File(context.getApplicationInfo().dataDir, "shared_prefs/" + name + ".xml");
                    if (file.exists() && !file.delete()) complete = false;
                    java.io.File backup = new java.io.File(file.getPath() + ".bak");
                    if (backup.exists() && !backup.delete()) complete = false;
                }
            }
            String[] apps = {"wechat", "alipay", "taobao", "qq", "unionpay"};
            java.io.File directory = context.getFilesDir();
            for (String framework : new String[]{"zygisk", "riru"}) for (String app : apps) {
                for (String suffix : new String[]{".apk", ".dex", ".debug.apk", ".debug.apk.crc"}) {
                    java.io.File file = new java.io.File(directory, framework + "-module-xfingerprint-pay-" + app + suffix);
                    if (file.isFile() && file.getCanonicalPath().equals(file.getAbsolutePath()) && !file.delete()) complete = false;
                }
            }
            if (complete) cleanedLegacyPackages.add(pkg);
        } catch (Exception ignored) {}
    }
    private boolean option(String key, boolean fallback) {
        try { JSONObject value = read(); return value.getJSONObject(ConfigContract.global(key) ? "globals" : "settings").optBoolean(key, fallback); }
        catch (Exception ignored) { return false; }
    }
    private void set(String key, Object value) {
        try {
            JSONObject valueNow = read(), values = new JSONObject().put(key, value);
            store.request(request("patch").put("revision", valueNow.getLong("revision")).put("values", values));
        }
        catch (Exception ignored) { Toaster.showShort("配置保存失败，请检查配置服务并重试"); }
    }
    public boolean isOn() {
        try { JSONObject value = read(), settings = value.getJSONObject("settings");
            return settings.optBoolean("scope") && settings.optBoolean("switch_on1") && settings.optBoolean("hasPassword")
                    && value.getJSONObject("globals").optBoolean("license_agree"); }
        catch (Exception ignored) { return false; }
    }
    public boolean isInScope() { return option("scope", true); }
    public String getPasswordEncrypted() {
        try { JSONObject settings = read().getJSONObject("settings");
            return settings.optBoolean("hasPassword") ? "tee-v2:" + settings.getLong("credentialVersion") : null; }
        catch (Exception ignored) { return null; }
    }
    public void setPasswordEncrypted(String value) { if (value == null || value.isEmpty()) { try { savePassword(""); } catch (Exception ignored) { Toaster.showShort("密码清除失败"); } } }
    public String beginPassword(String value) throws Exception {
        JSONObject current = read();
        if (!value.isEmpty() && !ConfigContract.TEE_FORMAT.equals(current.optString("passwordProtection")))
            throw new IllegalStateException("Update the module and restart before setting a password");
        JSONObject result = store.request(request("password").put("revision", current.getLong("revision")).put("password", value));
        return value.isEmpty() ? null : result.getJSONObject("pending").getString("id");
    }
    public void savePassword(String value) throws Exception {
        if (!value.isEmpty()) throw new SecurityException("Use biometric TEE encryption");
        beginPassword(value);
    }
    public String pendingPassword() throws Exception {
        return store.request(request("pendingPassword")).getJSONObject("pending").optString("id", null);
    }
    public String claimPassword(String id) throws Exception { return store.request(request("claimPassword").put("id", id)).getString("password"); }
    public void commitPassword(String id, JSONObject record) throws Exception {
        JSONObject current = read();
        store.request(request("commitPassword").put("revision", current.getLong("revision")).put("id", id).put("record", record));
    }
    public void cancelPassword(String id) { try { store.request(request("cancelPassword").put("id", id)); } catch (Exception ignored) {} }
    public JSONObject passwordRecord(String token) throws Exception {
        if (token == null || !token.startsWith("tee-v2:")) throw new IllegalArgumentException("Please set the password again");
        return store.request(request("credential").put("credentialVersion", Long.parseLong(token.substring(7)))).getJSONObject("record");
    }
    public void checkCredential(String token) throws Exception {
        if (!isOn() || !token.equals(getPasswordEncrypted())) throw new SecurityException("Password changed or payment disabled");
    }

    public boolean isShowFingerprintIcon() { return option("fingerprint_icon", false); }
    public boolean isUnderDisplayFingerprint() { return !isShowFingerprintIcon(); }
    public boolean isUseBiometricApi() { return option("biometric_api", false); }
    public boolean isVolumeDownMonitorEnabled() { return option("volume_down_monitor_enabled", true); }
    private String skipKey() { return "skip_" + PluginApp.getCurrentType().name().toLowerCase(Locale.ROOT); }
    public void setSkipVersion(String value) { set(skipKey(), value); }
    public String getSkipVersion() { try { return read().getJSONObject("globals").optString(skipKey(), ""); } catch (Exception ignored) { return ""; } }
    public void setLicenseAgree(boolean value) { set("license_agree", value); }
    public boolean isAutoUpdate() { return option("auto_update", true); }
}
