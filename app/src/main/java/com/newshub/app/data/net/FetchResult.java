package com.newshub.app.data.net;

/** Outcome of one HTTP GET, already mapped to the cases the app distinguishes. */
public final class FetchResult {

    public enum Status {
        OK,
        NOT_MODIFIED,
        HTTP_ERROR,
        RATE_LIMITED,
        TIMEOUT,
        NETWORK_ERROR,
        TOO_LARGE
    }

    public final Status status;
    public final int httpCode;
    public final byte[] body;
    public final String charset;
    public final String contentType;
    public final String etag;
    public final String lastModified;
    public final long retryAfterMillis;
    public final String finalUrl;
    public final String message;

    private FetchResult(Status status, int httpCode, byte[] body, String charset, String contentType, String etag,
                        String lastModified, long retryAfterMillis, String finalUrl, String message) {
        this.status = status;
        this.httpCode = httpCode;
        this.body = body;
        this.charset = charset;
        this.contentType = contentType;
        this.etag = etag;
        this.lastModified = lastModified;
        this.retryAfterMillis = retryAfterMillis;
        this.finalUrl = finalUrl;
        this.message = message;
    }

    public static FetchResult ok(int code, byte[] body, String charset, String contentType, String etag,
                                 String lastModified, String finalUrl) {
        return new FetchResult(Status.OK, code, body, charset, contentType, etag, lastModified, 0, finalUrl, null);
    }

    public static FetchResult notModified(String etag, String lastModified, String finalUrl) {
        return new FetchResult(Status.NOT_MODIFIED, 304, null, null, null, etag, lastModified, 0, finalUrl, null);
    }

    public static FetchResult error(Status status, int code, long retryAfterMillis, String message) {
        return new FetchResult(status, code, null, null, null, null, null, retryAfterMillis, null, message);
    }

    public boolean isOk() {
        return status == Status.OK;
    }
}
