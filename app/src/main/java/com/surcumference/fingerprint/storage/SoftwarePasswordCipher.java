package com.surcumference.fingerprint.storage;

import android.content.Context;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.util.Arrays;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.json.JSONObject;

public final class SoftwarePasswordCipher {
    private final File directory;
    public SoftwarePasswordCipher(Context context) {
        directory = new File(context.getNoBackupFilesDir(), "fingerprintpay-keys");
    }
    public JSONObject encrypt(String alias, String password) throws Exception {
        return TeePasswordCipher.encrypt(TeePasswordCipher.cipher(key(alias, true), null), alias, password)
                .put("format", ConfigContract.SOFTWARE_FORMAT);
    }
    public String decrypt(JSONObject record, String backend, int user, String pkg) throws Exception {
        String alias = record.getString("keyAlias"), prefix = ConfigContract.softwareAlias(backend, user, pkg, "");
        if (!ConfigContract.SOFTWARE_FORMAT.equals(record.getString("format")) || !alias.startsWith(prefix))
            throw new SecurityException("Wrong password key owner");
        ConfigContract.validatePasswordRecord(record, backend, user, pkg, alias.substring(prefix.length()));
        return TeePasswordCipher.decrypt(TeePasswordCipher.cipher(key(alias, false), record.getString("iv")), record);
    }
    private AtomicFile file(String alias) {
        if (!alias.matches("fingerprintpay\\.software\\.v1\\.(Xposed|Zygisk)\\.[0-9]+\\.[a-zA-Z0-9_.]+\\.[0-9a-f]{32}"))
            throw new IllegalArgumentException("Invalid software key alias");
        return new AtomicFile(new File(directory, alias));
    }
    private SecretKey key(String alias, boolean create) throws Exception {
        synchronized (SoftwarePasswordCipher.class) {
            AtomicFile file = file(alias);
            byte[] bytes;
            if (file.getBaseFile().exists() || new File(file.getBaseFile().getPath() + ".bak").exists()) {
                bytes = file.readFully();
            } else {
                if (!create) throw new UnrecoverableKeyException("Software key unavailable; set the password again");
                if (!directory.isDirectory() && !directory.mkdirs()) throw new java.io.IOException("Cannot create software key directory");
                bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
                FileOutputStream output = null;
                try {
                    output = file.startWrite(); output.write(bytes); file.finishWrite(output);
                } catch (Exception error) {
                    file.failWrite(output); Arrays.fill(bytes, (byte) 0); throw error;
                }
            }
            try {
                if (bytes.length != 32) throw new UnrecoverableKeyException("Invalid software key; set the password again");
                return new SecretKeySpec(bytes, "AES");
            } finally { Arrays.fill(bytes, (byte) 0); }
        }
    }
    public void discard(String alias) {
        synchronized (SoftwarePasswordCipher.class) { file(alias).delete(); }
    }
}
