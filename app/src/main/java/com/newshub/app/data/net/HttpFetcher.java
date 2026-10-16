package com.newshub.app.data.net;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLException;

import okhttp3.MediaType;
import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * Downloads documents with conditional requests, size limits, one retry for transient network
 * errors and Retry-After handling. Never called on the main thread.
 */
public class HttpFetcher {

    /** Feeds larger than this are rejected (protects memory and mobile data). */
    public static final int MAX_BODY_BYTES = 5 * 1024 * 1024;
    private static final long RETRY_DELAY_MILLIS = 1500;
    private static final long DEFAULT_RATE_LIMIT_MILLIS = 30 * 60_000L;

    private final FeedApi api;
    private final long retryDelayMillis;

    public HttpFetcher(FeedApi api) {
        this(api, RETRY_DELAY_MILLIS);
    }

    public HttpFetcher(FeedApi api, long retryDelayMillis) {
        this.api = api;
        this.retryDelayMillis = retryDelayMillis;
    }

    public FetchResult get(String url, String etag, String lastModified) {
        Map<String, String> headers = new HashMap<>();
        if (etag != null && !etag.isEmpty()) headers.put("If-None-Match", etag);
        if (lastModified != null && !lastModified.isEmpty()) headers.put("If-Modified-Since", lastModified);
        FetchResult first = attempt(url, headers);
        if (first.status == FetchResult.Status.TIMEOUT || first.status == FetchResult.Status.NETWORK_ERROR
                || (first.status == FetchResult.Status.HTTP_ERROR && first.httpCode >= 500)) {
            if (!sleep(retryDelayMillis)) return first;
            return attempt(url, headers);
        }
        return first;
    }

    private FetchResult attempt(String url, Map<String, String> headers) {
        Response<ResponseBody> response;
        try {
            response = api.get(url, headers).execute();
        } catch (SocketTimeoutException e) {
            return FetchResult.error(FetchResult.Status.TIMEOUT, 0, 0, "Превышено время ожидания ответа");
        } catch (InterruptedIOException e) {
            return FetchResult.error(FetchResult.Status.TIMEOUT, 0, 0, "Запрос прерван по тайм-ауту");
        } catch (UnknownHostException e) {
            return FetchResult.error(FetchResult.Status.NETWORK_ERROR, 0, 0, "Нет соединения или адрес не найден");
        } catch (SSLException e) {
            return FetchResult.error(FetchResult.Status.NETWORK_ERROR, 0, 0, "Ошибка защищённого соединения");
        } catch (IOException e) {
            return FetchResult.error(FetchResult.Status.NETWORK_ERROR, 0, 0, "Сетевая ошибка: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return FetchResult.error(FetchResult.Status.HTTP_ERROR, 0, 0, "Некорректный адрес");
        }

        int code = response.code();
        String finalUrl = response.raw().request().url().toString();
        String newEtag = response.headers().get("ETag");
        String newLastModified = response.headers().get("Last-Modified");

        if (code == 304) {
            closeQuietly(response.errorBody());
            closeQuietly(response.body());
            return FetchResult.notModified(newEtag, newLastModified, finalUrl);
        }
        if (!response.isSuccessful()) {
            closeQuietly(response.errorBody());
            if (code == 429 || code == 503) {
                long retryAfter = parseRetryAfter(response.headers().get("Retry-After"), System.currentTimeMillis());
                if (code == 429 || retryAfter > 0) {
                    return FetchResult.error(FetchResult.Status.RATE_LIMITED, code,
                            retryAfter > 0 ? retryAfter : DEFAULT_RATE_LIMIT_MILLIS, "Слишком много запросов (HTTP " + code + ")");
                }
            }
            return FetchResult.error(FetchResult.Status.HTTP_ERROR, code, 0, "Ошибка сервера: HTTP " + code);
        }

        ResponseBody body = response.body();
        if (body == null) {
            return FetchResult.ok(code, new byte[0], null, null, newEtag, newLastModified, finalUrl);
        }
        try {
            long declared = body.contentLength();
            if (declared > MAX_BODY_BYTES) {
                return FetchResult.error(FetchResult.Status.TOO_LARGE, code, 0, "Ответ слишком большой");
            }
            byte[] bytes = readLimited(body.byteStream());
            if (bytes == null) {
                return FetchResult.error(FetchResult.Status.TOO_LARGE, code, 0, "Ответ слишком большой");
            }
            MediaType type = body.contentType();
            String charset = type != null && type.charset() != null ? type.charset().name() : null;
            String contentType = type != null ? type.type() + "/" + type.subtype() : null;
            return FetchResult.ok(code, bytes, charset, contentType, newEtag, newLastModified, finalUrl);
        } catch (SocketTimeoutException e) {
            return FetchResult.error(FetchResult.Status.TIMEOUT, code, 0, "Превышено время ожидания ответа");
        } catch (IOException e) {
            return FetchResult.error(FetchResult.Status.NETWORK_ERROR, code, 0, "Обрыв соединения: " + e.getMessage());
        } finally {
            closeQuietly(body);
        }
    }

    private static byte[] readLimited(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        long total = 0;
        int r;
        while ((r = in.read(buf)) != -1) {
            total += r;
            if (total > MAX_BODY_BYTES) return null;
            out.write(buf, 0, r);
        }
        return out.toByteArray();
    }

    /** Retry-After is either delay-seconds or an HTTP date. */
    static long parseRetryAfter(String value, long now) {
        if (value == null) return 0;
        String v = value.trim();
        if (v.isEmpty()) return 0;
        try {
            long seconds = Long.parseLong(v);
            return Math.max(0, Math.min(seconds, 24 * 3600)) * 1000L;
        } catch (NumberFormatException ignored) {
            // not a number
        }
        try {
            long at = ZonedDateTime.parse(v, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli();
            return Math.max(0, at - now);
        } catch (Exception e) {
            return 0;
        }
    }

    private static boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void closeQuietly(ResponseBody body) {
        if (body != null) body.close();
    }
}
