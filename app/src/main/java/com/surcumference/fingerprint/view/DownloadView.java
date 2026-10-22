package com.surcumference.fingerprint.view;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.view.ContextThemeWrapper;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.surcumference.fingerprint.network.DownloadCoordinator;
import com.surcumference.fingerprint.Lang;
import com.surcumference.fingerprint.R;
import com.surcumference.fingerprint.util.DpUtils;
import com.surcumference.fingerprint.util.StyleUtils;
import com.surcumference.fingerprint.util.log.L;

import java.io.File;

/**
 * Created by Jason on 2021/8/28.
 */
public class DownloadView extends DialogFrameLayout {

    private ProgressBar mProgressBar;
    private TextView mProgressText;
    private DownloadCoordinator mDownload;

    public DownloadView(@NonNull Context context) {
        super(context);
        init(context);
    }

    private void init(Context context) {
        try {
            LinearLayout rootLinearLayout = new LinearLayout(context);
            rootLinearLayout.setOrientation(LinearLayout.VERTICAL);
            mProgressBar = new ProgressBar(new ContextThemeWrapper(context, android.R.style.Theme_Material_NoActionBar_Fullscreen),
                    null, android.R.attr.progressBarStyleHorizontal);
            int paddingH = DpUtils.dip2px(context, 20);
            rootLinearLayout.addView(mProgressBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, DpUtils.dip2px(context, 4)));

            mProgressText = new TextView(context);
            StyleUtils.apply(mProgressText);
            mProgressText.setText("0%");
            applyTheme();
            mProgressText.setPadding(0, DpUtils.dip2px(context, 5), 0,0);
            mProgressText.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            rootLinearLayout.addView(mProgressText);

            LayoutParams rootParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rootParams.leftMargin = paddingH;
            rootParams.rightMargin = paddingH;
            rootParams.topMargin = DpUtils.dip2px(context, 20);
            rootParams.bottomMargin = 0;
            this.addView(rootLinearLayout, rootParams);
        } catch (Exception e) {
            L.e(e);
        }

        withPositiveButtonText(Lang.getString(R.id.cancel));
    }

    public DownloadView download(String[] urls, File targetFile, long expectedSize,
                                 @Nullable String digest, @Nullable Runnable success) {
        if (mDownload != null) mDownload.cancel();
        mDownload = new DownloadCoordinator(getContext(), urls, targetFile, expectedSize, digest,
                new DownloadCoordinator.Listener() {
                    @Override public void progress(int percent, long bytes, long total) {
                        mProgressBar.setIndeterminate(percent < 0);
                        if (percent >= 0) mProgressBar.setProgress(percent);
                        mProgressText.setText(percent < 0 ? bytes / 1024 + " KiB" : percent + "%");
                    }
                    @Override public void success() {
                        if (success != null) success.run();
                        dismiss();
                    }
                    @Override public void failure(Throwable error) {
                        L.e(error);
                        new MessageView(getContext()).title(Lang.getString(R.id.download_title_failed))
                                .text(String.valueOf(error.getMessage())).showInDialog();
                        dismiss();
                    }
                });
        return this;
    }

    private void dismiss() {
        AlertDialog dialog = getDialog();
        if (dialog == null) {
           return;
        }
        if (dialog.isShowing()) {
            dialog.dismiss();
        }
    }

    private void applyTheme() {
        mProgressText.setTextColor(StyleUtils.secondaryTextColor(getContext()));
        ColorStateList accent = ColorStateList.valueOf(StyleUtils.accentColor(getContext()));
        mProgressBar.setProgressTintList(accent);
        mProgressBar.setIndeterminateTintList(accent);
        mProgressBar.setProgressBackgroundTintList(ColorStateList.valueOf(StyleUtils.pressedColor(getContext())));
    }

    @Override protected void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        applyTheme();
    }

    @Override
    public String getDialogTitle() {
        return Lang.getString(R.id.downloading);
    }

    @Override
    public AlertDialog showInDialog() {
        AlertDialog dialog = super.showInDialog();
        dialog.setCancelable(false);
        if (mDownload != null) mDownload.start();
        return dialog;
    }

    @Override
    public void onDismiss(DialogInterface dialogInterface) {
        super.onDismiss(dialogInterface);
        if (mDownload != null) mDownload.cancel();
        mDownload = null;
    }

}
