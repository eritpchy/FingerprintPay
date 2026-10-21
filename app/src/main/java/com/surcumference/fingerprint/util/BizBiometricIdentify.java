package com.surcumference.fingerprint.util;

import android.content.Context;
import android.os.Process;
import com.surcumference.fingerprint.storage.ConfigContract;
import com.surcumference.fingerprint.storage.TeePasswordCipher;
import com.surcumference.fingerprint.plugin.PluginApp;
import javax.crypto.Cipher;
import org.json.JSONObject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hjq.toast.Toaster;
import com.surcumference.fingerprint.Lang;
import com.surcumference.fingerprint.R;
import com.wei.android.lib.fingerprintidentify.bean.FingerprintIdentifyFailInfo;


public class BizBiometricIdentify extends XBiometricIdentify<BizBiometricIdentify> {

    private final Context context;
    public BizBiometricIdentify(Context context) {
        super(AESUtils.INSTANCE, context);
        this.context = context;
        Config config = Config.from(context);
        withUseBiometricApi(config.isUseBiometricApi());
    }

    private Config enrollmentConfig;
    private String enrollmentId, enrollmentAlias;
    private boolean enrollmentCompleted;
    private boolean passwordFailureNotified;
    private String backend() {
        return PluginApp.getCurrentType().name();
    }
    public BizBiometricIdentify decryptPasscode(String token, IdentifyListener listener) {
        passwordFailureNotified = false;
        IdentifyListener paymentListener = new IdentifyListener(listener) {
            @Override public void onFailed(BizBiometricIdentify identify, FingerprintIdentifyFailInfo info) {
                if (!info.isCancel() && !passwordFailureNotified && isPasswordFailure(info.throwable)) {
                    passwordFailureNotified = true;
                    PaymentPasswordGuard.invalid(context);
                }
                super.onFailed(identify, info);
            }
        };
        try {
            Config config = Config.from(context);
            JSONObject record = config.passwordRecord(token);
            withCipherAction(Cipher.DECRYPT_MODE,
                    () -> TeePasswordCipher.decryption(record, backend(), Process.myUid() / 100000, context.getPackageName()),
                    cipher -> {
                        config.checkCredential(token);
                        String password = TeePasswordCipher.decrypt(cipher, record);
                        if (password.isEmpty()) throw new IllegalArgumentException("Empty payment password");
                        config.checkCredential(token);
                        return password;
                    });
            startIdentify(paymentListener);
        } catch (Exception error) { paymentListener.onFailed(this, new FingerprintIdentifyFailInfo(false, error)); }
        return this;
    }
    static boolean isPasswordFailure(Throwable error) {
        return error instanceof java.security.InvalidKeyException
                && !(error instanceof android.security.keystore.UserNotAuthenticatedException)
                || error instanceof java.security.UnrecoverableKeyException
                || error instanceof javax.crypto.BadPaddingException
                || error instanceof org.json.JSONException
                || error instanceof IllegalArgumentException
                || error instanceof IllegalStateException && "TEE key unavailable; set the password again".equals(error.getMessage())
                || error instanceof SecurityException && "Wrong password key owner".equals(error.getMessage());
    }
    public BizBiometricIdentify encryptPending(String id, IdentifyListener listener) {
        if (android.os.Build.VERSION.SDK_INT >= 28) withUseBiometricApi(true);
        enrollmentConfig = Config.from(context); enrollmentId = id;
        enrollmentAlias = ConfigContract.teeAlias(backend(), Process.myUid() / 100000, context.getPackageName(), id);
        withCipherAction(Cipher.ENCRYPT_MODE,
                () -> TeePasswordCipher.encryption(enrollmentAlias),
                cipher -> {
                    String password = enrollmentConfig.claimPassword(id);
                    JSONObject record = TeePasswordCipher.encrypt(cipher, enrollmentAlias, password);
                    enrollmentConfig.commitPassword(id, record); enrollmentCompleted = true;
                    return "saved";
                });
        startIdentify(new IdentifyListener(listener) {
            @Override public void onFailed(BizBiometricIdentify identify, FingerprintIdentifyFailInfo info) {
                abortEnrollment(); super.onFailed(identify, info);
            }
        });
        return this;
    }
    private void abortEnrollment() {
        if (!enrollmentCompleted && enrollmentId != null) {
            enrollmentConfig.cancelPassword(enrollmentId);
            TeePasswordCipher.discard(enrollmentAlias);
        }
    }
    @Override public void cancelIdentify() { super.cancelIdentify(); abortEnrollment(); }

    @Override
    protected void onNotify(NotifyEnum notifyEnum, Object... args) {
        super.onNotify(notifyEnum, args);
        switch (notifyEnum) {
            case OnBiometricNotEnable:
                NotifyUtils.notifyBiometricIdentify(this.context, Lang.getString(R.id.toast_fingerprint_not_enable));
                break;
            case OnQQVersionTooLow:
                Toaster.showLong(Lang.getString(R.id.toast_need_qq_7_2_5));
                break;
            case OnDecryptionFailed:
                passwordFailureNotified = true;
                PaymentPasswordGuard.invalid(this.context);
                break;
            case OnBiometricRetryEnded:
                NotifyUtils.notifyBiometricIdentify(this.context, Lang.getString(R.id.toast_fingerprint_retry_ended));
                break;
            case OnBiometricFailed:
                NotifyUtils.notifyBiometricIdentify(this.context, Lang.getString(R.id.toast_fingerprint_failed));
                break;
            case OnBiometricLocked:
                NotifyUtils.notifyBiometricIdentify(this.context, Lang.getString(R.id.toast_fingerprint_unlock_reboot));
                break;
            case OnBiometricNotMatch:
                NotifyUtils.notifyBiometricIdentify(this.context, Lang.getString(R.id.toast_fingerprint_not_match));
                break;
            default:
                NotifyUtils.notifyBiometricIdentify(this.context, "Unknown notifyEnum: " + notifyEnum);
                break;
        }
    }

    public static class IdentifyListener extends XBiometricIdentify.IdentifyListener<BizBiometricIdentify> {
        public IdentifyListener() {
            super(null);
        }

        public IdentifyListener(IdentifyListener parentIdentifyListener) {
            super(parentIdentifyListener);
        }
    }
}
