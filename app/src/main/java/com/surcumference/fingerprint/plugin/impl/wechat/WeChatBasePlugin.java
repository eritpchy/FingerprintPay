package com.surcumference.fingerprint.plugin.impl.wechat;

import static com.surcumference.fingerprint.Constant.PACKAGE_NAME_WECHAT;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hjq.toast.Toaster;
import com.surcumference.fingerprint.BuildConfig;
import com.surcumference.fingerprint.Constant;
import com.surcumference.fingerprint.Lang;
import com.surcumference.fingerprint.R;
import com.surcumference.fingerprint.bean.DigitPasswordKeyPadInfo;
import com.surcumference.fingerprint.plugin.inf.IAppPlugin;
import com.surcumference.fingerprint.plugin.inf.IMockCurrentUser;
import com.surcumference.fingerprint.plugin.inf.OnFingerprintVerificationOKListener;
import com.surcumference.fingerprint.util.ActivityViewObserver;
import com.surcumference.fingerprint.util.ActivityViewObserverHolder;
import com.surcumference.fingerprint.util.ApplicationUtils;
import com.surcumference.fingerprint.util.BizBiometricIdentify;
import com.surcumference.fingerprint.util.BlackListUtils;
import com.surcumference.fingerprint.util.Config;
import com.surcumference.fingerprint.util.DpUtils;
import com.surcumference.fingerprint.util.FragmentObserver;
import com.surcumference.fingerprint.util.ImageUtils;
import com.surcumference.fingerprint.util.NotifyUtils;
import com.surcumference.fingerprint.util.StyleUtils;
import com.surcumference.fingerprint.util.Task;
import com.surcumference.fingerprint.util.ViewUtils;
import com.surcumference.fingerprint.util.WeChatVersionControl;
import com.surcumference.fingerprint.util.XBiometricIdentify;
import com.surcumference.fingerprint.util.drawable.XDrawable;
import com.surcumference.fingerprint.util.log.L;
import com.surcumference.fingerprint.util.paydialog.WeChatPayDialog;
import com.surcumference.fingerprint.view.SettingsView;
import com.wei.android.lib.fingerprintidentify.bean.FingerprintIdentifyFailInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.WeakHashMap;

public class WeChatBasePlugin implements IAppPlugin, IMockCurrentUser {

    protected static final int DISMISS_NORMAL = 2;
    protected static final int DISMISS_WXA_LITE_APP_PAUSE = 3;

    private WeakHashMap<View, View.OnAttachStateChangeListener> mView2OnAttachStateChangeListenerMap = new WeakHashMap<>();
    protected boolean mMockCurrentUser = false;
    protected XBiometricIdentify mFingerprintIdentify;
    private FragmentObserver mFragmentObserver;
    private int mWeChatVersionCode = 0;
    private boolean mFingerprintIdentifyTemporaryBlocking = false;

    // LiteApp payment activity support (WeChat 8.0.65+)
    private ViewTreeObserver.OnGlobalLayoutListener mKeyboardLayoutListener;
    private Activity mLiteAppActivity;
    private boolean mLiteAppFirstDetection;
    private boolean mFingerprintCoverShowing = false;
    private ImageView mFingerprintIconImageView;
    private ViewGroup mKeyboardPasswordLayout;
    private ViewGroup mKeyboardContainer;
    private final HashMap<Integer, Float> mSavedAlphaMap = new HashMap<>();
    private final HashMap<Integer, Boolean> mSavedClickableMap = new HashMap<>();

    @Override
    public int getVersionCode(Context context) {
        if (mWeChatVersionCode != 0) {
            return mWeChatVersionCode;
        }
        mWeChatVersionCode = ApplicationUtils.getPackageVersionCode(context, PACKAGE_NAME_WECHAT);
        return mWeChatVersionCode;
    }

    protected synchronized void initFingerPrintLock(Context context, Config config,
                                                    boolean smallPayDialogFloating, String passwordEncrypted,
                                                    OnFingerprintVerificationOKListener onSuccessUnlockCallback,
                                                    final Runnable onFailureUnlockCallback) {
        cancelFingerprintIdentify();
        mFingerprintIdentify = new BizBiometricIdentify(context)
                .withMockCurrentUserCallback(this)
                .decryptPasscode(passwordEncrypted, new BizBiometricIdentify.IdentifyListener() {

                    @Override
                    public void onDecryptionSuccess(BizBiometricIdentify identify, @NonNull String decryptedContent) {
                        super.onDecryptionSuccess(identify, decryptedContent);
                        onSuccessUnlockCallback.onFingerprintVerificationOK(decryptedContent);
                    }

                    @Override
                    public void onFailed(BizBiometricIdentify target, FingerprintIdentifyFailInfo failInfo) {
                        super.onFailed(target, failInfo);
                        onFailureUnlockCallback.run();
                    }
                });
    }

