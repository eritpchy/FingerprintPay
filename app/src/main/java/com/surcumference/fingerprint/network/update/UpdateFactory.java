package com.surcumference.fingerprint.network.update;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;


import com.hjq.toast.Toaster;
import com.surcumference.fingerprint.BuildConfig;
import com.surcumference.fingerprint.Constant;
import com.surcumference.fingerprint.Lang;
import com.surcumference.fingerprint.R;
import com.surcumference.fingerprint.bean.PluginType;
import com.surcumference.fingerprint.bean.UpdateInfo;
import com.surcumference.fingerprint.network.inf.UpdateResultListener;
import com.surcumference.fingerprint.network.update.github.GithubUpdateChecker;
import com.surcumference.fingerprint.plugin.PluginApp;
import com.surcumference.fingerprint.util.ApplicationUtils;
import com.surcumference.fingerprint.util.ActivityContext;
import com.surcumference.fingerprint.util.Config;
import com.surcumference.fingerprint.util.FileUtils;
import com.surcumference.fingerprint.util.Task;
import com.surcumference.fingerprint.util.log.L;
import com.surcumference.fingerprint.view.DownloadView;
import com.surcumference.fingerprint.view.MessageView;
import com.surcumference.fingerprint.view.UpdateInfoView;


import java.io.File;
import java.util.Locale;

/**
 * Created by Jason on 2017/9/10.
 */

public class UpdateFactory {

    public static void doUpdateCheck(final Context context) {
        doUpdateCheck(context, true, false);
    }

    public static void doUpdateCheck(final Context context, final boolean quite, final boolean dontSkip) {
        doUpdateCheck(context, quite, dontSkip, PluginApp.getCurrentType());
    }

    public static void doUpdateCheck(final Context context, final boolean quite, final boolean dontSkip, final PluginType type) {
        doUpdateCheck(context, quite, dontSkip, type, BuildConfig.VERSION_NAME);
    }

    public static void doUpdateCheck(final Context context, final boolean quite, final boolean dontSkip, final PluginType type, final String localVersion) {
        final boolean sameBackend = type == PluginApp.getCurrentType();
        if (!quite) {
            Toaster.showLong(Lang.getString(R.id.toast_checking_update));
        }
        try {
            new GithubUpdateChecker(localVersion, Constant.UPDATE_URLS, type,
                    new UpdateResultListener() {
                @Override
                public void onNoUpdate() {
                    if (!quite) {
                        Toaster.showLong(Lang.getString(R.id.toast_no_update));
                    }
                }

                @Override
                public void onNetErr(Exception e) {
                    if (!quite) {
                        Toaster.showLong(Lang.getString(R.id.toast_check_update_fail_net_err));
                    }
                }

                @Override
                public void onHasUpdate(UpdateInfo updateInfo) {
                    if (!dontSkip && sameBackend) {
                        if (isSkipVersion(context, updateInfo.version)) {
                            L.d("已跳過版本: " + updateInfo.version);
                            return;
                        }
                    }
                    Toaster.cancel();
                    UpdateInfoView updateInfoView = new UpdateInfoView(context);
                    updateInfoView.setTitle(Lang.getString(R.id.found_new_version) + updateInfo.version);
                    updateInfoView.setContent(updateInfo.content);
                    if (sameBackend) {
                        updateInfoView.withOnNeutralButtonClickListener((dialogInterface, i) -> {
                            Config.from(context).setSkipVersion(updateInfo.version);
                            dialogInterface.dismiss();
                        });
                    } else updateInfoView.withNeutralButtonText(null);
                    updateInfoView.withOnPositiveButtonClickListener((dialogInterface, i) -> {
                        if (type == PluginType.Zygisk
                                && !ModuleInstaller.isAvailable(context)) {
                            dialogInterface.dismiss();
                            createUpdateHandoff(context, Lang.getString(R.id.module_update_note),
                                    R.id.open_root_manager, () -> openRootManager(context)).showInDialog();
                        } else {
                            downloadUpdate(context, updateInfo, dialogInterface, type);
                        }
                    });
                    Task.onMain(200, updateInfoView::showInDialog);
                }
            }).doUpdateCheck();
        } catch (Exception | Error e) {
            //for OPPO R11 Plus 6.0 NoSuchFieldError: No instance field mResultListener
            L.e(e);
        }
    }

