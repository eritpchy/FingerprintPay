package com.surcumference.fingerprint.view;

import android.content.Context;
import android.content.res.Configuration;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.surcumference.fingerprint.Lang;
import com.surcumference.fingerprint.R;
import com.surcumference.fingerprint.util.DpUtils;
import com.surcumference.fingerprint.util.StyleUtils;
import com.surcumference.fingerprint.util.UrlUtils;

/**
 * Created by Jason on 2017/9/9.
 */

public class UpdateInfoView extends DialogFrameLayout {

    private String mTitle;
    private TextView mContentTextView;

    public UpdateInfoView(@NonNull Context context) {
        super(context);
        initView(context);
    }

    private void initView(Context context) {

        TextView contentTextView = new TextView(context);
        contentTextView.setClickable(true);
        contentTextView.setAutoLinkMask(Linkify.WEB_URLS);
        contentTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, StyleUtils.TEXT_SIZE_SMALL);

        int defHPadding = DpUtils.dip2px(context, 20);
        contentTextView.setPadding(defHPadding, 0, defHPadding, 0);
        contentTextView.setMovementMethod(LinkMovementMethod.getInstance());
        this.addView(contentTextView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        withNeutralButtonText(Lang.getString(R.id.skip_this_version));
        withNegativeButtonText(Lang.getString(R.id.cancel));
        withPositiveButtonText(Lang.getString(R.id.update_now));

        mContentTextView = contentTextView;
        applyTheme();
    }

    private void applyTheme() {
        mContentTextView.setTextColor(StyleUtils.textColor(getContext()));
        mContentTextView.setLinkTextColor(StyleUtils.accentColor(getContext()));
        mContentTextView.setHighlightColor(StyleUtils.pressedColor(getContext()));
    }

    @Override protected void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        applyTheme();
    }

    public void setContent(String text) {
        text = text.replaceAll("(((https|http|ftp|rtsp|mms):\\/\\/)[^\\s]+)", "<a href=\"$1\">$1</a>");
        CharSequence span = getClickableHtml(text);
        mContentTextView.setText(span);
    }

    public void setTitle(String title) {
        mTitle = title;
    }

    @Override
    public String getDialogTitle() {
        return mTitle;
    }

    private void setLinkClickable(final SpannableStringBuilder clickableHtmlBuilder, final URLSpan urlSpan) {
        int start = clickableHtmlBuilder.getSpanStart(urlSpan);
        int end = clickableHtmlBuilder.getSpanEnd(urlSpan);
        int flags = clickableHtmlBuilder.getSpanFlags(urlSpan);
        ClickableSpan clickableSpan = new ClickableSpan() {
            public void onClick(View view) {
                UrlUtils.openUrl(view.getContext(), urlSpan.getURL());
            }
        };
        clickableHtmlBuilder.setSpan(clickableSpan, start, end, flags);
    }

    private CharSequence getClickableHtml(String html) {
        html = html.replace("\n", "<br/>");
        Spanned spannedHtml = Html.fromHtml(html);
        SpannableStringBuilder clickableHtmlBuilder = new SpannableStringBuilder(spannedHtml);
        URLSpan[] urls = clickableHtmlBuilder.getSpans(0, spannedHtml.length(), URLSpan.class);
        for(final URLSpan span : urls) {
            setLinkClickable(clickableHtmlBuilder, span);
        }
        return clickableHtmlBuilder;
    }
}
