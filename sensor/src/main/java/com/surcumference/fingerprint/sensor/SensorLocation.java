package com.surcumference.fingerprint.sensor;

import android.content.Context;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Display;
import com.surcumference.fingerprintlocation.FingerprintLocation;
import com.surcumference.fingerprintlocation.core.Geometry;
import com.surcumference.fingerprintlocation.core.Resolution;
import java.util.LinkedHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SensorLocation {
    private static final ExecutorService WORK = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "FingerprintPay-sensor"); thread.setDaemon(true); return thread;
    });
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LinkedHashMap<String, Entry> CACHE = new LinkedHashMap<>();
    private static final class Entry {
        Resolution result;
        long expires;
        boolean pending;
        final java.util.List<Runnable> callbacks = new java.util.ArrayList<>();
    }
    private SensorLocation() { }
    public static Display display(Context context) {
        DisplayManager manager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        return manager == null ? null : manager.getDisplay(Display.DEFAULT_DISPLAY);
    }
    private static String key(Display display, int sensor) {
        Geometry.DisplayFrame frame = FingerprintLocation.displayFrame(display);
        return frame.uniqueId + ":" + frame.displayId + ":" + frame.rotation
                + ":" + frame.logicalWidth() + ":" + frame.logicalHeight()
                + ":" + frame.nativeWidth + ":" + frame.nativeHeight + ":" + sensor;
    }
    public static Resolution cached(Context context, Display display, int sensor, Runnable changed) {
        if (display == null || !display.isValid()) return null;
        final String key;
        try { key = key(display, sensor); } catch (RuntimeException unavailable) { return null; }
        synchronized (CACHE) {
            Entry entry = CACHE.get(key);
            if (entry == null) {
                if (CACHE.size() >= 8) CACHE.remove(CACHE.keySet().iterator().next());
                entry = new Entry(); CACHE.put(key, entry);
            }
            if (entry.expires > SystemClock.elapsedRealtime()) return entry.result;
            if (changed != null && !entry.callbacks.contains(changed) && entry.callbacks.size() < 8) entry.callbacks.add(changed);
            if (!entry.pending) {
                entry.pending = true;
                Entry task = entry;
                Context app = context.getApplicationContext();
                Context retained = app == null ? context : app;
                WORK.execute(() -> {
                    Resolution result = null;
                    try {
                        result = query(retained, display, sensor);
                        com.surcumference.fingerprint.util.log.L.d("Sensor geometry", retained.getPackageName(),
                                result.status, result.sensorType, result.bounds,
                                result.selected == null ? "none" : result.selected.evidence);
                        if (!display.isValid() || !key.equals(key(display, sensor))) result = null;
                    } catch (RuntimeException | LinkageError unavailable) {
                        result = new com.surcumference.fingerprintlocation.core.Probe().unavailable();
                    }
                    synchronized (CACHE) {
                        task.result = result; task.pending = false;
                        task.expires = SystemClock.elapsedRealtime() + (result != null && result.status == Resolution.Status.FOUND ? 300000 : 1000);
                        for (Runnable callback : task.callbacks) MAIN.post(callback);
                        task.callbacks.clear();
                    }
                });
            }
            return null;
        }
    }
    static Resolution query(Context context, Display display, int sensor) {
        Resolution result = FingerprintLocation.query(context, display, new FingerprintLocation.Options.Builder()
                .sensorId(sensor).vivoServiceTimeoutMillis(500).build());
        if (result.status == Resolution.Status.ACCESS_DENIED || result.status == Resolution.Status.UNAVAILABLE) {
            try {
                org.json.JSONObject response = com.surcumference.fingerprint.storage.ConfigStores.forContext(context)
                        .request(new org.json.JSONObject().put("op", "sensorProperties")
                                .put("package", context.getPackageName()).put("user", android.os.Process.myUid() / 100000));
                org.json.JSONObject properties = response.getJSONObject("properties");
                Resolution shared = FingerprintLocation.query(context, display, new FingerprintLocation.Options.Builder()
                        .sensorId(sensor).vivoServiceTimeoutMillis(500)
                        .properties(name -> properties.optString(name, "")).build());
                if (shared.status == Resolution.Status.FOUND) result = shared;
            } catch (Exception | LinkageError unavailable) { }
        }
        return result;
    }
    public static void forget(Runnable changed) {
        synchronized (CACHE) { for (Entry entry : CACHE.values()) entry.callbacks.remove(changed); }
    }
    public static boolean underDisplay(Context context) {
        Resolution result = cached(context, display(context), -1, null);
        return result != null && result.typeStatus == Resolution.TypeStatus.KNOWN && result.sensorType.underDisplay();
    }
    public static boolean underDisplay(Context context, org.json.JSONObject settings) {
        if (!settings.optBoolean("fingerprint_icon_auto", false) || settings.optBoolean("fingerprint_icon_detected", false))
            return !settings.optBoolean("fingerprint_icon", true);
        return underDisplay(context);
    }
    public static void applyDefault(Context context, org.json.JSONObject settings) throws org.json.JSONException {
        if (!settings.optBoolean("fingerprint_icon_auto", false)) return;
        Resolution result = cached(context, display(context), -1, null);
        boolean known = result != null && result.typeStatus == Resolution.TypeStatus.KNOWN;
        settings.put("fingerprint_icon", !(known && result.sensorType.underDisplay())).put("fingerprint_icon_detected", known);
    }
    public static Rect bounds(Resolution result) {
        if (result == null || result.status != Resolution.Status.FOUND || result.bounds == null) return null;
        return new Rect((int) Math.floor(result.bounds.left), (int) Math.floor(result.bounds.top),
                (int) Math.ceil(result.bounds.right), (int) Math.ceil(result.bounds.bottom));
    }
}
