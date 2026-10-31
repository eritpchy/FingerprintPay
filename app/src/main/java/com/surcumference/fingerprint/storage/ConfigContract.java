package com.surcumference.fingerprint.storage;

import com.surcumference.fingerprint.BuildConfig;
import java.util.Arrays;
import java.util.List;
import org.json.JSONObject;

@androidx.annotation.Keep
public final class ConfigContract {
    public static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".config";
    public static final String SOCKET = "fingerprintpay.config.v1";
    public static final List<String> PACKAGES = Arrays.asList("com.tencent.mm", "com.eg.android.AlipayGphone",
            "com.taobao.taobao", "com.tencent.mobileqq", "com.unionpay");
    public static final List<String> OPTIONS = Arrays.asList("scope", "switch_on1", "fingerprint_icon",
            "biometric_api", "hide_fingerprint_dialog", "volume_down_monitor_enabled");
    public static final String TEE_FORMAT = "tee-aes-gcm-v2";
    public static final String SOFTWARE_FORMAT = "software-aes-gcm-v1";
    public static String teeAlias(String backend, int user, String pkg, String id) {
        return "fingerprintpay.tee.v2." + backend + "." + user + "." + pkg + "." + id;
    }
    public static void validateTeeRecord(JSONObject record, String backend, int user, String pkg, String id) throws Exception {
        if (!TEE_FORMAT.equals(record.getString("format"))) throw new IllegalArgumentException("Invalid TEE password record");
        validatePasswordRecord(record, backend, user, pkg, id);
    }
    public static String softwareAlias(String backend, int user, String pkg, String id) {
        return "fingerprintpay.software.v1." + backend + "." + user + "." + pkg + "." + id;
    }
    public static void validatePasswordRecord(JSONObject record, String backend, int user, String pkg, String id) throws Exception {
        String format = record.getString("format");
        String alias = TEE_FORMAT.equals(format) ? teeAlias(backend, user, pkg, id)
                : SOFTWARE_FORMAT.equals(format) ? softwareAlias(backend, user, pkg, id) : "";
        if (!id.matches("[0-9a-f]{32}") || alias.isEmpty() || !alias.equals(record.getString("keyAlias"))
                || !record.getString("iv").matches("[0-9a-f]{24}")
                || !record.getString("data").matches("[0-9a-f]{34,2080}") || record.getString("data").length() % 2 != 0)
            throw new IllegalArgumentException("Invalid password record");
    }
    public static boolean global(String key) {
        return "show_icon".equals(key) || "license_agree".equals(key) || "auto_update".equals(key)
                || "skip_xposed".equals(key) || "skip_zygisk".equals(key);
    }
    public static JSONObject defaults() throws Exception {
        return new JSONObject().put("scope", true).put("switch_on1", false)
                .put("fingerprint_icon", true).put("fingerprint_icon_auto", true).put("biometric_api", false).put("hide_fingerprint_dialog", false)
                .put("volume_down_monitor_enabled", true).put("credentialVersion", 0)
                .put("hasPassword", false);
    }
    public static void validate(String key, Object value) {
        if (!OPTIONS.contains(key) && !global(key)) throw new IllegalArgumentException("Unknown option");
        if (key.startsWith("skip_")) {
            if (!(value instanceof String) || ((String) value).length() > 128) throw new IllegalArgumentException("Invalid version");
        } else if (!(value instanceof Boolean)) throw new IllegalArgumentException("Expected boolean");
    }
    private ConfigContract() {}
}
