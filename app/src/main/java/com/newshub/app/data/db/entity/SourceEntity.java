package com.newshub.app.data.db.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** A configured news source and its last fetch status. */
@Entity(tableName = "sources")
public class SourceEntity {
    @PrimaryKey
    @NonNull
    public String id = "";
    @NonNull
    public String name = "";
    /** {@link com.newshub.app.domain.SourceType} name. */
    @NonNull
    public String type = "RSS";
    /** Feed URL, website URL (feed is auto-discovered) or Telegram channel name. */
    @NonNull
    public String address = "";
    @Nullable
    public String homepage;
    /** {@link com.newshub.app.domain.Category} key. */
    @NonNull
    public String category = "world";
    public boolean enabled = true;
    public boolean builtIn;
    /** A built-in source the user removed; kept so that it is not re-added on the next start. */
    public boolean deleted;
    /** {@link com.newshub.app.domain.VerificationLevel} name. */
    @NonNull
    public String verification = "UNVERIFIED";
    @Nullable
    public String note;
    /** Feed URL found on the website (for sources configured with a website address). */
    @Nullable
    public String discoveredFeedUrl;
    @Nullable
    public String etag;
    @Nullable
    public String lastModified;
    public long lastFetchAt;
    public long lastSuccessAt;
    /** {@link com.newshub.app.data.source.ProviderResult.ErrorKind} name, null when the last fetch succeeded. */
    @Nullable
    public String lastErrorKind;
    @Nullable
    public String lastError;
    public int consecutiveFailures;
    /** Backoff / rate limit: automatic refreshes skip the source until this time. */
    public long nextAllowedFetchAt;
    public int lastItemCount;
    public int sortOrder;
    public long createdAt;

    public SourceEntity copy() {
        SourceEntity c = new SourceEntity();
        c.id = id;
        c.name = name;
        c.type = type;
        c.address = address;
        c.homepage = homepage;
        c.category = category;
        c.enabled = enabled;
        c.builtIn = builtIn;
        c.deleted = deleted;
        c.verification = verification;
        c.note = note;
        c.discoveredFeedUrl = discoveredFeedUrl;
        c.etag = etag;
        c.lastModified = lastModified;
        c.lastFetchAt = lastFetchAt;
        c.lastSuccessAt = lastSuccessAt;
        c.lastErrorKind = lastErrorKind;
        c.lastError = lastError;
        c.consecutiveFailures = consecutiveFailures;
        c.nextAllowedFetchAt = nextAllowedFetchAt;
        c.lastItemCount = lastItemCount;
        c.sortOrder = sortOrder;
        c.createdAt = createdAt;
        return c;
    }
}
