package com.surcumference.fingerprint.storage;

import android.os.SystemClock;
import com.surcumference.fingerprint.util.DonateUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.json.JSONObject;

final class PendingDonations {
    private final Map<String, Entry> entries = new HashMap<>();
    private static final class Entry {
        final String id = UUID.randomUUID().toString(), channel;
        final long deadline = SystemClock.elapsedRealtime() + 15000;
        String status = "waiting";
        JSONObject result;
        Entry(String channel) { this.channel = channel; }
        void expire() {
            if (SystemClock.elapsedRealtime() >= deadline && ("waiting".equals(status) || "claimed".equals(status))) status = "expired";
        }
        JSONObject json() throws Exception {
            return new JSONObject().put("id", id).put("channel", channel).put("status", status).put("result", result);
        }
    }
    synchronized JSONObject request(JSONObject request) throws Exception {
        String pkg = request.getString("package"), op = request.getString("op");
        Entry entry = entries.get(pkg);
        if (entry != null) entry.expire();
        if ("beginDonation".equals(op)) {
            String channel = request.getString("channel");
            if (!("wechat".equals(channel) || "qq".equals(channel)) || !DonateUtils.packageName(channel).equals(pkg))
                throw new SecurityException("Invalid donation target");
            entry = new Entry(channel); entries.put(pkg, entry);
        } else if ("pendingDonation".equals(op)) {
            return new JSONObject().put("ok", true).put("pending", entry != null && "waiting".equals(entry.status) ? entry.json() : new JSONObject());
        } else {
            if (entry == null || !entry.id.equals(request.getString("id"))) throw new IllegalStateException("Donation request replaced");
            if ("claimDonation".equals(op)) {
                if (!"waiting".equals(entry.status)) throw new IllegalStateException("Donation request is no longer pending");
                entry.status = "claimed";
            } else if ("completeDonation".equals(op)) {
                if (!"claimed".equals(entry.status)) throw new IllegalStateException("Donation request is no longer active");
                JSONObject result = request.getJSONObject("result");
                boolean ok = result.getBoolean("ok");
                String mode = result.optString("mode");
                if (ok && !("payment".equals(mode) || ("qq".equals(entry.channel) && "contact".equals(mode))))
                    throw new IllegalArgumentException("Invalid donation result");
                entry.result = new JSONObject().put("ok", ok);
                if (ok) entry.result.put("mode", mode);
                else entry.result.put("error", "此版本无法打开赞助页面，请使用收款码或在应用内手动转账");
                entry.status = "completed";
            } else if ("cancelDonation".equals(op)) {
                if ("waiting".equals(entry.status) || "claimed".equals(entry.status)) entry.status = "canceled";
            } else if (!"donationStatus".equals(op)) throw new IllegalArgumentException("Unknown donation operation");
        }
        return new JSONObject().put("ok", true).put("pending", entry.json());
    }
}
