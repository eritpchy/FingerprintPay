package com.surcumference.fingerprint.view;

import static com.surcumference.fingerprint.Constant.ICON_FINGER_PRINT_ALIPAY_BASE64;

import android.app.AlertDialog;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;

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
    private View hostDecor;
    private final View.OnLayoutChangeListener hostLayoutListener =
            (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> updateDialogSize();

    public AlipayPayView(@NonNull Context context) {
        super(context);
        init(context);
    }

    private void init(Context context) {
        fingerprintImage = new ImageView(context);
        fingerprintImage.setImageBitmap(ImageUtils.base64ToBitmap(ICON_FINGER_PRINT_ALIPAY_BASE64));
        fingerprintImage.setVisibility(Config.from(context).isShowFingerprintIcon() ? VISIBLE: INVISIBLE);
        int size = DpUtils.dip2px(context, 60);
        addView(fingerprintImage, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        withNeutralButtonText(Lang.getString(R.id.cancel));
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int height = resolveSize(DpUtils.dip2px(getContext(), 240), heightMeasureSpec);
        int size = Math.min(DpUtils.dip2px(getContext(), 60), height);
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED)
            size = Math.min(size, MeasureSpec.getSize(widthMeasureSpec));
        ViewGroup.LayoutParams icon = fingerprintImage.getLayoutParams();
        icon.width = icon.height = size;
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    @Override
    public AlertDialog showInDialog() {
        AlertDialog dialog = super.showInDialog();
        dialog.setCancelable(false);
        dialog.setOnKeyListener((dialog1, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                dialog1.dismiss();
                return true;
            }
            return false;
        });
        Window window = dialog.getWindow();
        if (window != null) {
            WindowManager.LayoutParams layoutParams = window.getAttributes();
            layoutParams.gravity = Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL;
            window.setAttributes(layoutParams);
        }
        Activity activity = ActivityContext.find(getContext());
        if (activity != null) {
            hostDecor = activity.getWindow().getDecorView();
            hostDecor.addOnLayoutChangeListener(hostLayoutListener);
        }
        updateDialogSize();
        return dialog;
    }

    private void updateDialogSize() {
        AlertDialog dialog = getDialog();
        if (dialog == null || dialog.getWindow() == null) return;
        int width = hostDecor == null ? 0 : hostDecor.getWidth();
        if (width <= 0) width = DpUtils.dip2px(getContext(), getResources().getConfiguration().screenWidthDp);
        width = Math.min(width, DpUtils.dip2px(getContext(), 480));
        Window window = dialog.getWindow();
        if (width > 0 && window.getAttributes().width != width)
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        requestLayout();
    }

    @Override protected void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        updateDialogSize();
    }

    @Override
    public void onDismiss(DialogInterface dialogInterface) {
        if (hostDecor != null) hostDecor.removeOnLayoutChangeListener(hostLayoutListener);
        hostDecor = null;
        super.onDismiss(dialogInterface);
    }

    public AlipayPayView withOnCancelButtonClickListener(OnCancelButtonClickListener listener) {
        withOnNeutralButtonClickListener((dialog, which) -> listener.onClicked(AlipayPayView.this));
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
