package com.surcumference.fingerprint.util;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Build;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public final class ActivityContext {
    private ActivityContext() {}

    public static Activity find(Context context) {
        Set<Context> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        while (context != null && seen.add(context)) {
            if (context instanceof Activity) {
                Activity activity = (Activity) context;
                return isAlive(activity) ? activity : null;
            }
            if (!(context instanceof ContextWrapper)) {
                return null;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    public static boolean isAlive(Activity activity) {
        return activity != null && !activity.isFinishing()
                && (Build.VERSION.SDK_INT < 17 || !activity.isDestroyed());
    }
}
