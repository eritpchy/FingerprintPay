package com.surcumference.fingerprint.plugin.impl.qq;

import static com.surcumference.fingerprint.Constant.ICON_QQ_SETTING_ENTRY_DARK_BASE64;
import static com.surcumference.fingerprint.Constant.ICON_QQ_SETTING_ENTRY_LIGHT_BASE64;
import static com.surcumference.fingerprint.Constant.PACKAGE_NAME_QQ;

import android.app.Activity;
import com.surcumference.fingerprint.util.PaymentPasswordGuard;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hjq.toast.Toaster;
import com.surcumference.fingerprint.BuildConfig;
import com.surcumference.fingerprint.Constant;
import com.surcumference.fingerprint.Lang;
import com.surcumference.fingerprint.R;
import com.surcumference.fingerprint.plugin.inf.IAppPlugin;
import com.surcumference.fingerprint.plugin.inf.IMockCurrentUser;
import com.surcumference.fingerprint.plugin.inf.OnFingerprintVerificationOKListener;
import com.surcumference.fingerprint.plugin.impl.SettingEntry;
import com.surcumference.fingerprint.util.ApplicationUtils;
import com.surcumference.fingerprint.util.BizBiometricIdentify;
import com.surcumference.fingerprint.util.Config;
import com.surcumference.fingerprint.util.DpUtils;
import com.surcumference.fingerprint.util.ImageUtils;
import com.surcumference.fingerprint.util.KeyboardUtils;
import com.surcumference.fingerprint.util.QQUtils;
import com.surcumference.fingerprint.util.Task;
import com.surcumference.fingerprint.util.PaymentController;
import com.surcumference.fingerprint.util.ActivityContext;
import android.os.SystemClock;
import com.surcumference.fingerprint.util.ViewUtils;
import com.surcumference.fingerprint.util.XBiometricIdentify;
import com.surcumference.fingerprint.util.log.L;
import com.surcumference.fingerprint.util.paydialog.QQPayDialog;
import com.wei.android.lib.fingerprintidentify.bean.FingerprintIdentifyFailInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

public class QQBasePlugin implements IAppPlugin, IMockCurrentUser {


    private static final String TAG_FINGER_PRINT_IMAGE = "FINGER_PRINT_IMAGE";
    private static final String TAG_PASSWORD_EDITTEXT = "TAG_PASSWORD_EDITTEXT";
    private static final String TAG_LONGPASSWORD_OK_BUTTON = "TAG_LONGPASSWORD_OK_BUTTON";
    private static final String TAG_ACTIVITY_PAY = "TAG_ACTIVITY_PAY";
    private static final String TAG_ACTIVITY_FIRST_RESUME = "TAG_ACTIVITY_FIRST_RESUME";

    private XBiometricIdentify mFingerprintIdentify;

    protected boolean mMockCurrentUser = false;
    private Activity mCurrentPayActivity;
    private final PaymentController mPayment = new PaymentController();
    private Activity mWatchingActivity;
    private long mWatchGeneration;
    private boolean mFingerprintScanStateReady = false;
    private WeakHashMap<Activity, String> mActivityPayMap = new WeakHashMap<>();
    private WeakHashMap<Activity, String> mActivityResumeMap = new WeakHashMap<>();
    private WeakHashMap<Activity, QQPayDialog> mActivityPayDialogMap = new WeakHashMap<>();
    private WeakHashMap<Activity, ViewTreeObserver.OnGlobalLayoutListener> mPayViewLayoutListenerMap = new WeakHashMap<>();
    private int mQQVersionCode;
    private ViewTreeObserver.OnWindowAttachListener mPayWindowAttachListener;
    private boolean mPayDialogTemporaryBlocking = false;
    private boolean mFingerprintIdentifyTemporaryBlocking = false;

    @Override
    public int getVersionCode(Context context) {
        if (mQQVersionCode != 0) {
            return mQQVersionCode;
        }
        mQQVersionCode = ApplicationUtils.getPackageVersionCode(context, PACKAGE_NAME_QQ);
        return mQQVersionCode;
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        //Xposed not hooked yet!
    }

