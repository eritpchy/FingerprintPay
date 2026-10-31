package com.surcumference.fingerprint.util;

import android.app.Activity;
import android.graphics.Rect;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Window;
import android.view.ViewTreeObserver;
import com.surcumference.fingerprint.bean.DigitPasswordKeyPadInfo;
import java.util.ArrayList;
import java.util.List;

public final class PaymentController extends PaymentSession {
    private final List<Task.Handle> pending = new ArrayList<>();
    private final ViewState state = new ViewState();
    private Runnable restore;
    private String credentialToken;
    private boolean requiresConfig;
    private XBiometricIdentify authentication;
    private View.OnAttachStateChangeListener watcher;
    private Window paymentWindow;
    private XWindowCallback windowCallback;
    private ViewTreeObserver layoutObserver;
    private ViewTreeObserver.OnGlobalLayoutListener visibilityWatcher;
    private View protectedKeyboard;
    private List<View> portraitKeyboards;
    private View digitRoot;
    private DigitPasswordKeyPadInfo digitKeys;
    private Runnable removeBackCallback;

    public static boolean isTargetReady(View target) {
        return target != null && target.getWindowToken() != null && target.isShown()
                && target.getRootView().getWindowVisibility() == View.VISIBLE;
    }
    @Override public boolean begin(Object owner, Object target, long now) {
        // Initial layout callbacks can run before the payment window is visible.
        return target instanceof View && isTargetReady((View) target) && super.begin(owner, target, now);
    }

