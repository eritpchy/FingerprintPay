package com.surcumference.fingerprint.util;

import android.view.View;
import android.view.ViewTreeObserver;

public final class PaymentMethodObserver implements ViewTreeObserver.OnGlobalLayoutListener {
    private final View root;
    private final Runnable opened, returned;
    private boolean selecting, closed;

    public PaymentMethodObserver(View root, Runnable opened, Runnable returned) {
        this.root = root;
        this.opened = opened;
        this.returned = returned;
        root.getViewTreeObserver().addOnGlobalLayoutListener(this);
    }

    @Override public void onGlobalLayout() {
        if (closed || root.getWindowToken() == null) return;
        View title = ViewUtils.findViewByText(root, "选择付款方式", "选择支付方式", "Select payment method");
        boolean visible = title != null && title.isShown() && ViewUtils.isViewVisibleInScreen(title)
                && ViewUtils.isShownInScreen(title);
        if (selecting == visible) return;
        selecting = visible;
        if (visible) opened.run();
        else returned.run();
    }

    public void close() {
        closed = true;
        if (root.getViewTreeObserver().isAlive()) root.getViewTreeObserver().removeOnGlobalLayoutListener(this);
    }
}
