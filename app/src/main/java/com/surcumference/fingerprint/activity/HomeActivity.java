package com.surcumference.fingerprint.activity;

import android.os.Bundle;
import android.content.Intent;
import com.surcumference.fingerprint.network.update.UpdateFactory;
import com.surcumference.fingerprint.util.Config;
import com.surcumference.fingerprint.util.Task;

@androidx.annotation.Keep
public final class HomeActivity extends ConfigActivity {
    private Task.Handle updateCheck;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (ACTION_CHECK_UPDATES.equals(getIntent().getAction())) { checkForUpdates(); return; }
        updateCheck = Task.onMain(1000, () -> {
            if (Config.from(this).isAutoUpdate()) UpdateFactory.doUpdateCheck(this);
        });
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (ACTION_CHECK_UPDATES.equals(intent.getAction())) checkForUpdates();
    }
    @Override protected void checkForUpdates() {
        if (updateCheck != null) updateCheck.cancel();
        super.checkForUpdates();
    }
    @Override public void onDestroy() {
        if (updateCheck != null) updateCheck.cancel();
        super.onDestroy();
    }
}
