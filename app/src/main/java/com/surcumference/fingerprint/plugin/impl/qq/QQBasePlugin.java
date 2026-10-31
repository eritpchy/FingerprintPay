package com.surcumference.fingerprint.plugin.impl.qq;

import android.app.Activity;
import com.surcumference.fingerprint.util.PaymentPasswordGuard;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hjq.toast.Toaster;
import com.surcumference.fingerprint.BuildConfig;
import com.surcumference.fingerprint.Lang;
import com.surcumference.fingerprint.R;
import com.surcumference.fingerprint.plugin.inf.IAppPlugin;
import com.surcumference.fingerprint.plugin.inf.IMockCurrentUser;
import com.surcumference.fingerprint.plugin.inf.OnFingerprintVerificationOKListener;
import com.surcumference.fingerprint.util.BizBiometricIdentify;
import com.surcumference.fingerprint.util.Config;
import com.surcumference.fingerprint.util.QQUtils;
import com.surcumference.fingerprint.util.Task;
import com.surcumference.fingerprint.util.PaymentController;
import com.surcumference.fingerprint.util.PaymentMethodObserver;
import com.surcumference.fingerprint.util.ActivityContext;
import android.os.SystemClock;
import com.surcumference.fingerprint.util.ViewUtils;
import com.surcumference.fingerprint.util.XBiometricIdentify;
import com.surcumference.fingerprint.util.log.L;
import com.surcumference.fingerprint.util.paydialog.QQPayDialog;
import com.wei.android.lib.fingerprintidentify.bean.FingerprintIdentifyFailInfo;

import java.util.WeakHashMap;

public class QQBasePlugin implements IAppPlugin, IMockCurrentUser {

    private XBiometricIdentify mFingerprintIdentify;

