package com.surcumference.fingerprint.plugin;

import com.surcumference.fingerprint.bean.PluginTarget;
import com.surcumference.fingerprint.bean.PluginType;

public class PluginApp {

    private static PluginType sPluginType = null;
    private static PluginTarget sPluginTarget = null;

    public static void setup(PluginType pluginType, PluginTarget pluginTarget) {
        String active = System.getProperty("fingerprintpay.active.backend");
        if (active != null && !active.equals(pluginType.name())) throw new IllegalStateException("Another FingerprintPay backend is active");
        System.setProperty("fingerprintpay.active.backend", pluginType.name());
        sPluginType = pluginType;
        sPluginTarget = pluginTarget;
    }

    public static void setup(String pluginTypeName, PluginTarget pluginTarget) {
        for (PluginType pluginType: PluginType.values()) {
            if (pluginType.name().equalsIgnoreCase(pluginTypeName)) {
                setup(pluginType, pluginTarget);
                return;
            }
        }
        throw new RuntimeException("Unsupported plugin type:" + pluginTypeName);
    }

    public static PluginType getCurrentType() {
        if (sPluginType == null) {
            throw new NullPointerException("PluginApp not initial yet");
        }
        return sPluginType;
    }

    public static PluginTarget getCurrentTarget() {
        if (sPluginTarget == null) {
            throw new NullPointerException("PluginApp not initial yet");
        }
        return sPluginTarget;
    }
}
