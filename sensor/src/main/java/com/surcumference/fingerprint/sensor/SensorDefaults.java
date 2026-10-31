package com.surcumference.fingerprint.sensor;

import android.content.Context;
import android.os.Build;
import android.os.Looper;
import com.surcumference.fingerprintlocation.FingerprintLocation;
import com.surcumference.fingerprintlocation.core.Resolution;

public final class SensorDefaults {
    public static void main(String[] args) throws Exception {
        if (android.os.Process.myUid() != 0) throw new SecurityException("Module service required");
        Looper.prepareMainLooper();
        Class<?> activityThread = Class.forName("android.app.ActivityThread");
        Object thread = activityThread.getMethod("systemMain").invoke(null);
        Context context = (Context) activityThread.getMethod("getSystemContext").invoke(thread);
        new android.os.Handler(Looper.getMainLooper()).postDelayed(() -> System.exit(1), 5000);
        new Thread(() -> {
            try {
                Resolution result = FingerprintLocation.query(context, SensorLocation.display(context),
                        new FingerprintLocation.Options.Builder().vivoServiceTimeoutMillis(500).build());
                String boot;
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader("/proc/sys/kernel/random/boot_id"))) { boot = reader.readLine(); }
                System.out.println(new org.json.JSONObject().put("fingerprint", Build.FINGERPRINT).put("bootId", boot)
                        .put("sensorType", result.sensorType.name()).put("typeStatus", result.typeStatus.name())
                        .put("fingerprint_icon", !(result.typeStatus == Resolution.TypeStatus.KNOWN && result.sensorType.underDisplay())));
                System.exit(0);
            } catch (Exception | LinkageError unavailable) { System.exit(1); }
        }, "FingerprintPay-default").start();
        Looper.loop();
    }
    private SensorDefaults() { }
}
