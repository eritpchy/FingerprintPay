package com.surcumference.fingerprint.network.update.github.bean;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.surcumference.fingerprint.bean.PluginType;
import com.surcumference.fingerprint.util.log.L;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by Jason on 2017/9/10.
 */

public class GithubLatestInfo {


    @SerializedName("html_url")
    public String contentUrl;

    @SerializedName("name")
    public String version;

    @SerializedName("body")
    public String content;

    @SerializedName("created_at")
    public Date date;


    public List<GithubAssetsInfo> assets = new ArrayList<>();

    @Nullable
    public GithubAssetsInfo getDownloadAssetsInfo(PluginType pluginType) {
        L.d("assets", new Gson().toJson(assets));
        if (assets.size() == 0) {
            return null;
        }
        String assetsMatchRegexRule = "^" + pluginType.name() + (pluginType == PluginType.Zygisk ? ".+all.+zip$" : ".+apk$");
        assetsMatchRegexRule = assetsMatchRegexRule.toLowerCase();
        L.d("assetsMatchRegexRule", assetsMatchRegexRule);
        for (GithubAssetsInfo asset : assets) {
            if (asset == null || TextUtils.isEmpty(asset.name) || TextUtils.isEmpty(asset.url)) {
                continue;
            }

            if (asset.name.toLowerCase().matches(assetsMatchRegexRule)) {
                return asset;
            }
        }
        return null;
    }

    public boolean isDataComplete(PluginType type) {
        GithubAssetsInfo downloadAssetsInfo = getDownloadAssetsInfo(type);
        if (downloadAssetsInfo == null) {
            L.d("downloadAssetsInfo == null");
            return false;
        }
        if (TextUtils.isEmpty(downloadAssetsInfo.url)) {
            L.d("url is empty");
            return false;
        }
        if (TextUtils.isEmpty(version)) {
            L.d("version is empty");
            return false;
        }
        if (TextUtils.isEmpty(contentUrl)) {
            L.d("contentUrl is empty");
            return false;
        }
        if (TextUtils.isEmpty(content)) {
            L.d("content is empty");
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}
