package com.surcumference.fingerprint.plugin.magisk;

import android.app.Application;

import androidx.annotation.Keep;

import com.hjq.toast.Toaster;
import com.surcumference.fingerprint.BuildConfig;
import com.surcumference.fingerprint.Constant;
import com.surcumference.fingerprint.network.update.UpdateFactory;
import com.surcumference.fingerprint.plugin.PluginApp;
import com.surcumference.fingerprint.plugin.PluginFactory;
import com.surcumference.fingerprint.plugin.inf.IAppPlugin;
import com.surcumference.fingerprint.util.ApplicationUtils;
import com.surcumference.fingerprint.util.Task;
import com.surcumference.fingerprint.util.Umeng;
import com.surcumference.fingerprint.util.log.L;

/**
 * Created by Jason on 2022/1/19.
 */
public class UnionPayPlugin {

    /**
     * >= 4.2.0
     */
    @Keep
    public static void main(String niceName, String pluginTypeName) {
        L.d("Xposed plugin init version: " + BuildConfig.VERSION_NAME);
        PluginApp.setup(pluginTypeName);
        Task.onApplicationReady(UnionPayPlugin::init);
    }

    public static void init() {
        Application application = ApplicationUtils.getApplication();
        IAppPlugin plugin = PluginFactory.loadPlugin(application, Constant.PACKAGE_NAME_UNIONPAY);
        Toaster.init(application);
        Task.onMain(1000, ()-> Umeng.init(application));

        UpdateFactory.lazyUpdateWhenActivityAlive();
        PluginFactory.register(application, plugin);
    }
}
