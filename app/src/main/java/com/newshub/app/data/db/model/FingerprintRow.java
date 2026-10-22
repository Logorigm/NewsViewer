package com.newshub.app.data.db.model;

/** Minimal columns needed to rebuild duplicate-detection fingerprints. */
public class FingerprintRow {
    public long id;
    public String sourceId;
    public String url;
    public String title;
    public String summary;
    public long publishedAt;
    public long clusterId;
}
