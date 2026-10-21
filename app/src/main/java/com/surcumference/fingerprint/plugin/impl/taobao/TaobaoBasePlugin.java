package com.surcumference.fingerprint.plugin.impl.taobao;

import static com.surcumference.fingerprint.Constant.PACKAGE_NAME_TAOBAO;

import android.app.Activity;
import com.surcumference.fingerprint.util.PaymentPasswordGuard;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.*;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hjq.toast.Toaster;
import com.surcumference.fingerprint.Lang;
import com.surcumference.fingerprint.R;
import com.surcumference.fingerprint.plugin.inf.IAppPlugin;
import com.surcumference.fingerprint.plugin.inf.OnFingerprintVerificationOKListener;
import com.surcumference.fingerprint.util.ActivityViewObserver;
import com.surcumference.fingerprint.util.ActivityViewObserverHolder;
import com.surcumference.fingerprint.util.ApplicationUtils;
import com.surcumference.fingerprint.util.BizBiometricIdentify;
import com.surcumference.fingerprint.util.BlackListUtils;
import com.surcumference.fingerprint.util.Config;
import com.surcumference.fingerprint.util.TaobaoVersionControl;
import com.surcumference.fingerprint.util.Task;
import com.surcumference.fingerprint.util.PaymentController;
import com.surcumference.fingerprint.util.ActivityContext;
import com.surcumference.fingerprint.util.ViewUtils;
import com.surcumference.fingerprint.util.XBiometricIdentify;
import com.surcumference.fingerprint.util.log.L;
import com.surcumference.fingerprint.view.AlipayPayView;
import com.surcumference.fingerprint.view.DialogUtils;
import com.wei.android.lib.fingerprintidentify.bean.FingerprintIdentifyFailInfo;

import java.util.ArrayList;
import java.util.List;

public class TaobaoBasePlugin implements IAppPlugin {


    private AlertDialog mFingerPrintAlertDialog;
    private int mPwdActivityReShowDelayTimeMsec;


    private XBiometricIdentify mFingerprintIdentify;

    private Activity mCurrentActivity;
    private final PaymentController mPayment = new PaymentController();
    private long mActivityGeneration;
    private final List<Runnable> mLayoutCleanup = new ArrayList<>();
    private Task.Handle mPaymentMethodTask;
    private final List<Task.Handle> mActivityTasks = new ArrayList<>();

    private boolean mIsViewTreeObserverFirst;
    private int mTaobaoVersionCode = 0;
    private boolean mFingerprintIdentifyTemporaryBlocking = false;


    @Override
    public int getVersionCode(Context context) {
        if (mTaobaoVersionCode != 0) {
            return mTaobaoVersionCode;
        }
        mTaobaoVersionCode = ApplicationUtils.getPackageVersionCode(context, PACKAGE_NAME_TAOBAO);
        return mTaobaoVersionCode;
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        L.d("activity", activity);
    }

    @Override
    public void onActivityPaused(Activity activity) {
        if (mPayment.isAuthenticating(activity) && mFingerprintIdentify != null
                && mFingerprintIdentify.isUsingBiometricApi()) return;
        closePayment(activity);
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
        return false;
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        //Xposed not hooked yet!
    }

