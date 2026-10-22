package com.surcumference.fingerprint.plugin.impl.alipay;

import static com.surcumference.fingerprint.Constant.PACKAGE_NAME_ALIPAY;

import android.app.Activity;
import com.surcumference.fingerprint.util.PaymentPasswordGuard;
import android.app.AlertDialog;
import android.content.Context;
import android.os.*;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hjq.toast.Toaster;
import com.surcumference.fingerprint.Lang;
import com.surcumference.fingerprint.R;
import com.surcumference.fingerprint.bean.DigitPasswordKeyPadInfo;
import com.surcumference.fingerprint.plugin.inf.IAppPlugin;
import com.surcumference.fingerprint.plugin.inf.OnFingerprintVerificationOKListener;
import com.surcumference.fingerprint.util.ActivityContext;
import com.surcumference.fingerprint.util.DigitKeyResolver;
import com.surcumference.fingerprint.util.PaymentController;
import com.surcumference.fingerprint.util.AlipayVersionControl;
import com.surcumference.fingerprint.util.ApplicationUtils;
import com.surcumference.fingerprint.util.BizBiometricIdentify;
import com.surcumference.fingerprint.util.BlackListUtils;
import com.surcumference.fingerprint.util.Config;
import com.surcumference.fingerprint.util.Task;
import com.surcumference.fingerprint.util.ViewUtils;
import com.surcumference.fingerprint.util.XBiometricIdentify;
import com.surcumference.fingerprint.util.log.L;
import com.surcumference.fingerprint.view.AlipayPayView;
import com.surcumference.fingerprint.view.DialogUtils;
import com.wei.android.lib.fingerprintidentify.bean.FingerprintIdentifyFailInfo;

import java.util.ArrayList;
import java.util.List;

public class AlipayBasePlugin implements IAppPlugin {



    private AlertDialog mFingerPrintAlertDialog;

    private XBiometricIdentify mFingerprintIdentify;
    private Activity mCurrentActivity;

    private int mAlipayVersionCode;
    private volatile boolean mFingerprintIdentifyTemporaryBlocking = false;
    private final PaymentController mPaymentSession = new PaymentController();
    private final Handler mInputHandler = new Handler(Looper.getMainLooper());
    private View mLayoutRoot;
    private ViewTreeObserver.OnGlobalLayoutListener mLayoutListener;
    private String mPaymentCredential;
    private String mPaymentMethod;
    private boolean mPasswordRejected;

    public int getVersionCode(Context context) {
        if (mAlipayVersionCode != 0) {
            return mAlipayVersionCode;
        }
        mAlipayVersionCode = ApplicationUtils.getPackageVersionCode(context, PACKAGE_NAME_ALIPAY);
        return mAlipayVersionCode;
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
    }

