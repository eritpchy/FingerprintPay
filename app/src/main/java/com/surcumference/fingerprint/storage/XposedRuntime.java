package com.surcumference.fingerprint.storage;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import com.surcumference.fingerprint.BuildConfig;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;

public final class XposedRuntime {
    private static final String ACTION = BuildConfig.APPLICATION_ID + ".action.XPOSED_RUNTIME";
    private static volatile String hostState;
    private static boolean registered;

    public static synchronized void install(Context context, boolean ready) {
        if (!ConfigContract.PACKAGES.contains(context.getPackageName())) return;
        try (FileInputStream input = new FileInputStream("/proc/self/cmdline")) {
            byte[] data = new byte[256]; int count = input.read(data), end = 0;
            while (end < count && data[end] != 0) end++;
            if (!context.getPackageName().equals(new String(data, 0, end, StandardCharsets.UTF_8))) return;
        } catch (Exception ignored) { return; }
        hostState = ready ? "active" : "failed";
        if (registered) return;
        Context app = context.getApplicationContext();
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ignored, Intent intent) {
                if (!ACTION.equals(intent.getAction()) || !isOrderedBroadcast()) return;
                try {
                    setResult(Activity.RESULT_OK, new JSONObject().put("state", hostState)
                            .put("package", app.getPackageName()).put("version", BuildConfig.VERSION_NAME)
                            .put("versionCode", BuildConfig.VERSION_CODE).toString(), null);
                } catch (Exception ignoredError) {}
            }
        };
        if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(receiver, new IntentFilter(ACTION), Context.RECEIVER_EXPORTED);
        else app.registerReceiver(receiver, new IntentFilter(ACTION));
        registered = true;
    }

    public static JSONObject read(Context context, String pkg) throws Exception {
        if (!ConfigContract.PACKAGES.contains(pkg)) throw new IllegalArgumentException("Unknown application");
        int api = XposedActivation.frameworkVersion();
        JSONObject module = new JSONObject().put("active", api > 0).put("frameworkVersion", api);
        JSONObject target;
        try { target = readTarget(context, pkg, api > 0); }
        catch (Exception error) { target = new JSONObject().put("package", pkg).put("state", "unknown"); }
        return new JSONObject().put("module", module).put("target", target);
    }

    private static JSONObject readTarget(Context context, String pkg, boolean activated) throws Exception {
        JSONObject result = new JSONObject().put("package", pkg).put("state", "no_response");
        PackageManager manager = context.getPackageManager();
        ApplicationInfo info;
        try { info = manager.getApplicationInfo(pkg, 0); }
        catch (PackageManager.NameNotFoundException error) { return result.put("state", "missing"); }
        if (!info.enabled) return result.put("state", "disabled");
        if (Build.VERSION.SDK_INT >= 24 && (info.flags & ApplicationInfo.FLAG_SUSPENDED) != 0)
            return result.put("state", "suspended");
        if (manager.getLaunchIntentForPackage(pkg) == null) return result.put("state", "unlaunchable");
        if (!activated || Looper.myLooper() == Looper.getMainLooper()) return result;
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> response = new AtomicReference<>();
        Intent query = new Intent(ACTION).setPackage(pkg).addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY | Intent.FLAG_RECEIVER_FOREGROUND);
        context.sendOrderedBroadcast(query, null, new BroadcastReceiver() {
            @Override public void onReceive(Context ignored, Intent intent) {
                if (getResultCode() == Activity.RESULT_OK) response.set(getResultData());
                done.countDown();
            }
        }, new Handler(Looper.getMainLooper()), Activity.RESULT_CANCELED, null, null);
        if (!done.await(1500, TimeUnit.MILLISECONDS) || response.get() == null) return result;
        JSONObject host = new JSONObject(response.get());
        if (!pkg.equals(host.optString("package"))) return result;
        String state = host.optString("state");
        if (!"active".equals(state) && !"failed".equals(state)) return result;
        if (!BuildConfig.VERSION_NAME.equals(host.optString("version")) || BuildConfig.VERSION_CODE != host.optInt("versionCode"))
            state = "outdated";
        return result.put("state", state);
    }

    private XposedRuntime() {}
}
