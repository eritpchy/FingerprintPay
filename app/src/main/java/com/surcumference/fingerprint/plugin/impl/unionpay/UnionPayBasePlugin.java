package com.surcumference.fingerprint.plugin.impl.unionpay;

import static com.surcumference.fingerprint.Constant.PACKAGE_NAME_UNIONPAY;

import android.app.Activity;
import com.surcumference.fingerprint.util.PaymentPasswordGuard;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hjq.toast.Toaster;
import com.surcumference.fingerprint.Lang;
import com.surcumference.fingerprint.R;
import com.surcumference.fingerprint.plugin.inf.IAppPlugin;
import com.surcumference.fingerprint.plugin.inf.IMockCurrentUser;
import com.surcumference.fingerprint.plugin.inf.OnFingerprintVerificationOKListener;
import com.surcumference.fingerprint.util.ActivityViewObserver;
import com.surcumference.fingerprint.util.ActivityViewObserverHolder;
import com.surcumference.fingerprint.util.BizBiometricIdentify;
import com.surcumference.fingerprint.util.BlackListUtils;
import com.surcumference.fingerprint.util.Config;
import com.surcumference.fingerprint.util.Task;
import com.surcumference.fingerprint.util.PaymentController;
import com.surcumference.fingerprint.util.PaymentMethodObserver;
import com.surcumference.fingerprint.util.ActivityContext;
import com.surcumference.fingerprint.bean.DigitPasswordKeyPadInfo;
import android.os.SystemClock;
import com.surcumference.fingerprint.util.ViewUtils;
import com.surcumference.fingerprint.util.XBiometricIdentify;
import com.surcumference.fingerprint.util.log.L;
import com.surcumference.fingerprint.view.AlipayPayView;
import com.surcumference.fingerprint.view.DialogUtils;
import com.wei.android.lib.fingerprintidentify.bean.FingerprintIdentifyFailInfo;

import java.util.Map;
import java.util.WeakHashMap;

public class UnionPayBasePlugin implements IAppPlugin, IMockCurrentUser {
    private long mActivityGeneration;
    private Activity mWatchingActivity;
    private Task.Handle mObserverRestart;


    private AlertDialog mFingerPrintAlertDialog;
    private final PaymentController mPayment = new PaymentController();
    private WeakHashMap<View, View.OnAttachStateChangeListener> mView2OnAttachStateChangeListenerMap = new WeakHashMap<>();
    protected boolean mMockCurrentUser = false;
    protected XBiometricIdentify mFingerprintIdentify;
    private boolean mFingerprintIdentifyTemporaryBlocking = false;


    private Map<Activity, Boolean> mActivityResumedMap = new WeakHashMap<>();

    protected synchronized void initFingerPrintLock(Context context,
                                                    AlertDialog dialog, String passwordEncrypted,
                                                    OnFingerprintVerificationOKListener onSuccessUnlockCallback) {
        final long token = mPayment.token();
        if (!mPayment.isActive(token)) return;
        L.d("指纹识别开始");
        BizBiometricIdentify authentication = new BizBiometricIdentify(context).withMockCurrentUserCallback(this);
        mFingerprintIdentify = authentication;
        mPayment.authentication(token, authentication);
        authentication.decryptPasscode(passwordEncrypted, new BizBiometricIdentify.IdentifyListener() {
                    @Override
                    public void onInited(BizBiometricIdentify identify) {
                        if (!mPayment.isActive(token)) { identify.cancelIdentify(); return; }
                        super.onInited(identify);
                        if (identify.isUsingBiometricApi()) {
                            ViewUtils.setAlpha(dialog, 0);
                            ViewUtils.setDimAmount(dialog, 0);
                        } else {
                            ViewUtils.setAlpha(dialog, 1);
                            ViewUtils.setDimAmount(dialog, 0.6f);
                        }
                    }

                    @Override
                    public void onDecryptionSuccess(BizBiometricIdentify identify, @NonNull String decryptedContent) {
                        super.onDecryptionSuccess(identify, decryptedContent);
                        if (mPayment.authenticated(token)) onSuccessUnlockCallback.onFingerprintVerificationOK(decryptedContent);
                    }

                    @Override
                    public void onFailed(BizBiometricIdentify target, FingerprintIdentifyFailInfo failInfo) {
                        super.onFailed(target, failInfo);
                        if (!mPayment.owns(token)) return;
                        mPayment.finish(token, SystemClock.uptimeMillis());
                        if (dialog != null) {
                            ViewUtils.setAlpha(dialog, 1);
                            ViewUtils.setDimAmount(dialog, 0.6f);
                            if (dialog.isShowing()) {
                                dialog.dismiss();
                            }
                        }
                    }
                });
    }

