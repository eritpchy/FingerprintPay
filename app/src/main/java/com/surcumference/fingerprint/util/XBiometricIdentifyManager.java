package com.surcumference.fingerprint.util;

import java.lang.ref.WeakReference;

/**
 * 用这个类来阻止重复调用FingerprintIdentify.startIdentify, 防止业务调用startIdentify前忘记取消之前的调用
 * 可能出现的问题如下:
 * 1. Biometric API 验证界面出现又消失了
 * 2. javax.crypto.IllegalBlockSizeException
 *    Caused by:
 *        0: In update: Trying to get auth tokens.
 *        1: In AuthInfo::get_auth_tokens.
 *        2: In get_auth_tokens: No operation auth token received.
 *        3: Error::Km(ErrorCode(-26))) (public error code: 2 internal Keystore code: -26)
 *    ....
 */
public class XBiometricIdentifyManager {

    public static final XBiometricIdentifyManager INSTANCE = new XBiometricIdentifyManager();

    private WeakReference<XBiometricIdentify> lastXBiometricIdentifyRef;

    public synchronized void cancelFingerprintIdentify() {
        XBiometricIdentify identify = lastXBiometricIdentifyRef == null ? null : lastXBiometricIdentifyRef.get();
        lastXBiometricIdentifyRef = null;
        if (identify != null) {
            identify.cancelIdentify();
        }
    }

    public synchronized void set(XBiometricIdentify identify) {
        lastXBiometricIdentifyRef = identify == null ? null : new WeakReference<>(identify);
    }

    public synchronized void clear(XBiometricIdentify identify) {
        if (lastXBiometricIdentifyRef != null && lastXBiometricIdentifyRef.get() == identify) {
            lastXBiometricIdentifyRef = null;
        }
    }
}
