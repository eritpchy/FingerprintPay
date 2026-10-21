package com.surcumference.fingerprint.network;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import com.hjq.http.EasyHttp;
import com.hjq.http.config.IRequestHandler;
import com.hjq.http.config.IRequestInterceptor;
import com.hjq.http.listener.OnDownloadListener;
import com.hjq.http.request.DownloadRequest;
import com.hjq.http.request.HttpRequest;
import com.surcumference.fingerprint.util.ActivityContext;
import com.surcumference.fingerprint.util.Task;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.security.MessageDigest;
import java.util.Locale;
import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.ForwardingSource;
import okio.Okio;

public final class DownloadCoordinator implements LifecycleOwner, Application.ActivityLifecycleCallbacks {
    public interface Listener {
        void progress(int percent, long bytes, long total);
        void success();
        void failure(Throwable error);
    }
    private final LifecycleRegistry lifecycle = new LifecycleRegistry(this);
    private final Activity activity;
    private final Application application;
    private final String[] urls;
    private final File target;
    private final long size;
    private final String digest;
    private Listener listener;
    private boolean cancelled;
    private boolean terminal;
    private boolean registered;
    private long generation;
    private long lastProgress;
    private int index;
    private DownloadRequest request;
    private Gate gate;
    private Task.Handle pending;

    public DownloadCoordinator(Context context, String[] urls, File target, long size,
                               String digest, Listener listener) {
        activity = ActivityContext.find(context);
        application = activity == null ? null : activity.getApplication();
        this.urls = urls.clone();
        this.target = target.getAbsoluteFile();
        this.size = size;
        this.digest = digest;
        this.listener = listener;
    }
    @Override public Lifecycle getLifecycle() { return lifecycle; }
    public void start() {
        Task.runOnMain(() -> {
            if (cancelled || terminal || registered) return;
            if (!ActivityContext.isAlive(activity)) { fail(new IOException("Download requires a live Activity")); return; }
            try { ModuleNetwork.client(); }
            catch (Exception error) { fail(error); return; }
            application.registerActivityLifecycleCallbacks(this);
            registered = true;
            lifecycle.setCurrentState(Lifecycle.State.STARTED);
            next(null);
        });
    }
    public void cancel() {
        Task.runOnMain(() -> {
            if (terminal || cancelled) return;
            cancelled = true;
            terminal = true;
            ++generation;
            if (pending != null) pending.cancel();
            if (gate != null) gate.cancel();
            if (request != null) request.stop();
            release();
        });
    }
    private boolean active(long token) { return !cancelled && !terminal && token == generation; }
    private void next(Throwable previous) {
        if (cancelled || terminal) return;
        if (index >= urls.length) { fail(previous == null ? new IOException("No download URL") : previous); return; }
        long token = ++generation;
        String url = urls[index++];
        pending = Task.onBackground(() -> {
            try {
                okhttp3.HttpUrl.get(url);
                File directory = target.getParentFile();
                if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create download directory");
                File temporary = File.createTempFile("fingerprintpay-", ".part", directory);
                Task.onMain(() -> {
                    if (!active(token)) { cleanup(temporary); return; }
                    Gate attempt = new Gate(temporary);
                    gate = attempt;
                    lastProgress = 0;
                    request = EasyHttp.download(this).url(url).file(temporary).resumableTransfer(false)
                            .interceptor(new IRequestInterceptor() {
                                @Override public Request interceptRequest(HttpRequest<?> httpRequest, Request original) {
                                    return original.newBuilder().tag(Gate.class, attempt).build();
                                }
                            }).handler(new IRequestHandler() {
                                @Override public Object requestSuccess(HttpRequest<?> r, Response response, Type type) throws Throwable {
                                    return ModuleNetwork.HANDLER.requestSuccess(r, response, type);
                                }
                                @Override public Throwable requestFail(HttpRequest<?> r, Throwable error) {
                                    attempt.finished();
                                    Task.onMain(() -> failedAttempt(token, attempt, error));
                                    return error;
                                }
                            }).listener(new OnDownloadListener() {
                                @Override public void onDownloadProgressChange(File file, int progress) {}
                                @Override public void onDownloadByteChange(File file, long total, long bytes) {
                                    if (!active(token)) return;
                                    long now = SystemClock.uptimeMillis();
                                    if (now - lastProgress < 100) return;
                                    lastProgress = now;
                                    listener.progress(total > 0 ? (int) Math.min(100, bytes * 100 / total) : -1, bytes, total);
                                }
                                @Override public void onDownloadSuccess(File file) {
                                    attempt.finished();
                                    if (!active(token)) { cleanup(file); return; }
                                    pending = Task.onBackground(() -> verify(token, attempt));
                                }
                                @Override public void onDownloadFail(File file, Throwable error) {
                                    // The handler also runs after the lifecycle has ended.
                                }
                            });
                    if (active(token)) request.start();
                    else { attempt.cancel(); request.stop(); }
                });
            } catch (Exception error) { Task.onMain(() -> { if (active(token)) next(error); }); }
        });
    }
    private void verify(long token, Gate attempt) {
        try {
            verifyFile(attempt.file, size, digest);
            Task.onMain(() -> {
                if (!active(token)) { cleanup(attempt.file); return; }
                // Queue the commit away from the UI; cancellation and commit share the gate lock.
                pending = Task.onBackground(() -> {
                    try {
                        attempt.commit(target);
                        Task.onMain(() -> {
                            if (!active(token)) return;
                            terminal = true;
                            Listener callback = listener;
                            release();
                            callback.success();
                        });
                    } catch (Exception error) { Task.onMain(() -> failedAttempt(token, attempt, error)); }
                });
            });
        } catch (Exception error) { Task.onMain(() -> failedAttempt(token, attempt, error)); }
    }
    private void failedAttempt(long token, Gate attempt, Throwable error) {
        cleanup(attempt.file);
        if (active(token)) next(error);
    }
    private void fail(Throwable error) {
        if (terminal || cancelled) return;
        terminal = true;
        Listener callback = listener;
        release();
        if (callback != null) callback.failure(error);
    }
    private void release() {
        if (registered) application.unregisterActivityLifecycleCallbacks(this);
        registered = false;
        if (lifecycle.getCurrentState() == Lifecycle.State.INITIALIZED) lifecycle.setCurrentState(Lifecycle.State.CREATED);
        lifecycle.setCurrentState(Lifecycle.State.DESTROYED);
        listener = null;
        request = null;
        gate = null;
    }
    private static void cleanup(File file) { Task.onBackground(() -> file.delete()); }

