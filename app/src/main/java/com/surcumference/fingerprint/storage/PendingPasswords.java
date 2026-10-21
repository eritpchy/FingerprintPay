package com.surcumference.fingerprint.storage;

import android.os.SystemClock;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.json.JSONObject;

final class PendingPasswords {
    private final Map<String, Entry> entries = new HashMap<>();
    private final android.os.Handler expiry = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Object storeLock;
    PendingPasswords(Object storeLock) { this.storeLock = storeLock; }
    private static final class Entry {
        final String id = UUID.randomUUID().toString().replace("-", "");
        final long expires = SystemClock.elapsedRealtime() + 120000;
        final long version;
        byte[] password;
        String status = "waiting";
        Entry(String password, long version) { this.password = password.getBytes(StandardCharsets.UTF_8); this.version = version; }
        void finish(String status) { Arrays.fill(password, (byte) 0); password = new byte[0]; this.status = status; }
    }
    synchronized JSONObject begin(String pkg, String password, long version) throws Exception {
        Entry old = entries.get(pkg);
        if (old != null) old.finish("superseded");
        Entry entry = new Entry(password, version); entries.put(pkg, entry);
        expiry.postDelayed(() -> { synchronized (storeLock) { synchronized (PendingPasswords.this) {
            if (entries.get(pkg) == entry && ("waiting".equals(entry.status) || "authenticating".equals(entry.status))) entry.finish("expired");
        }
        } }, 120000);
        return status(pkg, null);
    }
    synchronized JSONObject status(String pkg, String id) throws Exception {
        Entry entry = entries.get(pkg);
        if (entry == null || (id != null && !entry.id.equals(id))) return new JSONObject().put("status", "superseded");
        if (("waiting".equals(entry.status) || "authenticating".equals(entry.status)) && SystemClock.elapsedRealtime() >= entry.expires) entry.finish("expired");
        return new JSONObject().put("id", entry.id).put("status", entry.status);
    }
    synchronized JSONObject take(String pkg) throws Exception {
        JSONObject state = status(pkg, null);
        if (!"waiting".equals(state.optString("status"))) return new JSONObject();
        entries.get(pkg).status = "authenticating";
        return state;
    }
    private Entry active(String pkg, String id, long version) throws Exception {
        String status = status(pkg, id).getString("status");
        Entry entry = entries.get(pkg);
        if (!("waiting".equals(status) || "authenticating".equals(status)) || entry.version != version) throw new IllegalStateException("Password request expired or changed");
        return entry;
    }
    synchronized String claim(String pkg, String id, long version) throws Exception { return new String(active(pkg, id, version).password, StandardCharsets.UTF_8); }
    synchronized void validate(String pkg, String id, long version) throws Exception { active(pkg, id, version); }
    synchronized void finish(String pkg, String id, String status) throws Exception {
        Entry entry = entries.get(pkg);
        if (entry != null && entry.id.equals(id)) entry.finish(status);
    }
}
