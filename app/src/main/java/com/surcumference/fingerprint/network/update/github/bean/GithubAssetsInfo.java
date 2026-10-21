package com.surcumference.fingerprint.network.update.github.bean;

import com.google.gson.annotations.SerializedName;

/**
 * Created by Jason on 2017/9/10.
 */

public class GithubAssetsInfo {

    public String name;

    @SerializedName("browser_download_url")
    public String url;

    public long size;
    public String digest;
}
