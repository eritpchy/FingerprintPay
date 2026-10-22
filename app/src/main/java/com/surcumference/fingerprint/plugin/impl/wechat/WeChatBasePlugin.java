package com.surcumference.fingerprint.plugin.impl.wechat;

import static com.surcumference.fingerprint.Constant.PACKAGE_NAME_WECHAT;

import android.app.Activity;
import com.surcumference.fingerprint.util.PaymentPasswordGuard;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
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
import com.surcumference.fingerprint.plugin.impl.SettingEntry;
import com.surcumference.fingerprint.util.ActivityContext;
import com.surcumference.fingerprint.util.PaymentController;
import com.surcumference.fingerprint.util.ActivityViewObserver;
import com.surcumference.fingerprint.util.ActivityViewObserverHolder;
import com.surcumference.fingerprint.util.ApplicationUtils;
import com.surcumference.fingerprint.util.BizBiometricIdentify;
import com.surcumference.fingerprint.util.BlackListUtils;
import com.surcumference.fingerprint.util.Config;
import com.surcumference.fingerprint.util.NotifyUtils;
import com.surcumference.fingerprint.util.StyleUtils;
import com.surcumference.fingerprint.util.Task;
import com.surcumference.fingerprint.util.ViewUtils;
import com.surcumference.fingerprint.util.WeChatVersionControl;
import com.surcumference.fingerprint.util.XBiometricIdentify;
import com.surcumference.fingerprint.util.log.L;
import com.surcumference.fingerprint.util.paydialog.WeChatPayDialog;
import com.surcumference.fingerprint.view.AlipayPayView;
import com.surcumference.fingerprint.view.DialogUtils;
import com.wei.android.lib.fingerprintidentify.bean.FingerprintIdentifyFailInfo;

