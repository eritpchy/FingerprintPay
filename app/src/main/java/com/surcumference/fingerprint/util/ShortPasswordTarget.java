package com.surcumference.fingerprint.util;

import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;

public final class ShortPasswordTarget {
    public static View find(View view) {
        if (!view.isShown() || !view.isEnabled() || view.getAlpha() <= 0) return null;
        // Flybird supplies this label through an accessibility delegate.
        if (view.isClickable() && ViewUtils.isViewVisibleInScreen(view)) {
            AccessibilityNodeInfo node = view.createAccessibilityNodeInfo();
            if (node != null) {
                try {
                    if (TextUtils.equals("密码共6位，已输入0位", node.getContentDescription())) return view;
                } finally { node.recycle(); }
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View target = find(group.getChildAt(i));
                if (target != null) return target;
            }
        }
        return null;
    }
    private ShortPasswordTarget() {}
}
