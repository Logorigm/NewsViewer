package com.newshub.app.ui.common;

import android.content.Context;

import com.newshub.app.R;
import com.newshub.app.data.source.ProviderResult;

/** User-facing description of a source error. */
public final class ErrorText {

    private ErrorText() {
    }

    public static String describe(Context context, String kindName, String detail) {
        ProviderResult.ErrorKind kind = null;
        if (kindName != null) {
            try {
                kind = ProviderResult.ErrorKind.valueOf(kindName);
            } catch (IllegalArgumentException ignored) {
                // unknown value stored by an older version
            }
        }
        int res;
        if (kind == null) {
            res = R.string.error_unknown;
        } else {
            switch (kind) {
                case NETWORK:
                    res = R.string.error_kind_network;
                    break;
                case TIMEOUT:
                    res = R.string.error_kind_timeout;
                    break;
                case HTTP:
                    res = R.string.error_kind_http;
                    break;
                case RATE_LIMITED:
                    res = R.string.error_kind_rate_limited;
                    break;
                case PARSE:
                    res = R.string.error_kind_parse;
                    break;
                case NOT_A_FEED:
                    res = R.string.error_kind_not_a_feed;
                    break;
                case TOO_LARGE:
                    res = R.string.error_kind_too_large;
                    break;
                case CONFIG:
                    res = R.string.error_kind_config;
                    break;
                case DISABLED:
                case UNSUPPORTED:
                default:
                    res = R.string.error_kind_unsupported;
                    break;
            }
        }
        String base = context.getString(res);
        if (detail == null || detail.trim().isEmpty() || detail.equals(base)) return base;
        return base + ": " + detail;
    }
}
