package com.surcumference.fingerprint.sensor;

import org.json.JSONObject;

public final class SensorProperties {
    private static final String[] NAMES = {
            "persist.vendor.fingerprint.type", "ro.vendor.fingerprint.type",
            "persist.vendor.fingerprint.sensor_type", "sys.fingerprint.boot", "vendor.fingerprint.boot",
            "persist.sys.fptype", "persist.vendor.fingerprint.optical.iconsize",
            "persist.vendor.fingerprint.optical.iconlocation", "ro.hardware.fp.fod", "ro.hardware.fp.udfps",
            "persist.sys.fp.fod.location.X_Y", "persist.sys.fp.fod.size.width_height",
            "persist.vendor.sys.fp.fod.location.X_Y", "persist.vendor.sys.fp.fod.size.width_height",
            "persist.vendor.fingerprint.sensor_location", "ro.vendor.fingerprint.sensor_location",
            "ro.optical_fingerprint_support"
    };

    public static JSONObject read() throws Exception {
        java.lang.reflect.Method get = Class.forName("android.os.SystemProperties").getMethod("get", String.class);
        JSONObject properties = new JSONObject();
        for (String name : NAMES) {
            String value = "";
            try { value = (String) get.invoke(null, name); }
            catch (ReflectiveOperationException | RuntimeException unavailable) { }
            properties.put(name, value);
        }
        return new JSONObject().put("ok", true).put("properties", properties);
    }

    public static JSONObject snapshot(android.content.Context context) throws Exception {
        return read().put("fingerprint", android.os.Build.FINGERPRINT)
                .put("boot", android.provider.Settings.Global.getInt(context.getContentResolver(), "boot_count", -1));
    }

    public static JSONObject fromSnapshot(android.content.Context context, JSONObject snapshot) throws Exception {
        int boot = android.provider.Settings.Global.getInt(context.getContentResolver(), "boot_count", -1);
        if (snapshot == null || boot < 0 || snapshot.optInt("boot", -1) != boot
                || !android.os.Build.FINGERPRINT.equals(snapshot.optString("fingerprint"))) return null;
        JSONObject properties = new JSONObject(), saved = snapshot.getJSONObject("properties");
        for (String name : NAMES) properties.put(name, saved.optString(name, ""));
        return new JSONObject().put("ok", true).put("properties", properties);
    }

    private SensorProperties() { }
}
