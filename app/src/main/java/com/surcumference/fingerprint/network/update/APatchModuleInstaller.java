package com.surcumference.fingerprint.network.update;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import dalvik.system.PathClassLoader;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.IdentityHashMap;

public final class APatchModuleInstaller {
    public static final String PACKAGE = "me.bmax.apatch";
    private APatchModuleInstaller() {}

    public static Intent prepare(Context context, Uri uri) throws Exception {
        String apk = context.getPackageManager().getApplicationInfo(PACKAGE, 0).sourceDir;
        ClassLoader loader = new PathClassLoader(apk, ClassLoader.getSystemClassLoader());
        Class<?> graphClass = loader.loadClass("com.ramcosta.composedestinations.generated.navgraphs.RootNavGraph");
        Object graph = graphClass.getField("INSTANCE").get(null);
        for (Object destination : (Iterable<?>) graphClass.getMethod("getDestinations").invoke(graph)) {
            Class<?> type = destination.getClass();
            if (!"install_screen".equals(type.getMethod("getBaseRoute").invoke(destination))) continue;
            Serializable moduleType = findModuleType(type.getMethod("getArguments").invoke(destination), 0, new IdentityHashMap<>());
            if (moduleType == null) throw new IOException("APatch module installation arguments are unsupported");
            String rootRoute = (String) graphClass.getMethod("getRoute").invoke(graph);
            String installRoute = (String) type.getMethod("getRoute").invoke(destination);
            Bundle arguments = new Bundle();
            arguments.putParcelable("uri", uri);
            arguments.putSerializable("type", moduleType);
            ArrayList<Bundle> stack = new ArrayList<>();
            stack.add(new Bundle());
            stack.add(arguments);
            Intent intent = new Intent().setClassName(PACKAGE, PACKAGE + ".ui.MainActivity")
                    .putExtra("android-support-nav:controller:deepLinkIds", new int[]{routeId(rootRoute), routeId(installRoute)})
                    .putParcelableArrayListExtra("android-support-nav:controller:deepLinkArgs", stack)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(ClipData.newRawUri("FingerprintPay module", uri));
            return intent;
        }
        throw new IOException("This APatch version does not expose a compatible module installation destination");
    }

    private static int routeId(String route) { return ("android-app://androidx.navigation/" + route).hashCode(); }

    // Resolve the installed manager's enum instead of depending on obfuscated class names.
    private static Serializable findModuleType(Object value, int depth, IdentityHashMap<Object, Boolean> seen) throws Exception {
        if (value == null || depth > 5 || seen.put(value, true) != null) return null;
        if (value instanceof Class<?>) {
            Class<?> type = (Class<?>) value;
            if (!type.isEnum()) return null;
            Serializable result = null;
            boolean kernel = false;
            for (Object constant : type.getEnumConstants()) {
                String name = ((Enum<?>) constant).name();
                if ("APM".equals(name)) result = (Serializable) constant;
                if ("KPM".equals(name)) kernel = true;
            }
            return kernel ? result : null;
        }
        if (value instanceof Iterable<?>) {
            for (Object element : (Iterable<?>) value) {
                Serializable found = findModuleType(element, depth + 1, seen);
                if (found != null) return found;
            }
            return null;
        }
        Class<?> type = value.getClass();
        if (type.getName().startsWith("java.") || type.getName().startsWith("android.") || type.getName().startsWith("kotlin.")) return null;
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
            field.setAccessible(true);
            Serializable found = findModuleType(field.get(value), depth + 1, seen);
            if (found != null) return found;
        }
        return null;
    }
}
