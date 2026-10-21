package com.surcumference.fingerprint.util;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.surcumference.fingerprint.bean.DigitPasswordKeyPadInfo;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class DigitKeyResolver {
    private DigitKeyResolver() {}

    public static List<View> resolve(View keyboard, DigitPasswordKeyPadInfo info, String password) {
        if (keyboard == null || info == null || !keyboard.isShown() || keyboard.getAlpha() <= 0
                || password == null || password.isEmpty()) return null;
        Map<View, Character> assigned = new IdentityHashMap<>();
        List<View> result = new ArrayList<>();
        for (int i = 0; i < password.length(); i++) {
            char digit = password.charAt(i);
            if (digit < '0' || digit > '9') return null;
            View key = find(keyboard, info, digit);
            if (key == null || (assigned.containsKey(key) && assigned.get(key) != digit)) return null;
            assigned.put(key, digit);
            result.add(key);
        }
        return result;
    }

    private static View find(View keyboard, DigitPasswordKeyPadInfo info, char digit) {
        String expected = String.valueOf(digit);
        String[] ids = info.keys.get(expected);
        View idMatch = null;
        if (ids != null) {
            for (String id : ids) {
                View candidate = ViewUtils.findViewByName(keyboard, info.modulePackageName, id);
                if (!usable(candidate)) continue;
                String label = label(candidate);
                if (label == null || expected.equals(label)) {
                    if (idMatch != null && idMatch != candidate) return null;
                    idMatch = candidate;
                }
            }
        }
        List<View> labelled = new ArrayList<>();
        collect(keyboard, expected, labelled);
        if (labelled.size() > 1) return null;
        if (idMatch != null) {
            return labelled.isEmpty() || labelled.get(0) == idMatch ? idMatch : null;
        }
        return labelled.size() == 1 ? labelled.get(0) : null;
    }

    private static void collect(View view, String expected, List<View> result) {
        if (!view.isShown()) return;
        if (usable(view) && expected.equals(label(view))) {
            result.add(view);
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), expected, result);
        }
    }

    private static boolean usable(View view) {
        return view != null && view.isShown() && view.isEnabled() && view.getAlpha() > 0;
    }

    private static String label(View view) {
        CharSequence text = view instanceof TextView ? ((TextView) view).getText() : view.getContentDescription();
        if (text == null && !(view instanceof TextView)) {
            for (Class<?> type = view.getClass(); type != null && type != View.class; type = type.getSuperclass()) {
                try {
                    java.lang.reflect.Field field = type.getDeclaredField("mText");
                    field.setAccessible(true);
                    Object value = field.get(view);
                    if (value instanceof CharSequence) text = (CharSequence) value;
                    break;
                } catch (Exception ignored) {}
            }
        }
        if (text == null || text.toString().trim().isEmpty()) return null;
        return text.toString().trim();
    }
}
