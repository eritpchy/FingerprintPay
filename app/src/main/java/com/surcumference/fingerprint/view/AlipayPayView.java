package com.surcumference.fingerprint.view;

import static com.surcumference.fingerprint.Constant.ICON_FINGER_PRINT_ALIPAY_BASE64;

import android.app.AlertDialog;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.surcumference.fingerprintlocation.core.Resolution;
import com.surcumference.fingerprint.Lang;
import com.surcumference.fingerprint.R;
import com.surcumference.fingerprint.util.Config;
import com.surcumference.fingerprint.util.ActivityContext;
import com.surcumference.fingerprint.util.DpUtils;
import com.surcumference.fingerprint.util.ImageUtils;

/**
 * Created by Jason on 2021/8/27.
 */
public class AlipayPayView extends DialogFrameLayout<AlipayPayView> {
    private ImageView fingerprintImage;
    private LinearLayout header, brand;
    private FrameLayout badge;
    private ImageView badgeIcon;
    private TextView brandName, heading;
    private TextView subtitle;
    private TextView guidance;
    private TextView cancel;
    private OnCancelButtonClickListener cancelListener;
    private View hostDecor;
    private Rect sensor;
    private final Runnable sensorChanged = this::updateDialogSize;
    private boolean bottomSheet;
    private int dialogBottom;
    private boolean sensorLayoutReady;
    private final Runnable sheetRelayout = this::requestLayout;
    private boolean requestedUnderDisplay, placementChosen, placementPending;
    private Resolution placementResult;
    private String layoutDiagnostic = "";
    private final View.OnLayoutChangeListener hostLayoutListener =
            (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> updateDialogSize();

    public AlipayPayView(@NonNull Context context) {
        super(context);
        init(context);
    }

    private void init(Context context) {
        fingerprintImage = new ImageView(context);
        fingerprintImage.setImageBitmap(ImageUtils.base64ToBitmap(ICON_FINGER_PRINT_ALIPAY_BASE64));
        FingerprintPromptPalette palette = FingerprintPromptPalette.read(context);
        fingerprintImage.setColorFilter(palette.action);
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(Color.TRANSPARENT);
        ring.setStroke(dp(2), palette.action);
        fingerprintImage.setBackground(ring);
        fingerprintImage.setPadding(dp(8), dp(8), dp(8), dp(8));
        fingerprintImage.setVisibility(VISIBLE);
        addView(fingerprintImage, new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        header = new LinearLayout(context);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER_HORIZONTAL);
        header.setPadding(dp(24), 0, dp(24), 0);
        brand = new LinearLayout(context);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        badge = new FrameLayout(context);
        GradientDrawable badgeBackground = new GradientDrawable();
        badgeBackground.setColor(palette.badge);
        badgeBackground.setCornerRadius(dp(12));
        badge.setBackground(badgeBackground);
        badgeIcon = new ImageView(context);
        badgeIcon.setImageBitmap(ImageUtils.base64ToBitmap(ICON_FINGER_PRINT_ALIPAY_BASE64));
        badgeIcon.setColorFilter(palette.action);
        badge.addView(badgeIcon, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        brand.addView(badge, new LinearLayout.LayoutParams(dp(40), dp(40)));
        brandName = label(context, "指纹支付", 12, palette.secondary);
        LinearLayout.LayoutParams nameLayout = new LinearLayout.LayoutParams(-2, -2);
        nameLayout.leftMargin = dp(8);
        brand.addView(brandName, nameLayout);
        header.addView(brand, new LinearLayout.LayoutParams(-2, -2));
        heading = label(context, "验证您的身份", 20, palette.foreground);
        heading.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
        addLine(heading, 12);
        subtitle = label(context, "使用指纹完成验证以继续支付", 14, palette.secondary);
        addLine(subtitle, 4);
        guidance = label(context, "请验证指纹以继续", 14, palette.foreground);
        addLine(guidance, 12);
        addView(header, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        cancel = label(context, "取消", 16, palette.action);
        cancel.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
        cancel.setOnClickListener(view -> {
            if (cancelListener != null) cancelListener.onClicked(this);
            else if (getDialog() != null) getDialog().dismiss();
        });
        addView(cancel, new FrameLayout.LayoutParams(dp(96), dp(48), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        updateBackground(false);
    }

    private int dp(float value) { return DpUtils.dip2px(getContext(), value); }

    private static TextView label(Context context, String value, int size, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    private void addLine(TextView view, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(margin);
        header.addView(view, params);
    }

    private void updateBackground(boolean bottomSheet) {
        FingerprintPromptPalette palette = FingerprintPromptPalette.read(getContext());
        GradientDrawable background = new GradientDrawable();
        background.setColor(palette.surface);
        if (bottomSheet) background.setCornerRadii(new float[]{dp(28), dp(28), dp(28), dp(28), 0, 0, 0, 0});
        else { background.setCornerRadius(dp(32)); background.setStroke(dp(.5f), palette.outline); }
        brand.setOrientation(bottomSheet ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        brand.setGravity(bottomSheet ? Gravity.CENTER_HORIZONTAL : Gravity.CENTER_VERTICAL);
        badge.setLayoutParams(new LinearLayout.LayoutParams(dp(bottomSheet ? 48 : 36), dp(bottomSheet ? 48 : 36)));
        GradientDrawable badgeBackground = new GradientDrawable();
        badgeBackground.setColor(palette.badge); badgeBackground.setCornerRadius(dp(bottomSheet ? 16 : 12));
        badge.setBackground(badgeBackground); badgeIcon.setColorFilter(palette.action);
        LinearLayout.LayoutParams brandLayout = (LinearLayout.LayoutParams) brandName.getLayoutParams();
        brandLayout.leftMargin = bottomSheet ? 0 : dp(8); brandLayout.topMargin = bottomSheet ? dp(6) : 0;
        brandName.setLayoutParams(brandLayout); brandName.setTextColor(palette.secondary);
        heading.setText(bottomSheet ? "请验证指纹" : "验证您的身份"); heading.setTextColor(palette.foreground);
        LinearLayout.LayoutParams headingLayout = (LinearLayout.LayoutParams) heading.getLayoutParams();
        headingLayout.topMargin = dp(bottomSheet ? 16 : 12); heading.setLayoutParams(headingLayout);
        subtitle.setText(bottomSheet ? "验证后自动填写支付密码" : "使用指纹完成验证以继续支付");
        subtitle.setTextSize(bottomSheet ? 13 : 14); subtitle.setTextColor(palette.secondary);
        guidance.setText(bottomSheet ? "轻触下方指纹区域" : "请验证指纹以继续");
        guidance.setTextSize(bottomSheet ? 13 : 14); guidance.setTextColor(palette.foreground);
        cancel.setTextSize(bottomSheet ? 14 : 16); cancel.setTextColor(palette.action);
        fingerprintImage.setColorFilter(palette.action);
        GradientDrawable ring = new GradientDrawable(); ring.setShape(GradientDrawable.OVAL);
        ring.setColor(Color.TRANSPARENT); ring.setStroke(dp(2), palette.action); fingerprintImage.setBackground(ring);
        setBackground(background);
        setElevation(dp(8));
        GradientDrawable action = new GradientDrawable();
        action.setColor(bottomSheet ? Color.TRANSPARENT : palette.button);
        action.setCornerRadius(dp(24));
        cancel.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(palette.ripple), action, null));
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (placementPending) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY));
            return;
        }
        int width = View.MeasureSpec.getSize(widthMeasureSpec);
        if (width <= 0) width = getResources().getDisplayMetrics().widthPixels;
        boolean portrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        boolean sheet = bottomSheet;
        boolean tight = !sheet && View.MeasureSpec.getMode(heightMeasureSpec) != View.MeasureSpec.UNSPECIFIED
                && View.MeasureSpec.getSize(heightMeasureSpec) < dp(250);
        subtitle.setVisibility(tight ? GONE : VISIBLE);
        guidance.setVisibility(tight ? GONE : VISIBLE);
        int top = dp(sheet ? 32 : portrait ? 20 : 12);
        measureHeader(width);
        int headerHeight = header.getMeasuredHeight();
        int desired;
        if (sheet) {
            Rect visible = new Rect();
            if (hostDecor != null) hostDecor.getWindowVisibleDisplayFrame(visible);
            int bottom = dialogBottom > 0 ? dialogBottom
                    : visible.isEmpty() ? getResources().getDisplayMetrics().heightPixels : visible.bottom;
            desired = bottom - sensor.top + top + headerHeight + dp(24);
        } else if (portrait) desired = top + headerHeight + dp(tight ? 64 : 12 + 56 + 12 + 48 + 16);
        else desired = top + headerHeight + dp(12 + 48 + 12);
        int height = resolveSize(desired, heightMeasureSpec);
        FrameLayout.LayoutParams icon = (FrameLayout.LayoutParams) fingerprintImage.getLayoutParams();
        icon.topMargin = Math.max(0, Math.min(top + headerHeight + dp(12), height - dp(56)));
        fingerprintImage.setVisibility(sheet ? INVISIBLE : portrait && !tight ? VISIBLE : GONE);
        FrameLayout.LayoutParams headerLayout = (FrameLayout.LayoutParams) header.getLayoutParams();
        headerLayout.topMargin = top;
        FrameLayout.LayoutParams cancelLayout = (FrameLayout.LayoutParams) cancel.getLayoutParams();
        cancelLayout.height = dp(sheet ? 40 : 48);
        cancelLayout.width = sheet ? dp(72) : Math.max(dp(96), width - dp(48));
        cancelLayout.gravity = sheet ? Gravity.TOP | Gravity.RIGHT : Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        cancelLayout.topMargin = sheet ? dp(12) : 0;
        cancelLayout.rightMargin = sheet ? dp(16) : 0;
        cancelLayout.bottomMargin = sheet ? 0 : dp(16);
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (!bottomSheet || sensor == null) return;
        int[] location = new int[2];
        getLocationOnScreen(location);
        int actualBottom = location[1] + getHeight();
        if (actualBottom < sensor.bottom) {
            sensorLayoutReady = false;
            setVisibility(INVISIBLE);
            return;
        }
        if (dialogBottom != actualBottom) {
            dialogBottom = actualBottom;
            removeCallbacks(sheetRelayout);
            post(sheetRelayout);
        }
        int headerTop = sensor.top - location[1] - dp(24) - header.getMeasuredHeight();
        sensorLayoutReady = headerTop >= 0;
        if (sensorLayoutReady)
            header.layout(header.getLeft(), headerTop, header.getRight(), headerTop + header.getMeasuredHeight());
        setVisibility(sensorLayoutReady ? VISIBLE : INVISIBLE);
    }

    private void measureHeader(int width) {
        header.measure(View.MeasureSpec.makeMeasureSpec(Math.max(1, width), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
    }

    @Override
    public AlertDialog showInDialog() {
        AlertDialog dialog = super.showInDialog();
        dialog.setCancelable(false);
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.setOnKeyListener((dialog1, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                dialog1.dismiss();
                return true;
            }
            return false;
        });
        Activity activity = ActivityContext.find(getContext());
        if (activity != null) {
            hostDecor = activity.getWindow().getDecorView();
            hostDecor.addOnLayoutChangeListener(hostLayoutListener);
        }
        updateDialogSize();
        allowShadowOverflow();
        return dialog;
    }

    private void allowShadowOverflow() {
        AlertDialog dialog = getDialog();
        if (dialog == null || dialog.getWindow() == null) return;
        Window window = dialog.getWindow();
        window.setClipToOutline(false);
        window.setElevation(getElevation());
        View current = this;
        while (current instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) current;
            group.setClipChildren(false);
            group.setClipToPadding(false);
            if (current == window.getDecorView() || !(current.getParent() instanceof ViewGroup)) break;
            current = (ViewGroup) current.getParent();
        }
    }

    private void updateDialogSize() {
        AlertDialog dialog = getDialog();
        if (dialog == null || !dialog.isShowing() || dialog.getWindow() == null) return;
        Activity activity = ActivityContext.find(getContext());
        android.view.Display display = hostDecor == null ? com.surcumference.fingerprint.sensor.SensorLocation.display(getContext()) : hostDecor.getDisplay();
        boolean portrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        boolean windowed = activity != null && android.os.Build.VERSION.SDK_INT >= 24 && activity.isInMultiWindowMode();
        subtitle.setVisibility(VISIBLE);
        guidance.setVisibility(VISIBLE);
        Config config = Config.from(getContext());
        boolean automaticUnderDisplay = false;
        try {
            org.json.JSONObject settings = config.snapshot().getJSONObject("settings");
            automaticUnderDisplay = settings.optBoolean("fingerprint_icon_auto", false)
                    && !settings.optBoolean("fingerprint_icon_detected", false);
        } catch (Exception unavailable) { }
        boolean underDisplay = portrait && !windowed && (automaticUnderDisplay || config.isUnderDisplayFingerprint());
        if (requestedUnderDisplay != underDisplay) {
            requestedUnderDisplay = underDisplay;
            placementChosen = false;
            placementResult = null;
        }
        Resolution result = placementResult;
        if (underDisplay && !placementChosen)
            result = com.surcumference.fingerprint.sensor.SensorLocation.cached(getContext(), display, -1, sensorChanged);
        Rect candidate = underDisplay && placementChosen ? sensor : underDisplay
                ? com.surcumference.fingerprint.sensor.SensorLocation.bounds(result) : null;
        Rect visible = new Rect();
        if (hostDecor != null) hostDecor.getWindowVisibleDisplayFrame(visible);
        if (visible.isEmpty()) visible.set(0, 0, getResources().getDisplayMetrics().widthPixels,
                getResources().getDisplayMetrics().heightPixels);
        int width = hostDecor == null ? 0 : hostDecor.getWidth();
        if (width <= 0) width = DpUtils.dip2px(getContext(), getResources().getConfiguration().screenWidthDp);
        if (!placementChosen && candidate != null && !visible.contains(candidate)) candidate = null;
        placementPending = underDisplay && !placementChosen && result == null;
        if (!placementPending) {
            placementChosen = true;
            placementResult = result;
        }
        if (bottomSheet != (candidate != null) || sensor != null && !sensor.equals(candidate)) {
            dialogBottom = 0;
            sensorLayoutReady = false;
        }
        bottomSheet = candidate != null;
        String diagnostic = bottomSheet + ":" + (result == null ? "pending" : result.status) + ":" + candidate + ":" + visible;
        if (!diagnostic.equals(layoutDiagnostic)) {
            layoutDiagnostic = diagnostic;
            com.surcumference.fingerprint.util.log.L.d("Fingerprint prompt layout", getContext().getPackageName(), diagnostic);
        }
        sensor = candidate;
        setVisibility(placementPending || bottomSheet && !sensorLayoutReady ? INVISIBLE : VISIBLE);
        width = bottomSheet ? width : Math.min(width, dp(380));
        Window window = dialog.getWindow();
        window.setGravity(bottomSheet ? Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL : Gravity.CENTER);
        if (!bottomSheet) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
            window.setDimAmount(0);
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(.2f);
        }
        fingerprintImage.setVisibility(bottomSheet ? INVISIBLE : portrait ? VISIBLE : GONE);
        updateBackground(bottomSheet);
        if (width > 0 && window.getAttributes().width != width)
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        requestLayout();
        removeCallbacks(sensorChanged);
        if (placementPending)
            postDelayed(sensorChanged, 1000);
    }

    @Override protected View createDialogContentView(AlertDialog dialog) {
        return this;
    }

    @Override protected int dialogCornerRadius() { return DpUtils.dip2px(getContext(), 28); }

    @Override protected void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        placementChosen = false;
        placementResult = null;
        dialogBottom = 0;
        sensorLayoutReady = false;
        if (getDialog() != null && getDialog().getWindow() != null)
            getDialog().getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        updateDialogSize();
        allowShadowOverflow();
    }

    @Override
    public void onDismiss(DialogInterface dialogInterface) {
        if (hostDecor != null) hostDecor.removeOnLayoutChangeListener(hostLayoutListener);
        hostDecor = null;
        removeCallbacks(sensorChanged);
        removeCallbacks(sheetRelayout);
        com.surcumference.fingerprint.sensor.SensorLocation.forget(sensorChanged);
        super.onDismiss(dialogInterface);
    }

    public AlipayPayView withOnCancelButtonClickListener(OnCancelButtonClickListener listener) {
        cancelListener = listener;
        return this;
    }

    public interface OnCancelButtonClickListener {
        void onClicked(AlipayPayView target);
    }

    @Override
    public String getDialogTitle() {
        return Lang.getString(R.id.fingerprint_verification);
    }

    @Override
    public Rect dialogWindowInset() {
        return new Rect(0,0,0,0);
    }
}
