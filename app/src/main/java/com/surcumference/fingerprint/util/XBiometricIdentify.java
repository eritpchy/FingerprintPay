package com.surcumference.fingerprint.util;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.surcumference.fingerprint.Constant;
import com.surcumference.fingerprint.plugin.inf.IMockCurrentUser;
import com.surcumference.fingerprint.util.log.L;
import com.wei.android.lib.fingerprintidentify.FingerprintIdentify;
import com.wei.android.lib.fingerprintidentify.base.BaseFingerprint;
import com.wei.android.lib.fingerprintidentify.bean.FingerprintIdentifyFailInfo;
import com.wei.android.lib.fingerprintidentify.util.PasswordCipherHelper;

import javax.crypto.Cipher;

public class XBiometricIdentify<T extends XBiometricIdentify>{

    private final Context context;
    private final FingerprintIdentify fingerprintIdentify;
    private final ICryptoHandler cryptoHandler;

    private IMockCurrentUser mockCurrentUserCallback;
    public boolean fingerprintScanStateReady = false;

    private String cipherFallbackKey;
    private String cipherContent;
    private boolean allowPasswordCipherFallback = false;
    private java.util.concurrent.Callable<Cipher> cipherFactory;
    private CipherAction cipherAction;
    private long identifyGeneration;
    private boolean identifyFinished = true;
    private IdentifyListener lastIdentifyListener;
    private int remainingAttempts = 8;
    private String identifyStage = "idle";

    public XBiometricIdentify(ICryptoHandler cryptoHandler, Context context) {
        this(cryptoHandler, context, new FingerprintIdentify(context));
    }

    XBiometricIdentify(ICryptoHandler cryptoHandler, Context context, FingerprintIdentify fingerprintIdentify) {
        this.cryptoHandler = cryptoHandler;
        this.context = context;

        this.fingerprintIdentify = fingerprintIdentify;
        fingerprintIdentify.setMaxAvailableTimes(8);
        fingerprintIdentify.setSupportAndroidL(true);
        fingerprintIdentify.setUseBiometricApi(false);
        fingerprintIdentify.setExceptionListener(exception -> L.e("XBiometricIdentify", exception));
    }

    public T withMockCurrentUserCallback(IMockCurrentUser mockCurrentUserCallback) {
        this.mockCurrentUserCallback = mockCurrentUserCallback;
        return (T)this;
    }

    public T withEncryptionMode(String cipherContent, String cipherFallbackKey) {
        this.cipherContent = cipherContent;
        this.cipherFallbackKey = cipherFallbackKey;
        fingerprintIdentify.setCipherMode(Cipher.ENCRYPT_MODE, null);
        return (T)this;
    }

    public T withDecryptionMode(String cipherContent, byte[] cipherIV, String cipherFallbackKey) {
        this.cipherContent = cipherContent;
        this.cipherFallbackKey = cipherFallbackKey;
        fingerprintIdentify.setCipherMode(Cipher.DECRYPT_MODE, cipherIV);
        return (T)this;
    }

    public T withCipherAction(int mode, java.util.concurrent.Callable<Cipher> factory, CipherAction action) {
        cipherFactory = factory;
        cipherAction = action;
        fingerprintIdentify.setCipherMode(mode, null);
        return (T) this;
    }

    public T withUseBiometricApi(boolean on) {
        fingerprintIdentify.setUseBiometricApi(on);
        return (T)this;
    }

    public T withPasswordCipherFallback(boolean allow) {
        this.allowPasswordCipherFallback = allow;
        return (T)this;
    }

    public T startIdentify(IdentifyListener identifyListener) {
        return startIdentify(identifyListener, false);
    }