    public synchronized void showFingerPrintDialog(@Nullable Activity activity, View targetView) {
        if (activity == null) activity = ActivityContext.find(targetView.getContext());
        final Activity owner = activity;
        if (!ActivityContext.isAlive(owner) || !mActivityResumedMap.containsKey(owner)) return;
        View rootView = targetView.getRootView();
        final Context context = rootView.getContext();
        final Config config = Config.from(context);
        try {
            if (!PaymentPasswordGuard.ready(config, targetView)) return;
            if ("true".equals(rootView.getTag(R.id.unionpay_payview_shown))) {
                L.d("payview already shown, skip.");
                return;
            }
            watchPaymentMethod(owner, targetView, rootView);

            rootView.setTag(R.id.unionpay_payview_shown, "true");
            String passwordEncrypted = config.getPasswordEncrypted();
            if (!mPayment.begin(owner, targetView, SystemClock.uptimeMillis())) {
                rootView.setTag(R.id.unionpay_payview_shown, null); return;
            }
            hidePreviousPayDialog();
            final long token = mPayment.token();
            mPayment.restoreWith(token, () -> {
                rootView.setTag(R.id.unionpay_payview_shown, null);
                releasePaymentObjects();
            });
            OnFingerprintVerificationOKListener onSuccess = password -> {
                BlackListUtils.applyIfNeeded(context);

                if (!mPayment.isActive(token)) return;
                DigitPasswordKeyPadInfo keys = new DigitPasswordKeyPadInfo(PACKAGE_NAME_UNIONPAY,
                        null, null, null, null, null, null, null, null, null, null);
                if (!mPayment.inputDigits(token, rootView, keys, password,
                        () -> mPayment.finish(token, SystemClock.uptimeMillis()))) {
                    Toaster.showLong(Lang.getString(R.id.toast_password_auto_enter_fail));
                    mPayment.finish(token, SystemClock.uptimeMillis());
                }
            };
            if (config.isHideFingerprintDialog()) {
                mPayment.bindToPaymentWindow(token, owner.getWindow(), config.isVolumeDownMonitorEnabled(), () -> {
                    mFingerprintIdentifyTemporaryBlocking = true;
                    Toaster.showLong(Lang.getString(R.id.toast_fingerprint_temporary_disabled));
                    Task.onBackground(60000, () -> mFingerprintIdentifyTemporaryBlocking = false);
                });
                mPayment.protectDigits(token, rootView, new DigitPasswordKeyPadInfo(PACKAGE_NAME_UNIONPAY,
                        null, null, null, null, null, null, null, null, null, null));
                mPayment.showFingerprintPrompt(token, activity);
                initFingerPrintLock(context, null, passwordEncrypted, onSuccess);
                return;
            }
            boolean dialogMode = false;
            View payRootLayout = ViewUtils.findViewByName(rootView, PACKAGE_NAME_UNIONPAY, "fl_container");
            L.d("payRootLayout", payRootLayout);
            if (payRootLayout == null) {
                View payTitleTextView = ViewUtils.findViewByText(rootView, "请输入支付密码", "Input payment password");
                payRootLayout = (View)payTitleTextView.getParent().getParent().getParent();
                dialogMode = true;
            }
            L.d("payRootLayout", payRootLayout);
            boolean finalDialogMode = dialogMode;
            AlipayPayView payView = new AlipayPayView(context)
                    .withOnCancelButtonClickListener(target -> {
                    DialogUtils.dismiss(target.getDialog());
                    mPayment.finish(token, SystemClock.uptimeMillis());
            }).withOnDismissListener(v -> {
                if (!mPayment.owns(token)) return;
                mPayment.finish(token, SystemClock.uptimeMillis());
                XBiometricIdentify fingerprintIdentify = mFingerprintIdentify;
                if (fingerprintIdentify != null) {
                    fingerprintIdentify.cancelIdentify();
                    L.d("指纹识别取消1");
                }
                rootView.setTag(R.id.unionpay_payview_shown, null);
            }).withOnShowListener(target -> {
                ViewUtils.setAlpha(target.getDialog(), 0);
                ViewUtils.setDimAmount(target.getDialog(), 0);
                //for hidePreviousPayDialog
                mPayment.later(token, 250, () -> {
                    //窗口消失了
                    if (ViewUtils.isViewVisibleInScreen(rootView) == false) {
                        return;
                    }
                    AlertDialog dialog = target.getDialog();
                    ViewUtils.setAlpha(dialog, 1);
                    ViewUtils.setDimAmount(dialog, 0.6f);
                    initFingerPrintLock(context, dialog, passwordEncrypted, onSuccess);
                    // Dialog window owns the volume listener.
                    if (config.isVolumeDownMonitorEnabled()) {
                        ViewUtils.registerVolumeKeyDownEventListener(dialog.getWindow(), event -> {
                            if (mFingerprintIdentifyTemporaryBlocking) {
                                return false;
                            }
                            DialogUtils.dismiss(dialog);
                            Toaster.showLong(Lang.getString(R.id.toast_fingerprint_temporary_disabled));
                            mFingerprintIdentifyTemporaryBlocking = true;
                            Task.onBackground(60000, () -> mFingerprintIdentifyTemporaryBlocking = false);
                            return false;
                        });
                    }
                }
                );
            });
            L.d("ViewUtils.isViewVisibleInScreen(rootView) ",ViewUtils.isViewVisibleInScreen(rootView) );
            //窗口消失了
            if (ViewUtils.isViewVisibleInScreen(rootView) == false) {
                rootView.setTag(R.id.unionpay_payview_shown, null);
                return;
            }
            AlertDialog fingerPrintAlertDialog = payView.showInDialog();
            ViewUtils.setAlpha(fingerPrintAlertDialog, 0);
            ViewUtils.setDimAmount(fingerPrintAlertDialog, 0);
            if (mPayment.isActive(token)) mFingerPrintAlertDialog = fingerPrintAlertDialog;
            else DialogUtils.dismiss(fingerPrintAlertDialog);
        } catch (OutOfMemoryError e) {
        }
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        //Xposed not hooked yet!
    }

