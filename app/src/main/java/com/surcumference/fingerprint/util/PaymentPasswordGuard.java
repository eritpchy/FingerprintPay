package com.surcumference.fingerprint.util;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;
import android.widget.Toast;
import com.surcumference.fingerprint.R;
import org.json.JSONObject;

public final class PaymentPasswordGuard {
    static final String MISSING = "未设置支付密码，请先在指纹支付中设置";
    static final String INVALID = "支付密码错误或已失效，请在指纹支付中重新设置";

    public static boolean ready(Config config, View target) {
        if (target == null || !target.isShown()) return false;
        try { return ready(config.snapshot(), target); }
        catch (Exception ignored) { notice(target).show("无法读取配置，请打开指纹支付；仍无效请检查自启动/关联启动权限"); return false; }
    }
    static boolean ready(JSONObject config, View target) throws Exception {
        JSONObject settings = config.getJSONObject("settings");
        if (!settings.optBoolean("scope")) return false;
        Notice notice = notice(target);
        if (settings.optBoolean("requiresReentry")) { notice.show(INVALID); return false; }
        if (!settings.optBoolean("hasPassword")) { notice.show(MISSING); return false; }
        if (!settings.optBoolean("switch_on1") || !config.getJSONObject("globals").optBoolean("license_agree")) return false;
        if (hasPasswordError(target.getRootView())) { notice.show(INVALID); return false; }
        notice.message = null;
        return true;
    }
    public static void rejected(View target) { notice(target).show(INVALID); }
    public static void watch(View target, Runnable rejected) {
        Notice notice = notice(target);
        notice.rejected = rejected;
        notice.onGlobalLayout();
    }
    public static void invalid(Context context) {
        Task.onMain(() -> Toast.makeText(context, INVALID, Toast.LENGTH_LONG).show());
    }
    private static Notice notice(View target) {
        Object current = target.getTag(R.id.payment_password_notice);
        if (current instanceof Notice) return (Notice) current;
        Notice notice = new Notice(target);
        target.setTag(R.id.payment_password_notice, notice);
        return notice;
    }
    private static final class Notice implements ViewTreeObserver.OnGlobalLayoutListener, View.OnAttachStateChangeListener {
        final View target;
        final ViewTreeObserver observer;
        String message;
        Runnable rejected;
        Notice(View target) {
            this.target = target;
            observer = target.getRootView().getViewTreeObserver();
            observer.addOnGlobalLayoutListener(this);
            target.addOnAttachStateChangeListener(this);
        }
        void show(String value) {
            if (value.equals(message)) return;
            message = value;
            Toast.makeText(target.getContext(), value, Toast.LENGTH_LONG).show();
        }
        @Override public void onGlobalLayout() {
            if (!target.isShown()) message = null;
            if (rejected != null && hasPasswordError(target.getRootView())) {
                if (com.surcumference.fingerprint.storage.ConfigContract.PACKAGES.contains(target.getContext().getPackageName())
                        && !Config.from(target.getContext()).isOn()) { rejected = null; return; }
                show(INVALID);
                Runnable callback = rejected; rejected = null; callback.run();
            }
        }
        @Override public void onViewAttachedToWindow(View view) {}
        @Override public void onViewDetachedFromWindow(View view) {
            if (observer.isAlive()) observer.removeOnGlobalLayoutListener(this);
            target.removeOnAttachStateChangeListener(this);
            target.setTag(R.id.payment_password_notice, null);
            rejected = null;
        }
    }
    public static boolean hasPasswordError(View view) {
        if (!view.isShown() || view.getAlpha() <= 0) return false;
        if (ViewUtils.isViewVisibleInScreen(view)) {
            if (isPasswordError(view instanceof TextView ? ((TextView) view).getText() : view.getContentDescription())) return true;
            AccessibilityNodeInfo node = view.createAccessibilityNodeInfo();
            if (node != null) {
                try { if (isPasswordError(node.getText()) || isPasswordError(node.getContentDescription())) return true; }
                finally { node.recycle(); }
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (hasPasswordError(group.getChildAt(i))) return true;
        }
        return false;
    }
    private static boolean isPasswordError(CharSequence text) {
        if (text == null) return false;
        String value = text.toString().toLowerCase(java.util.Locale.ROOT);
        return value.contains("支付密码不正确") || value.contains("支付密码错误")
                || value.contains("支付密碼不正確") || value.contains("支付密碼錯誤")
                || value.contains("支付密码已锁定") || value.contains("支付密碼已鎖定")
                || value.contains("incorrect payment password") || value.contains("payment password is incorrect");
    }
    private PaymentPasswordGuard() {}
}
