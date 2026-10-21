package com.surcumference.fingerprint.xposed.loader;

import android.app.Application;
import android.content.Context;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Created by Jason on 2017/9/8.
 */

public class XposedPluginLoader {

    private static Map<Class, Object> sPluginCache = new HashMap<>();

    public static synchronized void load(Class pluginClz, Application application, XC_LoadPackage.LoadPackageParam lpparam) throws Exception {
        if (sPluginCache.containsKey(pluginClz)) return;
        Object pluginObj = loadFromLocal(pluginClz);
        callPluginMain(pluginObj, application, lpparam);
        sPluginCache.put(pluginClz, pluginObj);
    }

    private static Object loadFromLocal(Class pluginClz) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, InstantiationException {
        return pluginClz.newInstance();
    }

    private static void callPluginMain(Object pluginObj, Context context, XC_LoadPackage.LoadPackageParam lpparam) throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InstantiationException, InvocationTargetException {
        Method method = pluginObj.getClass().getDeclaredMethod("main", Application.class, XC_LoadPackage.LoadPackageParam.class);
        method.invoke(pluginObj, context, lpparam);
    }

}
