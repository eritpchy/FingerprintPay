package com.surcumference.fingerprint.plugin.impl.wechat;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import com.surcumference.fingerprint.util.log.L;

final class WeChatPaymentDimmer implements AutoCloseable {
    static final int FADE_MS = 120;
    static final int FEEDBACK_TAIL_MS = 80;
    static final float DIM_ALPHA = .20f;
    interface Layer { void setAlpha(float alpha); void close(); }
    interface Factory { Layer create(View target); }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Factory factory;
    private Layer layer;
    private View target;
    private float alpha;
    private boolean completed;
    private Runnable animation, expiry;
    private final View.OnAttachStateChangeListener watcher = new View.OnAttachStateChangeListener() {
        @Override public void onViewAttachedToWindow(View view) {}
        @Override public void onViewDetachedFromWindow(View view) { close(); }
    };

    WeChatPaymentDimmer() { this(WindowLayer::new); }
    WeChatPaymentDimmer(Factory factory) { this.factory = factory; }

    void show(View target) {
        close();
        if (!visible(target)) return;
        try {
            layer = factory.create(target);
            this.target = target;
            target.addOnAttachStateChangeListener(watcher);
            expiry = this::close;
            handler.postDelayed(expiry, 60000);
            fadeTo(DIM_ALPHA, null);
        } catch (RuntimeException error) {
            close();
            L.e("WeChat payment dim unavailable", error);
        }
    }

    void completed() {
        if (layer == null || completed) return;
        completed = true;
        handler.removeCallbacks(expiry);
        expiry = () -> fadeTo(0, this::close);
        handler.postDelayed(expiry, FEEDBACK_TAIL_MS);
    }

    void release() {
        if (!visible(target)) close();
        else if (!completed) {
            completed = true;
            handler.removeCallbacks(expiry);
            fadeTo(0, this::close);
        }
    }

    private void fadeTo(float to, Runnable finished) {
        if (animation != null) handler.removeCallbacks(animation);
        Layer expected = layer;
        float from = alpha;
        long started = SystemClock.uptimeMillis();
        animation = new Runnable() {
            @Override public void run() {
                if (layer != expected || expected == null) return;
                if (!visible(target)) { close(); return; }
                long elapsed = SystemClock.uptimeMillis() - started;
                float progress = Math.min(1f, elapsed / (float) FADE_MS);
                float eased = progress * progress * (3 - 2 * progress);
                try { expected.setAlpha(alpha = from + (to - from) * eased); }
                catch (RuntimeException error) { close(); return; }
                if (progress >= 1) {
                    animation = null;
                    if (finished != null) finished.run();
                } else handler.postDelayed(this, Math.min(16, FADE_MS - elapsed));
            }
        };
        handler.post(animation);
    }

    private static boolean visible(View target) {
        return target != null && target.getWindowToken() != null && target.isShown()
                && target.getWindowVisibility() == View.VISIBLE;
    }

    @Override public void close() {
        if (animation != null) handler.removeCallbacks(animation);
        if (expiry != null) handler.removeCallbacks(expiry);
        if (target != null) target.removeOnAttachStateChangeListener(watcher);
        Layer previous = layer;
        layer = null; target = null; animation = expiry = null; alpha = 0; completed = false;
        if (previous != null) previous.close();
    }

    private static final class WindowLayer implements Layer {
        private final WindowManager manager;
        private final View view;
        private final WindowManager.LayoutParams params;

        WindowLayer(View target) {
            Context context = target.getContext();
            manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            view = new View(context);
            params = new WindowManager.LayoutParams(1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                    WindowManager.LayoutParams.FLAG_DIM_BEHIND | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    android.graphics.PixelFormat.TRANSLUCENT);
            params.token = target.getWindowToken();
            params.gravity = Gravity.TOP | Gravity.LEFT;
            params.dimAmount = 0;
            params.setTitle("FingerprintPay - Payment dim");
            manager.addView(view, params);
        }
        @Override public void setAlpha(float alpha) {
            params.dimAmount = alpha;
            manager.updateViewLayout(view, params);
        }
        @Override public void close() {
            try { manager.removeViewImmediate(view); }
            catch (RuntimeException error) { L.e("WeChat payment dim removal failed", error); }
        }
    }
}
