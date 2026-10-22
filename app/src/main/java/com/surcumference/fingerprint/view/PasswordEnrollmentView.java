package com.surcumference.fingerprint.view;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.surcumference.fingerprint.Constant;
import com.surcumference.fingerprint.util.DpUtils;
import com.surcumference.fingerprint.util.ImageUtils;
import com.surcumference.fingerprint.util.StyleUtils;

public final class PasswordEnrollmentView extends DialogFrameLayout<PasswordEnrollmentView> {
    private final TextView title, status, description;
    private final ImageView fingerprint;
    private final FrameLayout icon;

    public PasswordEnrollmentView(Context context) {
        super(context);
        if (Build.VERSION.SDK_INT >= 29) setForceDarkAllowed(false);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(24), dp(28), dp(24), dp(4));
        icon = new FrameLayout(context);
        fingerprint = new ImageView(context);
        fingerprint.setImageBitmap(ImageUtils.base64ToBitmap(Constant.ICON_FINGER_PRINT_ALIPAY_BASE64));
        fingerprint.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        icon.addView(fingerprint, new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER));
        content.addView(icon, new LinearLayout.LayoutParams(dp(84), dp(84)));
        title = text("设置支付密码", 21);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        if (Build.VERSION.SDK_INT >= 28) title.setAccessibilityHeading(true);
        add(content, title, 20);
        status = text("请验证指纹", 16);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        add(content, status, 10);
        description = text("验证后自动保存并返回设置", 14);
        add(content, description, 8);
        addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        withNegativeButtonText("取消");
        applyTheme();
    }

    private int dp(int value) { return DpUtils.dip2px(getContext(), value); }
    private TextView text(String value, int size) {
        TextView view = new TextView(getContext());
        view.setText(value); view.setTextSize(size); view.setGravity(Gravity.CENTER);
        view.setIncludeFontPadding(false);
        return view;
    }
    private void add(LinearLayout content, View view, int spacing) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(spacing); content.addView(view, params);
    }
    private void applyTheme() {
        title.setTextColor(StyleUtils.textColor(getContext()));
        status.setTextColor(StyleUtils.accentColor(getContext()));
        description.setTextColor(StyleUtils.secondaryTextColor(getContext()));
        fingerprint.setColorFilter(StyleUtils.accentColor(getContext()), PorterDuff.Mode.SRC_IN);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL); circle.setColor(StyleUtils.pressedColor(getContext()));
        icon.setBackground(circle);
    }
    public void retry() { status.setText("指纹未匹配，请重试"); }
    public void saved() { status.setText("密码已保存"); }

    @Override protected int dialogCornerRadius() { return dp(24); }

    @Override public AlertDialog showInDialog() {
        AlertDialog dialog = super.showInDialog();
        dialog.setCanceledOnTouchOutside(false);
        fitWindow();
        return dialog;
    }
    private void fitWindow() {
        AlertDialog dialog = getDialog();
        Window window = dialog == null ? null : dialog.getWindow();
        if (window != null) window.setLayout(Math.min(dp(360), getResources().getDisplayMetrics().widthPixels - dp(40)), ViewGroup.LayoutParams.WRAP_CONTENT);
    }
    @Override protected void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        applyTheme(); fitWindow();
    }
}
