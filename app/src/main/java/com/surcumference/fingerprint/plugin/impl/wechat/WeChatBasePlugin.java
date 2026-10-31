package com.surcumference.fingerprint.plugin.impl.wechat;

import static com.surcumference.fingerprint.Constant.PACKAGE_NAME_WECHAT;

import android.app.Activity;
import com.surcumference.fingerprint.util.PaymentPasswordGuard;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hjq.toast.Toaster;
import com.surcumference.fingerprint.Constant;
import com.surcumference.fingerprint.Lang;
import com.surcumference.fingerprint.R;
import com.surcumference.fingerprint.bean.DigitPasswordKeyPadInfo;
import com.surcumference.fingerprint.plugin.inf.IAppPlugin;
import com.surcumference.fingerprint.plugin.inf.IMockCurrentUser;
import com.surcumference.fingerprint.plugin.inf.OnFingerprintVerificationOKListener;
import com.surcumference.fingerprint.util.ActivityContext;
import com.surcumference.fingerprint.util.PaymentController;
import com.surcumference.fingerprint.util.ActivityViewObserver;
import com.surcumference.fingerprint.util.ActivityViewObserverHolder;
import com.surcumference.fingerprint.util.ApplicationUtils;
import com.surcumference.fingerprint.util.BizBiometricIdentify;
import com.surcumference.fingerprint.util.BlackListUtils;
import com.surcumference.fingerprint.util.Config;
import com.surcumference.fingerprint.util.Task;
import com.surcumference.fingerprint.util.ViewUtils;
import com.surcumference.fingerprint.util.WeChatVersionControl;
import com.surcumference.fingerprint.util.XBiometricIdentify;
import com.surcumference.fingerprint.util.log.L;
import com.surcumference.fingerprint.util.paydialog.WeChatPayDialog;
import com.surcumference.fingerprint.view.AlipayPayView;
import com.surcumference.fingerprint.view.DialogUtils;
import com.wei.android.lib.fingerprintidentify.bean.FingerprintIdentifyFailInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

public class WeChatBasePlugin implements IAppPlugin, IMockCurrentUser {


    protected static final int DISMISS_NORMAL = 2;
    protected static final int DISMISS_WXA_LITE_APP_PAUSE = 3;

    private WeakHashMap<View, View.OnAttachStateChangeListener> mView2OnAttachStateChangeListenerMap = new WeakHashMap<>();
    protected boolean mMockCurrentUser = false;
    protected XBiometricIdentify mFingerprintIdentify;
    private int mWeChatVersionCode = 0;
    private volatile boolean mFingerprintIdentifyTemporaryBlocking = false;
    private final PaymentController mPaymentSession = new PaymentController();
    private Activity mObservedActivity;
    private long mObserverGeneration;
    private Task.Handle mObserverRestart;
    private final Handler mInputHandler = new Handler(Looper.getMainLooper());
    private Runnable mRestorePassword;
    private ViewGroup mPaymentRoot;
    private AlertDialog mPaymentDialog;
    private PaymentMethodWatch mPaymentMethodWatch;
    private final WeChatPaymentDimmer mPaymentDimmer = new WeChatPaymentDimmer();
    private final java.util.Random mInputRandom = new java.util.Random();

    public int getVersionCode(Context context) {
        if (mWeChatVersionCode != 0) {
            return mWeChatVersionCode;
        }
        mWeChatVersionCode = ApplicationUtils.getPackageVersionCode(context, PACKAGE_NAME_WECHAT);
        return mWeChatVersionCode;
    }