    static void verifyFile(File file, long expectedSize, String digest) throws Exception {
        if (expectedSize >= 0 && file.length() != expectedSize) throw new IOException("Downloaded file size mismatch");
        if (digest == null) return;
        if (!digest.matches("(?i)sha256:[0-9a-f]{64}")) throw new IOException("Invalid SHA-256 digest");
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = input.read(buffer)) != -1) hash.update(buffer, 0, length);
        }
        StringBuilder actual = new StringBuilder();
        for (byte value : hash.digest()) actual.append(String.format(Locale.ROOT, "%02x", value & 255));
        if (!actual.toString().equalsIgnoreCase(digest.substring(7))) throw new IOException("Downloaded SHA-256 mismatch");
    }

    static final class Gate {
        final File file;
        private boolean cancelled;
        private boolean dispatched;
        private boolean ended;
        private Call call;
        Gate(File file) { this.file = file; }
        synchronized void beforeCall(Call value) throws IOException {
            if (cancelled) throw new IOException("Download cancelled");
            dispatched = true;
            call = value;
        }
        synchronized void cancel() {
            cancelled = true;
            if (call != null) call.cancel();
            if (!dispatched || ended) cleanup(file);
        }
        synchronized void finished() {
            ended = true;
            call = null;
            if (cancelled) cleanup(file);
        }
        synchronized void commit(File target) throws Exception {
            if (cancelled) throw new IOException("Download cancelled");
            if (!file.renameTo(target)) throw new IOException("Cannot atomically replace downloaded file");
        }
        Response response(Response original) throws IOException {
            if (!original.isSuccessful()) {
                try (Response response = original) {
                    String body = response.body() == null ? "" : response.peekBody(4096).string();
                    throw new IOException("HTTP " + response.code() + ": " + body);
                }
            }
            ResponseBody body = original.body();
            if (body == null) { original.close(); throw new IOException("Empty download response"); }
            BufferedSource source = Okio.buffer(new ForwardingSource(body.source()) {
                @Override public long read(Buffer sink, long count) throws IOException {
                    long read = super.read(sink, count);
                    // EOF means EasyHttp has finished writing all data. Its lifecycle may suppress success.
                    if (read == -1) finished();
                    return read;
                }
            });
            return original.newBuilder().body(new ResponseBody() {
                @Override public okhttp3.MediaType contentType() { return body.contentType(); }
                @Override public long contentLength() { return body.contentLength(); }
                @Override public BufferedSource source() { return source; }
            }).build();
        }
    }
    @Override public void onActivityDestroyed(Activity value) { if (value == activity) cancel(); }
    @Override public void onActivityCreated(Activity value, Bundle state) {}
    @Override public void onActivityStarted(Activity value) {}
    @Override public void onActivityResumed(Activity value) {}
    @Override public void onActivityPaused(Activity value) {}
    @Override public void onActivityStopped(Activity value) {}
    @Override public void onActivitySaveInstanceState(Activity value, Bundle state) {}
}