    @Override
    public void onActivityResumed(Activity activity) {
        try {
            final String activityClzName = activity.getClass().getName();
            L.d("activity", activity, "clz", activityClzName);
            if (mCurrentActivity != activity) closePayment(mCurrentActivity);
            clearActivityObservers();
            mCurrentActivity = activity;
            final long activityGeneration = ++mActivityGeneration;
            int versionCode = getVersionCode(activity);
            if (activityClzName.contains(".PayPwdDialogActivity")
                    || activityClzName.contains(".MspContainerActivity")
                    || activityClzName.contains(".FlyBirdWindowActivity")) {
                L.d("found");
                if (mFingerprintIdentifyTemporaryBlocking) {
                    return;
                }
                mIsViewTreeObserverFirst = true;
                View rootView = activity.getWindow().getDecorView();
                watchLayout(rootView, () -> {
                    if (activityGeneration != mActivityGeneration || mCurrentActivity == null) {
                        return;
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                        if (activity.isDestroyed()) {
                            return;
                        }
                    }
                    if (mCurrentActivity != activity) {
                        return;
                    }
                    boolean isRechargePay = (ViewUtils.isShown(ViewUtils.findViewByName(activity, "com.alipay.android.phone.mobilecommon.verifyidentity", "input_et_password"))
                            && ViewUtils.isShown(ViewUtils.findViewByName(activity, "com.alipay.android.phone.mobilecommon.verifyidentity", "keyboard_container")));

                    boolean isNormalPay = ViewUtils.isShown(ViewUtils.findViewByText(rootView,
                            "支付宝支付密码", "支付寶支付密碼", "Alipay Payment Password", //兼容上一版本的支付组件, 参考 10.28.10(473)
                            "请输入长密码", "請輸入長密碼", "Payment Password"))
                            || (com.surcumference.fingerprint.util.ShortPasswordTarget.find(rootView) != null
                            && com.surcumference.fingerprint.util.DigitKeyResolver.resolve(rootView,
                            TaobaoVersionControl.getDigitPasswordKeyPad(versionCode), "0123456789") != null);
                    if (isRechargePay || isNormalPay) {
                        if (mIsViewTreeObserverFirst) {
                            mIsViewTreeObserverFirst = false;
                            showFingerPrintDialog(activity);
                        }
                        return;
                    }
                });

            } else if (activityClzName.contains("PayPwdHalfActivity")) {
                L.d("found");
                if (mFingerprintIdentifyTemporaryBlocking) {
                    return;
                }
                mActivityTasks.add(Task.onMain(1500, () -> {
                    if (activityGeneration != mActivityGeneration || !ActivityContext.isAlive(activity)) return;
                    TaobaoVersionControl.DigitPasswordKeyPad digitPasswordKeyPad = TaobaoVersionControl.getDigitPasswordKeyPad(mTaobaoVersionCode);
                    View key1View = ViewUtils.findViewByName(activity, digitPasswordKeyPad.modulePackageName, digitPasswordKeyPad.key1);
                    if (key1View != null) {
                        showFingerPrintDialog(activity);
                        return;
                    }

                    //try again
                    mActivityTasks.add(Task.onMain(2000, () -> {
                        if (activityGeneration == mActivityGeneration && ActivityContext.isAlive(activity)) showFingerPrintDialog(activity);
                    }));
                }));
            }
        } catch (Exception e) {
            L.e(e);
        }
    }

