package com.surcumference.fingerprint.view;

import android.app.AlertDialog;
import android.content.Context;
import android.text.TextUtils;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.surcumference.fingerprint.Lang;
import com.surcumference.fingerprint.R;
import com.surcumference.fingerprint.util.DpUtils;
import com.surcumference.fingerprint.util.StyleUtils;

/**
 * Created by Jason on 2021/8/29.
 */
public class MessageView extends DialogFrameLayout {

    private TextView mMessageText;
    private String mTitle;

    public MessageView(@NonNull Context context) {
        super(context);
        init(context);
    }

    private void init(Context context) {
        mMessageText = new TextView(context);
        int paddingH = DpUtils.dip2px(context, 20);
        mMessageText.setPadding(paddingH, DpUtils.dip2px(context, 5), paddingH, 0);
        StyleUtils.apply(mMessageText);
        this.addView(mMessageText);
        withPositiveButtonText(Lang.getString(R.id.ok));
    }

    public MessageView text(CharSequence s) {
        mMessageText.setText(s);
        return this;
    }

    public MessageView title(String s) {
        mTitle = s;
        return this;
    }

    @Override
    public String getDialogTitle() {
        String title = mTitle;
        return TextUtils.isEmpty(title) ? Lang.getString(R.string.app_name) : title;
    }

    @Override
    public AlertDialog showInDialog() {
        AlertDialog dialog = super.showInDialog();
        dialog.setCancelable(false);
        return dialog;
    }
}
