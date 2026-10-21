package com.surcumference.fingerprint.xposed;

import static com.surcumference.fingerprint.Constant.PACKAGE_NAME_ALIPAY;
import static com.surcumference.fingerprint.Constant.PACKAGE_NAME_QQ;
import static com.surcumference.fingerprint.Constant.PACKAGE_NAME_TAOBAO;
import static com.surcumference.fingerprint.Constant.PACKAGE_NAME_UNIONPAY;
import static com.surcumference.fingerprint.Constant.PACKAGE_NAME_WECHAT;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.app.Instrumentation;
import android.content.pm.PackageManager;
import android.text.TextUtils;

import androidx.annotation.Keep;

import com.surcumference.fingerprint.BuildConfig;
import com.surcumference.fingerprint.plugin.xposed.AlipayPlugin;
import com.surcumference.fingerprint.plugin.xposed.QQPlugin;
import com.surcumference.fingerprint.plugin.xposed.TaobaoPlugin;
import com.surcumference.fingerprint.plugin.xposed.UnionPayPlugin;
import com.surcumference.fingerprint.plugin.xposed.WeChatPlugin;
import com.surcumference.fingerprint.util.log.L;
import com.surcumference.fingerprint.xposed.loader.XposedPluginLoader;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

@Keep
public class XposedInit implements IXposedHookZygoteInit, IXposedHookLoadPackage {


    public void initZygote(StartupParam startupParam) throws Throwable {
        com.surcumference.fingerprint.util.ModuleResources.setPayloadPath(startupParam.modulePath);
    }

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (BuildConfig.APPLICATION_ID.equals(lpparam.packageName)) {
            XposedHelpers.findAndHookMethod("com.surcumference.fingerprint.storage.XposedActivation", lpparam.classLoader,
                    "frameworkVersion", de.robv.android.xposed.XC_MethodReplacement.returnConstant(
                            de.robv.android.xposed.XposedBridge.getXposedVersion()));
            return;
        }
        if (!com.surcumference.fingerprint.storage.ConfigContract.PACKAGES.contains(lpparam.packageName)) {
            initGeneric(lpparam);
            return;
        }
        String active = System.getProperty("fingerprintpay.active.backend");
        if (active != null && !"Xposed".equals(active)) return;
        com.surcumference.fingerprint.plugin.PluginApp.setup(com.surcumference.fingerprint.bean.PluginType.Xposed, null);
        initHost(lpparam);
        initGeneric(lpparam);
    }

    private void initHost(final LoadPackageParam lpparam) {
        if (PACKAGE_NAME_QQ.equals(lpparam.packageName) && !lpparam.isFirstApplication) return;
        Class<?> plugin;
        switch (lpparam.packageName) {
            case PACKAGE_NAME_WECHAT: plugin = WeChatPlugin.class; break;
            case PACKAGE_NAME_ALIPAY: plugin = AlipayPlugin.class; break;
            case PACKAGE_NAME_TAOBAO: plugin = TaobaoPlugin.class; break;
            case PACKAGE_NAME_QQ: plugin = QQPlugin.class; break;
            case PACKAGE_NAME_UNIONPAY: plugin = UnionPayPlugin.class; break;
            default: return;
        }
        L.d("loaded: [" + lpparam.packageName + "] version:" + BuildConfig.VERSION_NAME);
        HostInitializer initializer = new HostInitializer(plugin, lpparam);
        XposedHelpers.findAndHookMethod(Instrumentation.class, "callApplicationOnCreate", Application.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                initializer.initialize((Application) param.args[0]);
            }
        });
        // Host Instrumentation overrides may bypass callApplicationOnCreate entirely.
        de.robv.android.xposed.XposedBridge.hookAllMethods(Activity.class, "performResume", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                Activity activity = (Activity) param.thisObject;
                if (initializer.initialize(activity.getApplication()))
                    com.surcumference.fingerprint.storage.PasswordEnrollment.onHostResumed(activity);
            }
        });
        XC_MethodHook pause = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                com.surcumference.fingerprint.storage.PasswordEnrollment.onHostPaused((Activity) param.thisObject);
            }
        };
        de.robv.android.xposed.XposedBridge.hookAllMethods(Activity.class, "performPause", pause);
        de.robv.android.xposed.XposedBridge.hookAllMethods(Activity.class, "performDestroy", pause);
    }

    private static final class HostInitializer {
        private final Class<?> plugin;
        private final LoadPackageParam param;
        private boolean loaded;
        HostInitializer(Class<?> plugin, LoadPackageParam param) { this.plugin = plugin; this.param = param; }
        boolean initialize(Application application) {
            if (loaded) return true;
            if (application == null) return false;
            try {
                XposedPluginLoader.load(plugin, application, param);
                loaded = true;
                android.util.Log.i("FingerprintPay", "Xposed host initialized: " + param.processName);
            } catch (Throwable error) { L.e(error); }
            return loaded;
        }
    }

    private void initGeneric(final LoadPackageParam lpparam) {
        //for multi user
        if ("android".equals(lpparam.processName)
                || PACKAGE_NAME_WECHAT.equals(lpparam.packageName)
                || PACKAGE_NAME_QQ.equals(lpparam.packageName)) {
            XposedHelpers.findAndHookMethod(ActivityManager.class, "checkComponentPermission", String.class, int.class, int.class, boolean.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    String permission = (String) param.args[0];
                    if (TextUtils.isEmpty(permission)) {
                        return;
                    }
                    if (!permission.contains("MANAGE_USERS")) {
                        return;
                    }
                    param.setResult(PackageManager.PERMISSION_GRANTED);
                }
            });
        }
    }
}
