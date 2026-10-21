package com.surcumference.fingerprint.util;

import android.os.Handler;
import android.os.Looper;
import com.surcumference.fingerprint.util.log.L;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by Jason on 2017/9/10.
 */
public final class Task {
    private Task() {}

    private static class Main {
        static final Handler HANDLER = new Handler(Looper.getMainLooper());
    }

    private static class Background {
        static final ScheduledThreadPoolExecutor TIMER = new ScheduledThreadPoolExecutor(1,
                runnable -> new Thread(runnable, "FingerprintPay-timer"));
        static final ExecutorService WORKERS = Executors.newFixedThreadPool(2,
                runnable -> new Thread(runnable, "FingerprintPay-worker"));
        static { TIMER.setRemoveOnCancelPolicy(true); }
    }

    public static final class Handle {
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private volatile Runnable main;
        private volatile Future<?> future;
        public void cancel() {
            cancelled.set(true);
            Runnable callback = main;
            if (callback != null) Main.HANDLER.removeCallbacks(callback);
            Future<?> pending = future;
            if (pending != null) pending.cancel(false);
        }
        public boolean isCancelled() { return cancelled.get(); }
        private synchronized void future(Future<?> value) {
            future = value;
            if (isCancelled()) value.cancel(false);
        }
        private Runnable guard(Runnable runnable) {
            return () -> {
                if (isCancelled()) return;
                try { runnable.run(); } catch (Exception e) { L.e(e); }
            };
        }
    }

    public static void runOnMain(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) runnable.run();
        else onMain(runnable);
    }

    public static Handle onMain(Runnable runnable) { return onMain(0, runnable); }
    public static Handle onMain(long delay, Runnable runnable) {
        Handle handle = new Handle();
        handle.main = handle.guard(runnable);
        Main.HANDLER.postDelayed(handle.main, Math.max(0, delay));
        return handle;
    }

    public static Handle onBackground(Runnable runnable) { return onBackground(0, runnable); }
    public static Handle onBackground(long delay, Runnable runnable) {
        Handle handle = new Handle();
        if (delay <= 0) handle.future(Background.WORKERS.submit(handle.guard(runnable)));
        else handle.future(Background.TIMER.schedule(() -> {
            if (!handle.isCancelled()) handle.future(Background.WORKERS.submit(handle.guard(runnable)));
        }, delay, TimeUnit.MILLISECONDS));
        return handle;
    }

    public static Handle onApplicationReady(Runnable runnable) {
        Handle handle = new Handle();
        Runnable poll = new Runnable() {
            @Override public void run() {
                if (handle.isCancelled()) return;
                if (ApplicationUtils.getApplication() == null || Looper.getMainLooper() == null) {
                    handle.future(Background.TIMER.schedule(this, 200, TimeUnit.MILLISECONDS));
                    return;
                }
                handle.main = handle.guard(runnable);
                Main.HANDLER.post(handle.main);
                if (handle.isCancelled()) Main.HANDLER.removeCallbacks(handle.main);
            }
        };
        handle.future(Background.TIMER.schedule(poll, 0, TimeUnit.MILLISECONDS));
        return handle;
    }
}
