package com.surcumference.fingerprint.view;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.surcumference.fingerprint.listener.OnDismissListener;
import com.surcumference.fingerprint.listener.OnShowListener;
import com.surcumference.fingerprint.util.DpUtils;
import com.surcumference.fingerprint.util.StyleUtils;
import com.surcumference.fingerprint.util.Umeng;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Jason on 2017/9/9.
 */

public abstract class DialogFrameLayout<T extends DialogFrameLayout> extends FrameLayout implements DialogInterface.OnDismissListener, DialogInterface.OnShowListener {

    private OnDismissListener mDismissListener;
    private OnShowListener<T> mShowListener;
    private DialogInterface.OnClickListener mOnNeutralButtonClickListener;
    private DialogInterface.OnClickListener mOnPositiveButtonClickListener;
    private String mNeutralButtonText;
    private String mNegativeButtonText;
    private String mPositiveButtonText;
    private TextView mTitleView;
    private final List<TextView> mActionViews = new ArrayList<>();
    @Nullable
    private AlertDialog mDialog;

    public DialogFrameLayout(@NonNull Context context) {
        super(context);
    }

    public AlertDialog showInDialog() {
        Context context = getContext();
        ContextThemeWrapper contextThemeWrapper = new ContextThemeWrapper(context, StyleUtils.isDarkMode(context)
                ? android.R.style.Theme_Material_NoActionBar_Fullscreen : android.R.style.Theme_Material_Light_NoActionBar_Fullscreen);
        //修复支付宝主页显示更新页面时dialog宽度不正常
        contextThemeWrapper.applyOverrideConfiguration(new Configuration());
        AlertDialog.Builder builder = new AlertDialog.Builder(contextThemeWrapper)
                .setOnDismissListener(this);
        AlertDialog dialog;
        dialog = builder.create();
        dialog.setView(createDialogContentView(dialog));
        dialog.setOnShowListener(this);
        Window window = dialog.getWindow();
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        window.setBackgroundDrawable(dialogWindowBackground());
        mDialog = dialog;
        dialog.show();
        Umeng.onResume(getContext());
        mDialog = dialog;
        return dialog;
    }

    @Override
    public void onDismiss(DialogInterface dialogInterface) {
        OnDismissListener listener = mDismissListener;
        if (listener != null) {
            listener.onDismiss(this);
        }
        Umeng.onPause(getContext());
    }

    @Override
    public void onShow(DialogInterface dialog) {
        OnShowListener<T> listener = mShowListener;
        if (listener != null) {
            listener.onShow((T)this);
        }
        Umeng.onResume(getContext());
    }

    public T withOnDismissListener(OnDismissListener listener) {
        mDismissListener = listener;
        return (T)this;
    }

    public T withOnShowListener(OnShowListener<T> listener) {
        mShowListener = listener;
        return (T)this;
    }

    public T withOnNeutralButtonClickListener(DialogInterface.OnClickListener listener) {
        mOnNeutralButtonClickListener = listener;
        return (T)this;
    }

    public T withOnPositiveButtonClickListener(DialogInterface.OnClickListener listener) {
        mOnPositiveButtonClickListener = listener;
        return (T)this;
    }

    public T withNeutralButtonText(String text) {
        mNeutralButtonText = text;
        return (T)this;
    }

    public T withNegativeButtonText(String text) {
        mNegativeButtonText = text;
        return (T)this;
    }

    public T withPositiveButtonText(String text) {
        mPositiveButtonText = text;
        return (T)this;
    }

    public String getDialogTitle() {
        return null;
    }

    @Nullable
    public AlertDialog getDialog() {
        return mDialog;
    }

    /**
     * @return pixel
     */
    public Rect dialogWindowInset() {
        int padding = DpUtils.dip2px(getContext(), 4);
        return new Rect(padding, padding, padding, padding);
    }

    protected int dialogCornerRadius() { return DpUtils.dip2px(getContext(), 10); }