    private T startIdentify(IdentifyListener identifyListener, boolean resuming) {
        if (!resuming) remainingAttempts = 8;
        fingerprintIdentify.setMaxAvailableTimes(remainingAttempts);
        XBiometricIdentifyManager manager = XBiometricIdentifyManager.INSTANCE;
        manager.cancelFingerprintIdentify();
        final long generation = ++identifyGeneration;
        identifyFinished = false;
        lastIdentifyListener = identifyListener;
        manager.set(this);
        final int cipherMode = fingerprintIdentify.getCipherMode();
        try {
            identifyStage = "availability";
            if (cipherMode != Cipher.ENCRYPT_MODE && cipherMode != Cipher.DECRYPT_MODE) {
                throw new IllegalStateException("Cipher mode is not configured");
            }
            callMockCurrentUserCallback(true);
            fingerprintIdentify.init();
            if (!fingerprintIdentify.isFingerprintEnable()) {
                NotifyEnum notification = Constant.PACKAGE_NAME_QQ.equals(context.getPackageName())
                        && !PermissionUtils.hasFingerprintPermission(context)
                        ? NotifyEnum.OnQQVersionTooLow : NotifyEnum.OnBiometricNotEnable;
                fail(generation, identifyListener,
                        new FingerprintIdentifyFailInfo(false, -1, "Biometric unavailable"), notification);
                return (T) this;
            }
            identifyStage = "key";
            final Cipher expectedCipher = cipherFactory == null ? null : cipherFactory.call();
            fingerprintIdentify.setCipher(expectedCipher);
            fingerprintScanStateReady = true;
            identifyListener.onInited(this);
            if (!isActive(generation)) {
                return (T) this;
            }
            identifyStage = "authentication";
            fingerprintIdentify.startIdentify(new BaseFingerprint.IdentifyListener() {
                @Override
                public void onSucceed(@Nullable Cipher cipher) {
                    if (!isActive(generation)) {
                        return;
                    }
                    String result;
                    try {
                        identifyStage = "cipher-action";
                        if (cipherAction != null && (cipher == null || cipher != expectedCipher)) throw new SecurityException("Biometric cipher mismatch");
                        result = cipherAction != null ? cipherAction.run(cipher) : encryptionOrDecryption(cipherMode, cipher, cipherContent);
                        if (cipherAction == null && result == null && allowPasswordCipherFallback) {
                            cipher = PasswordCipherHelper.createCipher(cipherMode, cipherFallbackKey);
                            result = encryptionOrDecryption(cipherMode, cipher, cipherContent);
                        }
                        if (result == null) {
                            throw new IllegalStateException("Biometric cipher operation failed");
                        }
                    } catch (Exception e) {
                        L.e("Biometric cipher operation failed", e);
                        fail(generation, identifyListener, new FingerprintIdentifyFailInfo(false, e),
                                NotifyEnum.OnDecryptionFailed);
                        return;
                    }
                    if (!finish(generation)) {
                        return;
                    }
                    try {
                        if (cipherMode == Cipher.ENCRYPT_MODE) {
                            identifyListener.onEncryptionSuccess(XBiometricIdentify.this, result, cipher.getIV());
                        } else {
                            identifyListener.onDecryptionSuccess(XBiometricIdentify.this, result);
                        }
                    } catch (Exception e) {
                        L.e("Biometric success callback failed", e);
                        identifyListener.onFailed(XBiometricIdentify.this, new FingerprintIdentifyFailInfo(false, e));
                    }
                }

                @Override
                public void onNotMatch(int availableTimes) {
                    if (!isActive(generation)) {
                        return;
                    }
                    remainingAttempts = Math.max(1, availableTimes);
                    onNotify(NotifyEnum.OnBiometricNotMatch);
                    callMockCurrentUserCallback(false);
                    identifyListener.onNotMatch(XBiometricIdentify.this, availableTimes);
                }

                @Override
                public void onFailed(FingerprintIdentifyFailInfo failInfo) {
                    fail(generation, identifyListener, failInfo,
                            failInfo.isCancel() ? null : failInfo.deviceLocked ? NotifyEnum.OnBiometricLocked
                                    : failInfo.errorCode == -2 ? NotifyEnum.OnBiometricRetryEnded
                                    : NotifyEnum.OnBiometricFailed);
                }

                @Override
                public void onStartFailedByDeviceLocked() {
                    fail(generation, identifyListener, new FingerprintIdentifyFailInfo(true),
                            NotifyEnum.OnBiometricLocked);
                }
            });
        } catch (Exception e) {
            L.e("Unable to start biometric authentication", e);
            fail(generation, identifyListener, new FingerprintIdentifyFailInfo(false, e), null);
        }
        return (T) this;
    }

    public interface CipherAction { String run(Cipher authorized) throws Exception; }

    private boolean isActive(long generation) {
        return generation == identifyGeneration && !identifyFinished;
    }

    private boolean finish(long generation) {
        if (!isActive(generation)) {
            return false;
        }
        identifyFinished = true;
        fingerprintScanStateReady = false;
        lastIdentifyListener = null;
        cipherContent = null;
        XBiometricIdentifyManager.INSTANCE.clear(this);
        callMockCurrentUserCallback(false);
        return true;
    }

