package com.surcumference.fingerprint.storage;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import com.surcumference.fingerprint.util.BizBiometricIdentify;
import com.surcumference.fingerprint.util.Config;
import com.surcumference.fingerprint.util.Task;
import com.surcumference.fingerprint.view.PasswordEnrollmentView;
import com.wei.android.lib.fingerprintidentify.bean.FingerprintIdentifyFailInfo;
import java.io.FileInputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PasswordEnrollment implements Application.ActivityLifecycleCallbacks {
    private static PasswordEnrollment instance;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private WeakReference<Activity> foreground = new WeakReference<>(null);
    private Task.Handle poll;
    private BizBiometricIdentify authentication;
    private AlertDialog dialog;
    private boolean checking;
    private String readFailure;
    public static synchronized void install(Context context) {
        if (instance != null || !ConfigContract.PACKAGES.contains(context.getPackageName())) return;
        try (FileInputStream input = new FileInputStream("/proc/self/cmdline")) {
            byte[] data = new byte[256]; int count = input.read(data), end = 0;
            while (end < count && data[end] != 0) end++;
            if (!context.getPackageName().equals(new String(data, 0, end, StandardCharsets.UTF_8))) return;
        } catch (Exception ignored) { return; }
        Context app = context instanceof Activity ? ((Activity) context).getApplication()
                : context instanceof Application ? context : context.getApplicationContext();
        if (!(app instanceof Application)) return;
        instance = new PasswordEnrollment(); ((Application) app).registerActivityLifecycleCallbacks(instance);
        android.util.Log.i("FingerprintPay", "Password confirmation callbacks registered: "
                + context.getPackageName() + " user " + android.os.Process.myUid() / 100000);
        Task.onMain(() -> {
            Activity current = com.surcumference.fingerprint.util.ApplicationUtils.getCurrentActivity();
            if (current != null && instance.foreground.get() == null) instance.onActivityResumed(current);
        });
    }
    public static void onHostResumed(Activity activity) {
        install(activity);
        if (instance != null) instance.onActivityResumed(activity);
    }
    public static void onHostPaused(Activity activity) {
        if (instance != null) instance.onActivityPaused(activity);
    }
    private void schedule(Activity activity) {
        if (poll != null) poll.cancel();
        poll = Task.onMain(750, () -> check(activity));
    }
    private void check(Activity activity) {
        if (foreground.get() != activity || activity.isFinishing() || activity.isDestroyed()) return;
        if (authentication != null && !authentication.fingerprintScanStateReady && dialog != null) dialog.dismiss();
        if (checking || authentication != null) { schedule(activity); return; }
        checking = true;
        worker.execute(() -> {
            String pending = null;
            org.json.JSONObject donation = null;
            try {
                donation = com.surcumference.fingerprint.util.DonateUtils.pending(activity);
                if (donation == null) pending = Config.from(activity).pendingPassword();
                readFailure = null;
            }
            catch (Exception error) {
                String type = error.getClass().getName();
                if (!type.equals(readFailure)) android.util.Log.e("FingerprintPay", "Password confirmation service read failed: " + type);
                readFailure = type;
            }
            final String id = pending;
            final org.json.JSONObject donationRequest = donation;
            Task.onMain(() -> {
                checking = false;
                Activity current = foreground.get();
                if (current == null || current.isFinishing() || current.isDestroyed()) {
                    if (id != null) worker.execute(() -> Config.from(activity).cancelPassword(id));
                    return;
                }
                if (donationRequest != null) {
                    try { com.surcumference.fingerprint.util.DonateUtils.dispatch(current, donationRequest); }
                    catch (Exception error) { android.util.Log.e("FingerprintPay", "Donation launch failed", error); }
                } else if (id != null) show(current, id);
                schedule(current);
            });
        });
    }
    private void show(Activity activity, String id) {
        android.util.Log.i("FingerprintPay", "Starting foreground password confirmation: " + activity.getPackageName());
        authentication = new BizBiometricIdentify(activity);
        BizBiometricIdentify identify = authentication;
        PasswordEnrollmentView view = new PasswordEnrollmentView(activity);
        view.withOnDismissListener(ignored -> { identify.cancelIdentify(); if (authentication == identify) { authentication = null; dialog = null; } });
        AlertDialog confirmation = view.showInDialog();
        dialog = confirmation;
        identify.encryptPending(id, new BizBiometricIdentify.IdentifyListener() {
            @Override public void onNotMatch(BizBiometricIdentify target, int availableTimes) {
                if (authentication == identify && dialog == confirmation) view.retry();
            }
            @Override public void onEncryptionSuccess(BizBiometricIdentify target, String encrypted, byte[] iv) {
                if (authentication != identify || dialog != confirmation) return;
                view.saved();
                android.widget.Toast.makeText(activity, "此应用的支付密码已保存", android.widget.Toast.LENGTH_SHORT).show();
                confirmation.dismiss();
                returnToSettings(activity);
            }
            @Override public void onFailed(BizBiometricIdentify target, FingerprintIdentifyFailInfo info) {
                if (authentication != identify || dialog != confirmation) return;
                android.widget.Toast.makeText(activity, failureMessage(info), android.widget.Toast.LENGTH_LONG).show();
                confirmation.dismiss();
            }
        });
    }
    static void returnToSettings(Activity activity) {
        boolean returned = false;
        try { returned = activity.moveTaskToBack(true); }
        catch (Exception error) { android.util.Log.w("FingerprintPay", "Password enrollment task return failed", error); }
        android.util.Log.i("FingerprintPay", "Password enrollment return: " + activity.getPackageName()
                + " task=" + activity.getTaskId() + " moved=" + returned);
        if (!returned) android.widget.Toast.makeText(activity,
                "密码已保存，请返回指纹支付继续录入", android.widget.Toast.LENGTH_LONG).show();
    }
    static String failureMessage(FingerprintIdentifyFailInfo info) {
        if (info.isCancel()) return "已取消指纹验证，未保存密码";
        if (info.deviceLocked) return "指纹已锁定，请解锁手机后重新设置密码";
        if (info.errorCode == -1) return "指纹不可用，请先在系统设置中录入指纹";
        if (info.throwable instanceof SecurityException && "Hardware biometric TEE protection unavailable".equals(info.throwable.getMessage()))
            return "未保存密码：系统未提供硬件 TEE 指纹保护";
        if (info.throwable instanceof android.security.keystore.KeyPermanentlyInvalidatedException)
            return "指纹密钥已失效，请重新设置密码";
        return "指纹验证或密码保存失败，请重试";
    }
    @Override public void onActivityResumed(Activity activity) {
        if (foreground.get() == activity) return;
        foreground = new WeakReference<>(activity); schedule(activity);
    }
    @Override public void onActivityPaused(Activity activity) {
        if (foreground.get() != activity) return;
        foreground.clear(); if (poll != null) poll.cancel();
        if (dialog != null) dialog.dismiss();
    }
    @Override public void onActivityCreated(Activity activity, Bundle state) {}
    @Override public void onActivityStarted(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
    @Override public void onActivityDestroyed(Activity activity) { onActivityPaused(activity); }
    private PasswordEnrollment() {}
}