    @Override protected void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        if (mTitleView != null) mTitleView.setTextColor(StyleUtils.textColor(getContext()));
        for (TextView action : mActionViews) styleAction(action);
        if (mDialog != null && mDialog.getWindow() != null) mDialog.getWindow().setBackgroundDrawable(dialogWindowBackground());
    }

    private void styleAction(TextView action) {
        action.setTextColor(StyleUtils.accentColor(getContext()));
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_pressed}, actionBackground(StyleUtils.pressedColor(getContext())));
        background.addState(new int[]{}, actionBackground(Color.TRANSPARENT));
        action.setBackground(background);
    }

    private Drawable actionBackground(int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(DpUtils.dip2px(getContext(), 8));
        shape.setColor(color);
        return shape;
    }

    private TextView createTitleTextView() {
        String title = getDialogTitle();
        Context context = getContext();
        TextView titleTextView = new TextView(context);
        mTitleView = titleTextView;
        titleTextView.setText(title);
        titleTextView.setTextColor(StyleUtils.textColor(context));
        titleTextView.setBackgroundColor(Color.TRANSPARENT);
        titleTextView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        titleTextView.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
        int defHPadding = DpUtils.dip2px(context, 15);
        titleTextView.setPadding(defHPadding,  DpUtils.dip2px(context, 15), defHPadding,  DpUtils.dip2px(context, 8));
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, StyleUtils.TEXT_SIZE_BIG);

        if (TextUtils.isEmpty(title)) {
            titleTextView.setVisibility(GONE);
        }
        return titleTextView;
    }

    private View createButton(AlertDialog dialog, String text, int which) {
        Context context = getContext();
        TextView textView = new TextView(context);
        mActionViews.add(textView);
        styleAction(textView);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, StyleUtils.TEXT_SIZE_DEFAULT);
        textView.setMinWidth(DpUtils.dip2px(context, 45));
        textView.setGravity(Gravity.CENTER);
        int paddingH = DpUtils.dip2px(context, 6);
        int paddingV = DpUtils.dip2px(context, 10);
        textView.setPadding(paddingH, paddingV, paddingH, paddingV);
        if (TextUtils.isEmpty(text)) {
            textView.setVisibility(GONE);
        } else {
            textView.setText(text);
            textView.setOnClickListener(v -> {
                DialogInterface.OnClickListener listener = which == DialogInterface.BUTTON_NEUTRAL
                        ? mOnNeutralButtonClickListener : which == DialogInterface.BUTTON_POSITIVE
                        ? mOnPositiveButtonClickListener : null;
                if (listener != null) listener.onClick(dialog, which);
                else dialog.dismiss();
            });
        }
        LinearLayout container = new LinearLayout(context);
        container.addView(textView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        container.setGravity(which == DialogInterface.BUTTON_NEUTRAL ? Gravity.LEFT : Gravity.RIGHT);
        return container;
    }

    private View createBtnAreaView(AlertDialog dialog) {
        Context context = getContext();
        LinearLayout btnLLayout = new LinearLayout(context);
        btnLLayout.setOrientation(LinearLayout.HORIZONTAL);
        btnLLayout.setWeightSum(1);

        View neutralButton = createButton(dialog, mNeutralButtonText, DialogInterface.BUTTON_NEUTRAL);
        View negativeButton = createButton(dialog, mNegativeButtonText, DialogInterface.BUTTON_NEGATIVE);
        View positiveButton = createButton(dialog, mPositiveButtonText, DialogInterface.BUTTON_POSITIVE);

        int defPadding = DpUtils.dip2px(context, 20);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        params.leftMargin = defPadding;
        params.rightMargin = defPadding;
        btnLLayout.addView(neutralButton, params);
        params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.rightMargin = defPadding;
        btnLLayout.addView(negativeButton, params);
        params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.rightMargin = defPadding;
        btnLLayout.addView(positiveButton, params);

        if (hasButton()) {
            btnLLayout.setPadding(0, DpUtils.dip2px(context, 10), 0, DpUtils.dip2px(context, 15));
        }
        return btnLLayout;
    }

    protected View createDialogContentView(AlertDialog dialog) {
        Context context = getContext();
        View titleView = createTitleTextView();
        View contentView = this;
        View btnView = createBtnAreaView(dialog);

        LinearLayout rootLLayout = new LinearLayout(context);
        rootLLayout.setOrientation(LinearLayout.VERTICAL);
        rootLLayout.setWeightSum(1);
        rootLLayout.setBackgroundColor(Color.TRANSPARENT);
        rootLLayout.addView(titleView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        rootLLayout.addView(contentView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        rootLLayout.addView(btnView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        rootLLayout.setElevation(DpUtils.dip2px(context, 2));
        return rootLLayout;
    }

    private boolean hasButton() {
        return (!TextUtils.isEmpty(mNegativeButtonText))
                || (!TextUtils.isEmpty(mNeutralButtonText))
                || (!TextUtils.isEmpty(mPositiveButtonText));
    }

    private Drawable dialogWindowBackground() {
        GradientDrawable shape =  new GradientDrawable();
        shape.setCornerRadius(dialogCornerRadius());
        shape.setColor(StyleUtils.dialogColor(getContext()));
        Rect inset = dialogWindowInset();
        return new InsetDrawable(shape, inset.left, inset.top, inset.right, inset.bottom);
    }

}
