package com.surcumference.fingerprint.plugin;

import com.surcumference.fingerprint.bean.PluginType;

public class PluginApp {

    private static PluginType sPluginType = null;

    public static void setup(PluginType pluginType) {
        String active = System.getProperty("fingerprintpay.active.backend");
        if (active != null && !active.equals(pluginType.name())) throw new IllegalStateException("Another FingerprintPay backend is active");
        System.setProperty("fingerprintpay.active.backend", pluginType.name());
        sPluginType = pluginType;
    }

    public static void setup(String pluginTypeName) {
        for (PluginType pluginType: PluginType.values()) {
            if (pluginType.name().equalsIgnoreCase(pluginTypeName)) {
                setup(pluginType);
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

}