    /**
     * QQ 设置页。MainSettingFragment 是页面里的 Fragment，
     * 只出现在 Activity.toString() 中，故两者都要看。
     */
    private static boolean isQQSettingsActivity(Activity activity) {
        if (activity == null) {
            return false;
        }
        String name = activity.getClass().getName();
        return name.contains(".QQSettingSettingActivity")
                || activity.toString().contains(".MainSettingFragment");
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
            // 设置页注入「指纹支付」入口（上游 7.0.0 起移除了应用内入口，这里恢复）
            if (isQQSettingsActivity(activity)) {
                Task.onMain(250, () -> SettingEntry.injectAboveAnchor(activity,
                        "账号管理", "帐号管理", "账号与安全", "帐号与安全", "通用"));
                Task.onMain(1000, () -> SettingEntry.injectAboveAnchor(activity,
                        "账号管理", "帐号管理", "账号与安全", "帐号与安全", "通用"));
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
                if (Config.from(activity).isOn()) {
                    if (isActivityFirstResume(activity)) {
                        markActivityResumed(activity);
                        qqKeyboardFlashBugfixer(activity);
                        qqKeyboardLazyBugfixer(activity);
                        qqTitleBugfixer(activity);
                    } else if (isPayActivity(activity)) {
                        qqKeyboardFlashBugfixer(activity);
                    }
                }
                if (mPayment.isAuthenticating(activity) && mFingerprintIdentify != null
                        && mFingerprintIdentify.isUsingBiometricApi()) return;
                if (Config.from(activity).isUnderDisplayFingerprint() && mPayment.owner() == activity
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
        if (_payDialog == null) {
            if (retryCountdown > 0) {
                Task.onMain(retryDelay, () -> {
                    if (watchGeneration == mWatchGeneration) initPayActivity(activity, retryDelay, retryCountdown - 1);
                });
            }
            return;
        }
        QQPayDialog payDialog = _payDialog;
        if (!PaymentPasswordGuard.ready(config, payDialog.inputEditText)) return;
        if (!mPayment.begin(activity, payDialog.inputEditText, SystemClock.uptimeMillis())) return;
        final long[] paymentToken = {mPayment.token()};
        mCurrentPayActivity = activity;
        boolean longPassword = payDialog.isLongPassword();
        ViewGroup editCon = longPassword ? (ViewGroup) payDialog.inputEditText.getParent().getParent().getParent()
                : (ViewGroup) payDialog.inputEditText.getParent().getParent();
        setupPayWindowAttachListener(activity, payDialog.inputEditText);
        if (config.isUnderDisplayFingerprint()) {
            mPayment.restoreWith(paymentToken[0], this::releaseFingerprintIdentify);
            mPayment.bindToPaymentWindow(paymentToken[0], activity.getWindow(), config.isVolumeDownMonitorEnabled(), () -> {
                mFingerprintIdentifyTemporaryBlocking = true;
                Toaster.showLong(Lang.getString(R.id.toast_fingerprint_temporary_disabled));
                Task.onBackground(60000, () -> mFingerprintIdentifyTemporaryBlocking = false);
            });
            mPayment.protectKeyboard(paymentToken[0], payDialog.keyboardView);
            markAsPayActivity(activity);
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
            return;
        }
        View fingerprintView = prepareFingerprintView(context);
        int versionCode = getVersionCode(context);

        Runnable switchToPwdRunnable = () -> {
            if (!mPayment.owns(paymentToken[0])) return;
            mPayment.finish(paymentToken[0], SystemClock.uptimeMillis());
            if (activity != mCurrentPayActivity) {
                return;
            }
            if (editCon.getVisibility() != View.VISIBLE) {
                editCon.setVisibility(View.VISIBLE);
            }
            if (longPassword) {
                KeyboardUtils.switchIme(payDialog.inputEditText, true);
                payDialog.inputEditText.requestFocus();
            } else {
                payDialog.keyboardView.setAlpha(1);
                if (payDialog.keyboardView.getVisibility() != View.VISIBLE) {
                    payDialog.keyboardView.setVisibility(View.VISIBLE);
                }
            }
            if (fingerprintView.getVisibility() != View.GONE) {
                fingerprintView.setVisibility(View.GONE);
            }
            if (payDialog.titleTextView != null) {
                payDialog.titleTextView.setClickable(true);
                payDialog.titleTextView.setText("找回密码");
            }
            if (payDialog.usePasswordText != null) {
                payDialog.usePasswordText.setText(Lang.getString(R.id.qq_payview_fingerprint_switch_text));
            }
            if (longPassword && payDialog.okButton != null) {
                if (payDialog.okButton.getVisibility() != View.VISIBLE) {
                    payDialog.okButton.setVisibility(View.VISIBLE);
                }
            }
            if (payDialog.withdrawTitleTextView != null) {
                payDialog.withdrawTitleTextView.setText("输入支付密码，验证身份");
            }
            cancelFingerprintIdentify();
        };

        Runnable switchToFingerprintRunnable = () -> {
            if (!ActivityContext.isAlive(activity) || payDialog.inputEditText.getWindowToken() == null) return;
            if (mPayment.isAuthenticating(activity) && mFingerprintIdentify != null) return;
            if (!mPayment.isActive(paymentToken[0])) {
                mPayment.restart(activity, payDialog.inputEditText);
                paymentToken[0] = mPayment.token();
            }
            mPayment.capture(editCon, payDialog.keyboardView, payDialog.titleTextView,
                    payDialog.usePasswordText, payDialog.okButton, payDialog.withdrawTitleTextView);
            mPayment.restoreWith(paymentToken[0], () -> {
                releaseFingerprintIdentify();
                fingerprintView.setVisibility(View.GONE);
            });
            if (activity != mCurrentPayActivity) {
                return;
            }
            if (editCon.getVisibility() != View.GONE) {
                editCon.setVisibility(View.GONE);
            }
            if (longPassword) {
                KeyboardUtils.switchIme(payDialog.inputEditText, false);
                payDialog.inputEditText.clearFocus();
            } else {
                payDialog.keyboardView.setAlpha(0);
                if (payDialog.keyboardView.getVisibility() != View.INVISIBLE) {
                    payDialog.keyboardView.setVisibility(View.INVISIBLE);
                    //fix切换支付方式后键盘会出现
                    mPayment.later(paymentToken[0], 1000, () -> {
                        if (mFingerprintScanStateReady) {
                            payDialog.keyboardView.setVisibility(View.INVISIBLE);
                        }
                    });
                }
            }
            if (fingerprintView.getVisibility() != View.VISIBLE) {
                fingerprintView.setVisibility(View.VISIBLE);
            }
            if (payDialog.titleTextView != null) {
                payDialog.titleTextView.setText(Lang.getString(R.id.qq_payview_fingerprint_title));
                payDialog.titleTextView.setClickable(false);
            }
            if (payDialog.usePasswordText != null) {
                payDialog.usePasswordText.setText(Lang.getString(R.id.qq_payview_password_switch_text));
            }
            if (longPassword && payDialog.okButton != null) {
                if (payDialog.okButton.getVisibility() != View.GONE) {
                    payDialog.okButton.setVisibility(View.GONE);
                }
            }
            if (payDialog.withdrawTitleTextView != null) {
                payDialog.withdrawTitleTextView.setText("使用指纹验证身份");
            }
            initFingerPrintLock(context, passwordEncrypted, (password) -> { // success
                if (!mPayment.isActive(paymentToken[0])) return;
                try {
                    payDialog.inputEditText.setText(password);
                    if (longPassword && mPayment.isActive(paymentToken[0]) && payDialog.okButton != null) payDialog.okButton.performClick();
                } catch (Exception error) { L.e(error); }
                mPayment.finish(paymentToken[0], SystemClock.uptimeMillis());
                // 如果是非Biometric解锁, 由于ColorOS 15指纹识别出现弹窗, 这里会再次触发 PayDialog onResume
                // 支付成功后临时压制5秒, 超时后才能再次发起
                mPayDialogTemporaryBlocking = true;
                Task.onMain(5000, () -> {
                    mPayDialogTemporaryBlocking = false;
                });
            }, switchToPwdRunnable /** fail */);
        };

        fingerprintView.setOnClickListener(v -> {
            switchToPwdRunnable.run();
        });
        if (config.isVolumeDownMonitorEnabled()) {
            ViewUtils.registerVolumeKeyDownEventListener(activity.getWindow(), event -> {
                if (mFingerprintIdentifyTemporaryBlocking) {
                    return false;
                }
                switchToPwdRunnable.run();
                Toaster.showLong(Lang.getString(R.id.toast_fingerprint_temporary_disabled));
                mFingerprintIdentifyTemporaryBlocking = true;
                Task.onBackground(60000, () -> mFingerprintIdentifyTemporaryBlocking = false);
                return false;
            });
        }

        if (payDialog.usePasswordText != null) {
            payDialog.usePasswordText.setOnClickListener(v -> {
                if (Lang.getString(R.id.qq_payview_password_switch_text).equals(payDialog.usePasswordText.getText())) {
                    switchToPwdRunnable.run();
                } else {
                    switchToFingerprintRunnable.run();
                }
            });
            payDialog.usePasswordText.setVisibility(View.VISIBLE);
        }

        ViewGroup viewGroup = ((ViewGroup)(editCon.getParent()));
        removeAllFingerprintView(viewGroup);

        int keyboardViewPosition = ViewUtils.findChildViewPosition(viewGroup, payDialog.keyboardView);
        if (keyboardViewPosition >= 0) {
            viewGroup.addView(fingerprintView, keyboardViewPosition);
        } else {
            viewGroup.addView(fingerprintView);
        }

        mCurrentPayActivity = activity;
        markAsPayActivity(activity);
        switchToFingerprintRunnable.run();
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

    private void removeAllFingerprintView(ViewGroup viewGroup) {
        List<View> pendingRemoveList = new ArrayList<>();

        int childCount = viewGroup.getChildCount();
        for (int i = 0 ;i < childCount ; i++) {
            View view = viewGroup.getChildAt(i);
            if (TAG_FINGER_PRINT_IMAGE.equals(view.getTag())) {
                pendingRemoveList.add(view);
            }
        }

        for (View view : pendingRemoveList) {
            ViewUtils.removeFromSuperView(view);
        }
    }

    private View prepareFingerprintView(Context context) {

        LinearLayout linearLayout = new LinearLayout(context);
        TextView textView = new TextView(context);
        linearLayout.setTag(TAG_FINGER_PRINT_IMAGE);
        textView.setText("使用密码");
        textView.setTextSize(16);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
//        textView.setLayoutParams(params);


        final Bitmap bitmap = ImageUtils.base64ToBitmap(Constant.ICON_FINGER_PRINT_WECHAT_BASE64);
        ImageView fingerprintImageView = new ImageView(context);
        fingerprintImageView.setImageBitmap(bitmap);
        LinearLayout.LayoutParams fingerprintImageViewParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, DpUtils.dip2px(context, 64));
        fingerprintImageViewParams.topMargin = DpUtils.dip2px(context, 48);

        linearLayout.addView(textView, params);
        linearLayout.addView(fingerprintImageView, fingerprintImageViewParams);

        linearLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.gravity = Gravity.CENTER;
        linearLayout.setLayoutParams(layoutParams);

        if (!Config.from(context).isShowFingerprintIcon()) {
            fingerprintImageView.setVisibility(View.GONE);
            layoutParams.bottomMargin = DpUtils.dip2px(context, 48);
        }

        return linearLayout;
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

    private void closePayment(Activity activity) {
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
        mFingerprintScanStateReady = false;
        if (identify != null) identify.cancelIdentify();
    }

    /**
     * 支付界面标题异常修复
     * @param activity
     */
    private void qqTitleBugfixer(Activity activity) {
        if (Config.from(activity).isUnderDisplayFingerprint()) return;
        View titleView = ViewUtils.findViewByName(activity, "android", "title");
        ViewGroup contentView = (ViewGroup) ViewUtils.findViewByName(activity, "android", "content");
        if (titleView != null && contentView != null){

            activity.getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(() -> {
                if (contentView.getChildCount() > 0) {
                    View firstChild = contentView.getChildAt(0);
                    Drawable backgroundDrawable;
                    if (firstChild != null && (backgroundDrawable = firstChild.getBackground()) instanceof ColorDrawable) {
                        titleView.setBackgroundColor(((ColorDrawable) backgroundDrawable).getColor());
                    } else {
                        titleView.setBackgroundColor(0x66000000);
                    }
                }
                if (titleView instanceof TextView) {
                    ((TextView) titleView).setText("");
                }
            });
        }
    }

    /**
     * 支付界面键盘闪现修复
     * @param activity
     */
    private void qqKeyboardFlashBugfixer(Activity activity) {
        if (Config.from(activity).isUnderDisplayFingerprint()) return;
        View rootView = activity.getWindow().getDecorView();
        rootView.setAlpha(0);
        Task.onMain(200, () -> rootView.animate().alpha(1).start());

    }

    /**
     * 支付界面 指纹区域闪现修复
     * @param activity
     */
    private void qqKeyboardLazyBugfixer(Activity activity) {
        if (Config.from(activity).isUnderDisplayFingerprint()) return;
        activity.getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                List<View> keyboardViewList = new ArrayList<>();
                ViewUtils.getChildViewsByType((ViewGroup)activity.getWindow().getDecorView(), ".MyKeyboardWindow", keyboardViewList);
                if (keyboardViewList.size() == 0) {
                    return;
                }
                for (View view : keyboardViewList) {
                    View keyboardCon = (View) view.getParent();
                    if (keyboardCon == null) {
                        continue;
                    }
                    keyboardCon.setAlpha(0);
                }
                activity.getWindow().getDecorView().getViewTreeObserver().removeOnGlobalLayoutListener(this);
                Task.onMain(600, () -> {
                    for (View view : keyboardViewList) {

                        View keyboardCon = (View) view.getParent();
                        if (keyboardCon == null) {
                            continue;
                        }
                        keyboardCon.animate().alpha(1).start();
                    }
                });
            }
        });

    }

    private void markAsPayActivity(Activity activity) {
        mActivityPayMap.put(activity, TAG_ACTIVITY_PAY);
    }

    private boolean isPayActivity(Activity activity) {
        return TAG_ACTIVITY_PAY.equals(mActivityPayMap.get(activity));
    }

    private void markActivityResumed(Activity activity) {
        mActivityResumeMap.put(activity, TAG_ACTIVITY_FIRST_RESUME);
    }

    private boolean isActivityFirstResume(Activity activity) {
        return !TAG_ACTIVITY_FIRST_RESUME.equals(mActivityResumeMap.get(activity));
    }
}
