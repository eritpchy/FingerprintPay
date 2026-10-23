package com.surcumference.fingerprint.network;

import android.content.Context;
import com.hjq.http.EasyConfig;
import com.hjq.http.config.IRequestHandler;
import com.hjq.http.config.IRequestServer;
import com.hjq.http.request.HttpRequest;
import com.surcumference.fingerprint.util.ApplicationUtils;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttp;
import okhttp3.OkHttpClient;
import okhttp3.Response;

public final class ModuleNetwork {
    private static OkHttpClient client;
    private ModuleNetwork() {}
    public static synchronized OkHttpClient client() {
        if (client == null) {
            Context context = ApplicationUtils.getApplication();
            OkHttp.INSTANCE.initialize(context);
            EmbeddedPublicSuffixList.install();
            if (!"github.com".equals(okhttp3.HttpUrl.get("https://api.github.com/").topPrivateDomain())) {
                throw new IllegalStateException("Module public suffix database is unavailable");
            }
            client = new OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS).retryOnConnectionFailure(false)
                    .addInterceptor(chain -> {
                        DownloadCoordinator.Gate gate = chain.request().tag(DownloadCoordinator.Gate.class);
                        if (gate != null) gate.beforeCall(chain.call());
                        Response response = chain.proceed(chain.request());
                        if (gate != null) return gate.response(response);
                        return response;
                    }).build();
            EasyConfig.with(client).setServer((IRequestServer) () -> "https://api.github.com/")
                    .setHandler(HANDLER).setRetryCount(0).setLogEnabled(false).into();
        }
        return client;
    }
    public static final IRequestHandler HANDLER = new IRequestHandler() {
        @Override public Object requestSuccess(HttpRequest<?> request, Response response, Type type) throws IOException {
            throw new IOException("Only download requests are supported by EasyHttp");
        }
        @Override public Throwable requestFail(HttpRequest<?> request, Throwable error) { return error; }
    };
}
