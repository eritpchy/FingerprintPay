package com.surcumference.fingerprint.storage;

@androidx.annotation.Keep
public final class XposedActivation {
    public static int frameworkVersion() { return 0; }
    public static String frameworkName() { return "Xposed"; }
    private XposedActivation() {}
}
