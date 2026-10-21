package com.surcumference.fingerprint.plugin.magisk;

import android.app.Activity;
import android.app.Application;
import android.text.TextUtils;

import androidx.annotation.Keep;

import com.hjq.toast.Toaster;
import com.surcumference.fingerprint.BuildConfig;
import com.surcumference.fingerprint.Constant;
import com.surcumference.fingerprint.bean.PluginTarget;
import com.surcumference.fingerprint.network.update.UpdateFactory;
import com.surcumference.fingerprint.plugin.PluginApp;
import com.surcumference.fingerprint.plugin.PluginFactory;
import com.surcumference.fingerprint.plugin.inf.IAppPlugin;
import com.surcumference.fingerprint.util.ApplicationUtils;
import com.surcumference.fingerprint.util.Task;
import com.surcumference.fingerprint.util.Umeng;
import com.surcumference.fingerprint.util.log.L;

/**
 * Created by Jason on 2017/9/8.
 */

public class QQPlugin {

    /**
     * >= 4.2.0
     */
    @Keep
    public static void main(String niceName, String pluginTypeName) {
        L.d("Xposed plugin init version: " + BuildConfig.VERSION_NAME);
        PluginApp.setup(pluginTypeName, PluginTarget.QQ);
        Task.onApplicationReady(() -> init(niceName));
    }

    public static void init(String niceName) {
        try {
            Application application = ApplicationUtils.getApplication();
            IAppPlugin plugin = PluginFactory.loadPlugin(application, Constant.PACKAGE_NAME_QQ);
            Toaster.init(application);
            /**
             * FIX java.lang.NullPointerException: Attempt to invoke virtual method 'java.lang.Object java.lang.ref.WeakReference.get()' on a null object reference
             *     at com.tencent.mqq.shared_file_accessor.n.<init>(Unknown Source)
             *     at com.tencent.mqq.shared_file_accessor.SharedPreferencesProxyManager.getProxy(Unknown Source)
             *     at com.tencent.common.app.BaseApplicationImpl.getSharedPreferences(ProGuard:474)
             *     at com.tencent.common.app.QFixApplicationImpl.getSharedPreferences(ProGuard:247)
             *     at com.umeng.analytics.pro.ba.a(PreferenceWrapper.java:24)
             *     at com.umeng.analytics.pro.cc.f(StoreHelper.java:127)
             *     at com.umeng.analytics.AnalyticsConfig.getVerticalType(AnalyticsConfig.java:133)
             */
            Task.onMain(1000, ()-> Umeng.init(application));

            if (!TextUtils.isEmpty(niceName)
                && !niceName.contains(":")) {
                UpdateFactory.lazyUpdateWhenActivityAlive();
            }
            PluginFactory.register(application, plugin);
        } catch (Exception e) {
            L.e(e);
        }
    }
}