    protected boolean isHeaderViewExistsFallback(ListView listView) {
        if (listView == null) {
            return false;
        }
        if (listView.getHeaderViewsCount() <= 0) {
            return false;
        }
        try {
            Field mHeaderViewInfosField = ListView.class.getDeclaredField("mHeaderViewInfos");
            mHeaderViewInfosField.setAccessible(true);
            ArrayList<ListView.FixedViewInfo> mHeaderViewInfos = (ArrayList<ListView.FixedViewInfo>) mHeaderViewInfosField.get(listView);
            if (mHeaderViewInfos != null) {
                for (ListView.FixedViewInfo viewInfo : mHeaderViewInfos) {
                    if (viewInfo.view == null) {
                        continue;
                    }
                    Object tag = viewInfo.view.getTag();
                    if (BuildConfig.APPLICATION_ID.equals(tag)) {
                        L.d("found plugin settings headerView");
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            L.e(e);
        }
        return false;
    }

    /**
     * WeChat's LiteApp payment host was renamed to WxaLiteAppPayTransparentLiteUI
     * in newer releases (observed in 8.0.78 / versionCode 3180).
     */
    private boolean isLiteAppPaymentActivity(@NonNull String activityClzName) {
        return activityClzName.contains(".WxaLiteAppTransparentLiteUI")
                || activityClzName.contains(".WxaLiteAppPayTransparentLiteUI");
    }
    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        //Xposed not hooked yet!
    }

    @Override
    public void onActivityResumed(Activity activity) {
        L.d("Activity onResume =", activity);
        final String activityClzName = activity.getClass().getName();
        if (activityClzName.contains("com.tencent.mm.plugin.setting.ui.setting.SettingsUI")
                || activityClzName.contains("com.tencent.mm.plugin.wallet.pwd.ui.WalletPasswordSettingUI")
                || activityClzName.contains("com.tencent.mm.ui.vas.VASCommonActivity") /** 8.0.18 */) {
            Task.onMain(100, () -> doSettingsMenuInject(activity));
        } else if (activityClzName.equals("com.tencent.mm.plugin.setting.ui.setting_new.MainSettingsUI") /* 8.0.66 */) {
            Task.onMain(100, () -> doNewSettingsMenuInject(activity));
        } else if (getVersionCode(activity) >= Constant.WeChat.WECHAT_VERSION_CODE_8_0_20 && activityClzName.contains("com.tencent.mm.ui.LauncherUI")) {
            startFragmentObserver(activity);
        } else if (isLiteAppPaymentActivity(activityClzName)) {
            try {
                mLiteAppActivity = activity;
                mLiteAppFirstDetection = true;
                final ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
                final boolean[] wasVisible = new boolean[]{false};
                final Context listenerContext = decorView.getContext();
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
                            keyboardKey.post(() -> onPayDialogShownByKeyboard(activity, decorView, keyboardKey));
                        } else if (!keyboardVisible && wasVisible[0]) {
                            // Remove both cover layouts
                            removeFingerprintCover(decorView);
                            View kbCover = decorView.findViewWithTag("keyboardCoverLayout");
                            if (kbCover != null) {
                                ViewUtils.removeFromSuperView(kbCover);
                            }
                            restoreKeyboardContainerHeight(mKeyboardContainer);
                            cancelFingerprintIdentify();
                            // Restore child view states before clearing
                            restoreChildViewStates(mKeyboardPasswordLayout, true, mSavedAlphaMap, mSavedClickableMap);
                            mSavedAlphaMap.clear();
                            mSavedClickableMap.clear();
                            if (Config.from(listenerContext).isVolumeDownMonitorEnabled()) {
                                ViewUtils.unregisterVolumeKeyDownEventListener(activity.getWindow());
                            }
                        }
                        wasVisible[0] = keyboardVisible;
                    } catch (Exception e) {
                        L.e(e);
                    }
                };
                decorView.getViewTreeObserver().addOnGlobalLayoutListener(mKeyboardLayoutListener);
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
                            Context context = v.getContext();
                            onPayDialogDismiss(context, rootView);
                            if (Config.from(context).isVolumeDownMonitorEnabled()) {
                                ViewUtils.unregisterVolumeKeyDownEventListener(activity.getWindow());
                            }
                            Task.onMain(500, () -> observer.start(100, l));
                        }
                    };
                    view.addOnAttachStateChangeListener(listener);
                    mView2OnAttachStateChangeListenerMap.put(view, listener);
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
            L.d("Activity onPause =", activity);
            final String activityClzName = activity.getClass().getName();
            if (!activityClzName.contains(".WalletPayUI") && !activityClzName.contains(".UIPageFragmentActivity")) {
                if (getVersionCode(activity) >= Constant.WeChat.WECHAT_VERSION_CODE_8_0_20 && activityClzName.contains("com.tencent.mm.ui.LauncherUI")) {
                    stopFragmentObserver(activity);
                } else if (isLiteAppPaymentActivity(activityClzName)) {
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
        //Xposed not hooked yet!
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
        //Xposed not hooked yet!
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        //Xposed not hooked yet!
    }

    @Override
    public boolean getMockCurrentUser() {
        return this.mMockCurrentUser;
    }

    @Override
    public void setMockCurrentUser(boolean mock) {
        this.mMockCurrentUser = mock;
    }

    private void startFragmentObserver(Activity activity) {
        stopFragmentObserver(activity);
        FragmentObserver fragmentObserver = new FragmentObserver(activity);
        fragmentObserver.setFragmentIdentifyClassName("com.tencent.mm.ui.vas.VASCommonFragment");
        fragmentObserver.start((observer, fragmentObject, fragmentRootView) -> doSettingsMenuInject(fragmentRootView.getContext(), fragmentRootView, fragmentObject.getClass().getName()));
        mFragmentObserver = fragmentObserver;
    }

    private void stopFragmentObserver(Activity activity) {
        FragmentObserver fragmentObserver = mFragmentObserver;
        if (fragmentObserver != null) {
            fragmentObserver.stop();
            mFragmentObserver = null;
        }
    }

    /**
     * Matches module m1797: handle pay dialog via keyboard key detection.
     */
    protected void onPayDialogShownByKeyboard(Activity activity, ViewGroup rootView, View keyboardKeyView) {
        Context context = rootView.getContext();
        Config config = Config.from(context);
        if (!config.isOn()) {
            return;
        }
        int versionCode = getVersionCode(context);
        String passwordEncrypted = config.getPasswordEncrypted();
        if (TextUtils.isEmpty(passwordEncrypted) || TextUtils.isEmpty(config.getPasswordIV())) {
            NotifyUtils.notifyBiometricIdentify(context, Lang.getString(R.id.toast_password_not_set_wechat));
            return;
        }

        // Navigate: key -> row -> keyboardView(passwordLayout) -> container
        ViewGroup passwordLayout = (keyboardKeyView.getParent() == null || !(keyboardKeyView.getParent().getParent() instanceof ViewGroup))
                ? null : (ViewGroup) keyboardKeyView.getParent().getParent();
        ViewGroup keyboardContainer = (passwordLayout == null || passwordLayout.getParent() == null || !(passwordLayout.getParent() instanceof ViewGroup))
                ? null : (ViewGroup) passwordLayout.getParent();

        mKeyboardPasswordLayout = passwordLayout;
        mKeyboardContainer = keyboardContainer;

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

        // Remove old keyboard cover
        View oldKbCover = rootView.findViewWithTag("keyboardCoverLayout");
        if (oldKbCover != null) {
            ViewUtils.removeFromSuperView(oldKbCover);
        }

        // Create fingerprint cover layout (full screen overlay)
        final FrameLayout fingerPrintCoverLayout = new FrameLayout(context);
        fingerPrintCoverLayout.setTag("fingerPrintCoverLayout");
        fingerPrintCoverLayout.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Create fingerprint icon
        mFingerprintIconImageView = new ImageView(context);
        try {
            final Bitmap bitmap = ImageUtils.base64ToBitmap(Constant.ICON_FINGER_PRINT_WECHAT_BASE64);
            mFingerprintIconImageView.setImageBitmap(bitmap);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                mFingerprintIconImageView.getViewTreeObserver().addOnWindowAttachListener(new ViewTreeObserver.OnWindowAttachListener() {
                    @Override public void onWindowAttached() {}
                    @Override
                    public void onWindowDetached() {
                        mFingerprintIconImageView.getViewTreeObserver().removeOnWindowAttachListener(this);
                        try { bitmap.recycle(); } catch (Exception e) {}
                    }
                });
            }
        } catch (OutOfMemoryError e) {
            L.d(e);
        }
        mFingerprintIconImageView.setVisibility(config.isShowFingerprintIcon() ? View.VISIBLE : View.GONE);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                DpUtils.dip2px(context, 70), DpUtils.dip2px(context, 70));
        iconParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
        iconParams.bottomMargin = DpUtils.dip2px(context, 170);
        fingerPrintCoverLayout.addView(mFingerprintIconImageView, iconParams);