    @Override
    public void onActivityResumed(Activity activity) {
        L.d("Activity onResume =", activity);
        mActivityResumedMap.put(activity, true);
        final String activityClzName = activity.getClass().getName();
        if (activityClzName.contains(".UPActivityReactNative")) {

            watchPayView(activity);
        } else if (activityClzName.contains(".PayWalletActivity")) {
            watchPayView(activity);
        }
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        //Xposed not hooked yet!
    }

    @Override
    public void onActivityPaused(Activity activity) {
        if (mPayment.isAuthenticating(activity) && mFingerprintIdentify != null
                && mFingerprintIdentify.isUsingBiometricApi()) return;
        closePayment(activity);
    }

    private void closePayment(Activity activity) {
        mActivityResumedMap.remove(activity);
        if (activity == null || (mPayment.owner() != activity && mWatchingActivity != activity)) return;
        ++mActivityGeneration;
        mWatchingActivity = null;
        if (mObserverRestart != null) mObserverRestart.cancel();
        mObserverRestart = null;
        java.util.Iterator<java.util.Map.Entry<View, View.OnAttachStateChangeListener>> entries = mView2OnAttachStateChangeListenerMap.entrySet().iterator();
        while (entries.hasNext()) {
            java.util.Map.Entry<View, View.OnAttachStateChangeListener> entry = entries.next();
            if (ActivityContext.find(entry.getKey().getContext()) == activity) {
                entry.getKey().removeOnAttachStateChangeListener(entry.getValue());
                removePaymentMethodListener(entry.getKey());
                entries.remove();
            }
        }
        L.d("Activity onPause =", activity);
        if (mPayment.owner() == activity) releasePaymentObjects();
        mPayment.close(activity);
        final String activityClzName = activity.getClass().getName();
        if (activityClzName.contains(".UPActivityReactNative")) {
            hidePreviousPayDialog();
        } else if (activityClzName.contains(".PayWalletActivity")) {
            hidePreviousPayDialog();
        }
        ActivityViewObserverHolder.stop(ActivityViewObserverHolder.Key.UnionPayPasswordView);
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
        if (!Config.from(activity).isInScope()) {
            return;
        }
        if (mFingerprintIdentifyTemporaryBlocking) {
            return;
        }
        mWatchingActivity = activity;
        final long generation = ++mActivityGeneration;
        ActivityViewObserver activityViewObserver = new ActivityViewObserver(activity);
        activityViewObserver.setViewIdentifyText("请输入支付密码", "Input payment password");
        activityViewObserver.setWatchActivityViewOnly(true);
        ActivityViewObserverHolder.start(ActivityViewObserverHolder.Key.UnionPayPasswordView,  activityViewObserver,
                100, new ActivityViewObserver.IActivityViewListener() {
            @Override
            public void onViewFounded(ActivityViewObserver observer, View view) {
                if (generation != mActivityGeneration) return;
                //跳过没有显示的view, 云闪付专属
                if (!ViewUtils.isViewVisibleInScreen(view)) {
                    L.d("skip invisible view", ViewUtils.getViewInfo(view));
                    return;
                }
                if (!ViewUtils.isShownInScreen(view)) {
                    L.d("skip invisible view2", ViewUtils.getViewInfo(view));
                    return;
                }
                View rootView = view.getRootView();
                L.d("onViewFounded:", ViewUtils.getViewInfo(view), " rootView: ", rootView);
                if (rootView.toString().contains("[UPActivityPayPasswordSet]")) {
                    //跳过小额免密设置页面
                    return;
                }
                ActivityViewObserver.IActivityViewListener l = this;
                observer.stop();

                if (!mActivityResumedMap.containsKey(activity)) {
                    return;
                }

                onPayDialogShown(observer.getTargetActivity(), view);
                View.OnAttachStateChangeListener listener = mView2OnAttachStateChangeListenerMap.get(view);
                if (listener != null) {
                    view.removeOnAttachStateChangeListener(listener);
                }
                listener = new View.OnAttachStateChangeListener() {
                    @Override
                    public void onViewAttachedToWindow(View v) {
                        L.d("onViewAttachedToWindow:", view);

                    }

                    @Override
                    public void onViewDetachedFromWindow(View v) {
                        L.d("onViewDetachedFromWindow:", view);
                        Context context = v.getContext();
                        onPayDialogDismiss(context);
                        view.removeOnAttachStateChangeListener(this);
                        removePaymentMethodListener(view);
                        mView2OnAttachStateChangeListenerMap.remove(view);
                        mObserverRestart = Task.onMain(500, () -> {
                            if (generation == mActivityGeneration && ActivityContext.isAlive(activity)
                                    && mActivityResumedMap.containsKey(activity)) watchPayView(activity);
                        });
                    }
                };
                view.addOnAttachStateChangeListener(listener);
                mView2OnAttachStateChangeListenerMap.put(view, listener);
            }
        });
    }

