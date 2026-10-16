package com.newshub.app.data.db.model;

/** Columns used to aggregate story statistics. */
public class MemberRow {
    public String sourceId;
    public long publishedAt;
    public int categoryMask;
    public String primaryCategory;
    public int politicsScore;
    public boolean political;
}