        // Create keyboard cover layout
        final FrameLayout keyboardCoverLayout = createKeyboardCoverLayout(context, passwordLayout);

        // switchToPassword runnable - matches module RunnableC0084 case 3
        final ViewGroup finalPasswordLayout = passwordLayout;
        final ViewGroup finalKeyboardContainer = keyboardContainer;
        final Runnable switchToPasswordRunnable = () -> {
            removeFingerprintCover(rootView);
            View kbCover = rootView.findViewWithTag("keyboardCoverLayout");
            if (kbCover != null) {
                ViewUtils.removeFromSuperView(kbCover);
            }
            restoreKeyboardContainerHeight(finalKeyboardContainer);
            restoreChildViewStates(finalPasswordLayout, true, mSavedAlphaMap, mSavedClickableMap);
            cancelFingerprintIdentify();
            mMockCurrentUser = false;
        };

        // The run() block - matches module's inline Runnable that's called via runnable.run()
        if (mFingerprintIdentifyTemporaryBlocking) {
            return;
        }

        // Remove old fingerprint cover
        View oldFpCover = rootView.findViewWithTag("fingerPrintCoverLayout");
        if (oldFpCover != null) {
            rootView.removeView(oldFpCover);
        }
        rootView.addView(keyboardCoverLayout);
        rootView.addView(fingerPrintCoverLayout);
        mFingerprintCoverShowing = true;

        // Save and hide all children
        saveChildViewStates(finalPasswordLayout, mSavedAlphaMap, mSavedClickableMap);

        // Expand container height
        if (finalKeyboardContainer != null) {
            try {
                int origHeight = finalKeyboardContainer.getHeight();
                finalKeyboardContainer.setTag(R.id.app_settings_name, Integer.valueOf(origHeight));
                int expandedHeight = origHeight + DpUtils.dip2px(context, 76);
                ViewGroup.LayoutParams lp = finalKeyboardContainer.getLayoutParams();
                lp.height = expandedHeight;
                finalKeyboardContainer.setLayoutParams(lp);
                finalKeyboardContainer.requestLayout();
            } catch (Exception e) {
                L.e(e);
            }
        }

        // Request layout after delay
        Task.onMain(500, rootView::requestLayout);

        // Start fingerprint - matches module m1795 + inline callback
        initFingerPrintLock(context, config, false, passwordEncrypted, (password) -> {
            BlackListUtils.applyIfNeeded(context);
            // Restore clickable only (not alpha) so touch events work
            restoreChildViewStates(finalPasswordLayout, false, mSavedAlphaMap, mSavedClickableMap);
            try {
                inputDigitalPasswordByTouch(context, finalPasswordLayout, password, versionCode);
            } catch (NullPointerException e) {
                Toaster.showLong(Lang.getString(R.id.toast_password_auto_enter_fail));
                L.e("inputDigitPassword NPE", e);
            } catch (Exception e) {
                Toaster.showLong(Lang.getString(R.id.toast_password_auto_enter_fail));
                L.e(e);
            }
            rootView.postDelayed(switchToPasswordRunnable, 1000);
        }, switchToPasswordRunnable);

