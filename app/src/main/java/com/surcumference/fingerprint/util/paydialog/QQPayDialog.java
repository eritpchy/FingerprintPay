package com.surcumference.fingerprint.util.paydialog;

import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.surcumference.fingerprint.util.ViewUtils;
import com.surcumference.fingerprint.util.log.L;

import java.util.ArrayList;
import java.util.List;

public class QQPayDialog {

    private static final String TAG_PASSWORD_EDITTEXT = "TAG_PASSWORD_EDITTEXT";
    private static final String TAG_LONGPASSWORD_OK_BUTTON = "TAG_LONGPASSWORD_OK_BUTTON";


    public EditText inputEditText;
    public View keyboardView;
    @Nullable
    public TextView usePasswordText;
    @Nullable
    public TextView titleTextView;
    //长密码不为空
    @Nullable
    public View okButton;
    //提现时的标题
    @Nullable
    public TextView withdrawTitleTextView;


    @Nullable
    public static QQPayDialog findFrom(ViewGroup rootView) {
        try {
            QQPayDialog payDialog = new QQPayDialog();

            List<View> childViews = new ArrayList<>();
            List<View> keyboards = new ArrayList<>();
            ViewUtils.getChildViews(rootView, childViews);
            payDialog.okButton = ViewUtils.findViewByText(rootView, "立即支付", "立即验证", "确定");
            if (payDialog.okButton != null && payDialog.okButton.isShown()) {
                payDialog.okButton.setTag(TAG_LONGPASSWORD_OK_BUTTON);
            }
            boolean longPassword = payDialog.isLongPassword();
            for (View view : childViews) {
                if (view == null) {
                    continue;
                }
                if (longPassword) {
                    if (view instanceof EditText && "输入财付通支付密码".equals(((EditText) view).getHint())) {
                        if (view.isShown() || TAG_PASSWORD_EDITTEXT.equals(view.getTag())) {
                            payDialog.inputEditText = (EditText)view;
                            view.setTag(TAG_PASSWORD_EDITTEXT);
                        }
                    }
                } else {
                    if (view instanceof EditText && isShortPasswordField(view)) {
                        if (view.isShown() || TAG_PASSWORD_EDITTEXT.equals(view.getTag())) {
                            payDialog.inputEditText = (EditText)view;
                            view.setTag(TAG_PASSWORD_EDITTEXT);
                        }
                    }
                }
                if (view.getClass().getName().endsWith(".MyKeyboardWindow")
                        && (longPassword || (view.isShown() && view.getAlpha() > 0))) {
                    L.d("密码键盘:" + view);
                    if (view.getParent() != null) {
                        keyboards.add(view);
                    }
                }
            }

            if (payDialog.inputEditText == null) {
                L.d("inputEditText not found");
                return null;
            }

            for (android.view.ViewParent parent = payDialog.inputEditText.getParent(); parent instanceof View;
                 parent = parent.getParent()) {
                for (View keyboard : keyboards) {
                    View child = keyboard;
                    while (child != parent && child.getParent() instanceof View) child = (View) child.getParent();
                    if (child == parent) {
                        if (payDialog.keyboardView != null) return null;
                        payDialog.keyboardView = keyboard;
                    }
                }
                if (payDialog.keyboardView != null) break;
            }

            if (payDialog.keyboardView == null) {
                L.d("keyboardView not found");
                return null;
            }

            payDialog.usePasswordText = (TextView) ViewUtils.findViewByText(rootView,
                    "使用密码", "使用密碼", "Password",
                    "使用指纹", "使用指紋", "Fingerprint");
            if (payDialog.usePasswordText == null) {
                L.d("usePasswordText not found");
            }

            payDialog.titleTextView = (TextView) ViewUtils.findViewByText(rootView,
                    "请验证指纹", "請驗證指紋", "找回密码", "Verify fingerprint",
                    "请输入支付密码", "請輸入付款密碼", "Enter payment password");
            if (payDialog.titleTextView == null) {
                L.d("titleTextView not found");
            }

            payDialog.withdrawTitleTextView = (TextView) ViewUtils.findViewByText(rootView, "输入支付密码，验证身份");
            if (payDialog.withdrawTitleTextView == null) {
                L.d("withdrawTitleTextView not found");
            }
            return payDialog;
        } catch (Exception e) {
            L.e(e);
        }
        return null;
    }

    private static boolean isShortPasswordField(View view) {
        if (!view.isShown() || !view.isEnabled() || view.getAlpha() <= 0) return false;
        CharSequence description = view.getContentDescription();
        if (isPasswordDescription(description)) return true;
        AccessibilityNodeInfo node = view.createAccessibilityNodeInfo();
        try {
            CharSequence accessible = node == null ? null : node.getContentDescription();
            if (isPasswordDescription(accessible)) return true;
            if (!android.text.TextUtils.isEmpty(description) || !android.text.TextUtils.isEmpty(accessible)
                    || !"com.tenpay.password.PasswordEditText".equals(view.getClass().getName())) return false;
            EditText input = (EditText) view;
            int type = input.getInputType();
            return input.length() == 0
                    && (type & android.text.InputType.TYPE_MASK_CLASS) == android.text.InputType.TYPE_CLASS_NUMBER
                    && (type & android.text.InputType.TYPE_MASK_VARIATION) == android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD;
        } finally { if (node != null) node.recycle(); }
    }

    private static boolean isPasswordDescription(CharSequence description) {
        if (description == null) return false;
        String value = description.toString();
        return "支付密码".equals(value) || "支付密码输入框".equals(value)
                || "支付密码共6位，已输入0位".equals(value);
    }

    @Override
    public String toString() {
        return "PayDialog{" +
                "inputEditText=" + inputEditText +
                ", keyboardView=" + keyboardView +
                ", titleTextView=" + titleTextView +
                '}';
    }

    public boolean isLongPassword() {
        return okButton != null && (okButton.isShown() || TAG_LONGPASSWORD_OK_BUTTON.equals(okButton.getTag()));
    }
}