    @Override public void restart(Object owner, Object target) {
        unwatch();
        clear();
        super.restart(owner, target);
        requiresConfig = owner instanceof Activity && com.surcumference.fingerprint.storage.ConfigContract.PACKAGES.contains(((Activity) owner).getPackageName());
        credentialToken = owner instanceof Activity ? Config.from((Activity) owner).getPasswordEncrypted() : null;
        if (target instanceof View) {
            watcher = new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View view) {}
                @Override public void onViewDetachedFromWindow(View view) { close(owner()); }
            };
            ((View) target).addOnAttachStateChangeListener(watcher);
        }
        long token = token();
        if (target instanceof View) PaymentPasswordGuard.watch((View) target, () -> {
            if (owns(token)) finish(token, SystemClock.uptimeMillis());
        });
        pending.add(Task.onMain(60000, () -> { if (owns(token)) finish(token, SystemClock.uptimeMillis()); }));
    }
    @Override public boolean isActive(long token) {
        Object owner = owner();
        Object target = target();
        if (requiresConfig && credentialToken == null) return false;
        if (credentialToken != null && owner instanceof Activity) {
            Config config = Config.from((Activity) owner);
            if (!config.isOn() || !credentialToken.equals(config.getPasswordEncrypted())) return false;
        }
        return super.isActive(token) && owner instanceof Activity && ActivityContext.isAlive((Activity) owner)
                && target instanceof View && ((View) target).getWindowToken() != null
                && ((View) target).getRootView().isShown()
                && ((View) target).getRootView().getWindowVisibility() == View.VISIBLE;
    }
    public void capture(View... views) { for (View view : views) state.capture(view); }
    public void bindToPaymentWindow(long token, Window window, boolean monitorVolume, Runnable suspend) {
        if (!isActive(token)) return;
        unbindWindow();
        View target = (View) target();
        window = targetWindow(target, window);
        paymentWindow = window;
        windowCallback = new XWindowCallback(window.getCallback()) {
            private boolean blockedGesture;
            private boolean canceledBack;
            @Override public boolean dispatchTouchEvent(MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    boolean active = isActive(token);
                    blockedGesture = active && keyboardContains(event.getRawX(), event.getRawY());
                    if (!blockedGesture && (active || owns(token))) finish(token, SystemClock.uptimeMillis());
                }
                if (blockedGesture) {
                    if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL)
                        blockedGesture = false;
                    return true;
                }
                return super.dispatchTouchEvent(event);
            }
            @Override public boolean dispatchKeyEvent(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && canceledBack) {
                    if (event.getAction() == KeyEvent.ACTION_UP) {
                        canceledBack = false;
                        if (owns(token)) finish(token, SystemClock.uptimeMillis());
                    }
                    return true;
                }
                if (isActive(token) && event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN && monitorVolume) {
                        finish(token, SystemClock.uptimeMillis());
                        suspend.run();
                        return true;
                    }
                    if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                        canceledBack = true;
                        return true;
                    }
                    if (event.getUnicodeChar() != 0)
                        finish(token, SystemClock.uptimeMillis());
                }
                return super.dispatchKeyEvent(event);
            }
        };
        window.setCallback(windowCallback);
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                removeBackCallback = BackNavigation.register(window, () -> {
                    if (owns(token)) finish(token, SystemClock.uptimeMillis());
                });
            } catch (RuntimeException ignored) {}
        }
        layoutObserver = target.getViewTreeObserver();
        visibilityWatcher = () -> {
            if (owns(token) && (!target.isShown() || target.getWindowVisibility() != View.VISIBLE))
                finish(token, SystemClock.uptimeMillis());
        };
        layoutObserver.addOnGlobalLayoutListener(visibilityWatcher);
    }
    public void protectKeyboard(long token, View keyboard) {
        if (!isActive(token)) return;
        portraitKeyboards = null;
        protectedKeyboard = keyboard;
        digitRoot = null;
        digitKeys = null;
    }
    public void protectDigits(long token, View root, DigitPasswordKeyPadInfo keys) {
        if (!isActive(token)) return;
        protectedKeyboard = null;
        portraitKeyboards = null;
        digitRoot = root;
        digitKeys = keys;
    }
    public void protectPortraitKeyboards(long token, List<View> keyboards) {
        if (!isActive(token)) return;
        protectedKeyboard = null;
        digitRoot = null;
        digitKeys = null;
        portraitKeyboards = new ArrayList<>(keyboards);
    }
    private boolean keyboardContains(float x, float y) {
        if (((View) target()).getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE) return false;
        if (Build.VERSION.SDK_INT >= 24 && owner() instanceof Activity
                && ((Activity) owner()).isInMultiWindowMode()) return false;
        if (portraitKeyboards != null) {
            if (((View) target()).getResources().getConfiguration().orientation
                    != android.content.res.Configuration.ORIENTATION_PORTRAIT) return false;
            for (View keyboard : portraitKeyboards) {
                Rect bounds = visibleBounds(keyboard);
                if (bounds != null && bounds.contains((int) x, (int) y)) return true;
            }
            return false;
        }
        List<View> views = protectedKeyboard == null
                ? DigitKeyResolver.resolve(digitRoot, digitKeys, "0123456789")
                : java.util.Collections.singletonList(protectedKeyboard);
        if (views == null) return false;
        // Digit bounds cover the keypad rows without inheriting a payment sheet's height.
        Rect area = new Rect();
        for (View view : views) {
            Rect bounds = visibleBounds(view);
            if (bounds == null) return false;
            area.union(bounds);
        }
        return area.contains((int) x, (int) y);
    }
    private static Rect visibleBounds(View view) {
        if (!view.isShown() || view.getWindowToken() == null) return null;
        for (View current = view; current != null;
                current = current.getParent() instanceof View ? (View) current.getParent() : null) {
            if (current.getAlpha() <= 0) return null;
        }
        Rect bounds = new Rect();
        if (!view.getGlobalVisibleRect(bounds)) return null;
        int[] screen = new int[2], window = new int[2];
        view.getLocationOnScreen(screen);
        view.getLocationInWindow(window);
        bounds.offset(screen[0] - window[0], screen[1] - window[1]);
        return bounds;
    }
    @android.annotation.TargetApi(33)
    private static final class BackNavigation {
        static Runnable register(Window window, Runnable cancel) {
            android.window.OnBackInvokedDispatcher dispatcher = window.getOnBackInvokedDispatcher();
            if (dispatcher == null) return null;
            android.window.OnBackInvokedCallback callback = cancel::run;
            dispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_OVERLAY, callback);
            return () -> {
                try { dispatcher.unregisterOnBackInvokedCallback(callback); }
                catch (RuntimeException ignored) {}
            };
        }
    }
    private static Window targetWindow(View target, Window fallback) {
        View root = target.getRootView();
        if (root == fallback.getDecorView()) return fallback;
        for (Class<?> type = root.getClass(); type != null; type = type.getSuperclass()) {
            for (String name : new String[]{"mWindow", "this$0"}) {
                try {
                    java.lang.reflect.Field field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    Object value = field.get(root);
                    if (value instanceof Window) return (Window) value;
                } catch (ReflectiveOperationException | RuntimeException ignored) {}
            }
        }
        return fallback;
    }
    public void showFingerprintPrompt(long token, Activity activity) {
        if (!isActive(token) || Config.from(activity).isHideFingerprintDialog()) return;
        com.surcumference.fingerprint.view.AlipayPayView view = new com.surcumference.fingerprint.view.AlipayPayView(activity)
                .withOnCancelButtonClickListener(shown -> finish(token, SystemClock.uptimeMillis()))
                .withOnDismissListener(shown -> { if (isActive(token)) finish(token, SystemClock.uptimeMillis()); });
        android.app.AlertDialog dialog = view.showInDialog();
        dialog.setOnKeyListener((ignored, code, event) -> {
            if (code == KeyEvent.KEYCODE_VOLUME_DOWN && windowCallback != null)
                return windowCallback.dispatchKeyEvent(event);
            if (code == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == KeyEvent.ACTION_UP) finish(token, SystemClock.uptimeMillis());
                return true;
            }
            return false;
        });
        Runnable previous = restore;
        restoreWith(token, () -> {
            if (previous != null) previous.run();
            com.surcumference.fingerprint.view.DialogUtils.dismiss(dialog);
        });
    }

    public void restoreWith(long token, Runnable runnable) { if (owns(token)) restore = runnable; }
    public void authentication(long token, XBiometricIdentify identify) {
        if (isActive(token)) authentication = identify;
        else if (identify != null) identify.cancelIdentify();
    }
    public Task.Handle later(long token, long delay, Runnable runnable) {
        Task.Handle handle = Task.onMain(delay, () -> {
            if (isActive(token)) runnable.run();
            else if (owns(token)) finish(token, SystemClock.uptimeMillis());
        });
        pending.add(handle);
        return handle;
    }
    public boolean inputDigits(long token, View keyboard, DigitPasswordKeyPadInfo info,
                               String password, Runnable complete) {
        List<View> keys = DigitKeyResolver.resolve(keyboard, info, password);
        if (keys == null || !isActive(token)) return false;
        for (int i = 0; i < keys.size(); i++) {
            View key = keys.get(i);
            later(token, i * 70L, () -> {
                try {
                    if (!key.isShown() || !key.isEnabled() || key.getWindowToken() == null) {
                        finish(token, SystemClock.uptimeMillis());
                        return;
                    }
                    if (!ViewUtils.performActionClick(key)) finish(token, SystemClock.uptimeMillis());
                } catch (Exception error) { finish(token, SystemClock.uptimeMillis()); }
            });
        }
        later(token, keys.size() * 70L + 100, complete);
        return true;
    }
    @Override public boolean finish(long token, long now) {
        // Finish still needs to restore a session whose window has just disappeared.
        if (!super.owns(token)) return false;
        boolean result = super.finish(token, now);
        clear();
        return result;
    }
    @Override public void close(Object owner) {
        if (owner() != owner) return;
        unwatch();
        clear();
        super.close(owner);
    }
    private void unwatch() {
        Object target = target();
        if (target instanceof View && watcher != null) ((View) target).removeOnAttachStateChangeListener(watcher);
        watcher = null;
    }
    private void clear() {
        unbindWindow();
        for (Task.Handle task : pending) task.cancel();
        pending.clear();
        XBiometricIdentify identify = authentication;
        authentication = null;
        Runnable callback = restore;
        restore = null;
        if (identify != null) identify.cancelIdentify();
        state.restore();
        if (callback != null) callback.run();
    }
    private void unbindWindow() {
        if (removeBackCallback != null) {
            Runnable remove = removeBackCallback;
            removeBackCallback = null;
            remove.run();
        }
        if (paymentWindow != null && paymentWindow.getCallback() == windowCallback)
            paymentWindow.setCallback(windowCallback.getOriginalCallback());
        if (layoutObserver != null && layoutObserver.isAlive() && visibilityWatcher != null)
            layoutObserver.removeOnGlobalLayoutListener(visibilityWatcher);
        paymentWindow = null;
        windowCallback = null;
        layoutObserver = null;
        visibilityWatcher = null;
        portraitKeyboards = null;
        protectedKeyboard = null;
        digitRoot = null;
        digitKeys = null;
    }
}