    private static void downloadUpdate(Context context, UpdateInfo info, DialogInterface updateDialog, PluginType type) {
        boolean module = type == PluginType.Zygisk;
        File target = module ? new File(context.getCacheDir(), "fingerprintpay-module.zip")
                : FileUtils.getSharableFile(context, context.getPackageName() + ".apk");
        String mirror = String.format(Locale.ROOT, Constant.UPDATE_URL_MIRROR_FILE, info.version, info.name);
        updateDialog.dismiss();
        new DownloadView(context).download(new String[]{mirror, info.url}, target, info.size, info.digest, () -> {
            if (module) {
                Task.onBackground(() -> {
                    try {
                        Intent install = ModuleInstaller.prepare(context, target, info.version);
                        Task.onMain(() -> {
                            if (!ActivityContext.isAlive(ActivityContext.find(context))) return;
                            try { context.startActivity(install); }
                            catch (Exception error) { showUpdateError(context, error); }
                        });
                    } catch (Exception error) { Task.onMain(() -> showUpdateError(context, error)); }
                });
            } else {
                try { installApk(context, target); }
                catch (Exception error) { showUpdateError(context, error); }
            }
        }).showInDialog();
    }

    private static void openRootManager(Context context) {
        String[] managers = {"com.topjohnwu.magisk", "me.weishu.kernelsu", "com.rifsxd.ksunext", "me.bmax.apatch"};
        for (String manager : managers) {
            Intent launch = context.getPackageManager().getLaunchIntentForPackage(manager);
            if (launch != null) {
                try {
                    context.startActivity(launch);
                    return;
                } catch (Exception e) {
                    L.e(e);
                }
            }
        }
        throw new IllegalStateException(Lang.getString(R.id.root_manager_missing));
    }

    private static MessageView createUpdateHandoff(Context context, String message, int actionLabel, Runnable action) {
        MessageView guidance = new MessageView(context).text(message);
        guidance.withNegativeButtonText(Lang.getString(R.id.cancel));
        guidance.withPositiveButtonText(Lang.getString(actionLabel));
        guidance.withOnPositiveButtonClickListener((dialog, which) -> {
            dialog.dismiss();
            try { action.run(); }
            catch (Exception error) { showUpdateError(context, error); }
        });
        return guidance;
    }

    private static void showUpdateError(Context context, Exception error) {
        L.e(error);
        if (ActivityContext.isAlive(ActivityContext.find(context))) {
            new MessageView(context).title(Lang.getString(R.id.update_failed))
                    .text(error.getMessage()).showInDialog();
        }
    }

    public static void lazyUpdateWhenActivityAlive() {
        int lazyCheckTimeMsec = BuildConfig.DEBUG ? 200 : 6000;
        Task.onMain(lazyCheckTimeMsec, new Runnable() {
            @Override
            public void run() {
                Activity activity = ApplicationUtils.getCurrentActivity();
                if (activity == null
                    || activity.getClass().getName().contains("com.tencent.mm.app.WeChatSplashActivity")) {
                    Task.onMain(lazyCheckTimeMsec, this);
                    return;
                }
                if (Config.from(activity).isAutoUpdate()) UpdateFactory.doUpdateCheck(activity);
            }
        });
    }

    private static boolean isSkipVersion(Context context, String targetVersion) {
        Config config = Config.from(context);
        String skipVersion = config.getSkipVersion();
        if (TextUtils.isEmpty(skipVersion)) {
            return false;
        }
        if (String.valueOf(targetVersion).equals(skipVersion)) {
            return true;
        }
        return false;
    }

    public static void installApk(Context context, File file) {
        Uri uri = FileUtils.getUri(context, file);
        file.setReadable(true, false);
        file.getParentFile().setReadable(true, false);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        context.startActivity(intent);
    }
}
