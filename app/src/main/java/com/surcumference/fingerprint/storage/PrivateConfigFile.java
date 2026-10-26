package com.surcumference.fingerprint.storage;

import android.content.Context;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class PrivateConfigFile {
    private final AtomicFile file;

    PrivateConfigFile(Context context) {
        file = new AtomicFile(new File(context.getFilesDir(), "unified_config_v1.json"));
    }

    String read() throws IOException {
        try {
            return new String(file.readFully(), StandardCharsets.UTF_8);
        } catch (FileNotFoundException error) {
            File base = file.getBaseFile();
            if (base.exists() || new File(base.getPath() + ".bak").exists()) throw error;
            return null;
        }
    }
}
