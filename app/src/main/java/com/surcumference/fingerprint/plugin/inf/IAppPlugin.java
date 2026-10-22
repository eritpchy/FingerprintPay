package com.surcumference.fingerprint.plugin.inf;

import android.app.Application;

public interface IAppPlugin extends Application.ActivityLifecycleCallbacks {

    boolean getMockCurrentUser();
}