    public void initFingerPrintLock(final Context context,
                                    AlertDialog dialog, String passwordEncrypted,
                                    final OnFingerprintVerificationOKListener onSuccessUnlockCallback) {
        final long token = mPayment.token();
        if (!mPayment.isActive(token)) return;
        BizBiometricIdentify authentication = new BizBiometricIdentify(context);
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
                        if (mPayment.owns(token)) mPayment.finish(token, SystemClock.uptimeMillis());
                        if (mPayment.owns(token) && dialog != null) {
                            ViewUtils.setAlpha(dialog, 1);
                            ViewUtils.setDimAmount(dialog, 0.6f);
                            if (dialog.isShowing()) {
                                dialog.dismiss();
                            }
                        }
                    }
                });
    }

    public void showFingerPrintDialog(final Activity activity) {
        if (mCurrentActivity != activity || !ActivityContext.isAlive(activity)) return;
        final Context context = activity;
        final Config config = Config.from(context);
        try {
            String passwordEncrypted = config.getPasswordEncrypted();
            View paymentTarget = findPasswordEditText(activity);
            if (paymentTarget == null) {
                TaobaoVersionControl.DigitPasswordKeyPad keypad = TaobaoVersionControl.getDigitPasswordKeyPad(getVersionCode(activity));
                paymentTarget = ViewUtils.findViewByName(activity, keypad.modulePackageName, keypad.key1);
            }
            if (paymentTarget == null || !PaymentPasswordGuard.ready(config, paymentTarget) || !mPayment.begin(activity, paymentTarget, SystemClock.uptimeMillis())) return;
            final long token = mPayment.token();
            mPayment.restoreWith(token, this::releasePaymentObjects);
            mPwdActivityReShowDelayTimeMsec = 0;
            reEnteredPayDialogSolution(activity);
            OnFingerprintVerificationOKListener onSuccess = password -> {
                BlackListUtils.applyIfNeeded(context);

                if (!mPayment.isActive(token)) return;
                Runnable complete = () -> {
                    mPwdActivityReShowDelayTimeMsec = 1000;
                    mPayment.finish(token, SystemClock.uptimeMillis());
                };
                try {
                    if (tryInputGenericPassword(activity, password)) complete.run();
                    else if (!mPayment.inputDigits(token, activity.getWindow().getDecorView(),
                            TaobaoVersionControl.getDigitPasswordKeyPad(getVersionCode(activity)), password, complete)) {
                        Toaster.showLong(Lang.getString(R.id.toast_password_auto_enter_fail));
                        complete.run();
                    }
                } catch (Exception error) { L.e(error); complete.run(); }
            };
            if (config.isUnderDisplayFingerprint()) {
                mPayment.bindToPaymentWindow(token, activity.getWindow(), config.isVolumeDownMonitorEnabled(), () -> {
                    mFingerprintIdentifyTemporaryBlocking = true;
                    Toaster.showLong(Lang.getString(R.id.toast_fingerprint_temporary_disabled));
                    Task.onBackground(60000, () -> mFingerprintIdentifyTemporaryBlocking = false);
                });
                mPayment.protectDigits(token, paymentTarget.getRootView(),
                        TaobaoVersionControl.getDigitPasswordKeyPad(getVersionCode(activity)));
                initFingerPrintLock(context, null, passwordEncrypted, onSuccess);
                return;
            }
            AlipayPayView alipayPayView = new AlipayPayView(context)
                    .withOnShowListener((target) -> {
                        AlertDialog dialog = target.getDialog();
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
            }).withOnCancelButtonClickListener(target -> {
                DialogUtils.dismiss(target.getDialog());
            }).withOnDismissListener(v -> {
                if (!mPayment.owns(token)) return;
                mPayment.finish(token, SystemClock.uptimeMillis());
                XBiometricIdentify fingerprintIdentify = mFingerprintIdentify;
                if (fingerprintIdentify != null) {
                    fingerprintIdentify.cancelIdentify();
                }
            });
            AlertDialog fingerPrintAlertDialog = alipayPayView.showInDialog();
            ViewUtils.setAlpha(fingerPrintAlertDialog, 0);
            ViewUtils.setDimAmount(fingerPrintAlertDialog, 0);
            if (mPayment.isActive(token)) mFingerPrintAlertDialog = fingerPrintAlertDialog;
            else DialogUtils.dismiss(fingerPrintAlertDialog);
        } catch (OutOfMemoryError e) {
        }
    }

    private void watchLayout(View view, ViewTreeObserver.OnGlobalLayoutListener listener) {
        view.getViewTreeObserver().addOnGlobalLayoutListener(listener);
        mLayoutCleanup.add(() -> {
            if (view.getViewTreeObserver().isAlive()) view.getViewTreeObserver().removeOnGlobalLayoutListener(listener);
        });
    }

    private void clearActivityObservers() {
        for (Runnable cleanup : mLayoutCleanup) cleanup.run();
        mLayoutCleanup.clear();
        for (Task.Handle task : mActivityTasks) task.cancel();
        mActivityTasks.clear();
        if (mPaymentMethodTask != null) mPaymentMethodTask.cancel();
        mPaymentMethodTask = null;
    }

    private void closePayment(Activity activity) {
        if (activity == null || (mPayment.owner() != activity && mCurrentActivity != activity)) return;
        if (mPayment.owner() == activity) releasePaymentObjects();
        mPayment.close(activity);
        if (mCurrentActivity == activity) { clearActivityObservers(); mCurrentActivity = null; ++mActivityGeneration; }
        ActivityViewObserverHolder.stop(ActivityViewObserverHolder.Key.TaobaoPasswordView);
    }

    private void releasePaymentObjects() {
        XBiometricIdentify identify = mFingerprintIdentify;
        mFingerprintIdentify = null;
        AlertDialog dialog = mFingerPrintAlertDialog;
        mFingerPrintAlertDialog = null;
        if (identify != null) identify.cancelIdentify();
        DialogUtils.dismiss(dialog);
    }

    private void setupPaymentItemOnClickListener(ViewGroup rootView) {
        List<View> paymentMethodsViewList = new ArrayList<>();
        ViewUtils.getChildViewsByRegex(rootView, ".*选中,.+", paymentMethodsViewList);
        long paymentMethodsViewListSize = paymentMethodsViewList.size();
        for (int i = 0; i < paymentMethodsViewListSize; i++) {
            View paymentMethodView = paymentMethodsViewList.get(i);
            L.d("paymentMethodView", ViewUtils.getViewInfo(paymentMethodView));
            // 只取第一次, 防止多次调用造成出错
            View.OnClickListener originPaymentMethodListener = (View.OnClickListener) paymentMethodView.getTag(R.id.alipay_payment_method_item_click_listener);
            if (originPaymentMethodListener == null) {
                paymentMethodView.setTag(R.id.alipay_payment_method_item_click_listener, ViewUtils.getOnClickListener(paymentMethodView));
            }
            paymentMethodView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        if (mFingerprintIdentifyTemporaryBlocking) {
                            return;
                        }
                        AlertDialog dialog = mFingerPrintAlertDialog;
                        if (dialog == null) {
                            return;
                        }
                        mPayment.close(activityFromView(v));
                        DialogUtils.dismiss(dialog);
                    } finally {
                        View.OnClickListener originPaymentMethodListener = (View.OnClickListener) v.getTag(R.id.alipay_payment_method_item_click_listener);
                        if (originPaymentMethodListener != null && originPaymentMethodListener != this) {
                            originPaymentMethodListener.onClick(v);
                        }
                    }
                }
            });
        }
    }

    private Activity activityFromView(View view) { return ActivityContext.find(view.getContext()); }

    private void reEnteredPayDialogSolution(Activity activity) {
        int versionCode = getVersionCode(activity);
        if (versionCode < 643 /** 10.36.10 */) {
            return;
        }
        ViewGroup rootView = (ViewGroup)activity.getWindow().getDecorView();
        setupPaymentItemOnClickListener(rootView);
        // 在10s内寻找密码框
        ActivityViewObserver activityViewObserver = new ActivityViewObserver(activity);
        activityViewObserver.setActivityViewFinder(outViewList -> {
            EditText view = findPasswordEditText(activity);
            if (view != null) {
                outViewList.add(view);
            }
            View shortPwdView = com.surcumference.fingerprint.util.ShortPasswordTarget.find(rootView);
            if (ViewUtils.isShown(shortPwdView)) {
                outViewList.add(shortPwdView);
            }
        });
        ActivityViewObserverHolder.start(ActivityViewObserverHolder.Key.TaobaoPasswordView,
                activityViewObserver, 300, (observer, view) -> {
                    ActivityViewObserverHolder.stop(observer);
                    L.d("找到密码框", view);
                    long generation = mActivityGeneration;
                    watchLayout(view, () -> {
                        if (mPaymentMethodTask != null) mPaymentMethodTask.cancel();
                        mPaymentMethodTask = Task.onMain(666, () -> {
                            if (generation == mActivityGeneration && mCurrentActivity == activity
                                    && ActivityContext.isAlive(activity)) setupPaymentItemOnClickListener(rootView);
                        });
                    });
                },
                10000);
    }

    @Nullable
    private boolean tryInputGenericPassword(Activity activity, String password) {
        EditText pwdEditText = findPasswordEditText(activity);
        if (pwdEditText == null) {
            return false;
        }
        View confirmPwdBtn = findConfirmPasswordBtn(activity);
        L.d("confirmPwdBtn", confirmPwdBtn);
        if (confirmPwdBtn == null) {
            return false;
        }
        pwdEditText.setText(password);
        confirmPwdBtn.performClick();
        return true;
    }

    private EditText findPasswordEditText(Activity activity) {
        View pwdEditText = ViewUtils.findViewByName(activity, "com.taobao.taobao", "input_et_password");;
        if (pwdEditText instanceof EditText) {
            if (!pwdEditText.isShown()) {
                return null;
            }
            return (EditText) pwdEditText;
        }
        // long password
        ViewGroup viewGroup = (ViewGroup)activity.getWindow().getDecorView();
        List<View> outList = new ArrayList<>();
        ViewUtils.getChildViews(viewGroup, "", outList);
        for (View view : outList) {
            if (view instanceof EditText) {
                if (view.getId() != -1) {
                    continue;
                }
                if (!view.isShown()) {
                    continue;
                }
                return (EditText) view;
            }
        }
        return null;
    }

    private View findConfirmPasswordBtn(Activity activity) {
        View okView =  ViewUtils.findViewByName(activity, "com.taobao.taobao", "button_ok");
        if (okView != null) {
            if (!okView.isShown()) {
                return null;
            }
            return okView;
        }
        ViewGroup rootView = (ViewGroup)activity.getWindow().getDecorView();
        List<View> outList = new ArrayList<>();
        ViewUtils.getChildViewsByRegex(rootView, "确定|確定|OK|确认|付款|確認|Pay", outList);
        int versionCode = getVersionCode(activity);
        for (View view : outList) {
            if (view.getId() != -1) {
                continue;
            }
            if (!view.isShown()) {
                continue;
            }
            // 跳过键盘上的OK
            if (view.getParent().toString().contains(":id/key_enter")) {
                continue;
            }
            if (versionCode < 643 /** 10.36.10 */) {
                return (View) view.getParent();
            }
            return view;
        }
        return null;
    }
}
