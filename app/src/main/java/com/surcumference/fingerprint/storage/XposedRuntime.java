package com.surcumference.fingerprint.storage;

import android.content.Context;
import org.json.JSONObject;

public final class XposedRuntime {
    public static JSONObject read(Context context, String pkg) throws Exception {
        if (!ConfigContract.PACKAGES.contains(pkg)) throw new IllegalArgumentException("Unknown application");
        int api = XposedActivation.frameworkVersion();
        JSONObject module = new JSONObject().put("active", api > 0).put("frameworkVersion", api)
                .put("framework", XposedActivation.frameworkName());
        return new JSONObject().put("module", module);
    }

    private XposedRuntime() {}
}