    @Override
    public void onActivityPaused(Activity activity) {
        if (!isSystemAuthenticationShowing(activity)) closePayment(activity);
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

    public void onActivityResumed(Activity activity) {
        try {
            if (mCurrentActivity != null && mCurrentActivity != activity) closePayment(mCurrentActivity);
            mCurrentActivity = activity;
            String name = activity.getClass().getName();
            if (!(name.contains(".PayPwdDialogActivity") || name.contains(".PayPwdFullActivity")
                    || name.contains(".MspContainerActivity") || name.contains(".FlyBirdWindowActivity")
                    || name.contains("PayPwdHalfActivity"))) return;
            View root = activity.getWindow().getDecorView();
            removeLayoutListener();
            mLayoutRoot = root;
            mLayoutListener = () -> {
                try {
                    if (ActivityContext.isAlive(activity) && mCurrentActivity == activity) {
                        if (mPaymentSession.isActive(mPaymentSession.token()))
                            protectPaymentKeyboard(activity, mPaymentSession.token());
                        showFingerPrintDialog(activity);
                    }
                } catch (Exception e) {
                    L.e("Alipay payment detection failed", e);
                    restorePassword(mPaymentSession.token());
                }
            };
            root.getViewTreeObserver().addOnGlobalLayoutListener(mLayoutListener);
            mLayoutListener.onGlobalLayout();
        } catch (Exception e) {
            L.e("Alipay payment detection failed", e);
            restorePassword(mPaymentSession.token());
        }
    }

    public void initFingerPrintLock(final Context context, AlertDialog dialog, String encrypted,
                                    OnFingerprintVerificationOKListener onSuccess) {
        final long token = mPaymentSession.token();
        Activity activity = ActivityContext.find(context);
        if (activity == null || !mPaymentSession.isActive(token)) {
            restorePassword(token);
            return;
        }
        BizBiometricIdentify identify = new BizBiometricIdentify(activity);
        mFingerprintIdentify = identify;
        mPaymentSession.authentication(token, identify);
        identify.decryptPasscode(encrypted, new BizBiometricIdentify.IdentifyListener() {
            @Override
            public void onInited(BizBiometricIdentify identify) {
                ViewUtils.setAlpha(dialog, identify.isUsingBiometricApi() ? 0 : 1);
                ViewUtils.setDimAmount(dialog, identify.isUsingBiometricApi() ? 0 : 0.6f);
            }

            @Override
            public void onDecryptionSuccess(BizBiometricIdentify identify, @NonNull String password) {
                if (!mPaymentSession.authenticated(token)) return;
                try {
                    onSuccess.onFingerprintVerificationOK(password);
                } catch (Exception e) {
                    L.e("Alipay password input failed", e);
                    restorePassword(token);
                }
            }

            @Override
            public void onFailed(BizBiometricIdentify identify, FingerprintIdentifyFailInfo info) {
                if (mPaymentSession.isActive(token)) restorePassword(token);
            }
        });
    }

    public boolean showFingerPrintDialog(final Activity activity) {
        if (!ActivityContext.isAlive(activity) || mCurrentActivity != activity
                || mFingerprintIdentifyTemporaryBlocking) return false;
        Config config = Config.from(activity);
        String encrypted = config.getPasswordEncrypted();
        boolean rejected = PaymentPasswordGuard.hasPasswordError(activity.getWindow().getDecorView());
        View target = rejected ? null : findPasswordTarget(activity);
        if (rejected && !mPasswordRejected) PaymentPasswordGuard.rejected(mPaymentSession.target() instanceof View
                ? (View) mPaymentSession.target() : activity.getWindow().getDecorView());
        if (!rejected && target != null && !PaymentPasswordGuard.ready(config, target)) return false;
        if (!beginPasswordStage(activity, target, encrypted, rejected)) return false;
        final long token = mPaymentSession.token();
        mPaymentSession.restoreWith(token, () -> restorePassword(token));
        target.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) {}
            @Override public void onViewDetachedFromWindow(View view) {
                view.removeOnAttachStateChangeListener(this);
                if (mPaymentSession.owns(token)) {
                    restorePassword(token);
                    mPaymentSession.close(activity);
                }
            }
        });
        try {
            OnFingerprintVerificationOKListener onSuccess = password -> {
                BlackListUtils.applyIfNeeded(activity);
                focusPasswordTarget(activity, token);
                clickDigitPasswordWidget(activity);
                protectPaymentKeyboard(activity, token);
                inputPassword(activity, password, token, SystemClock.uptimeMillis() + 6000);
            };
            mPaymentSession.bindToPaymentWindow(token, activity.getWindow(), config.isVolumeDownMonitorEnabled(), () -> {
                mFingerprintIdentifyTemporaryBlocking = true;
                Toaster.showLong(Lang.getString(R.id.toast_fingerprint_temporary_disabled));
                Task.onBackground(60000, () -> mFingerprintIdentifyTemporaryBlocking = false);
            });
            protectPaymentKeyboard(activity, token);
            if (config.isUnderDisplayFingerprint()) {
                initFingerPrintLock(activity, null, encrypted, onSuccess);
                return true;
            }
            AlipayPayView view = new AlipayPayView(activity)
                    .withOnShowListener(shown -> {
                        if (!mPaymentSession.isActive(token)) return;
                        initFingerPrintLock(activity, shown.getDialog(), encrypted, onSuccess);
                        if (config.isVolumeDownMonitorEnabled() && mPaymentSession.isActive(token)) {
                            ViewUtils.registerVolumeKeyDownEventListener(shown.getDialog().getWindow(), event -> {
                                if (mFingerprintIdentifyTemporaryBlocking) return false;
                                mFingerprintIdentifyTemporaryBlocking = true;
                                restorePassword(token);
                                Toaster.showLong(Lang.getString(R.id.toast_fingerprint_temporary_disabled));
                                Task.onBackground(60000, () -> mFingerprintIdentifyTemporaryBlocking = false);
                                return true;
                            });
                        }
                    }).withOnCancelButtonClickListener(shown -> restorePassword(token))
                    .withOnDismissListener(shown -> {
                        if (mPaymentSession.isActive(token)) restorePassword(token);
                    });
            AlertDialog dialog = view.showInDialog();
            if (mPaymentSession.isActive(token)) {
                mFingerPrintAlertDialog = dialog;
                if (mFingerprintIdentify != null && mFingerprintIdentify.isUsingBiometricApi()) {
                    ViewUtils.setAlpha(dialog, 0);
                    ViewUtils.setDimAmount(dialog, 0);
                }
            } else {
                DialogUtils.dismiss(dialog);
            }
        } catch (Exception e) {
            L.e("Unable to show Alipay authentication", e);
            restorePassword(token);
        }
        return true;
    }

    private boolean isSystemAuthenticationShowing(Activity activity) {
        return mPaymentSession.isAuthenticating(activity) && mFingerprintIdentify != null
                && mFingerprintIdentify.isUsingBiometricApi() && ActivityContext.isAlive(activity);
    }

    private void restorePassword(long token) {
        if (!mPaymentSession.owns(token)) return;
        mPaymentSession.finish(token, SystemClock.uptimeMillis());
        mInputHandler.removeCallbacksAndMessages(null);
        XBiometricIdentify identify = mFingerprintIdentify;
        mFingerprintIdentify = null;
        if (identify != null) identify.cancelIdentify();
        hidePreviousPayDialog();
    }

    private void closePayment(Activity activity) {
        if (mPaymentSession.owner() == activity) {
            restorePassword(mPaymentSession.token());
            mPaymentSession.close(activity);
        }
        if (mCurrentActivity == activity) {
            mPaymentCredential = null;
            mPaymentMethod = null;
            mPasswordRejected = false;
            removeLayoutListener();
            mCurrentActivity = null;
        }
    }

    private boolean beginPasswordStage(Activity activity, View target, String credential, boolean rejected) {
        if (rejected) {
            if (!mPasswordRejected && mPaymentSession.owner() == null) mPaymentCredential = credential;
            mPasswordRejected = true;
            restorePassword(mPaymentSession.token());
            return false;
        }
        Object previous = mPaymentSession.target();
        String paymentMethod = findSelectedPaymentMethod(activity.getWindow().getDecorView());
        boolean newPaymentMethod = previous != null && mPaymentMethod != null && paymentMethod != null
                && !TextUtils.equals(mPaymentMethod, paymentMethod);
        if (paymentMethod != null) mPaymentMethod = paymentMethod;
        boolean newCredential = (previous != null || mPasswordRejected)
                && !TextUtils.equals(mPaymentCredential, credential);
        boolean newTarget = previous != null && target != null && previous != target
                && !mPaymentSession.isActive(mPaymentSession.token());
        boolean leftStage = target == null && previous instanceof View
                && (!((View) previous).isShown() || ((View) previous).getWindowToken() == null)
                && !isSystemAuthenticationShowing(activity);
        if (newCredential || newTarget || leftStage) {
            mPaymentSession.close(activity);
            mPasswordRejected = false;
        } else if (newPaymentMethod && !mPasswordRejected) {
            mPaymentSession.close(activity);
        }
        if (target == null || mPasswordRejected
                || !mPaymentSession.begin(activity, target, SystemClock.uptimeMillis())) return false;
        mPaymentCredential = credential;
        return true;
    }

    private String findSelectedPaymentMethod(View view) {
        if (!view.isShown() || view.getAlpha() <= 0) return null;
        if (view.isClickable() && ViewUtils.isViewVisibleInScreen(view)) {
            AccessibilityNodeInfo node = view.createAccessibilityNodeInfo();
            if (node != null) {
                try {
                    CharSequence description = node.getContentDescription();
                    if (description != null) {
                        String label = description.toString().replace('\uff0c', ',');
                        for (String prefix : new String[]{"已选中,", "选中,", "已選中,", "選中,"}) {
                            if (!label.startsWith(prefix)) continue;
                            int hint = label.indexOf(",支付渠道说明内容:");
                            return label.substring(prefix.length(), hint < 0 ? label.length() : hint);
                        }
                    }
                } finally { node.recycle(); }
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                String method = findSelectedPaymentMethod(group.getChildAt(i));
                if (method != null) return method;
            }
        }
        return null;
    }

    private void removeLayoutListener() {
        if (mLayoutRoot != null && mLayoutListener != null && mLayoutRoot.getViewTreeObserver().isAlive()) {
            if (Build.VERSION.SDK_INT >= 16) mLayoutRoot.getViewTreeObserver().removeOnGlobalLayoutListener(mLayoutListener);
            else mLayoutRoot.getViewTreeObserver().removeGlobalOnLayoutListener(mLayoutListener);
        }
        mLayoutListener = null;
        mLayoutRoot = null;
    }

    private View findPasswordTarget(Activity activity) {
        View root = activity.getWindow().getDecorView();
        View prompt = ViewUtils.findViewByText(root, "支付宝支付密码", "支付寶支付密碼", "Alipay Payment Password",
                "请输入支付密码", "請輸入支付密碼", "Payment password", "请输入长密码", "請輸入長密碼",
                "Payment Password", "密码共6位，已输入0位");
        boolean hasPrompt = ViewUtils.isShown(prompt);
        EditText edit = findPasswordEditText(activity);
        if (edit != null && edit.isShown()) return edit;
        for (String pkg : new String[]{"com.alipay.android.phone.mobilecommon.verifyidentity", "com.alipay.mobile.antui"}) {
            View six = ViewUtils.findViewByName(root, pkg, "ap_six_number_pwd_input");
            if (six != null && six.isShown()) return six;
        }
        View keyboard = findDigitKeyboard(activity);
        if (keyboard == null) return null;
        if (hasPrompt) return keyboard;
        if (DigitKeyResolver.resolve(keyboard,
                AlipayVersionControl.getDigitPasswordKeyPad(getVersionCode(activity)), "0123456789") == null) return null;
        return com.surcumference.fingerprint.util.ShortPasswordTarget.find(root);
    }

    /**
     * 修复某个设备不自动弹出键盘
     * @param activity
     */
    private void clickDigitPasswordWidget(Activity activity) {
        int versionCode = getVersionCode(activity);
        View view = ViewUtils.findViewByName(activity, (versionCode >= 352 /** 10.2.13.7000 */ ? "com.alipay.android.safepaysdk" : "com.alipay.android.app"), "simplePwdLayout");
        L.d("digit password widget", view);
        if (view == null) {
            return;
        }
        ViewUtils.performActionClick(view);
    }

    private void focusPasswordTarget(Activity activity, long token) {
        if (!isInputCurrent(activity, token)) return;
        View target = (View) mPaymentSession.target();
        if (target != findDigitKeyboard(activity)) {
            target.requestFocus();
            target.performClick();
        }
    }

    private void inputPassword(Activity activity, String password, long token, long deadline) {
        if (!isInputCurrent(activity, token)) {
            if (mPaymentSession.owns(token)) restorePassword(token);
            return;
        }
        try {
            EditText edit = findPasswordEditText(activity);
            View confirm = edit == null ? null : findConfirmPasswordBtn(edit);
            if (edit != null && confirm != null) {
                if (!edit.isEnabled()) {
                    if (SystemClock.uptimeMillis() >= deadline) inputFailed(token);
                    else mInputHandler.postDelayed(() -> inputPassword(activity, password, token, deadline), 100);
                    return;
                }
                edit.requestFocus();
                edit.setText(password);
                mInputHandler.postDelayed(() -> confirmPassword(activity, edit, confirm, token, deadline), 100);
                return;
            }
            View keyboard = findDigitKeyboard(activity);
            mPaymentSession.protectKeyboard(token, keyboard);
            List<View> keys = DigitKeyResolver.resolve(keyboard,
                    AlipayVersionControl.getDigitPasswordKeyPad(getVersionCode(activity)), password);
            if (keys == null) {
                if (SystemClock.uptimeMillis() >= deadline) {
                    Toaster.showLong(Lang.getString(R.id.toast_password_auto_enter_fail));
                    restorePassword(token);
                } else {
                    mInputHandler.postDelayed(() -> inputPassword(activity, password, token, deadline), 300);
                }
                return;
            }
            for (int i = 0; i < keys.size(); i++) {
                View key = keys.get(i);
                mInputHandler.postDelayed(() -> {
                    if (!isInputCurrent(activity, token)) return;
                    try {
                        if (!key.isShown() || !key.isEnabled() || key.getWindowToken() == null) {
                            restorePassword(token);
                            return;
                        }
                        if (!ViewUtils.performActionClick(key)) restorePassword(token);
                    } catch (Exception e) {
                        L.e("Alipay keyboard input failed", e);
                        restorePassword(token);
                    }
                }, i * 70L);
            }
            mInputHandler.postDelayed(() -> {
                if (mPaymentSession.isActive(token)) restorePassword(token);
            }, keys.size() * 70L + 100);
        } catch (Exception e) {
            L.e("Alipay password input failed", e);
            restorePassword(token);
        }
    }

    private void confirmPassword(Activity activity, EditText edit, View confirm, long token, long deadline) {
        if (!isInputCurrent(activity, token)) {
            if (mPaymentSession.owns(token)) restorePassword(token);
            return;
        }
        if (findPasswordEditText(activity) != edit || findConfirmPasswordBtn(edit) != confirm) {
            restorePassword(token);
            return;
        }
        if (!confirm.isEnabled()) {
            if (SystemClock.uptimeMillis() >= deadline) inputFailed(token);
            else mInputHandler.postDelayed(() -> confirmPassword(activity, edit, confirm, token, deadline), 100);
            return;
        }
        try { confirm.performClick(); }
        catch (Exception error) { L.e("Alipay confirmation failed", error); }
        finally { restorePassword(token); }
    }

    private void inputFailed(long token) {
        Toaster.showLong(Lang.getString(R.id.toast_password_auto_enter_fail));
        restorePassword(token);
    }

    private void protectPaymentKeyboard(Activity activity, long token) {
        View keyboard = findDigitKeyboard(activity);
        if (keyboard != null) mPaymentSession.protectKeyboard(token, keyboard);
        else mPaymentSession.protectDigits(token, activity.getWindow().getDecorView(),
                AlipayVersionControl.getDigitPasswordKeyPad(getVersionCode(activity)));
    }

    private boolean isInputCurrent(Activity activity, long token) {
        Object target = mPaymentSession.target();
        return mPaymentSession.isActive(token) && mCurrentActivity == activity
                && ActivityContext.isAlive(activity) && target instanceof View
                && ((View) target).getWindowToken() != null;
    }

    private View findDigitKeyboard(Activity activity) {
        View root = activity.getWindow().getDecorView();
        String pkg = "com.alipay.android.phone.mobilecommon.verifyidentity";
        for (String id : new String[]{"keyboard_container", "ll_key_area"}) {
            View keyboard = ViewUtils.findViewByName(root, pkg, id);
            if (keyboard instanceof ViewGroup && keyboard.isShown()) return keyboard;
        }
        DigitPasswordKeyPadInfo info = AlipayVersionControl.getDigitPasswordKeyPad(getVersionCode(activity));
        List<View> keys = new ArrayList<>();
        for (String[] ids : info.keys.values()) {
            View key = ViewUtils.findViewByName(root, info.modulePackageName, ids);
            if (key != null && key.isShown()) keys.add(key);
        }
        if (keys.size() < 2) return null;
        for (android.view.ViewParent parent = keys.get(0).getParent(); parent instanceof View && parent != root;
             parent = parent.getParent()) {
            boolean containsAll = true;
            for (View key : keys) {
                if (!isDescendant((View) parent, key)) { containsAll = false; break; }
            }
            if (containsAll) return (View) parent;
        }
        return null;
    }

    private static boolean isDescendant(View parent, View child) {
        for (View current = child; current != null; ) {
            if (current == parent) return true;
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }
        return false;
    }

    private EditText findPasswordEditText(Activity activity) {
        View root = activity.getWindow().getDecorView();
        View edit = ViewUtils.findViewByName(root, "com.alipay.android.phone.mobilecommon.verifyidentity", "input_et_password");
        if (edit instanceof EditText && edit.isShown()) return (EditText) edit;
        for (String pkg : new String[]{"com.alipay.android.phone.mobilecommon.verifyidentity", "com.alipay.mobile.antui"}) {
            edit = ViewUtils.findViewByName(root, pkg, "ap_six_number_pwd_input");
            if (edit instanceof EditText && edit.isShown()) return (EditText) edit;
        }
        List<View> children = new ArrayList<>();
        ViewUtils.getChildViews((ViewGroup) root, children);
        for (View view : children) {
            if (!(view instanceof EditText) || !view.isShown()) continue;
            int type = ((EditText) view).getInputType();
            int variation = type & android.text.InputType.TYPE_MASK_VARIATION;
            int inputClass = type & android.text.InputType.TYPE_MASK_CLASS;
            boolean password = inputClass == android.text.InputType.TYPE_CLASS_NUMBER
                    && variation == android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD;
            password |= inputClass == android.text.InputType.TYPE_CLASS_TEXT
                    && (variation == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                    || variation == android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD);
            if (password) return (EditText) view;
        }
        return null;
    }

    private View findConfirmPasswordBtn(EditText edit) {
        View root = edit.getRootView();
        for (android.view.ViewParent parent = edit.getParent(); parent instanceof ViewGroup && parent != root;
             parent = parent.getParent()) {
            ViewGroup form = (ViewGroup) parent;
            if (form.getId() == android.R.id.content) break;
            View idMatch = ViewUtils.findViewByName(form, "com.alipay.android.phone.mobilecommon.verifyidentity", "button_ok");
            if (isConfirmButton(idMatch, form)) return idMatch;
            List<View> children = new ArrayList<>();
            ViewUtils.getChildViews(form, children);
            View match = null;
            for (View view : children) {
                if (!(view instanceof TextView) || !((TextView) view).getText().toString().trim()
                        .matches("确定|確定|OK|确认|確認|付款|Pay")) continue;
                View button = view;
                while (!button.isClickable() && button.getParent() instanceof View && button.getParent() != form) {
                    button = (View) button.getParent();
                }
                if (!isConfirmButton(button, form)) continue;
                if (match != null && match != button) return null;
                match = button;
            }
            if (match != null) return match;
        }
        return null;
    }

    private boolean isConfirmButton(View view, ViewGroup form) {
        if (view == null || !view.isShown() || !view.isClickable() || view.getAlpha() <= 0) return false;
        for (View current = view; current != null; ) {
            if (current.getId() != View.NO_ID) {
                try {
                    String name = current.getResources().getResourceEntryName(current.getId());
                    if (name.equals("key_enter") || name.contains("keyboard") || name.equals("ll_key_area")) return false;
                } catch (android.content.res.Resources.NotFoundException ignored) {}
            }
            if (current == form) break;
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }
        return true;
    }

    private void hidePreviousPayDialog() {
        AlertDialog dialog = mFingerPrintAlertDialog;
        mFingerPrintAlertDialog = null;
        DialogUtils.dismiss(dialog);
    }
}
