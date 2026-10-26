package com.surcumference.fingerprint.storage;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

public final class ConfigService extends Service {
    static final String DESCRIPTOR = "com.surcumference.fingerprintpay.config.v1";
    private ConfigEndpoint endpoint;
    private final Binder binder = new Binder() {
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code != IBinder.FIRST_CALL_TRANSACTION) return super.onTransact(code, data, reply, flags);
            if (reply == null || (flags & IBinder.FLAG_ONEWAY) != 0) return false;
            data.enforceInterface(DESCRIPTOR);
            int uid = Binder.getCallingUid();
            String raw = data.readString();
            String response = endpoint.request(uid, raw == null ? "" : raw).toString();
            reply.writeNoException();
            reply.writeString(response);
            return true;
        }
    };
    @Override public void onCreate() {
        super.onCreate();
        endpoint = ConfigEndpoint.get(this);
    }
    @Override public IBinder onBind(Intent intent) { return binder; }
}
