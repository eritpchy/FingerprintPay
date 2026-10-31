package com.surcumference.fingerprint.util;

import android.content.Context;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

final class FingerprintPromptPolicy {
    private final Object manager;
    private final Method setter;
    private final String packageName;
    private boolean closed;

    private FingerprintPromptPolicy(Object manager, Method setter, String packageName) {
        this.manager = manager;
        this.setter = setter;
        this.packageName = packageName;
    }

    static FingerprintPromptPolicy acquire(Context context, boolean systemPrompt) throws Exception {
        String packageName = context.getPackageName();
        FingerprintPromptPolicy policy = configure(context.getSystemService("fingerprint"), packageName, systemPrompt);
        if (policy != null) return policy;
        try {
            Class<?> type = Class.forName("android.hardware.fingerprint.OplusFingerprintManager", false, Context.class.getClassLoader());
            Object manager = type.getConstructor(Context.class).newInstance(context);
            policy = new FingerprintPromptPolicy(manager, vendorSetter(type), packageName);
            policy.set(!systemPrompt);
            return policy;
        } catch (ClassNotFoundException unavailable) {
            return null;
        }
    }

    static Method vendorSetter(Class<?> type) throws NoSuchMethodException {
        try { return type.getMethod("setSkipAuthPrompt", boolean.class, String.class); }
        catch (NoSuchMethodException restricted) {
            if (android.os.Build.VERSION.SDK_INT < 28) throw restricted;
            return org.lsposed.hiddenapibypass.HiddenApiBypass.getDeclaredMethod(
                    type, "setSkipAuthPrompt", boolean.class, String.class);
        }
    }

    static FingerprintPromptPolicy configure(Object manager, String packageName, boolean systemPrompt) throws Exception {
        if (manager == null) return null;
        final Method setter;
        try { setter = manager.getClass().getMethod("setSkipAuthPrompt", boolean.class, String.class); }
        catch (NoSuchMethodException unavailable) { return null; }
        FingerprintPromptPolicy policy = new FingerprintPromptPolicy(manager, setter, packageName);
        policy.set(!systemPrompt);
        return policy;
    }

    private void set(boolean skip) throws Exception {
        try { setter.invoke(manager, skip, packageName); }
        catch (InvocationTargetException failure) {
            throw new IllegalStateException("Cannot apply the selected fingerprint prompt mode", failure.getCause());
        }
    }

    void close() {
        if (closed) return;
        closed = true;
        try { set(false); }
        catch (Exception failure) { com.surcumference.fingerprint.util.log.L.e("Cannot release fingerprint prompt mode", failure); }
    }
}
