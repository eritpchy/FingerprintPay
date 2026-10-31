package com.surcumference.fingerprint.activity;

import android.app.Activity;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.widget.FrameLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.surcumference.fingerprint.Constant;
import com.surcumference.fingerprint.BuildConfig;
import com.surcumference.fingerprint.bean.PluginType;
import com.surcumference.fingerprint.network.update.UpdateFactory;
import com.surcumference.fingerprint.storage.ConfigStores;
import org.json.JSONObject;

@androidx.annotation.Keep
public class ConfigActivity extends Activity {
    public static final String ACTION_CHECK_UPDATES = BuildConfig.APPLICATION_ID + ".action.CHECK_UPDATES";
    public static final String EXTRA_UPDATE_BACKEND = "update_backend";
    public static final String EXTRA_UPDATE_VERSION = "update_version";
    private WebView web;
    private volatile float statusBarInset;
    private volatile boolean resumed, windowFocused;
    private boolean passwordFlowActive;
    private Runnable unregisterBack;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ConfigStores.grantHostVisibility(this);
        web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setSaveFormData(false);
        web.getSettings().setSavePassword(false);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.getSettings().setBlockNetworkLoads(true);
        web.setWebChromeClient(new android.webkit.WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) { notifyInsetsChanged(); }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) { openProjectLink(url); return true; }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request.isForMainFrame()) openProjectLink(request.getUrl().toString());
                return true;
            }
        });
        web.addJavascriptInterface(new Bridge(), "FingerprintConfig");
        FrameLayout root = new FrameLayout(this);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        if (landscape && Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams attributes = getWindow().getAttributes();
            attributes.layoutInDisplayCutoutMode = Build.VERSION.SDK_INT >= 30
                    ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(attributes);
        }
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) getWindow().setStatusBarContrastEnforced(false);
        WindowCompat.getInsetsController(getWindow(), root).setAppearanceLightStatusBars(true);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            int insetTypes = WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime();
            if (!landscape) insetTypes |= WindowInsetsCompat.Type.displayCutout();
            Insets safe = windowInsets.getInsets(insetTypes);
            statusBarInset = safe.top / getResources().getDisplayMetrics().density;
            view.setPadding(safe.left, 0, safe.right, safe.bottom);
            notifyInsetsChanged();
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(root);
        web.loadUrl("file:///android_asset/index.html");
    }
    private void notifyInsetsChanged() {
        web.evaluateJavascript("window.dispatchEvent(new Event('fingerprint-insets'));", null);
    }
    private void openProjectLink(String url) {
        if (!Constant.PROJECT_URL.equals(url) && !Constant.PROJECT_URL_GITEE.equals(url)
                && !Constant.DOWNLOAD_CENTER_URL.equals(url)) return;
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)); }
        catch (ActivityNotFoundException error) { Toast.makeText(this, "未找到可打开链接的应用", Toast.LENGTH_SHORT).show(); }
    }
    protected void checkForUpdates() {
        Intent intent = getIntent();
        PluginType type = ACTION_CHECK_UPDATES.equals(intent.getAction())
                && PluginType.Zygisk.name().equals(intent.getStringExtra(EXTRA_UPDATE_BACKEND))
                ? PluginType.Zygisk : PluginType.Xposed;
        String version = type == PluginType.Zygisk ? intent.getStringExtra(EXTRA_UPDATE_VERSION) : null;
        if (version == null || version.length() > 80 || !version.matches("v?\\d+(?:\\.\\d+)+(?:[-+][A-Za-z0-9._-]+)?")) version = BuildConfig.VERSION_NAME;
        UpdateFactory.doUpdateCheck(this, false, true, type, version);
    }
    @Override public void onResume() {
        super.onResume();
        resumed = true;
        if (web != null) { web.onResume(); web.evaluateJavascript("window.dispatchEvent(new Event('focus'));", null); }
    }
    @Override public void onPause() {
        resumed = false; windowFocused = false;
        if (web != null) { web.evaluateJavascript("window.dispatchEvent(new Event('blur'));", null); web.onPause(); }
        super.onPause();
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        windowFocused = focused;
        if (focused && web != null) web.evaluateJavascript("window.dispatchEvent(new Event('focus'));", null);
    }
    private void setPasswordFlowActive(boolean enabled) {
        passwordFlowActive = enabled;
        if (unregisterBack != null) { unregisterBack.run(); unregisterBack = null; }
        if (enabled && Build.VERSION.SDK_INT >= 33) unregisterBack = BackApi.register(this, this::cancelPasswordFlow);
    }
    private void cancelPasswordFlow() {
        setPasswordFlowActive(false);
        if (web != null) web.evaluateJavascript("window.dispatchEvent(new Event('fingerprint-cancel-password'));", null);
    }
    @Override public void onBackPressed() {
        if (passwordFlowActive) cancelPasswordFlow(); else super.onBackPressed();
    }
    @androidx.annotation.RequiresApi(33)
    private static final class BackApi {
        static Runnable register(Activity activity, Runnable cancel) {
            android.window.OnBackInvokedDispatcher dispatcher = activity.getOnBackInvokedDispatcher();
            android.window.OnBackInvokedCallback callback = cancel::run;
            dispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_OVERLAY, callback);
            return () -> dispatcher.unregisterOnBackInvokedCallback(callback);
        }
    }
    @androidx.annotation.Keep
    private final class Bridge {
        @JavascriptInterface public boolean isForeground() { return resumed && windowFocused; }
        @JavascriptInterface public void passwordFlowActive(boolean enabled) { runOnUiThread(() -> setPasswordFlowActive(enabled)); }
        @JavascriptInterface public float statusBarInset() { return statusBarInset; }
        @JavascriptInterface public String checkUpdates() {
            runOnUiThread(() -> checkForUpdates());
            return "{\"ok\":true}";
        }
        @JavascriptInterface public int androidUser() { return android.os.Process.myUid() / 100000; }
        @JavascriptInterface public int androidSdk() { return android.os.Build.VERSION.SDK_INT; }
        @JavascriptInterface public String runtimeStatus(String pkg) {
            try { return com.surcumference.fingerprint.storage.XposedRuntime.read(ConfigActivity.this, pkg).toString(); }
            catch (Exception error) { return "{\"state\":\"unknown\"}"; }
        }
        @JavascriptInterface public String installedApplications() {
            org.json.JSONArray result = new org.json.JSONArray();
            for (String pkg : com.surcumference.fingerprint.storage.ConfigContract.PACKAGES) {
                if (getPackageManager().getLaunchIntentForPackage(pkg) != null) result.put(pkg);
            }
            return result.toString();
        }
        @JavascriptInterface public String openDonation(String channel) {
            try { return com.surcumference.fingerprint.util.DonateUtils.openFromSettings(ConfigActivity.this, channel).toString(); }
            catch (Exception error) { return "{\"ok\":false,\"error\":\"无法打开赞助页面\"}"; }
        }
        @JavascriptInterface public String donationStatus(String channel, String id) {
            try { return com.surcumference.fingerprint.util.DonateUtils.request(ConfigActivity.this, "donationStatus", channel, id).toString(); }
            catch (Exception error) { return "{\"ok\":false,\"error\":\"请确认 Xposed 模块已启用并作用于所选应用，再重新打开应用\"}"; }
        }
        @JavascriptInterface public void cancelDonation(String channel, String id) {
            try { com.surcumference.fingerprint.util.DonateUtils.request(ConfigActivity.this, "cancelDonation", channel, id); }
            catch (Exception ignored) {}
        }
        @JavascriptInterface public String openApplication(String pkg) {
            java.util.concurrent.FutureTask<String> launch = new java.util.concurrent.FutureTask<>(() -> launchApplication(pkg));
            runOnUiThread(launch);
            try { return launch.get(5, java.util.concurrent.TimeUnit.SECONDS); }
            catch (Exception error) { launch.cancel(false); return "{\"ok\":false,\"error\":\"打开应用超时，请返回设置重试\"}"; }
        }
        private String launchApplication(String pkg) {
            try {
                if (!com.surcumference.fingerprint.storage.ConfigContract.PACKAGES.contains(pkg)) throw new SecurityException("Unknown application");
                if (!isForeground()) throw new IllegalStateException("请返回设置页面后重试录入");
                Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg);
                java.util.List<android.content.pm.ResolveInfo> entries = getPackageManager().queryIntentActivities(intent, 0);
                if (entries.isEmpty()) throw new IllegalStateException("无法找到所选应用的主入口");
                android.content.pm.ActivityInfo entry = entries.get(0).activityInfo;
                if (entry == null || !pkg.equals(entry.packageName) || !entry.exported)
                    throw new IllegalStateException("无法找到所选应用的主入口");
                intent.setClassName(entry.packageName, entry.name);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                Toast.makeText(ConfigActivity.this,
                        "请在打开的应用中验证指纹；未弹出请返回设置重试，并检查模块作用域", Toast.LENGTH_LONG).show();
                startActivity(intent);
                return "{\"ok\":true}";
            } catch (Exception error) {
                try { return new JSONObject().put("ok", false).put("error", error.getMessage()).toString(); }
                catch (Exception ignored) { return "{\"ok\":false}"; }
            }
        }
        @JavascriptInterface public String request(String raw) {
            try {
                if (raw.length() > 65536) throw new IllegalArgumentException("Request too large");
                JSONObject request = new JSONObject(raw);
                if ("reveal".equals(request.optString("op"))) throw new SecurityException("Passwords are write-only");
                return ConfigStores.forContext(ConfigActivity.this).request(request).toString();
            } catch (Exception error) {
                try { return new JSONObject().put("ok", false).put("error", error.getMessage()).toString(); }
                catch (Exception ignored) { return "{\"ok\":false}"; }
            }
        }
    }
    @Override public void onDestroy() {
        setPasswordFlowActive(false);
        if (web != null) { web.removeJavascriptInterface("FingerprintConfig"); web.destroy(); }
        super.onDestroy();
    }
}
