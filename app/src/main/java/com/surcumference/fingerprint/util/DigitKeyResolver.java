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
        for (int i = 0; i < password.length(); i++) {
            if (password.charAt(i) < '0' || password.charAt(i) > '9') return null;
        }
        KeyboardIndex index = new KeyboardIndex();
        index.collect(keyboard, 0);
        View[] digits = new View[10];
        for (int digit = 0; digit < digits.length; digit++) {
            String expected = String.valueOf(digit);
            View idMatch = null;
            String[] ids = info.keys.get(expected);
            if (ids != null) {
                for (String name : ids) {
                    int id = keyboard.getResources().getIdentifier(name, "id", info.modulePackageName);
                    List<View> candidates = index.byId.get(id);
                    if (candidates == null) continue;
                    for (View candidate : candidates) {
                        String label = index.labels.get(candidate);
                        if (label != null && !expected.equals(label)) continue;
                        if (idMatch != null && idMatch != candidate) return null;
                        idMatch = candidate;
                    }
                }
            }
            List<View> labelled = index.byLabel.get(expected);
            if (labelled != null && labelled.size() > 1) return null;
            View labelMatch = labelled == null ? null : labelled.get(0);
            if (idMatch != null && labelMatch != null && idMatch != labelMatch) return null;
            digits[digit] = idMatch != null ? idMatch : labelMatch;
        }
        Map<View, Character> assigned = new IdentityHashMap<>();
        List<View> result = new ArrayList<>(password.length());
        for (int i = 0; i < password.length(); i++) {
            char digit = password.charAt(i);
            View key = digits[digit - '0'];
            if (key == null || (assigned.containsKey(key) && assigned.get(key) != digit)) return null;
            assigned.put(key, digit);
            result.add(key);
        }
        return result;
    }

    private static final class KeyboardIndex {
        final Map<Integer, List<View>> byId = new java.util.HashMap<>();
        final Map<String, List<View>> byLabel = new java.util.HashMap<>();
        final Map<View, String> labels = new IdentityHashMap<>();

        private static <K> void add(Map<K, List<View>> map, K key, View view) {
            List<View> views = map.get(key);
            if (views == null) { views = new ArrayList<>(); map.put(key, views); }
            views.add(view);
        }

        void collect(View view, int ancestorDigits) {
            if (!view.isShown() || view.getAlpha() <= 0) return;
            if (usable(view)) {
                String text = label(view);
                labels.put(view, text);
                if (view.getId() != View.NO_ID && view.getId() != 0)
                    add(byId, view.getId(), view);
                if (text != null && text.length() == 1 && text.charAt(0) >= '0' && text.charAt(0) <= '9') {
                    int bit = 1 << (text.charAt(0) - '0');
                    if ((ancestorDigits & bit) == 0)
                        add(byLabel, text, view);
                    ancestorDigits |= bit;
                }
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), ancestorDigits);
            }
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
