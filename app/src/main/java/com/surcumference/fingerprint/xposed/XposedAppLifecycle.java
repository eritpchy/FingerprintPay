package com.surcumference.fingerprint.xposed;

import android.app.Activity;
import com.surcumference.fingerprint.plugin.inf.IAppPlugin;
import com.surcumference.fingerprint.util.ActivityContext;
import com.surcumference.fingerprint.util.ApplicationUtils;
import com.surcumference.fingerprint.util.Task;
import java.lang.ref.WeakReference;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

public final class XposedAppLifecycle {
    public static void install(IAppPlugin plugin) {
        Dispatch dispatch = new Dispatch(plugin);
        for (String method : new String[]{"performResume", "performPause", "performStop", "performDestroy"}) {
            XposedBridge.hookAllMethods(Activity.class, method, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (!param.hasThrowable()) dispatch.accept(method, (Activity) param.thisObject);
                }
            });
        }
        Task.onMain(() -> {
            Activity activity = ApplicationUtils.getCurrentActivity();
            if (ActivityContext.isAlive(activity)) dispatch.accept("performResume", activity);
        });
    }
    static final class Dispatch {
        private final IAppPlugin plugin;
        private WeakReference<Activity> resumed = new WeakReference<>(null);
        Dispatch(IAppPlugin plugin) { this.plugin = plugin; }
        void accept(String event, Activity activity) {
            try {
                if ("performResume".equals(event)) {
                    if (resumed.get() == activity) return;
                    resumed = new WeakReference<>(activity);
                    android.util.Log.i("FingerprintPay", "Payment lifecycle resumed: " + activity.getClass().getName());
                    plugin.onActivityResumed(activity);
                } else {
                    if (resumed.get() == activity) resumed.clear();
                    if ("performPause".equals(event)) plugin.onActivityPaused(activity);
                    else if ("performStop".equals(event)) plugin.onActivityStopped(activity);
                    else if ("performDestroy".equals(event)) plugin.onActivityDestroyed(activity);
                }
            } catch (Throwable error) { android.util.Log.e("FingerprintPay", "Payment lifecycle dispatch failed", error); }
        }
    }
    private XposedAppLifecycle() {}
}