    protected void onPayDialogShown(@Nullable Activity activity, View targetView) {
        L.d("PayDialog show");
        showFingerPrintDialog(activity, targetView);
    }

    protected void onPayDialogDismiss(Context context) {
        mPayment.close(ActivityContext.find(context));
        L.d("PayDialog dismiss");
        if (!Config.from(context).isOn()) {
            return;
        }
        XBiometricIdentify fingerPrintIdentify = mFingerprintIdentify;
        if (fingerPrintIdentify != null) {
            fingerPrintIdentify.cancelIdentify();
            L.d("指纹识别取消2");
        }
        mMockCurrentUser = false;
    }

    private void hidePreviousPayDialog() {
        L.d("hidePreviousPayDialog", mFingerPrintAlertDialog);
        AlertDialog dialog = mFingerPrintAlertDialog;
        mFingerPrintAlertDialog = null;
        DialogUtils.dismiss(dialog);
    }

    private void releasePaymentObjects() {
        XBiometricIdentify identify = mFingerprintIdentify;
        mFingerprintIdentify = null;
        if (identify != null) identify.cancelIdentify();
        hidePreviousPayDialog();
    }

    private void watchPaymentMethod(Activity owner, View targetView, View rootView) {
        removePaymentMethodListener(targetView);
        PaymentMethodObserver listener = new PaymentMethodObserver(rootView, () -> {
            if (ActivityContext.isAlive(owner) && mActivityResumedMap.containsKey(owner)) mPayment.close(owner);
        }, () -> {
            if (ActivityContext.isAlive(owner) && targetView.getWindowToken() != null
                    && mActivityResumedMap.containsKey(owner)) showFingerPrintDialog(owner, targetView);
        });
        targetView.setTag(R.id.unionpay_payment_method_listener, listener);
    }

    private void removePaymentMethodListener(View view) {
        Object listener = view.getTag(R.id.unionpay_payment_method_listener);
        if (listener instanceof PaymentMethodObserver) ((PaymentMethodObserver) listener).close();
        else if (listener instanceof ViewTreeObserver.OnGlobalLayoutListener && view.getViewTreeObserver().isAlive()) {
            view.getViewTreeObserver().removeOnGlobalLayoutListener((ViewTreeObserver.OnGlobalLayoutListener) listener);
        }
        view.setTag(R.id.unionpay_payment_method_listener, null);
    }
}
