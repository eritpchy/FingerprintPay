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

import javax.crypto.Cipher;

public class XBiometricIdentify<T extends XBiometricIdentify>{

    private final Context context;
    private final FingerprintIdentify fingerprintIdentify;

    private IMockCurrentUser mockCurrentUserCallback;
    public boolean fingerprintScanStateReady = false;

    private java.util.concurrent.Callable<Cipher> cipherFactory;
    private CipherAction cipherAction;
    private java.util.concurrent.Callable<String> softwareAction;
    private boolean authenticationOnly;
    private int operationMode;
    private long identifyGeneration;
    private boolean identifyFinished = true;
    private int remainingAttempts = 8;
    private String identifyStage = "idle";

    public XBiometricIdentify(Context context) {
        this(context, new FingerprintIdentify(context));
    }

    XBiometricIdentify(Context context, FingerprintIdentify fingerprintIdentify) {
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

    public T withCipherAction(int mode, java.util.concurrent.Callable<Cipher> factory, CipherAction action) {
        operationMode = mode;
        authenticationOnly = false; softwareAction = null;
        cipherFactory = factory;
        cipherAction = action;
        fingerprintIdentify.setCipherMode(mode, null);
        return (T) this;
    }

    public T withEncryptionFallback(java.util.concurrent.Callable<String> action) {
        if (operationMode != Cipher.ENCRYPT_MODE || cipherAction == null) throw new IllegalStateException("Encryption action required");
        softwareAction = action;
        return (T) this;
    }

    public T withAuthenticationAction(int mode, java.util.concurrent.Callable<String> action) {
        withCipherAction(mode, null, null);
        authenticationOnly = true; softwareAction = action;
        return (T) this;
    }

    public T withUseBiometricApi(boolean on) {
        fingerprintIdentify.setUseBiometricApi(on);
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
        manager.set(this);
        final int cipherMode = operationMode;
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
            Cipher preparedCipher = null;
            if (!authenticationOnly && cipherFactory != null) {
                try { preparedCipher = cipherFactory.call(); }
                catch (Exception error) {
                    if (cipherMode != Cipher.ENCRYPT_MODE || softwareAction == null) throw error;
                    android.util.Log.w("FingerprintPay", "Using software password protection: " + error.getClass().getName());
                    authenticationOnly = true;
                }
                if (preparedCipher == null && softwareAction != null && cipherMode == Cipher.ENCRYPT_MODE) authenticationOnly = true;
            }
            final Cipher expectedCipher = preparedCipher;
            final boolean softwareOnly = authenticationOnly;
            // The fingerprint library uses decrypt-without-IV for authentication without a CryptoObject.
            fingerprintIdentify.setCipherMode(softwareOnly ? Cipher.DECRYPT_MODE : cipherMode, null);
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
                        if (softwareOnly || cipher == null && softwareAction != null && cipherMode == Cipher.ENCRYPT_MODE) {
                            result = softwareAction.call();
                        } else {
                            if (cipherAction == null || cipher == null || cipher != expectedCipher) throw new SecurityException("Biometric cipher mismatch");
                            result = cipherAction.run(cipher);
                        }
                        if (result == null) {
                            throw new IllegalStateException("Biometric cipher operation failed");
                        }
                    } catch (Exception e) {
                        L.e("Biometric cipher operation failed", e);
                        fail(generation, identifyListener, new FingerprintIdentifyFailInfo(false, e),
                                cipherMode == Cipher.DECRYPT_MODE ? NotifyEnum.OnDecryptionFailed : null);
                        return;
                    }
                    if (!finish(generation)) {
                        return;
                    }
                    try {
                        if (cipherMode == Cipher.ENCRYPT_MODE) {
                            identifyListener.onEncryptionSuccess(XBiometricIdentify.this, result, cipher == null ? null : cipher.getIV());
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
                    if (!isActive(generation)) return;
                    if (!softwareOnly && softwareAction != null && cipherMode == Cipher.ENCRYPT_MODE
                            && !failInfo.isCancel() && !failInfo.deviceLocked && isCryptoFailure(failInfo.throwable)) {
                        ++identifyGeneration;
                        fingerprintIdentify.cancelIdentify();
                        manager.clear(XBiometricIdentify.this);
                        authenticationOnly = true;
                        startIdentify(identifyListener, true);
                        return;
                    }
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

    private static boolean isCryptoFailure(Throwable error) {
        for (int depth = 0; error != null && depth < 8; depth++, error = error.getCause()) {
            if (error instanceof java.security.GeneralSecurityException || error instanceof java.security.ProviderException) return true;
        }
        return false;
    }

    private boolean isActive(long generation) {
        return generation == identifyGeneration && !identifyFinished;
    }

    private boolean finish(long generation) {
        if (!isActive(generation)) {
            return false;
        }
        identifyFinished = true;
        fingerprintScanStateReady = false;
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
                + " mode=" + operationMode + " code=" + info.errorCode
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
