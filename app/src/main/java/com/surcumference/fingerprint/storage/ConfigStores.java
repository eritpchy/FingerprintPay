package com.surcumference.fingerprint.storage;

import android.content.Context;
import android.content.ContentProviderClient;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.Uri;
import android.os.Bundle;
import com.surcumference.fingerprint.bean.PluginType;
import com.surcumference.fingerprint.plugin.PluginApp;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

public final class ConfigStores {
    private static final Uri VISIBILITY_URI = Uri.parse("content://" + ConfigContract.AUTHORITY + "/runtime");
    private static boolean visibilityPersisted;

    public static void grantHostVisibility(Context context) {
        for (String pkg : ConfigContract.PACKAGES) {
            try {
                context.getPackageManager().getApplicationInfo(pkg, 0);
                context.grantUriPermission(pkg, VISIBILITY_URI, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {}
        }
    }

    public static ConfigStore forContext(Context context) {
        PluginType type;
        try { type = PluginApp.getCurrentType(); } catch (NullPointerException ignored) { type = PluginType.Xposed; }
        if (type != PluginType.Xposed) return ConfigStores::socketRequest;
        Context app = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        if (!visibilityPersisted && ConfigContract.PACKAGES.contains(context.getPackageName())) {
            try {
                app.getContentResolver().takePersistableUriPermission(VISIBILITY_URI, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                visibilityPersisted = true;
            } catch (SecurityException ignored) { /* Older management APKs do not offer a grant. */ }
        }
        return request -> {
            String raw = request.toString();
            JSONObject response;
            try {
                response = providerRequest(app, raw);
            } catch (Exception unavailable) {
                // A dispatched mutation may have committed even when its response was lost.
                if (!(unavailable instanceof ProviderUnavailable) && !repeatable(request.optString("op")))
                    throw new IllegalStateException("Configuration result unavailable; refresh before retrying", unavailable);
                try {
                    return ConfigServiceClient.request(app, request);
                } catch (Exception serviceUnavailable) {
                    if (serviceUnavailable instanceof RequestRejected || serviceUnavailable instanceof SecurityException
                            || !repeatable(request.optString("op"))) throw serviceUnavailable;
                    if (ConfigContract.PACKAGES.contains(context.getPackageName())) {
                        try {
                            JSONObject local = XposedConfigSnapshot.read(context, request);
                            if (local != null) return local;
                        } catch (LinkageError | Exception snapshotUnavailable) {
                            serviceUnavailable.addSuppressed(snapshotUnavailable);
                        }
                    }
                    serviceUnavailable.addSuppressed(unavailable);
                    throw serviceUnavailable;
                }
            }
            return checked(response);
        };
    }
    private static JSONObject providerRequest(Context context, String request) throws Exception {
        ContentProviderClient client = context.getContentResolver().acquireUnstableContentProviderClient(
                Uri.parse("content://" + ConfigContract.AUTHORITY));
        if (client == null) throw new ProviderUnavailable();
        try {
            Bundle args = new Bundle();
            args.putString("request", request);
            Bundle result = client.call("request", null, args);
            if (result == null || !result.containsKey("response")) throw new IllegalStateException("Empty configuration response");
            JSONObject response = new JSONObject(result.getString("response"));
            if (!(response.opt("ok") instanceof Boolean)) throw new IllegalStateException("Invalid configuration response");
            return response;
        } finally {
            client.release();
        }
    }
    static boolean repeatable(String op) {
        return "read".equals(op) || "sensorProperties".equals(op) || "credential".equals(op) || "claimPassword".equals(op)
                || "passwordStatus".equals(op) || "pendingDonation".equals(op) || "donationStatus".equals(op);
    }
    private static final class ProviderUnavailable extends Exception {}
    private static final class RequestRejected extends IllegalStateException {
        RequestRejected(String message) { super(message); }
    }
    public static JSONObject checked(JSONObject response) {
        if (!response.optBoolean("ok")) throw new RequestRejected(response.optString("error", "Configuration unavailable"));
        return response;
    }
    @androidx.annotation.Keep
    public static JSONObject socketRequest(JSONObject request) throws Exception {
        try (LocalSocket socket = new LocalSocket()) {
            socket.connect(new LocalSocketAddress(ConfigContract.SOCKET, LocalSocketAddress.Namespace.ABSTRACT));
            socket.setSoTimeout(1500);
            if (socket.getPeerCredentials().getUid() != 0) throw new SecurityException("Untrusted configuration service");
            byte[] data = request.toString().getBytes(StandardCharsets.UTF_8);
            if (data.length > 65536) throw new IllegalArgumentException("Request too large");
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.writeInt(data.length);
            out.write(data);
            out.flush();
            DataInputStream in = new DataInputStream(socket.getInputStream());
            int size = in.readInt();
            if (size < 1 || size > 65536) throw new IllegalStateException("Invalid response length");
            byte[] response = new byte[size];
            in.readFully(response);
            return checked(new JSONObject(new String(response, StandardCharsets.UTF_8)));
        }
    }
    private ConfigStores() {}
}
