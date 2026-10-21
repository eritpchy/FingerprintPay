package com.surcumference.fingerprint.plugin;

import android.content.Context;

import com.surcumference.fingerprint.Constant;
import com.surcumference.fingerprint.plugin.impl.alipay.AlipayBasePlugin;
import com.surcumference.fingerprint.plugin.impl.qq.QQBasePlugin;
import com.surcumference.fingerprint.plugin.impl.qq.QQBasePlugin_8_2_11;
import com.surcumference.fingerprint.plugin.impl.taobao.TaobaoBasePlugin;
import com.surcumference.fingerprint.plugin.impl.unionpay.UnionPayBasePlugin;
import com.surcumference.fingerprint.plugin.impl.wechat.WeChatBasePlugin;
import com.surcumference.fingerprint.plugin.inf.IAppPlugin;
import com.surcumference.fingerprint.util.ApplicationUtils;

public class PluginFactory {
    public static void register(android.app.Application application, IAppPlugin plugin) {
        application.registerActivityLifecycleCallbacks(plugin);
        android.app.Activity current = ApplicationUtils.getCurrentActivity();
        if (current != null) plugin.onActivityResumed(current);
        android.util.Log.i("FingerprintPay", "Host callbacks registered: " + application.getPackageName()
                + " user " + android.os.Process.myUid() / 100000);
        com.surcumference.fingerprint.util.Task.onBackground(() -> {
            try {
                org.json.JSONObject value = com.surcumference.fingerprint.util.Config.from(application).snapshot();
                android.util.Log.i("FingerprintPay", "Host configuration backend " + PluginApp.getCurrentType()
                        + " revision " + value.getLong("revision") + " scope "
                        + value.getJSONObject("settings").optBoolean("scope"));
            } catch (Exception e) {
                android.util.Log.e("FingerprintPay", "Host configuration unavailable: " + e.getClass().getSimpleName());
            }
        });
    }

    public static IAppPlugin loadPlugin(Context context, String packageName) {
        com.surcumference.fingerprint.storage.PasswordEnrollment.install(context);
        int versionCode = ApplicationUtils.getPackageVersionCode(context, packageName);
        switch (packageName) {
            case Constant.PACKAGE_NAME_QQ:
                if (versionCode <= Constant.QQ.QQ_VERSION_CODE_8_2_11) {
                    return new QQBasePlugin_8_2_11();
                }
                return new QQBasePlugin();
            case Constant.PACKAGE_NAME_ALIPAY:
                return new AlipayBasePlugin();
            case Constant.PACKAGE_NAME_TAOBAO:
                return new TaobaoBasePlugin();
            case Constant.PACKAGE_NAME_UNIONPAY:
                return new UnionPayBasePlugin();
            case Constant.PACKAGE_NAME_WECHAT:
                return new WeChatBasePlugin();
        }
        throw new RuntimeException("Unsupported package: " + packageName);
    }
}
