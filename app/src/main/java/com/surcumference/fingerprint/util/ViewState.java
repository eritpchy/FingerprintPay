package com.surcumference.fingerprint.util;

import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

import java.util.IdentityHashMap;
import java.util.Map;

public final class ViewState {
    private final Map<View, State> states = new IdentityHashMap<>();

    public void capture(View view) {
        if (view != null && !states.containsKey(view)) {
            states.put(view, new State(view));
        }
    }

    public void restore() {
        for (Map.Entry<View, State> entry : states.entrySet()) {
            entry.getValue().restore(entry.getKey());
        }
        states.clear();
    }

    private static final class State {
        final int visibility;
        final float alpha;
        final boolean clickable;
        final int height;
        final int top;
        final Drawable background;
        final boolean clipChildren;

        State(View view) {
            visibility = view.getVisibility();
            alpha = view.getAlpha();
            clickable = view.isClickable();
            height = view.getLayoutParams() == null ? 0 : view.getLayoutParams().height;
            top = view.getTop();
            background = view.getBackground();
            clipChildren = view instanceof ViewGroup
                    && (Build.VERSION.SDK_INT < 18 || ((ViewGroup) view).getClipChildren());
        }

        void restore(View view) {
            view.setVisibility(visibility);
            view.setAlpha(alpha);
            view.setClickable(clickable);
            view.setTop(top);
            view.setBackgroundDrawable(background);
            if (view.getLayoutParams() != null) {
                view.getLayoutParams().height = height;
                view.requestLayout();
            }
            if (view instanceof ViewGroup) {
                ((ViewGroup) view).setClipChildren(clipChildren);
            }
        }
    }
}
