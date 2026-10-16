package com.newshub.app.data.net;

import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.HeaderMap;
import retrofit2.http.Streaming;
import retrofit2.http.Url;

/** Generic GET used for feeds, websites (feed discovery) and Telegram preview pages. */
public interface FeedApi {

    @GET
    @Streaming
    Call<ResponseBody> get(@Url String url, @HeaderMap Map<String, String> headers);
}
