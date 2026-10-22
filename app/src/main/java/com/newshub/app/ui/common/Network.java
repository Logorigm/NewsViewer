package com.newshub.app.ui.common;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;

/** Connectivity checks used for image policy and offline hints. */
public final class Network {

    private Network() {
    }

    private static NetworkCapabilities capabilities(Context context) {
        ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
        if (cm == null) return null;
        android.net.Network n = cm.getActiveNetwork();
        return n == null ? null : cm.getNetworkCapabilities(n);
    }

    public static boolean isOnline(Context context) {
        NetworkCapabilities caps = capabilities(context);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    public static boolean isUnmetered(Context context) {
        NetworkCapabilities caps = capabilities(context);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
    }

    /** Image policy: "always", "wifi" (unmetered networks only) or "never". */
    public static boolean shouldLoadImages(Context context, String policy) {
        if ("never".equals(policy)) return false;
        if ("wifi".equals(policy)) return isUnmetered(context);
        return true;
    }
}
