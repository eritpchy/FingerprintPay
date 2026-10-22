package com.surcumference.fingerprint.storage;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Iterator;
import org.json.JSONObject;

@androidx.annotation.Keep
public final class LocalConfigStore implements ConfigStore {
    private static final Object LOCK = new Object();
    private final SharedPreferences preferences;
    private final PendingPasswords pending = new PendingPasswords(LOCK);
    private final Context context;
    private final XposedConfigSnapshot snapshot;
    public LocalConfigStore(Context context) {
        this.context = context.getApplicationContext();
        preferences = context.getSharedPreferences("unified_config_v1", Context.MODE_PRIVATE);
        snapshot = new XposedConfigSnapshot(context);
    }
    private JSONObject document() throws Exception {
        if (preferences.contains("document")) {
            JSONObject doc = new JSONObject(preferences.getString("document", ""));
            if (doc.getInt("schema") == 1) {
                JSONObject apps = doc.getJSONObject("apps");
                Iterator<String> names = apps.keys();
                while (names.hasNext()) {
                    JSONObject settings = apps.getJSONObject(names.next());
                    if (settings.has("secret")) {
                        settings.put("legacySecret", settings.remove("secret")).put("hasPassword", false)
                                .put("requiresReentry", true).put("switch_on1", false)
                                .put("credentialVersion", settings.optLong("credentialVersion") + 1);
                    }
                }
                doc.put("schema", 2).put("revision", doc.getLong("revision") + 1);
                if (!preferences.edit().putString("document", doc.toString()).commit()) throw new IllegalStateException("Cannot migrate configuration");
            }
            return doc;
        }
        JSONObject globals = new JSONObject().put("auto_update", true).put("license_agree", false).put("show_icon", true);
        SharedPreferences old = context.getSharedPreferences("main_prefs", Context.MODE_PRIVATE);
        SharedPreferences launcher = context.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_PRIVATE);
        JSONObject doc = new JSONObject().put("schema", 2).put("revision", 0).put("apps", new JSONObject()).put("globals", globals);
        if (old.contains("license_agree")) globals.put("license_agree", old.getBoolean("license_agree", false));
        if (old.contains("skip_version")) globals.put("skip_xposed", old.getString("skip_version", ""));
        if (launcher.contains("show_icon")) globals.put("show_icon", launcher.getBoolean("show_icon", true));
        if (!preferences.edit().putString("document", doc.toString()).commit()) throw new IllegalStateException("Cannot initialize configuration");
        old.edit().remove("license_agree").remove("skip_version").commit();
        launcher.edit().remove("show_icon").commit();
        return doc;
    }
    @Override public JSONObject request(JSONObject request) throws Exception {
        synchronized (LOCK) {
            JSONObject doc = document();
            if (doc.getInt("schema") != 2) throw new IllegalStateException("Unsupported configuration schema");
            snapshot.publish(doc);
            String pkg = request.getString("package");
            if (!ConfigContract.PACKAGES.contains(pkg)) throw new SecurityException("Unknown application");
            JSONObject apps = doc.getJSONObject("apps");
            JSONObject settings = apps.optJSONObject(pkg);
            if (settings == null) settings = ConfigContract.defaults();
            JSONObject globals = doc.getJSONObject("globals");
            long revision = doc.getLong("revision");
            String op = request.getString("op");
            if ("reveal".equals(op)) throw new SecurityException("Passwords require host TEE decryption");
            long version = settings.optLong("credentialVersion");
            if ("pendingPassword".equals(op)) return new JSONObject().put("ok", true).put("pending", pending.take(pkg));
            if ("passwordStatus".equals(op)) return new JSONObject().put("ok", true).put("pending", pending.status(pkg, request.getString("id")));
            if ("cancelPassword".equals(op)) {
                String id = request.getString("id"), status = pending.status(pkg, id).getString("status");
                if ("waiting".equals(status) || "authenticating".equals(status)) pending.finish(pkg, id, "canceled");
                return new JSONObject().put("ok", true);
            }
            if ("claimPassword".equals(op)) return new JSONObject().put("ok", true).put("password", pending.claim(pkg, request.getString("id"), version));
            if ("credential".equals(op)) {
                if (!settings.optBoolean("scope") || !settings.optBoolean("switch_on1") || !globals.optBoolean("license_agree")
                        || request.getLong("credentialVersion") != version || !settings.has("secret")) throw new SecurityException("Password changed or payment disabled");
                return new JSONObject().put("ok", true).put("record", settings.getJSONObject("secret"));
            }
            if (!"read".equals(op)) {
                if (request.getLong("revision") != revision) throw new IllegalStateException("Configuration changed; refresh and retry");
                if ("patch".equals(op)) {
                    JSONObject values = request.getJSONObject("values");
                    Iterator<String> keys = values.keys();
                    while (keys.hasNext()) {
                        String name = keys.next(); Object value = values.get(name);
                        ConfigContract.validate(name, value);
                        if (ConfigContract.global(name)) globals.put(name, value);
                        else settings.put(name, value);
                    }
                    if (settings.optBoolean("biometric_api")) settings.put("volume_down_monitor_enabled", false);
                    if (settings.optBoolean("switch_on1") && (!settings.has("secret") || !globals.optBoolean("license_agree"))) {
                        throw new IllegalStateException("Accept the license and set a password first");
                    }
                } else if ("password".equals(op)) {
                    String password = request.getString("password");
                    if (password.length() > 256) throw new IllegalArgumentException("Password too long");
                    if (!password.isEmpty()) {
                        if (!settings.optBoolean("scope")) throw new IllegalStateException("Enable this application first");
                        pending.begin(pkg, password, version);
                        if (request.optBoolean("inline")) pending.take(pkg);
                        return visible(pkg, settings, globals, revision);
                    }
                    JSONObject old = pending.status(pkg, null);
                    pending.finish(pkg, old.optString("id"), "canceled");
                    settings.remove("secret"); settings.remove("legacySecret");
                    settings.put("hasPassword", false).put("requiresReentry", false).put("switch_on1", false).put("credentialVersion", version + 1);
                } else if ("commitPassword".equals(op)) {
                    String id = request.getString("id");
                    pending.validate(pkg, id, version);
                    if (!settings.optBoolean("scope")) throw new IllegalStateException("Application disabled");
                    JSONObject record = request.getJSONObject("record");
                    ConfigContract.validatePasswordRecord(record, "Xposed", android.os.Process.myUid() / 100000, pkg, id);
                    settings.put("secret", record).put("hasPassword", true).put("requiresReentry", false).put("credentialVersion", version + 1);
                    settings.remove("legacySecret");
                } else throw new IllegalArgumentException("Unknown operation");
                apps.put(pkg, settings); doc.put("revision", ++revision);
                snapshot.invalidate();
                if (!preferences.edit().putString("document", doc.toString()).commit()) throw new IllegalStateException("Cannot save configuration");
                snapshot.publish(doc);
                if ("commitPassword".equals(op)) pending.finish(pkg, request.getString("id"), "done");
            }
            return visible(pkg, settings, globals, revision);
        }
    }
    private JSONObject visible(String pkg, JSONObject settings, JSONObject globals, long revision) throws Exception {
        JSONObject visible = new JSONObject(settings.toString()); visible.remove("secret"); visible.remove("legacySecret");
        return new JSONObject().put("ok", true).put("passwordProtection", ConfigContract.TEE_FORMAT).put("revision", revision).put("settings", visible).put("globals", globals).put("pending", pending.status(pkg, null));
    }
}
