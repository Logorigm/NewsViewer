package com.newshub.app.data.repo;

/** Observable refresh status for the UI. */
public final class RefreshState {
    public final boolean running;
    /** Last finished run, or null if there was none in this process. */
    public final RefreshSummary last;

    private RefreshState(boolean running, RefreshSummary last) {
        this.running = running;
        this.last = last;
    }

    public static RefreshState idle(RefreshSummary last) {
        return new RefreshState(false, last);
    }

    public static RefreshState running(RefreshSummary previous) {
        return new RefreshState(true, previous);
    }
}
