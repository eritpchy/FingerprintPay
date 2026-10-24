package com.surcumference.fingerprint.storage;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import org.json.JSONObject;

public final class ConfigProvider extends ContentProvider {
    private LocalConfigStore store;
    private final PendingDonations donations = new PendingDonations();
    @Override public boolean onCreate() {
        store = new LocalConfigStore(getContext());
        LauncherIcon.restore(store);
        return true;
    }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        if (!"request".equals(method) || extras == null) throw new SecurityException("Invalid request");
        try {
            String raw = extras.getString("request", "");
            if (raw.length() > 65536) throw new IllegalArgumentException("Request too large");
            JSONObject request = new JSONObject(raw);
            int uid = Binder.getCallingUid();
            if (uid / 100000 != android.os.Process.myUid() / 100000) throw new SecurityException("Wrong Android user");
            String pkg = request.getString("package");
            String[] callers = getContext().getPackageManager().getPackagesForUid(uid);
            boolean own = uid == android.os.Process.myUid();
            if (own) store.managementActive();
            boolean host = false;
            if (callers != null) for (String caller : callers) if (caller.equals(pkg) && ConfigContract.PACKAGES.contains(caller)) host = true;
            if (!own && !host) throw new SecurityException("Application access denied");
            request.put("user", uid / 100000);
            request.put("inline", !own);
            String op = request.optString("op");
            if (op.endsWith("Donation") || "donationStatus".equals(op)) {
                boolean management = "beginDonation".equals(op) || "cancelDonation".equals(op) || "donationStatus".equals(op);
                boolean target = "pendingDonation".equals(op) || "claimDonation".equals(op) || "completeDonation".equals(op);
                if (!(own && management || !own && host && target)) throw new SecurityException("Donation access denied");
                Bundle result = new Bundle(); result.putString("response", donations.request(request).toString()); return result;
            }
            if (own && ("claimPassword".equals(op) || "pendingPassword".equals(op) || "commitPassword".equals(op) || "credential".equals(op)))
                throw new SecurityException("This operation requires the target application");
            if (!own && request.optJSONObject("values") != null && request.getJSONObject("values").has("show_icon"))
                throw new SecurityException("Module settings require management access");
            // Management surfaces may replace a password but never retrieve one.
            if (own && "reveal".equals(request.optString("op"))) throw new SecurityException("Password is write-only here");
            JSONObject response = store.request(request);
            if (own && "patch".equals(request.optString("op")) && request.getJSONObject("values").has("show_icon")) {
                store.syncLauncherIcon();
            }
            Bundle result = new Bundle(); result.putString("response", response.toString()); return result;
        } catch (Exception error) {
            Bundle result = new Bundle();
            try { result.putString("response", new JSONObject().put("ok", false).put("error", error.getMessage()).toString()); }
            catch (Exception ignored) { result.putString("response", "{\"ok\":false}"); }
            return result;
        }
    }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { throw new SecurityException("Use the configuration protocol"); }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { throw new SecurityException("Use the configuration protocol"); }
    @Override public int delete(Uri u, String s, String[] a) { throw new SecurityException("Use the configuration protocol"); }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new SecurityException("Use the configuration protocol"); }
}
