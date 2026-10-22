package com.surcumference.fingerprint;

import android.app.Application;
import android.content.Context;

import com.hjq.toast.Toaster;
import com.surcumference.fingerprint.bean.PluginType;
import com.surcumference.fingerprint.plugin.PluginApp;

public class XApplication extends Application {

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        PluginApp.setup(PluginType.Xposed);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Toaster.init(this);
    }
}