        // Icon click -> switch to password
        mFingerprintIconImageView.setOnClickListener(view -> switchToPasswordRunnable.run());

        // Volume key monitoring
        if (config.isVolumeDownMonitorEnabled()) {
            ViewUtils.registerVolumeKeyDownEventListener(activity.getWindow(), event -> {
                if (mFingerprintIdentifyTemporaryBlocking) {
                    return false;
                }
                switchToPasswordRunnable.run();
                Toaster.showLong(Lang.getString(R.id.toast_fingerprint_temporary_disabled));
                mFingerprintIdentifyTemporaryBlocking = true;
                Task.onBackground(60000, () -> mFingerprintIdentifyTemporaryBlocking = false);
                return false;
            });
        }
    }

    /**
     * Matches module m1788: create keyboard cover layout.
     */
    private static FrameLayout createKeyboardCoverLayout(Context context, ViewGroup keyboardView) {
        int coverHeight = DpUtils.dip2px(context, 76) + keyboardView.getHeight();
        FrameLayout coverLayout = new FrameLayout(context);
        coverLayout.setTag("keyboardCoverLayout");
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, coverHeight);
        lp.gravity = Gravity.BOTTOM;
        coverLayout.setLayoutParams(lp);
        boolean isDarkMode = StyleUtils.isDarkMode(context);
        int bgColor = isDarkMode ? 0xFF191919 : Color.WHITE;
        Drawable bg = keyboardView.getBackground();
        if (bg instanceof ColorDrawable) {
            bgColor = ((ColorDrawable) bg).getColor();
        }
        coverLayout.setBackgroundColor(bgColor);
        return coverLayout;
    }

    /**
     * Matches module m1798: remove fingerprint cover.
     */
    private void removeFingerprintCover(View rootView) {
        if (rootView == null) {
            return;
        }
        mFingerprintCoverShowing = false;
        View fpCover = rootView.findViewWithTag("fingerPrintCoverLayout");
        if (fpCover != null) {
            ViewUtils.removeFromSuperView(fpCover);
        }
    }

    /**
     * Matches module m1790: restore keyboard container height.
     */
    private static void restoreKeyboardContainerHeight(ViewGroup keyboardContainer) {
        if (keyboardContainer == null) {
            return;
        }
        try {
            Object tag = keyboardContainer.getTag(R.id.app_settings_name);
            if (tag instanceof Integer) {
                ViewGroup.LayoutParams lp = keyboardContainer.getLayoutParams();
                lp.height = ((Integer) tag).intValue();
                keyboardContainer.setLayoutParams(lp);
                keyboardContainer.requestLayout();
            }
        } catch (Exception e) {
            L.e(e);
        }
    }

    /**
     * Matches module m1792: save child view states and hide.
     */
    private static void saveChildViewStates(ViewGroup viewGroup, HashMap<Integer, Float> alphaMap, HashMap<Integer, Boolean> clickableMap) {
        if (viewGroup == null) {
            return;
        }
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            if (child != null) {
                int key = System.identityHashCode(child);
                if (!alphaMap.containsKey(key)) {
                    alphaMap.put(key, child.getAlpha());
                }
                if (!clickableMap.containsKey(key)) {
                    clickableMap.put(key, child.isClickable());
                }
                child.setAlpha(0.0f);
                child.setClickable(false);
                if (child instanceof ViewGroup) {
                    saveChildViewStates((ViewGroup) child, alphaMap, clickableMap);
                }
            }
        }
    }

    /**
     * Matches module m1791: restore child view states.
     */
    private static void restoreChildViewStates(ViewGroup viewGroup, boolean restoreAlpha, HashMap<Integer, Float> alphaMap, HashMap<Integer, Boolean> clickableMap) {
        if (viewGroup == null) {
            return;
        }
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            if (child != null) {
                int key = System.identityHashCode(child);
                if (clickableMap.containsKey(key)) {
                    child.setClickable(clickableMap.get(key));
                    clickableMap.remove(key);
                }
                if (restoreAlpha && alphaMap.containsKey(key)) {
                    child.setAlpha(alphaMap.get(key));
                    alphaMap.remove(key);
                }
                if (child instanceof ViewGroup) {
                    restoreChildViewStates((ViewGroup) child, restoreAlpha, alphaMap, clickableMap);
                }
            }
        }
    }

    /**
     * Matches module m2056: input password by simulating touch events.
     */
    private void inputDigitalPasswordByTouch(Context context, View keyboardParent, String pwd, int versionCode) {
        DigitPasswordKeyPadInfo digitPasswordKeyPad = WeChatVersionControl.getDigitPasswordKeyPad(versionCode);
        if (keyboardParent == null || keyboardParent.getContext() == null) {
            throw new NullPointerException("rootView is null");
        }
        if (digitPasswordKeyPad == null) {
            throw new NullPointerException("keyPadInfo is null");
        }
        if (pwd == null) {
            throw new NullPointerException("password is null");
        }
        if (pwd.isEmpty()) {
            throw new IllegalArgumentException("password is empty");
        }

        Handler handler = new Handler(Looper.getMainLooper());
        Random random = new Random();
        int totalDelay = 0;
        for (int i = 0; i < pwd.length(); i++) {
            final char c = pwd.charAt(i);
            if (i > 0) {
                int delay = (int) (random.nextGaussian() * 3.33d + 70);
                if (delay < 60) delay = 60;
                if (delay > 80) delay = 80;
                totalDelay += delay;
            }
            final View finalKeyboardParent = keyboardParent;
            final String packageName = context.getPackageName();
            handler.postDelayed(() -> {
                String[] keyIds = digitPasswordKeyPad.keys.get(String.valueOf(c));
                if (keyIds == null) {
                    throw new IllegalArgumentException("Password contains invalid character: " + c);
                }
                View digitView = ViewUtils.findViewByName(finalKeyboardParent, packageName, keyIds);
                if (digitView == null) {
                    throw new NullPointerException("Cannot find digit view");
                }
                if (digitView.getContext() == null) {
                    return;
                }
                int w = Math.max(digitView.getWidth(), 0);
                int h = Math.max(digitView.getHeight(), 0);
                Random r = new Random(SystemClock.uptimeMillis());
                float x = w > 0 ? r.nextInt(w) : 0;
                float y = h > 0 ? r.nextInt(h) : 0;
                ArrayList<MotionEvent> events = new ArrayList<>();
                long downTime = SystemClock.uptimeMillis();
                events.add(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0));
                events.add(MotionEvent.obtain(downTime, downTime + 25, MotionEvent.ACTION_UP, x, y, 0));
                if (digitView.getContext() == null || events.isEmpty()) {
                    return;
                }
                for (int j = 0; j < events.size(); j++) {
                    MotionEvent event = events.get(j);
                    try {
                        digitView.dispatchTouchEvent(event);
                    } finally {
                        event.recycle();
                    }
                }
            }, totalDelay);
        }
    }

    protected void onPayDialogShown(Activity activity, ViewGroup rootView) {
        L.d("PayDialog show");
        Context context = rootView.getContext();
        Config config = Config.from(context);
        if (!config.isOn()) {
            return;
        }
        if (mFingerprintIdentifyTemporaryBlocking) {
            return;
        }
        String passwordEncrypted = config.getPasswordEncrypted();
        if (TextUtils.isEmpty(passwordEncrypted) || TextUtils.isEmpty(config.getPasswordIV())) {
            NotifyUtils.notifyBiometricIdentify(context, Lang.getString(R.id.toast_password_not_set_wechat));
            return;
        }

        int versionCode = getVersionCode(context);
        WeChatPayDialog payDialogView = WeChatPayDialog.findFrom(versionCode, rootView);
        L.d(payDialogView);
        if (payDialogView == null) {
            NotifyUtils.notifyVersionUnSupport(context, Constant.PACKAGE_NAME_WECHAT);
            return;
        }

        ViewGroup passwordLayout = payDialogView.passwordLayout;
        EditText mInputEditText = payDialogView.inputEditText;
        List<View> keyboardViews = payDialogView.keyboardViews;
        TextView usePasswordText = payDialogView.usePasswordText;
        TextView titleTextView = payDialogView.titleTextView;

        boolean smallPayDialogFloating = isSmallPayDialogFloating(passwordLayout);
        RelativeLayout fingerPrintLayout = new RelativeLayout(context);
        fingerPrintLayout.setTag("fingerPrintLayout");
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        fingerPrintLayout.setLayoutParams(layoutParams);

        fingerPrintLayout.setClipChildren(false);
        ImageView fingerprintImageView = new ImageView(context);
        try {
            final Bitmap bitmap = ImageUtils.base64ToBitmap(Constant.ICON_FINGER_PRINT_WECHAT_BASE64);
            fingerprintImageView.setImageBitmap(bitmap);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                fingerprintImageView.getViewTreeObserver().addOnWindowAttachListener(new ViewTreeObserver.OnWindowAttachListener() {
                    @Override
                    public void onWindowAttached() {

                    }

                    @Override
                    public void onWindowDetached() {
                        fingerprintImageView.getViewTreeObserver().removeOnWindowAttachListener(this);
                        try {
                            bitmap.recycle();
                        } catch (Exception e) {
                        }
                    }
                });
            }
        } catch (OutOfMemoryError e) {
            L.d(e);
        }
        RelativeLayout.LayoutParams fingerprintImageViewLayoutParams = new RelativeLayout.LayoutParams(DpUtils.dip2px(context, 70), DpUtils.dip2px(context, 70));
        fingerprintImageViewLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        fingerprintImageViewLayoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
        if (smallPayDialogFloating) {
            fingerprintImageViewLayoutParams.topMargin = DpUtils.dip2px(context, -14);
            fingerPrintLayout.addView(fingerprintImageView, fingerprintImageViewLayoutParams);
            fingerprintImageView.setVisibility(View.VISIBLE);
        } else {
            fingerprintImageViewLayoutParams.bottomMargin = DpUtils.dip2px(context, 180);
            fingerPrintLayout.addView(fingerprintImageView, fingerprintImageViewLayoutParams);
            fingerprintImageView.setVisibility(config.isShowFingerprintIcon() ? View.VISIBLE : View.GONE);
        }

        final Runnable switchToPasswordRunnable = ()-> {
            if (smallPayDialogFloating) {
                passwordLayout.removeView(fingerPrintLayout);
            } else {
                rootView.removeView(fingerPrintLayout);
            }
            mInputEditText.setVisibility(View.VISIBLE);
            keyboardViews.get(keyboardViews.size() - 1).setVisibility(View.VISIBLE);
            mInputEditText.requestFocus();
            mInputEditText.performClick();
            cancelFingerprintIdentify();
            mMockCurrentUser = false;
            if (titleTextView != null) {
                titleTextView.setText(Lang.getString(R.id.wechat_payview_password_title));
            }
            if (usePasswordText != null) {
                usePasswordText.setText(Lang.getString(R.id.wechat_payview_fingerprint_switch_text));
            }
        };

        final Runnable switchToFingerprintRunnable = ()-> {
            mInputEditText.setVisibility(View.GONE);
            for (View keyboardView : keyboardViews) {
                keyboardView.setVisibility(View.GONE);
            }
            if (smallPayDialogFloating) {
                View fingerPrintLayoutLast = passwordLayout.findViewWithTag("fingerPrintLayout");
                if (fingerPrintLayoutLast != null) {
                    passwordLayout.removeView(fingerPrintLayoutLast);
                }
                // 禁止修改, 会导致layoutListener 再次调用 switchToFingerprintRunnable
                // onPayDialogShown 调用 initFingerPrintLock
                // switchToFingerprintRunnable 调用 initFingerPrintLock 导致 onFailed 调用 switchToPasswordRunnable
                // switchToPasswordRunnable 调用 cancelFingerprintIdentify cancel 掉当前, 最终导致全部指纹识别取消
                // fingerPrintLayout.setVisibility(View.GONE);
                passwordLayout.addView(fingerPrintLayout);
                // ensure image icon visibility
                Task.onMain(1000, fingerPrintLayout::requestLayout);
                passwordLayout.setClipChildren(false);
                ((ViewGroup) passwordLayout.getParent()).setClipChildren(false);
                ((ViewGroup) passwordLayout.getParent().getParent()).setTop(((ViewGroup) passwordLayout.getParent().getParent()).getTop() + 200);
                ((ViewGroup) passwordLayout.getParent().getParent()).setClipChildren(false);
                ((ViewGroup) passwordLayout.getParent().getParent()).setBackgroundColor(Color.TRANSPARENT);
                ((ViewGroup) passwordLayout.getParent()).setBackgroundColor(Color.TRANSPARENT);
            } else {
                View fingerPrintLayoutLast = rootView.findViewWithTag("fingerPrintLayout");
                if (fingerPrintLayoutLast != null) {
                    rootView.removeView(fingerPrintLayoutLast);
                }
                rootView.addView(fingerPrintLayout, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }
            initFingerPrintLock(context, config, smallPayDialogFloating, passwordEncrypted, (password)-> {
                BlackListUtils.applyIfNeeded(context);
                inputDigitalPassword(context, mInputEditText, password, keyboardViews, smallPayDialogFloating);
            }, switchToPasswordRunnable);
            if (titleTextView != null) {
                titleTextView.setText(Lang.getString(R.id.wechat_payview_fingerprint_title));
            }
            if (usePasswordText != null) {
                usePasswordText.setText(Lang.getString(R.id.wechat_payview_password_switch_text));
            }
        };

        if (usePasswordText != null) {
            Task.onMain(()-> usePasswordText.setVisibility(View.VISIBLE));
            usePasswordText.setOnTouchListener((view, motionEvent) -> {
                try {
                    if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                        if (mInputEditText.getVisibility() == View.GONE) {
                            switchToPasswordRunnable.run();
                        } else {
                            switchToFingerprintRunnable.run();
                        }
                    }
                } catch (Exception e) {
                    L.e(e);
                }
                return true;
            });
        }
        if (titleTextView != null) {
            titleTextView.setOnTouchListener((view, motionEvent) -> {
                try {
                    if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                        if (mInputEditText.getVisibility() == View.GONE) {
                            switchToPasswordRunnable.run();
                        } else {
                            switchToFingerprintRunnable.run();
                        }
                    }
                } catch (Exception e) {
                    L.e(e);
                }
                return true;
            });
        }

        fingerprintImageView.setOnClickListener(view -> switchToPasswordRunnable.run());
        switchToFingerprintRunnable.run();
        if (config.isVolumeDownMonitorEnabled()) {
            ViewUtils.registerVolumeKeyDownEventListener(activity.getWindow(), event -> {
                if (mFingerprintIdentifyTemporaryBlocking) {
                    return false;
                }
                switchToPasswordRunnable.run();
                Toaster.showLong(Lang.getString(R.id.toast_fingerprint_temporary_disabled));
                mFingerprintIdentifyTemporaryBlocking = true;
                Task.onBackground(60000, () -> mFingerprintIdentifyTemporaryBlocking = false);
                return false;
            });
        }
        watchForSwitchPaymentMethod(activity, rootView, switchToPasswordRunnable, switchToFingerprintRunnable);
    }

    private void watchForSwitchPaymentMethod(Activity activity, ViewGroup rootView, Runnable switchToPasswordRunnable, Runnable switchToFingerprintRunnable) {
        L.d("watchForSwitchPaymentMethod", activity, ViewUtils.getViewInfo(rootView));
        ActivityViewObserver activityViewObserver = new ActivityViewObserver(activity);
        activityViewObserver.setViewIdentifyText("选择付款方式", "選擇付款方式", "Select payment method", "请选择优惠", "請選擇優惠", "Select discount");
        ActivityViewObserverHolder.start(ActivityViewObserverHolder.Key.WeChatPaymentMethodView, activityViewObserver, 333, (ActivityViewObserver observer, View view) -> {
            L.d("选择付款方式 founded", ViewUtils.getViewInfo(view));
            switchToPasswordRunnable.run();
            observer.stop();
            view.addOnAttachStateChangeListener(
                    new View.OnAttachStateChangeListener() {
                        @Override
                        public void onViewAttachedToWindow(@NonNull View view) {
                            L.d("onViewAttachedToWindow", ViewUtils.getViewInfo(view));
                        }

                        @Override
                        public void onViewDetachedFromWindow(@NonNull View view) {
                            L.d("onViewDetachedFromWindow", ViewUtils.getViewInfo(view));
                            view.removeOnAttachStateChangeListener(this);
                            switchToFingerprintRunnable.run();
                            rootView.post(() -> watchForSwitchPaymentMethod(activity, rootView, switchToPasswordRunnable, switchToFingerprintRunnable));
                        }
                    }
            );
        });
    }

    private void inputDigitalPassword(Context context, EditText inputEditText, String pwd,
                                      List<View> keyboardViews, boolean smallPayDialogFloating) {
        int versionCode = getVersionCode(context);
        if (versionCode >= Constant.WeChat.WECHAT_VERSION_CODE_8_0_43) {
            DigitPasswordKeyPadInfo digitPasswordKeyPad = WeChatVersionControl.getDigitPasswordKeyPad(versionCode);
            inputEditText.getText().clear();
            View keyboardView = keyboardViews.get(0); //测了很多遍就是第一个
            // 在半高支付界面需要先激活inputEditText才能正常输入
            if (!smallPayDialogFloating) {
                ((ViewGroup)inputEditText.getParent().getParent()).setAlpha(0.01f);
                inputEditText.setVisibility(View.VISIBLE);
            }
            ViewGroup.LayoutParams keyboardViewParams = keyboardView.getLayoutParams();
            int keyboardViewHeight = keyboardViewParams.height;
            keyboardViewParams.height = 2;
            inputEditText.requestFocus();
            inputEditText.post(() -> {
                for (char c : pwd.toCharArray()) {
                    String[] keyIds = digitPasswordKeyPad.keys.get(String.valueOf(c));
                    if (keyIds == null) {
                        continue;
                    }
                    View digitView = ViewUtils.findViewByName(keyboardView, context.getPackageName(), keyIds);
                    if (digitView != null) {
                        ViewUtils.performActionClick(digitView);
                    }
                }
                // inputEditText.setVisibility(View.VISIBLE); 副作用反制
                keyboardView.post(() -> inputEditText.setVisibility(View.GONE));
                keyboardView.postDelayed(() -> {
                    ((ViewGroup)inputEditText.getParent().getParent()).setAlpha(1f);
                    keyboardViewParams.height = keyboardViewHeight;
                }, 1000);
            });
            return;
        }
        if (getVersionCode(context) >= Constant.WeChat.WECHAT_VERSION_CODE_8_0_18) {
            inputEditText.getText().clear();
            for (char c : pwd.toCharArray()) {
                inputEditText.append(String.valueOf(c));
            }
            return;
        }
        inputEditText.setText(pwd);
    }

    private boolean isSmallPayDialogFloating(ViewGroup passwordLayout) {
        ViewGroup floatRootView = ((ViewGroup) passwordLayout.getParent().getParent().getParent().getParent().getParent());
        int []location = new int[]{0,0};
        floatRootView.getLocationOnScreen(location);
        L.d("floatRootView", ViewUtils.getViewInfo(floatRootView));
        return location[0] > 0 || floatRootView.getChildCount() > 1;
    }

    /**
     * Matches module m1796.
     * @param param {@link #DISMISS_NORMAL} for normal dismiss, {@link #DISMISS_WXA_LITE_APP_PAUSE} for WxaLiteApp pause.
     */
    protected void onPayDialogDismiss(Context context, View rootView, int param) {
        L.d("PayDialog dismiss");
        if (!Config.from(context).isOn()) {
            return;
        }
        ViewGroup viewGroup = (ViewGroup) rootView;
        cancelFingerprintIdentify();
        if (rootView != null) {
            ViewTreeObserver vto = rootView.getViewTreeObserver();
            ViewTreeObserver.OnGlobalLayoutListener listener = mKeyboardLayoutListener;
            if (listener != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    vto.removeOnGlobalLayoutListener(listener);
                } else {
                    vto.removeGlobalOnLayoutListener(listener);
                }
            }
            if (param == DISMISS_WXA_LITE_APP_PAUSE) {
                restoreChildViewStates(mKeyboardPasswordLayout, true, mSavedAlphaMap, mSavedClickableMap);
                mSavedAlphaMap.clear();
                mSavedClickableMap.clear();
                if (Config.from(context).isVolumeDownMonitorEnabled() && (context instanceof Activity)) {
                    ViewUtils.unregisterVolumeKeyDownEventListener(((Activity) context).getWindow());
                }
            }
        }
        View fingerPrintLayoutLast = rootView.findViewWithTag("fingerPrintLayout");
        if (fingerPrintLayoutLast != null) {
            ViewUtils.removeFromSuperView(fingerPrintLayoutLast);
        }
        if (mFingerprintCoverShowing) {
            removeFingerprintCover(viewGroup);
            restoreKeyboardContainerHeight(mKeyboardContainer);
            if (mKeyboardPasswordLayout != null) {
                viewGroup.addView(createKeyboardCoverLayout(context, mKeyboardPasswordLayout));
            }
        }
        mMockCurrentUser = false;
    }

    /**
     * Backward compatible wrapper for existing callers.
     */
    protected void onPayDialogDismiss(Context context, View rootView) {
        onPayDialogDismiss(context, rootView, DISMISS_NORMAL);
    }

    private void cancelFingerprintIdentify() {
        XBiometricIdentify fingerprintIdentify = mFingerprintIdentify;
        if (fingerprintIdentify == null) {
            return;
        }
        if (!fingerprintIdentify.fingerprintScanStateReady) {
            return;
        }
        fingerprintIdentify.cancelIdentify();
        mFingerprintIdentify = null;
    }

    protected void doSettingsMenuInject(final Activity activity) {
        doSettingsMenuInject(activity, activity.getWindow().getDecorView(), activity.getClass().getName());
    }

    protected void doSettingsMenuInject(Context context, View targetView, String targetClassName) {
        int versionCode = getVersionCode(context);
        ListView itemView = (ListView) ViewUtils.findViewByName(targetView, "android", "list");
        if (ViewUtils.findViewByText(itemView, Lang.getString(R.id.app_settings_name)) != null
                || isHeaderViewExistsFallback(itemView)) {
            return;
        }
        if (versionCode >= Constant.WeChat.WECHAT_VERSION_CODE_8_0_18) {
            //整个设置界面的class 都是 com.tencent.mm.ui.vas.VASCommonActivity...
            if (targetClassName.contains("com.tencent.mm.ui.vas.VASCommonActivity")
                || targetClassName.contains("com.tencent.mm.ui.vas.VASCommonFragment") /** 8.0.20 */) {
                if (ViewUtils.findViewByText(itemView, Lang.getString(R.id.wechat_general),
                        "通用", "一般", "General") == null) {
                    return;
                }
            }
        }

        boolean isDarkMode = StyleUtils.isDarkMode(context);

        LinearLayout settingsItemRootLLayout = new LinearLayout(context);
        settingsItemRootLLayout.setOrientation(LinearLayout.VERTICAL);
        settingsItemRootLLayout.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (versionCode >= Constant.WeChat.WECHAT_VERSION_CODE_8_0_20) {
            // 减少页面跳动
            settingsItemRootLLayout.setPadding(0, 0, 0, 0);
        } else {
            settingsItemRootLLayout.setPadding(0, DpUtils.dip2px(context, 20), 0, 0);
        }

        LinearLayout settingsItemLinearLayout = new LinearLayout(context);
        settingsItemLinearLayout.setOrientation(LinearLayout.VERTICAL);

        settingsItemLinearLayout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));


        LinearLayout itemHlinearLayout = new LinearLayout(context);
        itemHlinearLayout.setOrientation(LinearLayout.HORIZONTAL);
        itemHlinearLayout.setWeightSum(1);

        itemHlinearLayout.setBackground(new XDrawable.Builder()
                .defaultColor(isDarkMode ? 0xFF191919 : Color.WHITE)
                .pressedColor(isDarkMode ? 0xFF1D1D1D : 0xFFE5E5E5)
                .create());
        itemHlinearLayout.setGravity(Gravity.CENTER_VERTICAL);
        itemHlinearLayout.setClickable(true);
        itemHlinearLayout.setOnClickListener(view -> new SettingsView(context).showInDialog());

        int defHPadding = DpUtils.dip2px(context, 15);

        TextView itemNameText = new TextView(context);
        itemNameText.setTextColor(isDarkMode ? 0xFFD3D3D3 : 0xFF353535);
        itemNameText.setText(Lang.getString(R.id.app_settings_name));
        itemNameText.setGravity(Gravity.CENTER_VERTICAL);
        itemNameText.setPadding(DpUtils.dip2px(context, 16), 0, 0, 0);
        itemNameText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, StyleUtils.TEXT_SIZE_BIG);

        TextView itemSummerText = new TextView(context);
        StyleUtils.apply(itemSummerText);
        itemSummerText.setText(BuildConfig.VERSION_NAME);
        itemSummerText.setGravity(Gravity.CENTER_VERTICAL);
        itemSummerText.setPadding(0, 0, defHPadding, 0);
        itemSummerText.setTextColor(isDarkMode ? 0xFF656565 : 0xFF999999);

        //try use WeChat style
        try {
            View generalView = ViewUtils.findViewByText(itemView, "微信密码", "微信密碼", "Password", "账号安全", "賬號安全", "Account Security", "通用", "一般", "General", "服务管理", "服務管理", "Manage Services");
            L.d("generalView", generalView);
            if (generalView instanceof TextView) {
                TextView generalTextView = (TextView) generalView;
                float scale = itemNameText.getTextSize() / generalTextView.getTextSize();
                itemNameText.setTextSize(TypedValue.COMPLEX_UNIT_PX, generalTextView.getTextSize());

                itemSummerText.setTextSize(TypedValue.COMPLEX_UNIT_PX, itemSummerText.getTextSize() / scale);
                View generalItemView;
                if (versionCode >= Constant.WeChat.WECHAT_VERSION_CODE_8_0_60) {
                    generalItemView = (View) generalView.getParent().getParent().getParent().getParent().getParent().getParent().getParent();
                } else {
                    generalItemView = (View) generalView.getParent().getParent().getParent().getParent().getParent();
                }
                if (generalItemView != null) {
                    Drawable background = generalItemView.getBackground();
                    if (background != null) {
                        Drawable.ConstantState constantState = background.getConstantState();
                        if (constantState != null) {
                            itemHlinearLayout.setBackground(constantState.newDrawable());
                        }
                    }
                }
                itemNameText.setTextColor(generalTextView.getCurrentTextColor());
            }
        } catch (Exception e) {
            L.e(e);
        }

        itemHlinearLayout.addView(itemNameText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        itemHlinearLayout.addView(itemSummerText, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        View lineView = new View(context);
        lineView.setBackgroundColor(isDarkMode ? 0xFF2E2E2E : 0xFFD5D5D5);
        settingsItemLinearLayout.addView(lineView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        settingsItemLinearLayout.addView(itemHlinearLayout, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, DpUtils.dip2px(context, 55)));

        settingsItemRootLLayout.addView(settingsItemLinearLayout);
        settingsItemRootLLayout.setTag(BuildConfig.APPLICATION_ID);

        itemView.addHeaderView(settingsItemRootLLayout);
    }

    protected void doNewSettingsMenuInject(final Activity activity) {
        try {
            Method mAddMenuMethod = activity.getClass().getMethod("addTextOptionMenu", int.class, String.class, MenuItem.OnMenuItemClickListener.class);
            mAddMenuMethod.setAccessible(true);
            mAddMenuMethod.invoke(activity, R.id.app_settings_name, Lang.getString(R.id.app_settings_name), (MenuItem.OnMenuItemClickListener) item -> {
                new SettingsView(activity).showInDialog();
                return true;
            });
        } catch (Exception e) {
            L.e(e);
        }
    }
}