    protected boolean mMockCurrentUser = false;
    private Activity mCurrentPayActivity;
    private final PaymentController mPayment = new PaymentController();
    private Activity mWatchingActivity;
    private long mWatchGeneration;
    private WeakHashMap<Activity, QQPayDialog> mActivityPayDialogMap = new WeakHashMap<>();
    private final WeakHashMap<Activity, PaymentMethodObserver> mPaymentMethodObservers = new WeakHashMap<>();
    private WeakHashMap<Activity, ViewTreeObserver.OnGlobalLayoutListener> mPayViewLayoutListenerMap = new WeakHashMap<>();
    private ViewTreeObserver.OnWindowAttachListener mPayWindowAttachListener;
    private boolean mPayDialogTemporaryBlocking = false;
    private boolean mFingerprintIdentifyTemporaryBlocking = false;

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        //Xposed not hooked yet!
    }

    @Override
    public void onActivityResumed(Activity activity) {
        try {
            final String activityClzName = activity.getClass().getName();
            if (BuildConfig.DEBUG) {
                L.d("activity", activity, "clz", activityClzName);
            }
            if (activityClzName.contains(".SplashActivity")) {
                QQUtils.checkBlackListQQ(activity);
            }
            if (activityClzName.contains(".QWalletPluginProxyActivity")
                || activityClzName.contains(".QWalletToolFragmentActivity")) {
                L.d("found");
                if (!Config.from(activity).isInScope()) {
                    return;
                }
                if (mPayDialogTemporaryBlocking) {
                    L.d("mPayDialogTemporBlocking=true");
                    return;
                }
                if (mFingerprintIdentifyTemporaryBlocking) {
                    return;
                }
                if (mPayment.isAuthenticating(activity) && mFingerprintIdentify != null
                        && mFingerprintIdentify.isUsingBiometricApi()) return;
                if (mPayment.owner() == activity
                        && mPayment.target() instanceof View && ((View) mPayment.target()).isShown()
                        && ((View) mPayment.target()).getWindowToken() != null) return;
                mPayment.close(mPayment.owner());
                cancelFingerprintIdentify();
                mWatchingActivity = activity;
                ++mWatchGeneration;
                watchPayView(activity);
            }
        } catch (Exception e) {
            L.e(e);
        }
    }

    @Override
    public void onActivityPaused(Activity activity) {
        if (mPayment.isAuthenticating(activity) && mFingerprintIdentify != null
                && mFingerprintIdentify.isUsingBiometricApi()) return;
        closePayment(activity);
        try {
            final String activityClzName = activity.getClass().getName();
            if (BuildConfig.DEBUG) {
                L.d("activity", activity, "clz", activityClzName);
            }
            if (activityClzName.contains(".QWalletPluginProxyActivity")
                || activityClzName.contains(".QWalletToolFragmentActivity")) {
                removePayViewLayoutListener(activity);
                if (activity == mCurrentPayActivity) {
                    L.d("found");
                    mCurrentPayActivity = null;
                    cancelFingerprintIdentify();
                }
            }
        } catch (Exception e) {
            L.e(e);
        }
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        closePayment(activity);
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
        //Xposed not hooked yet!
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        closePayment(activity);
    }

    @Override
    public boolean getMockCurrentUser() {
        return this.mMockCurrentUser;
    }

    @Override
    public void setMockCurrentUser(boolean mock) {
        this.mMockCurrentUser = mock;
    }

    private void watchPayView(Activity activity) {
        removePayViewLayoutListener(activity);
        ViewGroup rootView = (ViewGroup) activity.getWindow().getDecorView();
        ViewTreeObserver.OnGlobalLayoutListener listener = new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (mPayDialogTemporaryBlocking || mFingerprintIdentifyTemporaryBlocking) {
                    return;
                }
                QQPayDialog payDialog = mActivityPayDialogMap.get(activity);
                if (payDialog != null) {
                    return;
                }
                payDialog = QQPayDialog.findFrom(rootView);
                if (payDialog == null) {
                    return;
                }
                removePayViewLayoutListener(activity);
                initPayActivity(activity, 10, 20);
            }
        };
        mPayViewLayoutListenerMap.put(activity, listener);
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(listener);
        // Also try immediately in case views are already loaded
        initPayActivity(activity, 10, 20);
    }

    private void removePayViewLayoutListener(Activity activity) {
        ViewTreeObserver.OnGlobalLayoutListener listener = mPayViewLayoutListenerMap.remove(activity);
        if (listener != null) {
            activity.getWindow().getDecorView().getViewTreeObserver().removeOnGlobalLayoutListener(listener);
        }
    }

    private synchronized void initPayActivity(Activity activity, int retryDelay, int retryCountdown) {
        if (mWatchingActivity != activity || !ActivityContext.isAlive(activity)) return;
        final long watchGeneration = mWatchGeneration;
        Context context = activity;
        Config config = Config.from(context);
        String passwordEncrypted = config.getPasswordEncrypted();
        ViewGroup rootView = (ViewGroup) activity.getWindow().getDecorView();

        QQPayDialog _payDialog = mActivityPayDialogMap.get(activity);
        if (_payDialog == null) {
            _payDialog = QQPayDialog.findFrom(rootView);
            mActivityPayDialogMap.put(activity, _payDialog);
        }
        if (_payDialog == null || !PaymentController.isTargetReady(_payDialog.inputEditText)) {
            if (retryCountdown > 0) {
                Task.onMain(retryDelay, () -> {
                    if (watchGeneration == mWatchGeneration) initPayActivity(activity, retryDelay, retryCountdown - 1);
                });
            }
            return;
        }
        QQPayDialog payDialog = _payDialog;
        watchPaymentMethod(activity, rootView);
        if (!PaymentPasswordGuard.ready(config, payDialog.inputEditText)) return;
        if (!mPayment.begin(activity, payDialog.inputEditText, SystemClock.uptimeMillis())) return;
        final long[] paymentToken = {mPayment.token()};
        mCurrentPayActivity = activity;
        boolean longPassword = payDialog.isLongPassword();
        setupPayWindowAttachListener(activity, payDialog.inputEditText);

        mPayment.restoreWith(paymentToken[0], this::releaseFingerprintIdentify);
        mPayment.bindToPaymentWindow(paymentToken[0], activity.getWindow(), config.isVolumeDownMonitorEnabled(), () -> {
            mFingerprintIdentifyTemporaryBlocking = true;
            Toaster.showLong(Lang.getString(R.id.toast_fingerprint_temporary_disabled));
            Task.onBackground(60000, () -> mFingerprintIdentifyTemporaryBlocking = false);
        });
        mPayment.protectKeyboard(paymentToken[0], payDialog.keyboardView);
        mPayment.showFingerprintPrompt(paymentToken[0], activity);
        initFingerPrintLock(context, passwordEncrypted, password -> {
            if (!mPayment.isActive(paymentToken[0])) return;
            try {
                payDialog.inputEditText.setText(password);
                if (longPassword && payDialog.okButton != null) payDialog.okButton.performClick();
            } catch (Exception error) { L.e(error); }
            mPayment.finish(paymentToken[0], SystemClock.uptimeMillis());
            mPayDialogTemporaryBlocking = true;
            Task.onMain(5000, () -> mPayDialogTemporaryBlocking = false);
        }, () -> mPayment.finish(paymentToken[0], SystemClock.uptimeMillis()));

    }

    private void setupPayWindowAttachListener(Activity activity, View targetView) {
        ViewTreeObserver viewTreeObserver = targetView.getViewTreeObserver();
        if (mPayWindowAttachListener != null) {
            viewTreeObserver.removeOnWindowAttachListener(mPayWindowAttachListener);
            mPayWindowAttachListener = null;
        }
        mPayWindowAttachListener = new ViewTreeObserver.OnWindowAttachListener() {
            @Override
            public void onWindowAttached() {

            }

            @Override
            public void onWindowDetached() {
                viewTreeObserver.removeOnWindowAttachListener(this);
                mPayWindowAttachListener = null;
                cancelFingerprintIdentify();
                if (Config.from(targetView.getContext()).isVolumeDownMonitorEnabled()) {
                    ViewUtils.unregisterVolumeKeyDownEventListener(activity.getWindow());
                }
            }
        };
        viewTreeObserver.addOnWindowAttachListener(mPayWindowAttachListener);
    }

    public void initFingerPrintLock(final Context context, String passwordEncrypted,
                                    OnFingerprintVerificationOKListener onSuccessUnlockCallback, final Runnable onFailureUnlockCallback) {
        L.d("initFingerPrintLock");
        final long token = mPayment.token();
        if (!mPayment.isActive(token)) return;
        if (mFingerprintIdentify != null) mFingerprintIdentify.cancelIdentify();
        BizBiometricIdentify authentication = new BizBiometricIdentify(context).withMockCurrentUserCallback(this);
        mFingerprintIdentify = authentication;
        mPayment.authentication(token, authentication);
        authentication.decryptPasscode(passwordEncrypted, new BizBiometricIdentify.IdentifyListener() {

                    @Override
                    public void onDecryptionSuccess(BizBiometricIdentify identify, @NonNull String decryptedContent) {
                        super.onDecryptionSuccess(identify, decryptedContent);
                        if (mPayment.authenticated(token)) onSuccessUnlockCallback.onFingerprintVerificationOK(decryptedContent);
                    }

                    @Override
                    public void onFailed(BizBiometricIdentify target, FingerprintIdentifyFailInfo failInfo) {
                        super.onFailed(target, failInfo);
                        if (mPayment.owns(token)) onFailureUnlockCallback.run();
                    }
                });
    }

    private void watchPaymentMethod(Activity activity, View root) {
        if (mPaymentMethodObservers.containsKey(activity)) return;
        PaymentMethodObserver observer = new PaymentMethodObserver(root, () -> {
            mPayment.close(activity);
            mActivityPayDialogMap.remove(activity);
        }, () -> {
            if (mWatchingActivity != activity || !ActivityContext.isAlive(activity)) return;
            mActivityPayDialogMap.remove(activity);
            mPayDialogTemporaryBlocking = false;
            initPayActivity(activity, 10, 20);
        });
        mPaymentMethodObservers.put(activity, observer);
    }

    private void closePayment(Activity activity) {
        PaymentMethodObserver observer = mPaymentMethodObservers.remove(activity);
        if (observer != null) observer.close();
        if (mPayment.owner() == activity) releaseFingerprintIdentify();
        mPayment.close(activity);
        if (mWatchingActivity == activity) { mWatchingActivity = null; ++mWatchGeneration; }
        if (mCurrentPayActivity == activity) mCurrentPayActivity = null;
        mActivityPayDialogMap.remove(activity);
    }

    private void cancelFingerprintIdentify() {
        mPayment.finish(mPayment.token(), SystemClock.uptimeMillis());
        L.d("cancelFingerprintIdentify");
        releaseFingerprintIdentify();
    }

    private void releaseFingerprintIdentify() {
        XBiometricIdentify identify = mFingerprintIdentify;
        mFingerprintIdentify = null;
        if (identify != null) identify.cancelIdentify();
    }

}
