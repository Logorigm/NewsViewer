package com.newshub.app.data.source;

import com.newshub.app.data.db.entity.SourceEntity;

/** One entry of the built-in source catalog (assets/sources.json). */
public final class CatalogSource {
    public String id;
    public String name;
    public String type;
    public String address;
    public String homepage;
    public String category;
    public boolean enabledByDefault;
    public String verification;
    public String note;

    public SourceEntity toEntity(int sortOrder, long now) {
        SourceEntity e = new SourceEntity();
        e.id = id;
        e.name = name;
        e.type = type;
        e.address = address == null ? "" : address;
        e.homepage = homepage;
        e.category = category;
        e.enabled = enabledByDefault && !e.address.isEmpty();
        e.builtIn = true;
        e.verification = verification;
        e.note = note;
        e.sortOrder = sortOrder;
        e.createdAt = now;
        return e;
    }
}
