package com.surcumference.fingerprint.storage;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Process;
import android.system.Os;
import android.util.AtomicFile;
import android.util.Xml;
import com.surcumference.fingerprint.BuildConfig;
import de.robv.android.xposed.XSharedPreferences;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Iterator;
import org.json.JSONObject;
import org.xmlpull.v1.XmlSerializer;

public final class XposedConfigSnapshot {
    private static final String NAME = "runtime_config_v2";
    private static boolean reportedDirectRead;
    private static File runtimeFile;
    private final AtomicFile shared;
    private final int user;
    private String contents = "";
    private long managementUntil;
    private String published;
    XposedConfigSnapshot(Context context) {
        user = Process.myUid() / 100000;
        AtomicFile available = null;
        if (XposedActivation.frameworkVersion() >= 93) {
            boolean legacyRevoked = true;
            try {
                SharedPreferences preferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
                Field field = preferences.getClass().getDeclaredField("mFile");
                field.setAccessible(true);
                File legacy = (File) field.get(preferences);
                File target = snapshotFile(legacy);
                if (legacy.exists()) {
                    legacyRevoked = false;
                    Os.chmod(legacy.getAbsolutePath(), 0600);
                    legacyRevoked = true;
                }
                AtomicFile candidate = new AtomicFile(target);
                if (target.exists()) {
                    org.xmlpull.v1.XmlPullParser xml = Xml.newPullParser();
                    try (java.io.FileInputStream input = candidate.openRead()) {
                        xml.setInput(input, "UTF-8");
                        while (xml.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                            if (xml.getEventType() != org.xmlpull.v1.XmlPullParser.START_TAG) continue;
                            if ("string".equals(xml.getName()) && "document".equals(xml.getAttributeValue(null, "name")))
                                contents = xml.nextText();
                            else if ("long".equals(xml.getName()) && "managementUntil".equals(xml.getAttributeValue(null, "name")))
                                managementUntil = Long.parseLong(xml.getAttributeValue(null, "value"));
                        }
                    }
                }
                if (!candidate.getBaseFile().exists() || !requiresRevocation(contents))
                    write(candidate, contents, managementUntil);
                available = candidate;
            }
            catch (LinkageError | Exception unavailable) {
                if (!legacyRevoked) throw new IllegalStateException("Cannot revoke legacy runtime configuration", unavailable);
                android.util.Log.w("FingerprintPay", "Runtime configuration sharing unavailable; using provider", unavailable);
            }
        }
        shared = available;
    }
    void invalidate() {
        published = null;
        if (shared != null) {
            write(shared, "", managementUntil);
            contents = "";
        }
    }
    void publish(JSONObject document) throws Exception {
        if (shared == null) return;
        String value = sanitized(document, user).toString();
        if (value.equals(published)) return;
        write(shared, value, managementUntil);
        contents = value;
        published = value;
    }
    private static File snapshotFile(File legacy) {
        return new File(legacy.getParentFile(), NAME + ".snapshot");
    }
    private static synchronized File hostSnapshotFile() {
        if (runtimeFile == null) {
            File legacy = new XSharedPreferences(BuildConfig.APPLICATION_ID, NAME).getFile();
            runtimeFile = snapshotFile(legacy);
        }
        return runtimeFile;
    }
    private static void write(AtomicFile file, String document, long managementUntil) {
        FileOutputStream output = null;
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            XmlSerializer xml = Xml.newSerializer();
            xml.setOutput(buffer, "UTF-8");
            xml.startDocument("UTF-8", true);
            xml.startTag(null, "map");
            xml.startTag(null, "string").attribute(null, "name", "document").text(document).endTag(null, "string");
            xml.startTag(null, "long").attribute(null, "name", "managementUntil")
                    .attribute(null, "value", Long.toString(managementUntil)).endTag(null, "long");
            xml.endTag(null, "map");
            xml.endDocument();
            xml.flush();
            byte[] bytes = buffer.toByteArray();
            output = file.startWrite();
            output.write(bytes);
            Os.fchmod(output.getFD(), 0644);
            file.finishWrite(output);
            output = null;
            // AtomicFile reports some commit failures only through logging.
            if (!Arrays.equals(bytes, file.readFully())) throw new IllegalStateException("Runtime configuration commit failed");
        } catch (Exception error) {
            if (output != null) file.failWrite(output);
            throw new IllegalStateException("Cannot publish runtime configuration", error);
        }
    }
    private static boolean requiresRevocation(String raw) {
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
        long now = System.currentTimeMillis();
        if (managementUntil < now + 120000 || managementUntil > now + 180000) {
            write(shared, contents, now + 180000);
            managementUntil = now + 180000;
        }
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
            preferences = new XSharedPreferences(hostSnapshotFile());
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
