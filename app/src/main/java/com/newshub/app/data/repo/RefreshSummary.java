package com.newshub.app.data.repo;

import java.util.ArrayList;
import java.util.List;

/** Result of one refresh run over all due sources. */
public final class RefreshSummary {
    public boolean skipped;
    public int sourcesChecked;
    public int sourcesOk;
    public int sourcesNotModified;
    public int sourcesFailed;
    public int networkFailures;
    public int newArticles;
    public int newStories;
    public final List<Long> importantClusterIds = new ArrayList<>();
    public final List<String> failedSources = new ArrayList<>();
    public final long finishedAt;

    public RefreshSummary(long finishedAt) {
        this.finishedAt = finishedAt;
    }

    public static RefreshSummary skippedRun(long now) {
        RefreshSummary s = new RefreshSummary(now);
        s.skipped = true;
        return s;
    }

    /** Every checked source failed with a connectivity problem: the device is probably offline. */
    public boolean looksOffline() {
        return sourcesChecked > 0 && networkFailures == sourcesChecked;
    }
}