    private void fail(long generation, IdentifyListener listener, FingerprintIdentifyFailInfo info,
                      @Nullable NotifyEnum notification) {
        if (!finish(generation)) {
            return;
        }
        android.util.Log.e("FingerprintPay", "Biometric failure stage=" + identifyStage
                + " mode=" + fingerprintIdentify.getCipherMode() + " code=" + info.errorCode
                + " locked=" + info.deviceLocked + " exception="
                + (info.throwable == null ? "none" : info.throwable.getClass().getName()));
        try {
            if (notification != null) {
                onNotify(notification, info);
            }
        } finally {
            listener.onFailed(this, info);
        }
    }

    @Nullable
    private String encryptionOrDecryption(int cipherMode, Cipher cipher, String content) {
        if (cipher == null) {
            return null;
        }
        return cipherMode == Cipher.ENCRYPT_MODE
                ? cryptoHandler.encrypt(cipher, content) : cryptoHandler.decrypt(cipher, content);
    }

    public void cancelIdentify() {
        ++identifyGeneration;
        identifyFinished = true;
        fingerprintScanStateReady = false;
        XBiometricIdentifyManager.INSTANCE.clear(this);
        try {
            fingerprintIdentify.cancelIdentify();
        } finally {
            callMockCurrentUserCallback(false);
        }
    }

    public void resumeIdentify() {
        IdentifyListener listener = lastIdentifyListener;
        if (listener != null) {
            startIdentify(listener, true);
        }
    }

    public boolean isUsingBiometricApi() {
        return fingerprintIdentify.isUsingBiometricApi();
    }

    private void callMockCurrentUserCallback(boolean mock) {
        IMockCurrentUser mockCurrentUserCallback = this.mockCurrentUserCallback;
        if (mockCurrentUserCallback == null) {
            return;
        }
        mockCurrentUserCallback.setMockCurrentUser(mock);
    }

    protected void onNotify(NotifyEnum notifyEnum, Object...args) {

    }

    public static class IdentifyListener<T extends XBiometricIdentify> {

        private IdentifyListener<T> parentIdentifyListener;

        public IdentifyListener() {
            this(null);
        }

        public IdentifyListener(IdentifyListener<T> parentIdentifyListener) {
            this.parentIdentifyListener = parentIdentifyListener;
        }

        public void onInited(T identify) {
            IdentifyListener<T> listener = this.parentIdentifyListener;
            if (listener != null) {
                listener.onInited(identify);
            }
        }

        public void onDecryptionSuccess(T identify, @NonNull String decryptedContent) {
            IdentifyListener<T> listener = this.parentIdentifyListener;
            if (listener != null) {
                listener.onDecryptionSuccess(identify, decryptedContent);
            }
        }
        
        public void onEncryptionSuccess(T identify, @NonNull String encryptedContent, @Nullable byte[] encryptedIV) {
            IdentifyListener<T> listener = this.parentIdentifyListener;
            if (listener != null) {
                listener.onEncryptionSuccess(identify, encryptedContent, encryptedIV);
            }
        }

        public void onNotMatch(T identify, int availableTimes) {
            IdentifyListener<T> listener = this.parentIdentifyListener;
            if (listener != null) {
                listener.onNotMatch(identify, availableTimes);
            }
        }

        public void onFailed(T identify, FingerprintIdentifyFailInfo failInfo) {
            IdentifyListener<T> listener = this.parentIdentifyListener;
            if (listener != null) {
                listener.onFailed(identify, failInfo);
            }
        }

        public void onStartFailedByDeviceLocked(T identify) {
            IdentifyListener<T> listener = this.parentIdentifyListener;
            if (listener != null) {
                listener.onStartFailedByDeviceLocked(identify);
            }
        }
    }

    public interface ICryptoHandler {
        @Nullable
        String decrypt(@NonNull Cipher cipher, @NonNull String content);
        @Nullable
        String encrypt(@NonNull Cipher cipher, @NonNull String content);
    }

    public enum NotifyEnum {
        OnBiometricNotEnable,
        OnQQVersionTooLow,
        OnDecryptionFailed,
        OnBiometricRetryEnded,
        OnBiometricFailed,
        OnBiometricLocked,
        OnBiometricNotMatch,
    }

}