    protected synchronized void initFingerPrintLock(Context context, Config config,
                                                    String passwordEncrypted,
                                                    OnFingerprintVerificationOKListener onSuccessUnlockCallback,
                                                    final Runnable onFailureUnlockCallback) {
        cancelFingerprintIdentify();
        Activity activity = ActivityContext.find(context);
        final long token = mPaymentSession.token();
        if (activity == null || !mPaymentSession.isActive(token)) {
            onFailureUnlockCallback.run();
            return;
        }
        BizBiometricIdentify identify = new BizBiometricIdentify(activity).withMockCurrentUserCallback(this);
        mFingerprintIdentify = identify;
        mPaymentSession.authentication(token, identify);
        identify.decryptPasscode(passwordEncrypted, new BizBiometricIdentify.IdentifyListener() {
            @Override public void onInited(BizBiometricIdentify identify) {
                if (!mPaymentSession.isActive(token)) return;
                ViewUtils.setAlpha(mPaymentDialog, identify.isUsingBiometricApi() ? 0 : 1);
                if (mPaymentDialog != null) mPaymentDialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            }

            @Override
            public void onDecryptionSuccess(BizBiometricIdentify identify, @NonNull String password) {
                if (!mPaymentSession.authenticated(token)) {
                    return;
                }
                try {
                    if (!ActivityContext.isAlive(activity)) {
                        onFailureUnlockCallback.run();
                        return;
                    }
                    onSuccessUnlockCallback.onFingerprintVerificationOK(password);
                } catch (Exception e) {
                    L.e("WeChat password input failed", e);
                    onFailureUnlockCallback.run();
                }
            }

            @Override
            public void onFailed(BizBiometricIdentify identify, FingerprintIdentifyFailInfo info) {
                if (mPaymentSession.isActive(token)) {
                    onFailureUnlockCallback.run();
                }
            }
        });
    }



    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        //Xposed not hooked yet!
    }

    @Override
    public void onActivityResumed(Activity activity) {
        try {
            handleActivityResumed(activity);
        } catch (Exception e) {
            L.e("WeChat payment detection failed", e);
            restorePassword();
        }
    }

    private void handleActivityResumed(Activity activity) {
        if (mObservedActivity != null) clearObservation(mObservedActivity);
        mObservedActivity = activity;
        final long observerGeneration = ++mObserverGeneration;
        if (mPaymentSession.owner() != null && mPaymentSession.owner() != activity) {
            closePayment();
        }
        L.d("Activity onResume =", activity);
        final String activityClzName = activity.getClass().getName();
        if (isLiteAppPaymentActivity(activityClzName)) {
            ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
            ActivityViewObserver observer = new ActivityViewObserver(activity);
            observer.setWatchActivityViewOnly(true);
            observer.setActivityViewFinder(views -> {
                int id = decorView.getResources().getIdentifier("tenpay_keyboard_0", "id", activity.getPackageName());
                int fallbackId = decorView.getResources().getIdentifier("tenpay_keyboard_1", "id", activity.getPackageName());
                if (id == 0 && fallbackId == 0) return;
                List<View> keys = new ArrayList<>();
                if (id != 0) ViewUtils.getChildViews(decorView, id, keys);
                if (fallbackId != 0 && fallbackId != id) ViewUtils.getChildViews(decorView, fallbackId, keys);
                for (View key : keys) {
                    if (isPaymentViewVisible(key) && ViewUtils.isShownInScreen(key)) views.add(key);
                }
            });
            ActivityViewObserverHolder.start(ActivityViewObserverHolder.Key.WeChatPayView, observer, 100,
                    (current, key) -> {
                        if (observerGeneration != mObserverGeneration || mObservedActivity != activity
                                || !ActivityContext.isAlive(activity)) {
                            ActivityViewObserverHolder.stop(current);
                            return;
                        }
                        // A finished session still owns its target, so cancellation cannot restart authentication.
                        if (!ownsKeyboardPayment(activity, key)) {
                            onPayDialogShownByKeyboard(activity, decorView, key);
                        }
                        if (ownsKeyboardPayment(activity, key)) {
                            L.d("onViewFounded(LiteApp):", key, " rootView:", decorView);
                            ActivityViewObserverHolder.stop(current);
                        }
                    });
        } else if (activityClzName.contains(".WalletPayUI")
                || activityClzName.contains(".UIPageFragmentActivity")) {
            ActivityViewObserver activityViewObserver = new ActivityViewObserver(activity);
            activityViewObserver.setWatchActivityViewOnly(true);
            activityViewObserver.setActivityViewFinder(views -> {
                List<View> windows = new ArrayList<>(ViewUtils.getWindowManagerViews());
                View decor = activity.getWindow().getDecorView();
                if (!windows.contains(decor)) windows.add(decor);
                for (View window : windows) {
                    if (!(window instanceof ViewGroup)) continue;
                    List<View> forms = new ArrayList<>();
                    ViewUtils.getChildViewsByType((ViewGroup) window, ".EditHintPasswdView", forms);
                    for (View form : forms) {
                        if (ActivityContext.find(form.getContext()) == activity) views.add(form);
                    }
                }
            });
            ActivityViewObserverHolder.start(ActivityViewObserverHolder.Key.WeChatPayView,  activityViewObserver,
                    100, new ActivityViewObserver.IActivityViewListener() {
                @Override
                public void onViewFounded(ActivityViewObserver observer, View view) {
                    if (observerGeneration != mObserverGeneration || mObservedActivity != activity
                            || !ActivityContext.isAlive(activity)) {
                        ActivityViewObserverHolder.stop(observer);
                        return;
                    }
                    if (!(view instanceof ViewGroup) || !ViewUtils.isShownInScreen(view)) return;
                    if (mPaymentMethodWatch != null && mPaymentMethodWatch.activity == activity
                            && (mPaymentMethodWatch.selecting || mPaymentMethodWatch.awaitingPayment)) return;
                    Object target = mPaymentSession.target();
                    if (mPaymentSession.owner() == activity && target instanceof View) {
                        for (View current = (View) target; current != null;
                                current = current.getParent() instanceof View ? (View) current.getParent() : null) {
                            if (current == view) return;
                        }
                    }
                    ViewGroup rootView = (ViewGroup) view.getRootView();
                    if (WeChatPayDialog.findFrom(getVersionCode(activity), rootView, false) != null)
                        onPayDialogShown(activity, rootView);
                }
            });
        }
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        //Xposed not hooked yet!
    }

    @Override
    public void onActivityPaused(Activity activity) {
        try {
            if (isSystemAuthenticationShowing(activity)) {
                return;
            }
            if (mPaymentMethodWatch != null && mPaymentMethodWatch.activity == activity)
                stopPaymentMethodWatch();
            clearObservation(activity);
            L.d("Activity onPause =", activity);
            final String activityClzName = activity.getClass().getName();
            if (!activityClzName.contains(".WalletPayUI") && !activityClzName.contains(".UIPageFragmentActivity")) {
                if (isLiteAppPaymentActivity(activityClzName)) {
                    onPayDialogDismiss(activity, activity.getWindow().getDecorView(), DISMISS_WXA_LITE_APP_PAUSE);
                }
            }
            onPayDialogDismiss(activity, activity.getWindow().getDecorView(), DISMISS_NORMAL);
        } catch (Exception e) {
            L.e(e);
        }
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        boolean inlineOwner = mPaymentMethodWatch != null && mPaymentMethodWatch.activity == activity;
        clearObservation(activity);
        if (mPaymentSession.owner() == activity || inlineOwner) {
            closePayment();
        }
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
        //Xposed not hooked yet!
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        onActivityStopped(activity);
    }

    @Override
    public boolean getMockCurrentUser() {
        return this.mMockCurrentUser;
    }

    @Override
    public void setMockCurrentUser(boolean mock) {
        this.mMockCurrentUser = mock;
    }

    protected void onPayDialogShownByKeyboard(Activity activity, ViewGroup rootView, View keyboardKeyView) {
        try {
            showKeyboardPayment(activity, rootView, keyboardKeyView);
        } catch (Exception e) {
            L.e("WeChat keyboard payment failed", e);
            restorePassword();
        }
    }

    private void showKeyboardPayment(Activity activity, ViewGroup rootView, View keyboardKeyView) {
        Context context = activity;
        if (!ActivityContext.isAlive(activity) || mFingerprintIdentifyTemporaryBlocking) {
            return;
        }
        Config config = Config.from(context);
        if (!config.isInScope()) {
            return;
        }
        String passwordEncrypted = config.getPasswordEncrypted();

        if (ownsKeyboardPayment(activity, keyboardKeyView)) {
            return;
        }
        ViewGroup passwordLayout = keyboardLayout(keyboardKeyView);
        ViewGroup keyboardContainer = (passwordLayout == null || passwordLayout.getParent() == null || !(passwordLayout.getParent() instanceof ViewGroup))
                ? null : (ViewGroup) passwordLayout.getParent();

        if (passwordLayout == null || keyboardContainer == null) {
            return;
        }

        // Module checks: container's parent must be a ViewGroup with exactly 1 child
        if (!(keyboardContainer.getParent() instanceof ViewGroup)
                || ((ViewGroup) keyboardContainer.getParent()).getChildCount() != 1) {
            return;
        }

        EditText input = null;
        List<View> inputs = new ArrayList<>();
        ViewUtils.getChildViewsByType(rootView, ".TenpaySecureEditText", inputs);
        for (View candidate : inputs)
            if (candidate instanceof EditText && isPaymentViewVisible(candidate)) input = (EditText) candidate;
        startPayment(activity, rootView, config, passwordEncrypted, input,
                java.util.Collections.singletonList(passwordLayout), keyboardKeyView);
    }

    private static ViewGroup keyboardLayout(View key) {
        ViewParent row = key == null ? null : key.getParent();
        return row != null && row.getParent() instanceof ViewGroup ? (ViewGroup) row.getParent() : null;
    }

    private boolean ownsKeyboardPayment(Activity activity, View key) {
        if (mPaymentSession.owner() != activity) return false;
        Object target = mPaymentSession.target();
        ViewGroup keyboard = keyboardLayout(key);
        return target == key || (keyboard != null && target instanceof View
                && keyboardLayout((View) target) == keyboard);
    }

    private void inputDigitalPasswordByKeyboard(Context context, View keyboard, String password, int versionCode,
                                             long token, Runnable restore) {
        List<View> keys = resolvePasswordKeys(context, keyboard, password,
                WeChatVersionControl.getDigitPasswordKeyPad(versionCode));
        boolean direct = true;
        for (View key : keys) direct &= key.hasOnClickListeners();
        if (!direct) {
            inputPasswordKey(keys, new java.util.IdentityHashMap<>(), 0, token, restore, 0);
            return;
        }
        mInputHandler.post(() -> {
            try {
                for (View key : keys) {
                    if (!isInputCurrent(token, key) || !key.isEnabled()) {
                        if (mPaymentSession.owns(token)) restore.run();
                        return;
                    }
                    // Invoke the host listener synchronously so repeated keys cannot coalesce in View's posted click.
                    if (!key.performClick()) { restore.run(); return; }
                    if (!mPaymentSession.isActive(token)) return;
                }
                mPaymentDimmer.completed();
                restore.run();
            } catch (Exception error) {
                L.e("WeChat keyboard input failed", error);
                restore.run();
            }
        });
    }

    private void inputPasswordKey(List<View> keys, java.util.Map<View, Long> readyAt,
                                  int index, long token, Runnable restore, long delay) {
        mInputHandler.postDelayed(() -> {
            View key = keys.get(index);
            if (!isInputCurrent(token, key) || !key.isEnabled()) {
                if (mPaymentSession.owns(token)) restore.run();
                return;
            }
            try {
                long dispatchedAt = SystemClock.uptimeMillis();
                if (!ViewUtils.performActionClick(key)) { restore.run(); return; }
                if (!mPaymentSession.isActive(token)) return;
                // Android can defer clearing pressed state after UP on the same View.
                readyAt.put(key, SystemClock.uptimeMillis() + 65 + mInputRandom.nextInt(6));
                if (index + 1 < keys.size()) {
                    Long nextReady = readyAt.get(keys.get(index + 1));
                    long due = Math.max(dispatchedAt + 4 + mInputRandom.nextInt(5), nextReady == null ? 0 : nextReady);
                    inputPasswordKey(keys, readyAt, index + 1, token, restore,
                            Math.max(0, due - SystemClock.uptimeMillis()));
                } else {
                    mPaymentDimmer.completed();
                    restore.run();
                }
            } catch (Exception e) {
                L.e("WeChat keyboard input failed", e);
                restore.run();
            }
        }, delay);
    }

    private List<View> resolvePasswordKeys(Context context, View keyboard, String password,
                                           DigitPasswordKeyPadInfo keyPad) {
        List<View> keys = com.surcumference.fingerprint.util.DigitKeyResolver.resolve(keyboard, keyPad, password);
        if (keys == null) throw new IllegalStateException("Password keyboard mapping is incomplete or ambiguous");
        return keys;
    }

    private boolean isInputCurrent(long token, View view) {
        return mPaymentSession.isActive(token) && isPaymentViewVisible(view)
                && ActivityContext.isAlive((Activity) mPaymentSession.owner());
    }

    protected void onPayDialogShown(Activity activity, ViewGroup rootView) {
        try {
            showClassicPayment(activity, rootView);
        } catch (Exception e) {
            L.e("WeChat payment failed", e);
            restorePassword();
        }
    }

    private void showClassicPayment(Activity activity, ViewGroup rootView) {
        if (!ActivityContext.isAlive(activity)) return;
        L.d("PayDialog show");
        Context context = activity;
        Config config = Config.from(context);
        if (!config.isInScope()) {
            return;
        }
        if (mFingerprintIdentifyTemporaryBlocking) {
            return;
        }
        String passwordEncrypted = config.getPasswordEncrypted();

        int versionCode = getVersionCode(context);
        WeChatPayDialog payDialogView = WeChatPayDialog.findFrom(versionCode, rootView, false);
        L.d(payDialogView);
        if (payDialogView == null) {
            return;
        }

        Object previous = mPaymentSession.target();
        if (mPaymentSession.owner() == activity && previous != payDialogView.inputEditText
                && previous instanceof View && !ViewUtils.isShownInScreen((View) previous)) {
            closePayment();
        }
        startPayment(activity, rootView, config, passwordEncrypted, payDialogView.inputEditText,
                payDialogView.keyboardViews, null);
    }

    private void startPayment(Activity activity, ViewGroup root, Config config, String encrypted,
                              EditText input, List<View> keyboards, View keyboardKey) {
        View target = keyboardKey != null ? keyboardKey : input;
        if (keyboards.isEmpty() || !isPaymentViewVisible(target) || !isPasswordInputEmpty(input)
                || !PaymentPasswordGuard.ready(config, target)
                || !mPaymentSession.begin(activity, target, SystemClock.uptimeMillis())) return;
        long token = mPaymentSession.token();
        mPaymentRoot = root;
        watchPaymentTarget(target, token);
        Runnable finish = () -> {
            if (mPaymentSession.owns(token)) mPaymentSession.finish(token, SystemClock.uptimeMillis());
        };
        Runnable suspend = () -> {
            mFingerprintIdentifyTemporaryBlocking = true;
            finish.run();
            Toaster.showLong(Lang.getString(R.id.toast_fingerprint_temporary_disabled));
            Task.onBackground(60000, () -> mFingerprintIdentifyTemporaryBlocking = false);
        };
        mRestorePassword = finish;
        mPaymentSession.restoreWith(token, () -> {
            mInputHandler.removeCallbacksAndMessages(null);
            dismissPaymentDialog();
            mPaymentDimmer.release();
            cancelFingerprintIdentify();
            mMockCurrentUser = false;
        });
        mPaymentSession.bindToPaymentWindow(token, activity.getWindow(), config.isVolumeDownMonitorEnabled(), suspend);
        mPaymentSession.protectPortraitKeyboards(token, keyboards);
        stopPaymentMethodWatch();
        mPaymentMethodWatch = new PaymentMethodWatch(activity, root, keyboardKey);
        mPaymentMethodWatch.schedule();
        mPaymentDimmer.show(target);
        if (!config.isHideFingerprintDialog()) {
            AlipayPayView prompt = new AlipayPayView(activity)
                    .withOnCancelButtonClickListener(shown -> finish.run())
                    .withOnDismissListener(shown -> {
                        if (mPaymentDialog == ((AlipayPayView) shown).getDialog()) finish.run();
                    });
            AlertDialog dialog = prompt.showInDialog();
            if (!mPaymentSession.isActive(token)) { DialogUtils.dismiss(dialog); return; }
            mPaymentDialog = dialog;
            dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            if (config.isVolumeDownMonitorEnabled()) {
                ViewUtils.registerVolumeKeyDownEventListener(dialog.getWindow(), event -> {
                    if (!mPaymentSession.isActive(token)) return false;
                    suspend.run();
                    return true;
                });
            }
        }
        initFingerPrintLock(activity, config, encrypted, password -> {
            if (!mPaymentSession.isActive(token)) return;
            if (!isPasswordInputEmpty(input)) { finish.run(); return; }
            stopPaymentMethodWatch();
            dismissPaymentDialog();
            wakePasswordKeyboard(input);
            if (!isPasswordInputEmpty(input)) { finish.run(); return; }
            BlackListUtils.applyIfNeeded(activity);
            if (keyboardKey != null) {
                inputDigitalPasswordByKeyboard(activity, keyboards.get(0), password, getVersionCode(activity), token, finish);
            } else {
                inputDigitalPassword(activity, input, password, keyboards, token, finish);
            }
        }, finish);
    }

    private void dismissPaymentDialog() {
        AlertDialog dialog = mPaymentDialog;
        mPaymentDialog = null;
        if (dialog != null) ViewUtils.unregisterVolumeKeyDownEventListener(dialog.getWindow());
        DialogUtils.dismiss(dialog);
    }

    private static boolean isPasswordInputEmpty(EditText input) {
        if (input == null) return true;
        if (input.length() != 0) return false;
        try {
            // The secure buffer can retain characters even when no password dots are visible.
            Object length = input.getClass().getMethod("getInputLength").invoke(input);
            return length instanceof Number && ((Number) length).intValue() == 0;
        } catch (NoSuchMethodException ignored) {
            return true;
        } catch (Exception error) {
            L.e("WeChat password state unavailable", error);
            return false;
        }
    }

    private static void wakePasswordKeyboard(EditText input) {
        if (isPaymentViewVisible(input) && input.length() == 0) {
            input.requestFocus();
            input.performClick();
        }
    }

    private void stopPaymentMethodWatch() {
        if (mPaymentMethodWatch != null && mPaymentMethodWatch.pending != null)
            mPaymentMethodWatch.pending.cancel();
        mPaymentMethodWatch = null;
    }

    private final class PaymentMethodWatch {
        final Activity activity;
        final ViewGroup root;
        final View keyboardKey;
        final View paymentTarget;
        Task.Handle pending;
        boolean selecting;
        boolean awaitingPayment;
        long missingSince;
        PaymentMethodWatch(Activity activity, ViewGroup root, View keyboardKey) {
            this.activity = activity;
            this.root = root;
            this.keyboardKey = keyboardKey;
            paymentTarget = (View) mPaymentSession.target();
        }
        void schedule() { pending = Task.onMain(150, this::check); }
        void check() {
            if (mPaymentMethodWatch != this) return;
            if (!ActivityContext.isAlive(activity)) { stopPaymentMethodWatch(); return; }
            List<View> windows = new ArrayList<>(ViewUtils.getWindowManagerViews());
            if (!windows.contains(root)) windows.add(root);
            boolean visible = false;
            for (View window : windows) {
                if (!(window instanceof ViewGroup) || (window != root && ActivityContext.find(window.getContext()) != activity)) continue;
                List<View> choices = new ArrayList<>();
                for (String text : new String[]{"选择付款方式", "選擇付款方式", "Select payment method", "请选择优惠", "請選擇優惠", "Select discount"})
                    ViewUtils.getChildViews((ViewGroup) window, text, choices);
                for (View choice : choices) if (isPaymentViewVisible(choice)) visible = true;
            }
            if (visible && !selecting) {
                mPaymentSession.close(activity);
                awaitingPayment = true;
            }
            selecting = visible;
            if (!visible && awaitingPayment && !mFingerprintIdentifyTemporaryBlocking) {
                for (View window : windows) {
                    if (!(window instanceof ViewGroup) || !isPaymentViewVisible(window)
                            || (window != root && ActivityContext.find(window.getContext()) != activity)) continue;
                    if (keyboardKey != null) {
                        View key = keyboardKey.getWindowToken() != null ? keyboardKey
                                : ViewUtils.findViewByName(window, activity.getPackageName(), "tenpay_keyboard_0");
                        if (!isPaymentViewVisible(key) || key.getRootView() != window) continue;
                        stopPaymentMethodWatch();
                        onPayDialogShownByKeyboard(activity, (ViewGroup) window, key);
                    } else {
                        List<View> forms = new ArrayList<>();
                        ViewUtils.getChildViewsByType((ViewGroup) window, ".EditHintPasswdView", forms);
                        if (forms.isEmpty()) continue;
                        // The host restores its form and keyboard across multiple layout passes.
                        WeChatPayDialog form = WeChatPayDialog.findFrom(getVersionCode(activity), (ViewGroup) window, false);
                        if (form == null || !isPaymentViewVisible(form.inputEditText)) continue;
                        stopPaymentMethodWatch();
                        if (form.inputEditText.length() == 0) onPayDialogShown(activity, (ViewGroup) window);
                    }
                    return;
                }
            }
            if (!visible && (!isPaymentViewVisible(root) || (!awaitingPayment && !isPaymentViewVisible(paymentTarget)))) {
                if (missingSince == 0) missingSince = SystemClock.uptimeMillis();
                if (SystemClock.uptimeMillis() - missingSince >= 1000) { stopPaymentMethodWatch(); return; }
            } else missingSince = 0;
            schedule();
        }
    }

    private static boolean isPaymentViewVisible(View view) {
        if (view == null || view.getWindowToken() == null || !view.isShown() || view.getWindowVisibility() != View.VISIBLE) return false;
        for (View ancestor = view; ancestor != null; ancestor = ancestor.getParent() instanceof View ? (View) ancestor.getParent() : null)
            if (ancestor.getAlpha() <= 0) return false;
        return true;
    }

    private void inputDigitalPassword(Context context, EditText input, String password,
                                      List<View> keyboards, long token, Runnable restore) {
        if (!isInputCurrent(token, input) || !isPasswordInputEmpty(input)) {
            restore.run();
            return;
        }
        int version = getVersionCode(context);
        if (version >= Constant.WeChat.WECHAT_VERSION_CODE_8_0_43) {
            View keyboard = null;
            for (View candidate : keyboards) {
                if (ViewUtils.isShownInScreen(candidate)) { keyboard = candidate; break; }
            }
            if (keyboard == null) { restore.run(); return; }
            inputDigitalPasswordByKeyboard(context, keyboard, password, version, token, restore);
        } else {
            if (version >= Constant.WeChat.WECHAT_VERSION_CODE_8_0_18) {
                for (int i = 0; i < password.length() && mPaymentSession.isActive(token); i++) {
                    input.append(String.valueOf(password.charAt(i)));
                }
            } else {
                input.setText(password);
            }
            if (mPaymentSession.isActive(token)) {
                mPaymentDimmer.completed();
                restore.run();
            }
        }
    }

    /**
     * @param param {@link #DISMISS_NORMAL} for normal dismiss, {@link #DISMISS_WXA_LITE_APP_PAUSE} for WxaLiteApp pause.
     */
    protected void onPayDialogDismiss(Context context, View rootView, int param) {
        if (rootView != mPaymentRoot) return;
        Activity activity = ActivityContext.find(context);
        if (activity != null && isSystemAuthenticationShowing(activity)) return;
        closePayment();
    }

    protected void onPayDialogDismiss(Context context, View rootView) {
        if (rootView == mPaymentRoot) closePayment();
    }

    private boolean isSystemAuthenticationShowing(Activity activity) {
        return mPaymentSession.isAuthenticating(activity) && mFingerprintIdentify != null
                && mFingerprintIdentify.isUsingBiometricApi() && ActivityContext.isAlive(activity);
    }

    private static boolean isLiteAppPaymentActivity(String name) {
        return name.contains(".WxaLiteAppTransparentLiteUI") || name.contains(".WxaLiteAppPayTransparentLiteUI");
    }

    private void clearObservation(Activity activity) {
        if (mObservedActivity == activity) {
            mObservedActivity = null;
            ++mObserverGeneration;
            if (mObserverRestart != null) mObserverRestart.cancel();
            mObserverRestart = null;
            ActivityViewObserverHolder.stop(ActivityViewObserverHolder.Key.WeChatPayView);
        }
        java.util.Iterator<java.util.Map.Entry<View, View.OnAttachStateChangeListener>> entries = mView2OnAttachStateChangeListenerMap.entrySet().iterator();
        while (entries.hasNext()) {
            java.util.Map.Entry<View, View.OnAttachStateChangeListener> entry = entries.next();
            if (ActivityContext.find(entry.getKey().getContext()) == activity) {
                entry.getKey().removeOnAttachStateChangeListener(entry.getValue());
                entries.remove();
            }
        }
    }

    private void watchPaymentTarget(View target, long token) {
        Activity activity = (Activity) mPaymentSession.owner();
        long generation = mObserverGeneration;
        View.OnAttachStateChangeListener previous = mView2OnAttachStateChangeListenerMap.remove(target);
        if (previous != null) target.removeOnAttachStateChangeListener(previous);
        View.OnAttachStateChangeListener listener = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) {}
            @Override public void onViewDetachedFromWindow(View view) {
                view.removeOnAttachStateChangeListener(this);
                mView2OnAttachStateChangeListenerMap.remove(view);
                if (mPaymentSession.owns(token)) {
                    closePayment();
                } else if (mPaymentSession.owner() != null || mPaymentSession.token() != token + 1) {
                    return;
                }
                if (mObserverRestart != null) mObserverRestart.cancel();
                mObserverRestart = Task.onMain(500, () -> {
                    if (generation == mObserverGeneration && mObservedActivity == activity
                            && ActivityContext.isAlive(activity) && activity.hasWindowFocus()
                            && mPaymentSession.owner() == null) onActivityResumed(activity);
                });
            }
        };
        target.addOnAttachStateChangeListener(listener);
        mView2OnAttachStateChangeListenerMap.put(target, listener);
    }

    private void restorePassword() {
        try {
            if (mRestorePassword != null) mRestorePassword.run();
        } finally {
            mPaymentSession.finish(mPaymentSession.token(), SystemClock.uptimeMillis());
            mInputHandler.removeCallbacksAndMessages(null);
            cancelFingerprintIdentify();
        }
    }

    private void closePayment() {
        stopPaymentMethodWatch();
        Object owner = mPaymentSession.owner();
        restorePassword();
        mInputHandler.removeCallbacksAndMessages(null);
        cancelFingerprintIdentify();
        mPaymentSession.close(owner);
        mPaymentDimmer.close();
        mRestorePassword = null;
        mPaymentRoot = null;
    }

    private void cancelFingerprintIdentify() {
        XBiometricIdentify identify = mFingerprintIdentify;
        mFingerprintIdentify = null;
        if (identify != null) identify.cancelIdentify();
    }

}
