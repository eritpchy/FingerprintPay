package com.surcumference.fingerprint.util;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.AssetManager;
import android.content.res.Resources;
import com.surcumference.fingerprint.BuildConfig;
import java.io.File;
import java.lang.reflect.Method;

@androidx.annotation.Keep
public final class ModuleResources {
    private static String payloadPath;
    private static Context context;
    private ModuleResources() {}
    public static synchronized void setPayloadPath(String path) {
        if (context != null) throw new IllegalStateException("Module resources already initialized");
        payloadPath = path;
    }
    public static synchronized Context context(Context host) {
        if (context != null) return context;
        if (host == null) throw new IllegalStateException("Application is not ready");
        try {
            final AssetManager assets;
            final Resources resources;
            if (payloadPath == null) {
                Context plugin = host.createPackageContext(BuildConfig.APPLICATION_ID, Context.CONTEXT_IGNORE_SECURITY);
                assets = plugin.getAssets();
                resources = plugin.getResources();
            } else {
                if (!new File(payloadPath).isFile()) throw new IllegalStateException("Module payload missing");
                assets = AssetManager.class.getDeclaredConstructor().newInstance();
                Method add = AssetManager.class.getDeclaredMethod("addAssetPath", String.class);
                int cookie = (Integer) add.invoke(assets, payloadPath);
                if (cookie == 0) throw new IllegalStateException("Cannot open module payload");
                resources = new Resources(assets, host.getResources().getDisplayMetrics(), host.getResources().getConfiguration());
            }
            ClassLoader loader = ModuleResources.class.getClassLoader();
            for (Class<?> dependency : new Class<?>[]{com.hjq.http.EasyHttp.class, okhttp3.OkHttp.class,
                    kotlin.Unit.class, androidx.lifecycle.LifecycleRegistry.class}) {
                if (dependency.getClassLoader() != loader) {
                    throw new IllegalStateException("Unexpected module dependency loader: " + dependency.getName());
                }
            }
            try (java.io.InputStream ignored = assets.open("PublicSuffixDatabase.list")) {}
            context = new ContextWrapper(host.getApplicationContext()) {
                @Override public AssetManager getAssets() { return assets; }
                @Override public Resources getResources() { return resources; }
                @Override public Context getApplicationContext() { return this; }
                @Override public ClassLoader getClassLoader() { return ModuleResources.class.getClassLoader(); }
            };
            return context;
        } catch (Exception e) { throw new IllegalStateException("Cannot initialize module resources", e); }
    }
}
