package com.surcumference.fingerprint.bean;

public class UpdateInfo {
    public String version;
    public String content;
    public String pageUrl;
    public String url;
    public String name;
    public long size;
    public String digest;

    public UpdateInfo(String version, String content, String pageUrl, String url, String name, long size) {
        this.version = version;
        this.content = content;
        this.pageUrl = pageUrl;
        this.url = url;
        this.name = name;
        this.size = size;
    }

    public UpdateInfo() {
    }
}
