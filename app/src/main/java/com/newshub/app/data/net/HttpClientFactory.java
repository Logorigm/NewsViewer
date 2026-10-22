package com.newshub.app.data.net;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Retrofit;

/** Shared HTTP stack. No API keys or user identifiers are sent anywhere. */
public final class HttpClientFactory {

    private HttpClientFactory() {
    }

    public static final String USER_AGENT = "NewsHub/1.0 (Android; RSS reader)";

    public static OkHttpClient createClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .callTimeout(45, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    Request.Builder b = original.newBuilder();
                    if (original.header("User-Agent") == null) b.header("User-Agent", USER_AGENT);
                    if (original.header("Accept") == null) {
                        b.header("Accept", "application/rss+xml, application/atom+xml, application/xml;q=0.9, "
                                + "text/xml;q=0.9, text/html;q=0.8, */*;q=0.5");
                    }
                    if (original.header("Accept-Language") == null) b.header("Accept-Language", "ru,en;q=0.8");
                    return chain.proceed(b.build());
                })
                .build();
    }

    public static FeedApi createApi(OkHttpClient client) {
        return new Retrofit.Builder()
                .baseUrl("https://localhost/") // every call passes an absolute @Url
                .client(client)
                .build()
                .create(FeedApi.class);
    }
}
