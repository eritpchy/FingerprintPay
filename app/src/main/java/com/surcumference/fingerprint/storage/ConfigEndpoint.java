package com.surcumference.fingerprint.storage;

import android.content.Context;
import org.json.JSONObject;

final class ConfigEndpoint {
    private static ConfigEndpoint instance;
    private final Context context;
    private final LocalConfigStore store;
    private final PendingDonations donations = new PendingDonations();

    static synchronized ConfigEndpoint get(Context context) {
        Context app = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        if (instance == null || instance.context != app) instance = new ConfigEndpoint(app);
        return instance;
    }
    private ConfigEndpoint(Context context) {
        this.context = context;
        store = new LocalConfigStore(context);
    }
    void restoreLauncher() { LauncherIcon.restore(store); }
    JSONObject request(int uid, String raw) {
        try {
            if (raw.length() > 65536) throw new IllegalArgumentException("Request too large");
            JSONObject request = new JSONObject(raw);
            if (uid / 100000 != android.os.Process.myUid() / 100000) throw new SecurityException("Wrong Android user");
            String pkg = request.getString("package");
            String[] callers = context.getPackageManager().getPackagesForUid(uid);
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
                return donations.request(request);
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
            return response;
        } catch (Exception error) {
            try { return new JSONObject().put("ok", false).put("error", error.getMessage()); }
            catch (Exception ignored) { return new JSONObject(); }
        }
    }
}
