# base64 app/proguard-rules.pro
-keep class com.hjq.toast.** {*;}

# 忽略三星指纹识别SDK相关的缺失类
-dontwarn com.samsung.android.fingerprint.**
-dontwarn com.samsung.android.sdk.pass.**

# 保留 Gson 注解
-keepattributes *Annotation*

# 保留 Gson 用到的泛型信息
-keepattributes Signature

# 保留使用 Gson 进行序列化/反序列化的类及其成员
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# 如果使用了 Gson 的 Expose 注解，也需要保留
-keepclassmembers class * {
    @com.google.gson.annotations.Expose <fields>;
}

-keepclassmembers class
com.surcumference.fingerprint.network.update.github.bean.**,
com.surcumference.fingerprint.plugin.xposed.WeChatPlugin,
com.surcumference.fingerprint.plugin.xposed.AlipayPlugin,
com.surcumference.fingerprint.plugin.xposed.QQPlugin,
com.surcumference.fingerprint.plugin.xposed.TaobaoPlugin,
com.surcumference.fingerprint.plugin.xposed.UnionPayPlugin {
    <init>(...);
}

-keepclassmembernames class  com.surcumference.fingerprint.network.update.github.bean.** {
    <fields>;
}

-dontwarn android.os.ServiceManager

# 提高优化等级
-optimizationpasses 11
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizations !method/inlining/*
-optimizations !code/allocation/variable

# 启用激进优化
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses

# Preserve legacy analytics bytecode for strict ART verifiers.
-keep class com.umeng.** { *; }
