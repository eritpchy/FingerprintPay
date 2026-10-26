package com.surcumference.fingerprint.storage;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabaseCorruptException;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;

final class ConfigDatabase extends SQLiteOpenHelper {
    ConfigDatabase(Context context) {
        super(context, "unified_config.db", null, 1, database -> {
            // Never delete authoritative state and silently reimport stale preferences.
            throw new SQLiteDatabaseCorruptException("Configuration database is corrupt");
        });
    }

    @Override public void onCreate(SQLiteDatabase database) {
        database.execSQL("CREATE TABLE configuration (id INTEGER PRIMARY KEY CHECK (id = 1), document TEXT NOT NULL)");
    }

    @Override public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        throw new SQLiteException("Unsupported configuration database version");
    }

    String read() {
        try (Cursor cursor = getWritableDatabase().rawQuery("SELECT document FROM configuration WHERE id = 1", null)) {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        }
    }

    void write(String document) {
        getWritableDatabase().execSQL("INSERT OR REPLACE INTO configuration (id, document) VALUES (1, ?)",
                new Object[]{document});
    }
}