import java.lang.reflect.Method;
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
    private View mKeyboardListenerRoot;
    private AlertDialog mPaymentDialog;
    private PaymentMethodWatch mPaymentMethodWatch;
    /** 已注入过右上角菜单的设置页，防止 onResume 重复添加菜单项。 */
    private final WeakHashMap<Activity, Boolean> mSettingsMenuInjected = new WeakHashMap<>();

    // LiteApp payment activity support (WeChat 8.0.65+)
    private ViewTreeObserver.OnGlobalLayoutListener mKeyboardLayoutListener;
    private Activity mLiteAppActivity;
    private boolean mLiteAppFirstDetection;

    @Override
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
                ViewUtils.setDimAmount(mPaymentDialog, identify.isUsingBiometricApi() ? 0 : 0.6f);
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
        // 设置页注入「指纹支付」入口。与支付检测互不影响，故独立于下方分支。
        if (isWeChatSettingsActivity(activityClzName)) {
            Task.onMain(100, () -> injectSettingsEntry(activity));
        }
        if (isLiteAppPaymentActivity(activityClzName)) {
            try {
                mLiteAppActivity = activity;
                mLiteAppFirstDetection = true;
                final ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
                removeKeyboardLayoutListener();
                final View initialKey = ViewUtils.findViewByName(decorView, activity.getPackageName(), "tenpay_keyboard_0");
                final boolean[] wasVisible = new boolean[]{initialKey != null && ViewUtils.isShownInScreen(initialKey)};
                mKeyboardLayoutListener = () -> {
                    try {
                        Activity currentActivity = mLiteAppActivity;
                        if (currentActivity == null || currentActivity != activity) {
                            return;
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                            if (activity.isDestroyed()) {
                                return;
                            }
                        }
                        final View keyboardKey = ViewUtils.findViewByName(decorView, activity.getPackageName(), "tenpay_keyboard_0");
                        boolean keyboardVisible = keyboardKey != null && ViewUtils.isShownInScreen(keyboardKey);
                        if (keyboardVisible && !wasVisible[0]) {
                            if (mLiteAppFirstDetection) {
                                L.d("onViewFounded(LiteApp): ", ViewUtils.getViewInfo(keyboardKey), " rootView: ", keyboardKey.getRootView());
                                mLiteAppFirstDetection = false;
                            }
                            Task.onMain(() -> {
                                if (observerGeneration == mObserverGeneration && mObservedActivity == activity
                                        && ActivityContext.isAlive(activity)) onPayDialogShownByKeyboard(activity, decorView, keyboardKey);
                            });
                        } else if (!keyboardVisible && wasVisible[0]) {
                            if (keyboardKey == null || keyboardKey.getWindowToken() == null) {
                                closePayment();
                            }
                        }
                        wasVisible[0] = keyboardVisible;
                    } catch (Exception e) {
                        L.e(e);
                        restorePassword();
                    }
                };
                mKeyboardListenerRoot = decorView;
                decorView.getViewTreeObserver().addOnGlobalLayoutListener(mKeyboardLayoutListener);
                if (wasVisible[0]) {
                    initialKey.post(() -> onPayDialogShownByKeyboard(activity, decorView, initialKey));
                }
            } catch (Exception e) {
                L.e(e);
            }
        } else if (activityClzName.contains(".WalletPayUI")
                || activityClzName.contains(".UIPageFragmentActivity")) {
            ActivityViewObserver activityViewObserver = new ActivityViewObserver(activity);
            activityViewObserver.setViewIdentifyType(".EditHintPasswdView");
            ActivityViewObserverHolder.start(ActivityViewObserverHolder.Key.WeChatPayView,  activityViewObserver,
                    100, new ActivityViewObserver.IActivityViewListener() {
                @Override
                public void onViewFounded(ActivityViewObserver observer, View view) {
                    ActivityViewObserver.IActivityViewListener l = this;
                    ActivityViewObserverHolder.stop(observer);
                    L.d("onViewFounded:", view, " rootView: ", view.getRootView());
                    ViewGroup rootView = (ViewGroup) view.getRootView();
                    view.post(() -> onPayDialogShown(activity, rootView));

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
                            Object target = mPaymentSession.target();
                            if (mPaymentSession.owner() != activity || rootView != mPaymentRoot
                                    || !(target instanceof View) || ((View) target).getWindowToken() != null) {
                                return;
                            }
                            Context context = activity;
                            onPayDialogDismiss(context, rootView);
                            if (Config.from(context).isVolumeDownMonitorEnabled()) {
                                ViewUtils.unregisterVolumeKeyDownEventListener(activity.getWindow());
                            }
                            if (mObserverRestart != null) mObserverRestart.cancel();
                            mObserverRestart = Task.onMain(500, () -> {
                                if (observerGeneration == mObserverGeneration && mObservedActivity == activity
                                        && ActivityContext.isAlive(activity) && activity.hasWindowFocus()) {
                                    ActivityViewObserverHolder.start(ActivityViewObserverHolder.Key.WeChatPayView, observer, 100, l);
                                }
                            });
                        }
                    };
                    view.addOnAttachStateChangeListener(listener);
                    mView2OnAttachStateChangeListenerMap.put(view, listener);
                }
            });
        }
    }

    /** 微信设置页。老版走 ListView 头部，8.0.66+ 的 MainSettingsUI 走右上角菜单。 */
    private static final String WECHAT_SETTINGS_UI = "com.tencent.mm.plugin.setting.ui.setting.SettingsUI";
    private static final String WECHAT_MAIN_SETTINGS_UI = "com.tencent.mm.plugin.setting.ui.setting_new.MainSettingsUI";

    private static boolean isWeChatSettingsActivity(String name) {
        if (name == null) {
            return false;
        }
        return name.contains(WECHAT_SETTINGS_UI)
                || name.equals(WECHAT_MAIN_SETTINGS_UI)
                || name.contains("com.tencent.mm.plugin.wallet.pwd.ui.WalletPasswordSettingUI")
                // 8.0.18+ 设置页整体迁到 VAS 容器，需靠页面内容进一步判别
                || name.contains("com.tencent.mm.ui.vas.VASCommonActivity")
                || name.contains("com.tencent.mm.ui.vas.VASCommonFragment");
    }

    private void injectSettingsEntry(Activity activity) {
        try {
            if (!ActivityContext.isAlive(activity)) {
                return;
            }
            final String name = activity.getClass().getName();
            if (name.equals(WECHAT_MAIN_SETTINGS_UI)) {
                injectSettingsOptionMenu(activity);
            } else {
                injectSettingsHeader(activity, name);
            }
        } catch (Exception e) {
            L.e("WeChat settings entry injection failed", e);
        }
    }

    /** 老版设置页：把入口插到设置列表顶部。 */
    private void injectSettingsHeader(Activity activity, String targetClassName) {
        final View decorView = activity.getWindow().getDecorView();
        if (SettingEntry.hasEntry(decorView)) {
            return;
        }
        final boolean vas = targetClassName.contains("com.tencent.mm.ui.vas.VASCommon");
        if (vas && ViewUtils.findViewByText(decorView, "通用", "一般", "General") == null) {
            // VAS 容器承载大量页面，只有设置主页才注入
            return;
        }
        final ListView listView = (ListView) ViewUtils.findViewByName(decorView, "android", "list");
        if (listView == null) {
            return;
        }
        final LinearLayout section = SettingEntry.buildSection(activity, StyleUtils.isDarkMode(activity), 15);
        section.setLayoutParams(new AbsListView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        try {
            listView.addHeaderView(section);
        } catch (IllegalStateException alreadyHasAdapter) {
            // setAdapter 之后再 addHeaderView 会抛此异常，改为插到列表的父容器中（视觉等效）
            insertAbove(listView, section);
        }
    }

    /** 把视图插到目标视图的前面，用于无法 addHeaderView 的场景。 */
    private static void insertAbove(View target, View view) {
        View parent = (View) target.getParent();
        if (!(parent instanceof ViewGroup)) {
            return;
        }
        final ViewGroup group = (ViewGroup) parent;
        int index = group.indexOfChild(target);
        if (index < 0) {
            return;
        }
        group.addView(view, index, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** 8.0.66+ 设置页：通过 addTextOptionMenu 在右上角菜单加一项。 */
    private void injectSettingsOptionMenu(Activity activity) {
        // 菜单项不在 View 树里，hasEntry() 查不到，需单独记已注入的 Activity
        if (mSettingsMenuInjected.containsKey(activity)) {
            return;
        }
        try {
            Method addTextOptionMenu = activity.getClass().getMethod(
                    "addTextOptionMenu", int.class, String.class, MenuItem.OnMenuItemClickListener.class);
            addTextOptionMenu.setAccessible(true);
            addTextOptionMenu.invoke(activity, R.id.app_settings_name,
                    Lang.getString(R.id.app_settings_name),
                    (MenuItem.OnMenuItemClickListener) item -> {
                        SettingEntry.open(activity);
                        return true;
                    });
            mSettingsMenuInjected.put(activity, Boolean.TRUE);
            L.d("WeChat settings option menu injected");
        } catch (Exception e) {
            // 反射失败时退化为：往设置页顶部插一个条目
            L.e("addTextOptionMenu unavailable, falling back to header injection", e);
            injectSettingsHeader(activity, activity.getClass().getName());
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
            ActivityViewObserverHolder.stop(ActivityViewObserverHolder.Key.WeChatPayView);
            ActivityViewObserverHolder.stop(ActivityViewObserverHolder.Key.WeChatPaymentMethodView);
            onPayDialogDismiss(activity, activity.getWindow().getDecorView(), DISMISS_NORMAL);
        } catch (Exception e) {
            L.e(e);
        }
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        boolean inlineOwner = mPaymentMethodWatch != null && mPaymentMethodWatch.activity == activity;
        clearObservation(activity);
        if (mLiteAppActivity == activity) removeKeyboardLayoutListener();
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
        if (mLiteAppActivity == activity) {
            removeKeyboardLayoutListener();
            mLiteAppActivity = null;
        }
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

        // Navigate: key -> row -> keyboardView(passwordLayout) -> container
        ViewGroup passwordLayout = (keyboardKeyView.getParent() == null || !(keyboardKeyView.getParent().getParent() instanceof ViewGroup))
                ? null : (ViewGroup) keyboardKeyView.getParent().getParent();
        ViewGroup keyboardContainer = (passwordLayout == null || passwordLayout.getParent() == null || !(passwordLayout.getParent() instanceof ViewGroup))
                ? null : (ViewGroup) passwordLayout.getParent();

        if (passwordLayout == null || keyboardContainer == null) {
            ArrayList<View> childViews = new ArrayList<>();
            ViewUtils.getChildViews(rootView, childViews);
            L.d("[WeChat keyboardView NOT FOUND]  " + ViewUtils.viewsDesc(childViews));
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

    private void inputDigitalPasswordByTouch(Context context, View keyboard, String password, int versionCode,
                                             long token, Runnable restore) {
        List<View> keys = resolvePasswordKeys(context, keyboard, password,
                WeChatVersionControl.getDigitPasswordKeyPad(versionCode));
        for (int i = 0; i < keys.size(); i++) {
            View key = keys.get(i);
            mInputHandler.postDelayed(() -> {
                if (!isInputCurrent(token, key)) {
                    if (mPaymentSession.owns(token)) restore.run();
                    return;
                }
                try {
                    long now = SystemClock.uptimeMillis();
                    float x = key.getWidth() / 2f;
                    float y = key.getHeight() / 2f;
                    MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
                    MotionEvent up = MotionEvent.obtain(now, now + 25, MotionEvent.ACTION_UP, x, y, 0);
                    try {
                        boolean pressed = key.dispatchTouchEvent(down);
                        boolean released = key.dispatchTouchEvent(up);
                        if (!pressed || !released) restore.run();
                    } finally {
                        down.recycle();
                        up.recycle();
                    }
                } catch (Exception e) {
                    L.e("WeChat keyboard input failed", e);
                    restore.run();
                }
            }, i * 70L);
        }
        mInputHandler.postDelayed(() -> {
            if (mPaymentSession.isActive(token)) restore.run();
        }, keys.size() * 70L + 300);
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
        WeChatPayDialog payDialogView = WeChatPayDialog.findFrom(versionCode, rootView);
        L.d(payDialogView);
        if (payDialogView == null) {
            NotifyUtils.notifyVersionUnSupport(context, Constant.PACKAGE_NAME_WECHAT);
            return;
        }

        startPayment(activity, rootView, config, passwordEncrypted, payDialogView.inputEditText,
                payDialogView.keyboardViews, null);
    }

    private void startPayment(Activity activity, ViewGroup root, Config config, String encrypted,
                              EditText input, List<View> keyboards, View keyboardKey) {
        View target = keyboardKey != null ? keyboardKey : input;
        if (keyboards.isEmpty() || !isPaymentViewVisible(target)
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
            cancelFingerprintIdentify();
            mMockCurrentUser = false;
        });
        mPaymentSession.bindToPaymentWindow(token, activity.getWindow(), config.isVolumeDownMonitorEnabled(), suspend);
        mPaymentSession.protectPortraitKeyboards(token, keyboards);
        stopPaymentMethodWatch();
        mPaymentMethodWatch = new PaymentMethodWatch(activity, root, keyboardKey);
        mPaymentMethodWatch.schedule();
        if (!config.isUnderDisplayFingerprint()) {
            AlipayPayView prompt = new AlipayPayView(activity)
                    .withOnCancelButtonClickListener(shown -> finish.run())
                    .withOnDismissListener(shown -> {
                        if (mPaymentDialog == ((AlipayPayView) shown).getDialog()) finish.run();
                    });
            AlertDialog dialog = prompt.showInDialog();
            if (!mPaymentSession.isActive(token)) { DialogUtils.dismiss(dialog); return; }
            mPaymentDialog = dialog;
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
            stopPaymentMethodWatch();
            dismissPaymentDialog();
            wakePasswordKeyboard(input);
            BlackListUtils.applyIfNeeded(activity);
            if (keyboardKey != null) {
                inputDigitalPasswordByTouch(activity, keyboards.get(0), password, getVersionCode(activity), token, finish);
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
                        WeChatPayDialog form = WeChatPayDialog.findFrom(getVersionCode(activity), (ViewGroup) window);
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
        if (!isInputCurrent(token, input)) {
            restore.run();
            return;
        }
        int version = getVersionCode(context);
        if (version >= Constant.WeChat.WECHAT_VERSION_CODE_8_0_43) {
            View keyboard = null;
            for (View candidate : keyboards) {
                if (isPaymentViewVisible(candidate)) { keyboard = candidate; break; }
            }
            if (keyboard == null) { restore.run(); return; }
            List<View> keys = resolvePasswordKeys(context, keyboard, password,
                    WeChatVersionControl.getDigitPasswordKeyPad(version));
            input.getText().clear();
            input.requestFocus();
            mInputHandler.post(() -> {
                if (!isInputCurrent(token, input)) {
                    if (mPaymentSession.owns(token)) restore.run();
                    return;
                }
                try {
                    for (View key : keys) {
                        if (!isInputCurrent(token, key)) {
                            if (mPaymentSession.owns(token)) restore.run();
                            return;
                        }
                        if (!ViewUtils.performActionClick(key)) { restore.run(); return; }
                    }
                    if (mPaymentSession.isActive(token)) restore.run();
                } catch (Exception e) {
                    L.e("WeChat password input failed", e);
                    restore.run();
                }
            });
        } else {
            if (version >= Constant.WeChat.WECHAT_VERSION_CODE_8_0_18) {
                input.getText().clear();
                for (int i = 0; i < password.length() && mPaymentSession.isActive(token); i++) {
                    input.append(String.valueOf(password.charAt(i)));
                }
            } else {
                input.setText(password);
            }
            if (mPaymentSession.isActive(token)) restore.run();
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
        removeKeyboardLayoutListener();
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

    private void removeKeyboardLayoutListener() {
        if (mKeyboardListenerRoot != null && mKeyboardLayoutListener != null) {
            ViewTreeObserver observer = mKeyboardListenerRoot.getViewTreeObserver();
            if (observer.isAlive()) {
                if (Build.VERSION.SDK_INT >= 16) observer.removeOnGlobalLayoutListener(mKeyboardLayoutListener);
                else observer.removeGlobalOnLayoutListener(mKeyboardLayoutListener);
            }
        }
        mKeyboardLayoutListener = null;
        mKeyboardListenerRoot = null;
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
        View.OnAttachStateChangeListener previous = mView2OnAttachStateChangeListenerMap.remove(target);
        if (previous != null) target.removeOnAttachStateChangeListener(previous);
        View.OnAttachStateChangeListener listener = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) {}
            @Override public void onViewDetachedFromWindow(View view) {
                view.removeOnAttachStateChangeListener(this);
                mView2OnAttachStateChangeListenerMap.remove(view);
                if (!mPaymentSession.owns(token)) return;
                Activity activity = (Activity) mPaymentSession.owner();
                long generation = mObserverGeneration;
                closePayment();
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
        ActivityViewObserverHolder.stop(ActivityViewObserverHolder.Key.WeChatPaymentMethodView);
        Object owner = mPaymentSession.owner();
        restorePassword();
        mInputHandler.removeCallbacksAndMessages(null);
        cancelFingerprintIdentify();
        mPaymentSession.close(owner);
        mRestorePassword = null;
        mPaymentRoot = null;
    }

    private void cancelFingerprintIdentify() {
        XBiometricIdentify identify = mFingerprintIdentify;
        mFingerprintIdentify = null;
        if (identify != null) identify.cancelIdentify();
    }

}
