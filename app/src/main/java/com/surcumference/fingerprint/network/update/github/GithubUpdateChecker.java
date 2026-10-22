package com.surcumference.fingerprint.network.update.github;

import android.text.TextUtils;

import com.google.gson.Gson;
import com.surcumference.fingerprint.BuildConfig;
import com.surcumference.fingerprint.bean.PluginType;
import com.surcumference.fingerprint.Lang;
import com.surcumference.fingerprint.R;
import com.surcumference.fingerprint.bean.UpdateInfo;
import com.surcumference.fingerprint.network.inf.UpdateResultListener;
import com.surcumference.fingerprint.network.ModuleNetwork;
import com.surcumference.fingerprint.util.Task;
import android.os.SystemClock;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import com.surcumference.fingerprint.network.update.BaseUpdateChecker;
import com.surcumference.fingerprint.network.update.github.bean.GithubAssetsInfo;
import com.surcumference.fingerprint.network.update.github.bean.GithubLatestInfo;
import com.surcumference.fingerprint.util.DateUtils;
import com.surcumference.fingerprint.util.StringUtils;

import java.io.IOException;
import java.util.Date;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Created by Jason on 2017/9/9.
 */
public class GithubUpdateChecker extends BaseUpdateChecker {

    public final OkHttpClient mHttpClient;
    private final String mLocalVersion;
    private final String[] mUpdateUrls;
    private final PluginType mPluginType;
    private final AtomicLong generation = new AtomicLong();

    public GithubUpdateChecker(String localVersion, String[] updateUrls, PluginType type, UpdateResultListener listener) {
        super(listener);
        if (updateUrls.length == 0) {
            throw new IllegalArgumentException("Error: expected update urls, got zero");
        }
        this.mLocalVersion = localVersion;
        this.mUpdateUrls = updateUrls.clone();
        this.mPluginType = type;
        this.mHttpClient = ModuleNetwork.client();
    }

    @Override
    public void doUpdateCheck() {
        long token = generation.incrementAndGet();
        Task.onBackground(() -> {
            long deadline = SystemClock.elapsedRealtime() + 60000;
            Exception failure = new IOException("Update check timed out");
            for (String url : mUpdateUrls) {
                if (generation.get() != token) return;
                long remaining = deadline - SystemClock.elapsedRealtime();
                if (remaining <= 0) break;
                try {
                    Call call = mHttpClient.newCall(new Request.Builder().url(url).build());
                    call.timeout().timeout(remaining, TimeUnit.MILLISECONDS);
                    try (Response response = call.execute()) {
                        if (!response.isSuccessful()) {
                            String body = response.body() == null ? "" : response.peekBody(4096).string();
                            throw new IOException("HTTP " + response.code() + ": " + body);
                        }
                        if (response.body() == null) throw new IOException("Empty update response");
                        GithubLatestInfo info = new Gson().fromJson(response.body().string(), GithubLatestInfo.class);
                        if (info == null || !info.isDataComplete(mPluginType)) throw new IOException("Incomplete update data");
                        if (generation.get() != token) return;
                        if (BuildConfig.DEBUG || StringUtils.isAppNewVersion(mLocalVersion, info.version)) {
                            GithubAssetsInfo asset = info.getDownloadAssetsInfo(mPluginType);
                            UpdateInfo update = new UpdateInfo(info.version,
                                    appendUpdateExtInfo(info.content, info.date, info.contentUrl),
                                    asset.url, asset.name, asset.size);
                            update.digest = asset.digest;
                            onHasUpdate(update);
                        } else onNoUpdate();
                        return;
                    }
                } catch (Exception error) { failure = error; }
            }
            if (generation.get() == token) onNetErr(failure);
        });
    }

    private String appendUpdateExtInfo(String content, Date date, String pageUrl) {
        StringBuilder sb = new StringBuilder(content);
        if (date != null) {
            sb.append("\n");
            if (TextUtils.isEmpty(pageUrl)) {
                sb.append("\n");
            } else {
                sb.append("<a href='");
                sb.append(pageUrl.replaceAll("http(s)*://", ""));
                sb.append("'>");
                sb.append(Lang.getString(R.id.goto_update_page));
                sb.append("</a>");
                sb.append("\n");
            }
            sb.append(Lang.getString(R.id.update_time)).append(": ").append(DateUtils.toString(date));
        }
        return sb.toString();
    }

}
