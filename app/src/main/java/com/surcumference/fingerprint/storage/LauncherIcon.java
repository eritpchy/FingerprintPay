package com.surcumference.fingerprint.storage;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

public final class LauncherIcon extends BroadcastReceiver {
    static void setVisible(Context context, boolean visible) {
        PackageManager manager = context.getPackageManager();
        ComponentName component = new ComponentName(context, "com.surcumference.fingerprint.Main");
        int state = visible ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        if (manager.getComponentEnabledSetting(component) != state)
            manager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP);
    }

    static void restore(LocalConfigStore store) {
        try { store.syncLauncherIcon(); }
        catch (Exception error) { Log.e("FingerprintPay", "Cannot restore launcher icon", error); }
    }

    @Override public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) restore(new LocalConfigStore(context));
    }
}
