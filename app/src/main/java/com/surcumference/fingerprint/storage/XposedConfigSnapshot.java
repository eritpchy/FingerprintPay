package com.surcumference.fingerprint.storage;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Process;
import com.surcumference.fingerprint.BuildConfig;
import de.robv.android.xposed.XSharedPreferences;
import java.util.Iterator;
import java.util.UUID;
import org.json.JSONObject;

public final class XposedConfigSnapshot {
    private static final String NAME = "runtime_config_v2";
    private static boolean reportedDirectRead;
    private final SharedPreferences shared;
    private final int user;
    private String published;
    XposedConfigSnapshot(Context context) {
        user = Process.myUid() / 100000;
        SharedPreferences available = null;
        if (XposedActivation.frameworkVersion() >= 93) {
            try {
                SharedPreferences candidate = context.getSharedPreferences(NAME, Context.MODE_WORLD_READABLE);
                // Opening preferences does not guarantee that LSPosed's shared directory is writable.
                if (requiresRevocation(candidate) || write(candidate).commit()) available = candidate;
                else {
                    android.util.Log.w("FingerprintPay", "Runtime configuration sharing unavailable; using provider");
                }
            }
            catch (SecurityException ignored) {}
        }
        shared = available;
    }
    void invalidate() {
        published = null;
        // Keep a tombstone even in memory after a failed commit, so retries cannot skip revocation.
        if (shared != null && !write(shared).putString("document", "").commit())
            throw new IllegalStateException("Cannot invalidate runtime configuration");
    }
    void publish(JSONObject document) throws Exception {
        if (shared == null) return;
        String value = sanitized(document, user).toString();
        if (value.equals(published)) return;
        if (!write(shared).putString("document", value).commit())
            throw new IllegalStateException("Cannot publish runtime configuration");
        published = value;
    }
    private static SharedPreferences.Editor write(SharedPreferences preferences) {
        // commit() updates memory before disk; force a disk write when retrying a failed value.
        return preferences.edit().putString("writeToken", UUID.randomUUID().toString());
    }
    private static boolean requiresRevocation(SharedPreferences preferences) {
        String raw = preferences.getString("document", null);
        if (raw == null) return false;
        if (raw.isEmpty()) return true;
        try {
            JSONObject document = new JSONObject(raw);
            if (document.getInt("schema") != 2) return true;
            if (!document.getJSONObject("globals").getBoolean("license_agree")) return false;
        } catch (Exception ignored) {}
        return true;
    }
    void managementActive() {
        if (shared == null) return;
        long now = System.currentTimeMillis(), until = shared.getLong("managementUntil", 0);
        if (until < now + 120000 || until > now + 180000)
            shared.edit().putLong("managementUntil", now + 180000).commit();
    }
    static JSONObject sanitized(JSONObject document, int user) throws Exception {
        JSONObject result = new JSONObject(document.toString()).put("user", user);
        JSONObject apps = result.getJSONObject("apps");
        Iterator<String> names = apps.keys();
        while (names.hasNext()) {
            JSONObject settings = apps.getJSONObject(names.next());
            settings.remove("legacySecret");
        }
        return result;
    }
    public static JSONObject read(Context context, JSONObject request) throws Exception {
        String op = request.optString("op");
        if (!"read".equals(op) && !"credential".equals(op)
                && !"pendingPassword".equals(op) && !"pendingDonation".equals(op)) return null;
        XSharedPreferences preferences;
        try {
            preferences = new XSharedPreferences(BuildConfig.APPLICATION_ID, NAME);
            if (!preferences.getFile().canRead()) return null;
        } catch (LinkageError | RuntimeException unavailable) { return null; }
        // A fresh reader prevents a same-size, same-timestamp update from retaining a revoked credential.
        String raw = preferences.getString("document", null);
        if (raw == null || raw.isEmpty()) return null;
        int user = Process.myUid() / 100000;
        JSONObject document = new JSONObject(raw);
        validateOwner(document, request, context.getPackageName(), user);
        // A snapshot without consent cannot authorize payment and may outlive a failed first setup.
        if (!document.getJSONObject("globals").optBoolean("license_agree")) return null;
        if ("pendingPassword".equals(op) || "pendingDonation".equals(op)) {
            long remaining = preferences.getLong("managementUntil", 0) - System.currentTimeMillis();
            return remaining > 0 && remaining <= 180000 ? null
                    : new JSONObject().put("ok", true).put("pending", new JSONObject());
        }
        JSONObject result = respond(document, request, context.getPackageName(), user);
        if (!reportedDirectRead) {
            reportedDirectRead = true;
            android.util.Log.i("FingerprintPay", "Xposed configuration read without provider: " + context.getPackageName());
        }
        return result;
    }
    private static void validateOwner(JSONObject document, JSONObject request, String pkg, int user) throws Exception {
        if (document.getInt("schema") != 2 || document.getInt("user") != user
                || request.optInt("user", user) != user || !pkg.equals(request.getString("package"))
                || !ConfigContract.PACKAGES.contains(pkg)) throw new SecurityException("Wrong runtime configuration owner");
    }
    static JSONObject respond(JSONObject document, JSONObject request, String pkg, int user) throws Exception {
        validateOwner(document, request, pkg, user);
        JSONObject settings = document.getJSONObject("apps").optJSONObject(pkg);
        if (settings == null) settings = ConfigContract.defaults();
        JSONObject globals = document.getJSONObject("globals");
        if ("credential".equals(request.getString("op"))) {
            if (!settings.optBoolean("scope") || !settings.optBoolean("switch_on1") || !settings.optBoolean("hasPassword")
                    || settings.optBoolean("requiresReentry") || !globals.optBoolean("license_agree")
                    || request.getLong("credentialVersion") != settings.optLong("credentialVersion") || !settings.has("secret"))
                throw new SecurityException("Password changed or payment disabled");
            return new JSONObject().put("ok", true).put("record", new JSONObject(settings.getJSONObject("secret").toString()));
        }
        if (!"read".equals(request.getString("op"))) throw new SecurityException("Runtime configuration is read-only");
        JSONObject visible = new JSONObject(settings.toString()); visible.remove("secret"); visible.remove("legacySecret");
        return new JSONObject().put("ok", true).put("revision", document.getLong("revision"))
                .put("passwordProtection", ConfigContract.TEE_FORMAT).put("settings", visible)
                .put("globals", globals).put("pending", new JSONObject());
    }
}
