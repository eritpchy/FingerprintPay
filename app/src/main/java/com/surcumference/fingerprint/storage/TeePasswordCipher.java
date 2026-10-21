package com.surcumference.fingerprint.storage;

import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyInfo;
import android.security.keystore.KeyProperties;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

@androidx.annotation.Keep
public final class TeePasswordCipher {
    public static Cipher encryption(String alias) throws Exception { return cipher(alias, null); }
    public static Cipher decryption(JSONObject record, String backend, int user, String pkg) throws Exception {
        String alias = record.getString("keyAlias"), prefix = ConfigContract.teeAlias(backend, user, pkg, "");
        if (!alias.startsWith(prefix)) throw new SecurityException("Wrong password key owner");
        ConfigContract.validateTeeRecord(record, backend, user, pkg, alias.substring(prefix.length()));
        return cipher(alias, record.getString("iv"));
    }
    private static Cipher cipher(String alias, String iv) throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (!store.containsAlias(alias)) {
            if (iv != null) throw new IllegalStateException("TEE key unavailable; set the password again");
            KeyGenParameterSpec.Builder spec = new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256).setRandomizedEncryptionRequired(true).setUserAuthenticationRequired(true);
            if (Build.VERSION.SDK_INT >= 24) spec.setInvalidatedByBiometricEnrollment(true);
            if (Build.VERSION.SDK_INT >= 30) spec.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG);
            else spec.setUserAuthenticationValidityDurationSeconds(-1);
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(spec.build()); generator.generateKey();
        }
        SecretKey key = (SecretKey) store.getKey(alias, null);
        KeyInfo info = (KeyInfo) SecretKeyFactory.getInstance("AES", "AndroidKeyStore").getKeySpec(key, KeyInfo.class);
        android.util.Log.i("FingerprintPay", "TEE key protection hardware=" + info.isInsideSecureHardware()
                + " authRequired=" + info.isUserAuthenticationRequired()
                + " authHardware=" + info.isUserAuthenticationRequirementEnforcedBySecureHardware()
                + " validity=" + info.getUserAuthenticationValidityDurationSeconds()
                + " authType=" + (Build.VERSION.SDK_INT >= 30 ? info.getUserAuthenticationType() : -1)
                + " enrollmentInvalidation=" + (Build.VERSION.SDK_INT >= 24 && info.isInvalidatedByBiometricEnrollment()));
        if (!info.isInsideSecureHardware() || !info.isUserAuthenticationRequired() || !info.isUserAuthenticationRequirementEnforcedBySecureHardware()
                || info.getUserAuthenticationValidityDurationSeconds() > 0
                || (Build.VERSION.SDK_INT >= 30 && info.getUserAuthenticationType() != KeyProperties.AUTH_BIOMETRIC_STRONG)
                || (Build.VERSION.SDK_INT >= 24 && !info.isInvalidatedByBiometricEnrollment()))
            throw new SecurityException("Hardware biometric TEE protection unavailable");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        if (iv == null) cipher.init(Cipher.ENCRYPT_MODE, key);
        else cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, unhex(iv)));
        return cipher;
    }
    public static JSONObject encrypt(Cipher authorized, String alias, String password) throws Exception {
        byte[] plain = password.getBytes(StandardCharsets.UTF_8);
        try { authorized.updateAAD(alias.getBytes(StandardCharsets.UTF_8));
            return new JSONObject().put("format", ConfigContract.TEE_FORMAT).put("keyAlias", alias)
                .put("iv", hex(authorized.getIV())).put("data", hex(authorized.doFinal(plain))); }
        finally { Arrays.fill(plain, (byte) 0); }
    }
    public static String decrypt(Cipher authorized, JSONObject record) throws Exception {
        authorized.updateAAD(record.getString("keyAlias").getBytes(StandardCharsets.UTF_8));
        byte[] plain = authorized.doFinal(unhex(record.getString("data")));
        try { return new String(plain, StandardCharsets.UTF_8); } finally { Arrays.fill(plain, (byte) 0); }
    }
    public static void discard(String alias) {
        try { KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null); store.deleteEntry(alias); }
        catch (Exception ignored) {}
    }
    private static String hex(byte[] bytes) {
        char[] result = new char[bytes.length * 2], digits = "0123456789abcdef".toCharArray();
        for (int i = 0; i < bytes.length; i++) { result[i * 2] = digits[(bytes[i] & 255) >>> 4]; result[i * 2 + 1] = digits[bytes[i] & 15]; }
        return new String(result);
    }
    private static byte[] unhex(String value) {
        if (value.length() % 2 != 0 || !value.matches("[0-9a-f]+")) throw new IllegalArgumentException("Invalid ciphertext");
        byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < result.length; i++) result[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        return result;
    }
    private TeePasswordCipher() {}
}
