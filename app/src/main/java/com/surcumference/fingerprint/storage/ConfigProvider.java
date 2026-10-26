package com.surcumference.fingerprint.storage;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;

public final class ConfigProvider extends ContentProvider {
    private ConfigEndpoint endpoint;
    @Override public boolean onCreate() {
        endpoint = ConfigEndpoint.get(getContext());
        endpoint.restoreLauncher();
        return true;
    }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        if (!"request".equals(method) || extras == null) throw new SecurityException("Invalid request");
        Bundle result = new Bundle();
        result.putString("response", endpoint.request(Binder.getCallingUid(), extras.getString("request", "")).toString());
        return result;
    }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { throw new SecurityException("Use the configuration protocol"); }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { throw new SecurityException("Use the configuration protocol"); }
    @Override public int delete(Uri u, String s, String[] a) { throw new SecurityException("Use the configuration protocol"); }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new SecurityException("Use the configuration protocol"); }
}
