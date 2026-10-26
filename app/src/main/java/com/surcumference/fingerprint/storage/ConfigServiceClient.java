package com.surcumference.fingerprint.storage;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import com.surcumference.fingerprint.BuildConfig;
import org.json.JSONObject;

final class ConfigServiceClient {
    private static Connection connection;
    private static final java.util.concurrent.Executor CALLBACKS = java.util.concurrent.Executors.newSingleThreadExecutor();

    static JSONObject request(Context context, JSONObject request) throws Exception {
        for (int attempt = 0; ; attempt++) {
            Connection current = connect(context);
            IBinder binder = current.await();
            if (!binder.isBinderAlive()) {
                reset(current);
                if (attempt == 0) continue;
                throw new android.os.DeadObjectException();
            }
            try {
                return transact(context, binder, request);
            } catch (RemoteException error) {
                reset(current);
                if (attempt != 0 || !ConfigStores.repeatable(request.optString("op"))) throw error;
            }
        }
    }
    private static JSONObject transact(Context context, IBinder binder, JSONObject request) throws Exception {
        Parcel input = Parcel.obtain(), output = Parcel.obtain();
        try {
            input.writeInterfaceToken(ConfigService.DESCRIPTOR);
            input.writeString(request.toString());
            if (!binder.transact(IBinder.FIRST_CALL_TRANSACTION, input, output, 0))
                throw new RemoteException("Configuration service protocol unavailable");
            output.readException();
            JSONObject response = ConfigStores.checked(new JSONObject(output.readString()));
            android.util.Log.d("FingerprintPay", "Configuration read through Binder service: " + context.getPackageName());
            return response;
        } finally {
            input.recycle(); output.recycle();
        }
    }
    private static synchronized Connection connect(Context context) {
        if (connection != null) return connection;
        Context app = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        Connection next = new Connection(app);
        Intent intent = new Intent().setComponent(new ComponentName(BuildConfig.APPLICATION_ID, ConfigService.class.getName()));
        boolean bound = Build.VERSION.SDK_INT >= 29
                ? app.bindService(intent, Context.BIND_AUTO_CREATE, CALLBACKS, next)
                : app.bindService(intent, next, Context.BIND_AUTO_CREATE);
        if (!bound) throw new IllegalStateException("Configuration service unavailable");
        connection = next;
        return next;
    }
    private static synchronized void reset(Connection current) {
        current.close();
        if (connection != current) return;
        connection = null;
        try { current.context.unbindService(current); } catch (IllegalArgumentException ignored) {}
    }
    private static final class Connection implements ServiceConnection {
        private final Context context;
        private IBinder binder;
        private boolean closed;
        Connection(Context context) { this.context = context; }
        synchronized void close() { closed = true; binder = null; notifyAll(); }
        synchronized IBinder await() throws InterruptedException {
            // Before API 29, Android always delivers binding callbacks on the main thread.
            if (binder == null && Build.VERSION.SDK_INT < 29 && Looper.myLooper() == Looper.getMainLooper())
                throw new IllegalStateException("Configuration service connecting; retry");
            long until = android.os.SystemClock.uptimeMillis() + 1500;
            while (binder == null) {
                if (closed) throw new IllegalStateException("Configuration service disconnected");
                long remaining = until - android.os.SystemClock.uptimeMillis();
                if (remaining <= 0) throw new IllegalStateException("Configuration service connection timed out");
                wait(remaining);
            }
            return binder;
        }
        @Override public synchronized void onServiceConnected(ComponentName name, IBinder service) {
            if (!closed) { binder = service; notifyAll(); }
        }
        @Override public synchronized void onServiceDisconnected(ComponentName name) { binder = null; }
        @Override public void onBindingDied(ComponentName name) { reset(this); }
        @Override public void onNullBinding(ComponentName name) { reset(this); }
    }
    private ConfigServiceClient() {}
}
